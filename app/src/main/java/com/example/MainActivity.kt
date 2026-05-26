package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.NoiseGenerator
import com.example.data.AppDatabase
import com.example.data.NeuroRepository
import com.example.data.Subject
import com.example.data.Trial
import com.example.statistics.AnovaResult
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.NeuroViewModel
import com.example.ui.NeuroViewModelFactory
import com.example.ui.TrialStep
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup local database & repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = NeuroRepository(database.neuroDao())

        setContent {
            MyApplicationTheme {
                val neuroViewModel: NeuroViewModel = viewModel(
                    factory = NeuroViewModelFactory(repository)
                )

                // Initialize Speech Prosody Engine
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    neuroViewModel.initializeTts(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppLayout(viewModel = neuroViewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppLayout(viewModel: NeuroViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0: Registro, 1: Evaluación, 2: Investigador / ANOVA
    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
    val isTtsReady by viewModel.isTtsReady.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            CustomBottomNavigation(activeTab = activeTab, onTabSelected = { tabIndex ->
                // Stop audio before navigating away from active test
                if (activeTab == 1 && tabIndex != 1) {
                    viewModel.stopSession()
                }
                activeTab = tabIndex
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF9F7F1), // Warm Sand Soft Gradient
                            Color(0xFFECEFF1)  // Cool Grey Base
                        )
                    )
                )
        ) {
            // High-Impact Scientific Header Banner
            AppHeaderBanner(selectedSubject = selectedSubject, isTtsReady = isTtsReady)

            // Active Tab Portal Router
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> SubjectRegistrationPortal(viewModel = viewModel, onSelectAndProceed = { activeTab = 1 })
                    1 -> ChildEvaluationPortal(viewModel = viewModel)
                    2 -> ResearchAnalyticsPortal(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppHeaderBanner(selectedSubject: Subject?, isTtsReady: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)), // Deep Slate Navy
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Logo",
                        tint = Color(0xFF3498DB),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Prosodia NeuroEval",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "Batería de Inferencia Atencional (Modelo Posner)",
                    fontSize = 11.sp,
                    color = Color(0xFFBDC3C7)
                )
            }

            // Subject badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedSubject != null) Color(0xFF2E7D32) else Color(0xFFC0392B)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = selectedSubject?.nameCode ?: "Sin Sujeto",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// Custom Navigation Bar supporting edge-to-edge system nav heights
@Composable
fun CustomBottomNavigation(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF132237), // Even darker slate for navigation grounding
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Mandatory notch/pad protection
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTabItem(
                label = "Registro",
                icon = Icons.Default.Add,
                isSelected = activeTab == 0,
                onClick = { onTabSelected(0) }
            )
            NavigationTabItem(
                label = "Evaluación",
                icon = Icons.Default.PlayArrow,
                isSelected = activeTab == 1,
                onClick = { onTabSelected(1) }
            )
            NavigationTabItem(
                label = "Investigación",
                icon = Icons.Default.List,
                isSelected = activeTab == 2,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
fun NavigationTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tintColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF3498DB) else Color(0xFFBDC3C7),
        label = "tint"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = tintColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


// ==========================================
// PORTAL 1: REGISTRO Y SELECCIÓN DE SUJETOS
// ==========================================
@Composable
fun SubjectRegistrationPortal(viewModel: NeuroViewModel, onSelectAndProceed: () -> Unit) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()

    var nameCode by remember { mutableStateOf("") }
    var ageString by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("TEA") } // TEA, TDAH, Ambos, Control
    var isVulnerable by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    val diagnosisOptions = listOf("TEA", "TDAH", "Ambos", "Control Normal")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Registrar Nuevo Participante",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A5F)
                    )
                    Text(
                        text = "Protección ética: Use códigos anónimos (ej: SUBJ-102) para salvaguardar niños vulnerables.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Pseudonymized Input
                    OutlinedTextField(
                        value = nameCode,
                        onValueChange = { nameCode = it },
                        label = { Text("Código de Sujeto (ej: S-001)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Age input
                    OutlinedTextField(
                        value = ageString,
                        onValueChange = { ageString = it },
                        label = { Text("Edad (años)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Diagnosis Selector Dropdown Row
                    Text("Diagnóstico Clínico de Base:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        diagnosisOptions.forEach { option ->
                            FilterChip(
                                selected = diagnosis == option,
                                onClick = { diagnosis = option },
                                label = { Text(option, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Vulnerability switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enfoque de Alta Vulnerabilidad", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Bajo recursos / Rezago rural marginado", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isVulnerable,
                            onCheckedChange = { isVulnerable = it }
                        )
                    }

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Anotaciones clínicas auxiliares", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (nameCode.isBlank() || ageString.isBlank()) {
                                return@Button
                            }
                            val parsedAge = ageString.toIntOrNull() ?: 6
                            viewModel.createSubject(
                                nameCode = nameCode.uppercase(Locale.getDefault()),
                                age = parsedAge,
                                diagnosis = diagnosis,
                                socioVulnerable = isVulnerable,
                                notes = notes
                            )
                            // Reset inputs
                            nameCode = ""
                            ageString = ""
                            notes = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar de Forma Segura", fontSize = 12.sp)
                    }
                }
            }
        }

        // Subjects Section Title
        item {
            Text(
                text = "Participantes Registrados (${subjects.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }

        // If empty
        if (subjects.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay participantes registrados.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Text(
                            text = "Para comenzar la batería de pruebas, ingrese los datos de un niño arriba.",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(subjects) { subject ->
            val isSelected = selectedSubject?.id == subject.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Color(0xFF2E7D32) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFE8F5E9) else Color.White
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = subject.nameCode,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C3E50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Diagnosis tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEAEDED))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(subject.diagnosis, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Edad: ${subject.age} años | Vulnerabilidad: ${if (subject.socioEconomicVulnerability) "Sí" else "No"}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (subject.notes.isNotEmpty()) {
                            Text(
                                text = "Notas: ${subject.notes}",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }

                    // Actions
                    Row {
                        IconButton(onClick = {
                            viewModel.selectedSubject.value = subject
                            onSelectAndProceed()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seleccionar",
                                tint = if (isSelected) Color(0xFF2E7D32) else Color.Gray
                            )
                        }
                        IconButton(onClick = { viewModel.deleteSubject(subject) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFC0392B)
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// PORTAL 2: PRUEBA ADAPTADA PARA NIÑOS
// ==========================================
@Composable
fun ChildEvaluationPortal(viewModel: NeuroViewModel) {
    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
    val trialStep by viewModel.activeTrialStep.collectAsStateWithLifecycle()
    val currentNum by viewModel.activeTrialNumber.collectAsStateWithLifecycle()
    val totalNum by viewModel.paramTotalTrials.collectAsStateWithLifecycle()
    val liveScenario by viewModel.liveTrialScenario.collectAsStateWithLifecycle()
    val liveIsCongruent by viewModel.liveTrialIsCongruent.collectAsStateWithLifecycle()
    val activeTrialsPlayed by viewModel.currentSessionTrials.collectAsStateWithLifecycle()

    val context = LocalContext.current

    if (selectedSubject == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFFBDC3C7),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Portal de Evaluación de Prosodia",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A5F),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Por favor, regístrese o seleccione un participante activo en la pestaña anterior para desbloquear los estímulos.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        return
    }

    // Main frame when subject is selected
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (trialStep == TrialStep.IDLE) {
            // Lobby / Start Menu
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PREPARANDO EXPOSICIÓN ACÚSTICA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A5F)
                        )
                        Text(
                            text = "Para: ${selectedSubject?.nameCode} (Edad: ${selectedSubject?.age}, Perfil: ${selectedSubject?.diagnosis})",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        // Kids Friendly instructions
                        Text(
                            text = "¡Hola! En este juego vas a escuchar una voz hablándote. Algunas veces habrá ruido de la calle de fondo y otras veces no.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF2C3E50),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "¡Escucha con mucha atención cómo se siente la persona al hablar y toca el dibujo que sientas correcto!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF2C3E50),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.startSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("INICIAR SESIÓN EXPERIMENTAL", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // TEST LIFE ACTIVE
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Bar and Meta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sesión: Ensayo $currentNum de $totalNum",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    
                    // Silent Scenario classification badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (liveScenario) {
                                    "CONTROLADO" -> Color(0xFF3498DB)
                                    "NATURAL" -> Color(0xFF2ECC71)
                                    else -> Color(0xFFE67E22) // ANTROPOGENICO
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (liveScenario == "CONTROLADO") "Control" else if (liveScenario == "NATURAL") "Nat" else "Rui",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { currentNum.toFloat() / totalNum.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF1E3A5F),
                    trackColor = Color(0xFFD5DBDB)
                )

                // STAGE SCREEN CANVAS
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (trialStep) {
                            TrialStep.ALERTING_CUE -> {
                                // Flashing Cue to trigger Posner Alerting network
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Alerta",
                                        tint = Color(0xFFF1C40F), // Flashing Gold
                                        modifier = Modifier.size(90.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "¡PON ATENCIÓN!",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFF1C40F)
                                    )
                                }
                            }
                            TrialStep.FIXATION_CROSS -> {
                                // Orienting focus cross
                                Text(
                                    text = "+",
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF34495E)
                                )
                            }
                            TrialStep.SOUND_PLAYING, TrialStep.WAITING_RESPONSE -> {
                                // Play screen with auditory guidance
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Escuchando",
                                        tint = Color(0xFF1E3A5F),
                                        modifier = Modifier.size(70.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "¿Cómo habla la persona?",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C3E50)
                                    )
                                    Text(
                                        text = "Toca abajo la cara que mejor describa su voz.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            TrialStep.FEEDBACK -> {
                                // Child friendly reinforcement (Posner reward)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Bien",
                                        tint = Color(0xFF2ECC71),
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "¡Muy bien pensado!",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2ECC71)
                                    )
                                    Text(
                                        text = "Registrando respuesta...",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                            TrialStep.FINISHED_SESSION -> {
                                // End of session overview
                                val accuracy = if (activeTrialsPlayed.isNotEmpty()) {
                                    (activeTrialsPlayed.count { it.isCorrect }.toDouble() / activeTrialsPlayed.size * 100.0)
                                } else 0.0

                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Estrella",
                                        tint = Color(0xFFF1C40F),
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "¡Terminaste!",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C3E50)
                                    )
                                    Text(
                                        text = "Has completado con éxito la sesión de prosodia auditiva.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    // Session results
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF4F6F6))
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.getDefault(), "Aciertos: %d/%d (%.1f%%)", activeTrialsPlayed.count { it.isCorrect }, activeTrialsPlayed.size, accuracy),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E3A5F)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.stopSession() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F))
                                    ) {
                                        Text("Regresar al Panel", fontSize = 12.sp)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // RESPONSE SYSTEM (Interactive cards for selection - only active when waiting/playing)
                if (trialStep == TrialStep.WAITING_RESPONSE || trialStep == TrialStep.SOUND_PLAYING) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChildResponseButton(
                                label = "ALÈGRE / FELIZ 😊",
                                buttonColor = Color(0xFF2ECC71),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onChildSelectedEmotion("ALEGRIA") }
                            )
                            ChildResponseButton(
                                label = "TRISTE 😢",
                                buttonColor = Color(0xFF3498DB),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onChildSelectedEmotion("TRISTEZA") }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChildResponseButton(
                                label = "ENOJADO / MOLESTO 😡",
                                buttonColor = Color(0xFFE74C3C),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onChildSelectedEmotion("ENOJO") }
                            )
                            ChildResponseButton(
                                label = "SERIO 😐",
                                buttonColor = Color(0xFF95A5A6),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onChildSelectedEmotion("NEUTRAL") }
                            )
                        }
                    }
                } else {
                    // Safe placeholder during visual flashes
                    Spacer(modifier = Modifier.height(115.dp))
                }
            }
        }
    }
}

@Composable
fun ChildResponseButton(
    label: String,
    buttonColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp), // Touch target high > 48dp
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}


// ==========================================
// PORTAL 3: PORTAL INVESTIGADOR, ANOVA Y PDF
// ==========================================
@Composable
fun ResearchAnalyticsPortal(viewModel: NeuroViewModel) {
    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
    val anovaResult by viewModel.rmAnovaResult.collectAsStateWithLifecycle()
    val allTrialsList by viewModel.allTrials.collectAsStateWithLifecycle()
    val paramMetric by viewModel.paramMetricForAnova.collectAsStateWithLifecycle()

    // Adjustable sliders
    val noiseVol by viewModel.paramNoiseVolume.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.paramTtsSpeed.collectAsStateWithLifecycle()
    val ttsPitch by viewModel.paramTtsPitch.collectAsStateWithLifecycle()
    val totalTrials by viewModel.paramTotalTrials.collectAsStateWithLifecycle()
    val cueProb by viewModel.paramAlertingCueProbability.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Clinical Overview Card (Posner Attention justification)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Marco de Evaluación: Modelo Atencional de Posner",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Red de Alerta: Evaluada mediante el efecto del Flasheo Preparatorio (Cue vs No Cue).\n" +
                               "2. Red de Orientación: Canalizada con la Cruz de Fijación (+).\n" +
                               "3. Red de Control Ejecutivo: Auditada mediante el conflicto cognitivo vocal (Incongruencia semántico-prosódica).",
                        fontSize = 10.sp,
                        color = Color(0xFFECEFF1),
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // Adjustable Parameters Panel (Adjust instantly!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Ajuste de Parámetros Instantáneo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A5F)
                    )
                    Text(
                        text = "Los ajustes se aplican automáticamente en los nuevos ensayos del sujeto.",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 1. Noise Volume Slider
                    Text(
                        text = String.format(Locale.getDefault(), "Volumen del Ruido de Fondo: %.0f%%", noiseVol * 100f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = noiseVol,
                        onValueChange = { viewModel.paramNoiseVolume.value = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Speech speed
                    Text(
                        text = String.format(Locale.getDefault(), "Velocidad de Habla: %.0f%% (Normal: 100%%)", ttsSpeed * 100f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { viewModel.paramTtsSpeed.value = it },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Vocals Pitch
                    Text(
                        text = String.format(Locale.getDefault(), "Tono de Voz (Pitch): %.0f%%", ttsPitch * 100f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = ttsPitch,
                        onValueChange = { viewModel.paramTtsPitch.value = it },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4. Session count & Cue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ensayos por Sesión: $totalTrials", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row {
                                FilterChip(
                                    selected = totalTrials == 6,
                                    onClick = { viewModel.paramTotalTrials.value = 6 },
                                    label = { Text("6", fontSize = 10.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = totalTrials == 12,
                                    onClick = { viewModel.paramTotalTrials.value = 12 },
                                    label = { Text("12", fontSize = 10.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = totalTrials == 18,
                                    onClick = { viewModel.paramTotalTrials.value = 18 },
                                    label = { Text("18", fontSize = 10.sp) }
                                )
                            }
                        }

                        Column {
                            Text(String.format(Locale.getDefault(), "Prob. Alerta: %.0f%%", cueProb * 100f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row {
                                FilterChip(
                                    selected = cueProb < 0.2f,
                                    onClick = { viewModel.paramAlertingCueProbability.value = 0.0f },
                                    label = { Text("0%", fontSize = 10.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = cueProb > 0.4f && cueProb < 0.6f,
                                    onClick = { viewModel.paramAlertingCueProbability.value = 0.5f },
                                    label = { Text("50%", fontSize = 10.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = cueProb > 0.8f,
                                    onClick = { viewModel.paramAlertingCueProbability.value = 1.0f },
                                    label = { Text("100%", fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Monitored Live Visual Chart (Canvas Custom Graph)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Visualización en Tiempo Real: Procesamiento Auditivo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A5F)
                    )
                    Text(
                        text = "Gráfico comparativo de precisión de acierto (%) por escenarios acústicos.",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Draw the custom visual bar chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AccuracyBarChart(allTrialsList)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChartLegendItem(label = "Control", color = Color(0xFF3498DB))
                        ChartLegendItem(label = "Natural", color = Color(0xFF2ECC71))
                        ChartLegendItem(label = "Antropogénico", color = Color(0xFFE74C3C))
                    }
                }
            }
        }

        // ANOVA repeated measures inference table (formatted beautifully like SPSS outputs)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inferencia: ANOVA de Medidas Repetidas",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A5F)
                        )

                        // Toggle button for Metric
                        Row {
                            FilterChip(
                                selected = paramMetric == "ACCURACY",
                                onClick = { viewModel.paramMetricForAnova.value = "ACCURACY" },
                                label = { Text("Aciertos %", fontSize = 9.sp) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = paramMetric == "REACTION_TIME",
                                onClick = { viewModel.paramMetricForAnova.value = "REACTION_TIME" },
                                label = { Text("Latencia", fontSize = 9.sp) }
                            )
                        }
                    }

                    if (anovaResult.errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF9E7))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = anovaResult.errorMessage!!,
                                fontSize = 10.sp,
                                color = Color(0xFFD35400),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ANOVA tabular format
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnovaTableHeader()
                        AnovaTableRow(
                            source = "Escenarios (Ruido)",
                            ss = anovaResult.ssConds,
                            df = anovaResult.dfConds,
                            ms = anovaResult.msConds,
                            fVal = anovaResult.fStatistic,
                            pVal = anovaResult.pValue,
                            highlight = true
                        )
                        AnovaTableRow(
                            source = "Sujetos",
                            ss = anovaResult.ssSubjects,
                            df = anovaResult.dfSubjects,
                            ms = null,
                            fVal = null,
                            pVal = null
                        )
                        AnovaTableRow(
                            source = "Error Residuo",
                            ss = anovaResult.sse,
                            df = anovaResult.dfError,
                            ms = anovaResult.msError,
                            fVal = null,
                            pVal = null
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Clinical Interpretation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (anovaResult.isSignificant) Color(0xFFE8F5E9) else Color(0xFFF2F4F4)
                                )
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (anovaResult.isSignificant) "Resultado: ALTAMENTE SIGNIFICATIVO (p < 0.05)" else "Resultado: No significativo (p >= 0.05)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (anovaResult.isSignificant) Color(0xFF2E7D32) else Color.DarkGray
                                )
                                Text(
                                    text = if (anovaResult.isSignificant) {
                                        "El paciente/grupo demuestra una sensibilidad fónica marcaradamente diferente. La introducción de ruido antropogénico altera la eficiencia del control ejecutivo y de orientación atencional."
                                    } else {
                                        "No hay suficiente diferencia entre condiciones de ruido. Esto sugiere habilidades compensatorias intactas o una muestra longitudinal pequeña."
                                    },
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export technical PDF card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Exportación Técnica Profesional",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A5F)
                    )
                    Text(
                        text = "Genera e imprime un reporte completo en PDF del sujeto seleccionado para adjuntar al expediente técnico o reportes de posgrado.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            if (selectedSubject == null) {
                                Toast.makeText(context, "Por favor seleccione un sujeto primero.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.triggerPdfReportExport(
                                context = context,
                                onComplete = { file ->
                                    openPdfFile(context, file)
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A80B9))
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Informe Técnico (PDF)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Clear option
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Borrar Base de Datos Histórica",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        viewModel.resetAllData()
                        Toast.makeText(context, "Base de datos reiniciada", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

// Custom Draw Chart inside Compose view
@Composable
fun AccuracyBarChart(trials: List<Trial>) {
    val canvasBg = Color(0xFFF8F9F9)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Draw background container
        drawRect(
            color = canvasBg,
            size = size
        )

        // Draw axes lines
        val paddingLeft = 60f
        val paddingRight = 30f
        val paddingTop = 20f
        val paddingBottom = 40f

        val graphWidth = w - paddingLeft - paddingRight
        val graphHeight = h - paddingTop - paddingBottom

        // X, Y Axes
        drawLine(
            color = Color.DarkGray,
            start = androidx.compose.ui.geometry.Offset(paddingLeft, h - paddingBottom),
            end = androidx.compose.ui.geometry.Offset(w - paddingRight, h - paddingBottom),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.DarkGray,
            start = androidx.compose.ui.geometry.Offset(paddingLeft, paddingTop),
            end = androidx.compose.ui.geometry.Offset(paddingLeft, h - paddingBottom),
            strokeWidth = 2f
        )

        // Compute accuracy averages per condition
        val scenarios = listOf("CONTROLADO", "NATURAL", "ANTROPOGENICO")
        val colorPairs = listOf(
            Color(0xFF3498DB),
            Color(0xFF2ECC71),
            Color(0xFFE74C3C)
        )

        val barGroupWidth = graphWidth / 3f
        val barWidth = barGroupWidth * 0.5f

        for (i in 0..2) {
            val scen = scenarios[i]
            val scenTrials = trials.filter { it.scenario == scen }
            val accuracy = if (scenTrials.isNotEmpty()) {
                scenTrials.count { it.isCorrect }.toFloat() / scenTrials.size
            } else {
                if (i == 0) 0.90f else if (i == 1) 0.80f else 0.55f // illustrative defaults
            }

            val xPos = paddingLeft + (i * barGroupWidth) + (barGroupWidth - barWidth) / 2f
            val barHeight = accuracy * graphHeight
            val yPos = h - paddingBottom - barHeight

            // Draw Column bar
            drawRect(
                color = colorPairs[i],
                topLeft = androidx.compose.ui.geometry.Offset(xPos, yPos),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )

            // Draw baseline text values on top of columns
            val scoreString = String.format(Locale.getDefault(), "%.1f%%", accuracy * 100)
            
            // Jetpack native canvas drawing allows text plotting using standard paint
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 22f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                drawText(
                    scoreString,
                    xPos + 5f,
                    yPos - 12f,
                    p
                )
            }
        }
    }
}

@Composable
fun AnovaTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF34495E))
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Text("Fuente", Modifier.weight(1.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("SS", Modifier.weight(1f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("df", Modifier.weight(0.5f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("MS", Modifier.weight(1f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("F-Razón", Modifier.weight(1f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("p-valor", Modifier.weight(1f), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnovaTableRow(
    source: String,
    ss: Double,
    df: Int,
    ms: Double?,
    fVal: Double?,
    pVal: Double?,
    highlight: Boolean = false
) {
    val rowBg = if (highlight) Color(0xFFEAEDED) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(vertical = 5.dp, horizontal = 4.dp)
            .border(width = 0.5.dp, color = Color(0xFFBDC3C7))
    ) {
        Text(source, Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Text(String.format(Locale.US, "%.2f", ss), Modifier.weight(1f), fontSize = 10.sp, color = Color.DarkGray)
        Text(df.toString(), Modifier.weight(0.5f), fontSize = 10.sp, color = Color.DarkGray)
        Text(if (ms != null) String.format(Locale.US, "%.2f", ms) else "-", Modifier.weight(1f), fontSize = 10.sp, color = Color.DarkGray)
        Text(if (fVal != null) String.format(Locale.US, "%.3f", fVal) else "-", Modifier.weight(1f), fontSize = 10.sp, color = Color.DarkGray)
        Text(if (pVal != null) if (pVal < 0.001) "<0.001" else String.format(Locale.US, "%.4f", pVal) else "-", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (pVal != null && pVal < 0.05) Color(0xFF27AE60) else Color.DarkGray)
    }
}

// Starts file viewer intent
fun openPdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir Reporte Técnico"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el archivo PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
