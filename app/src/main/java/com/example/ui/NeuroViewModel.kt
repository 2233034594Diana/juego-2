package com.example.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NeuroRepository
import com.example.data.Subject
import com.example.data.Trial
import com.example.audio.SpeechProsodyEngine
import com.example.audio.NoiseGenerator
import com.example.statistics.AnovaEngine
import com.example.statistics.AnovaResult
import com.example.pdf.PdfExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

// Trial States
enum class TrialStep {
    IDLE,
    ALERTING_CUE,    // Posner Alerting Network flash
    FIXATION_CROSS,  // Posner Orienting Network cue
    SOUND_PLAYING,   // TTS Output playing
    WAITING_RESPONSE,// Stopwatch running
    FEEDBACK,        // Mild child-friendly score review
    FINISHED_SESSION
}

class NeuroViewModel(private val repository: NeuroRepository) : ViewModel() {

    // Subjects & Trials from DB
    val subjects: StateFlow<List<Subject>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrials: StateFlow<List<Trial>> = repository.allTrials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App state
    val selectedSubject = MutableStateFlow<Subject?>(null)
    val activeTrialStep = MutableStateFlow(TrialStep.IDLE)
    
    // Test parameters (instantly adjustable by researcher)
    val paramNoiseVolume = MutableStateFlow(0.5f)
    val paramTtsSpeed = MutableStateFlow(1.0f)
    val paramTtsPitch = MutableStateFlow(1.0f)
    val paramTotalTrials = MutableStateFlow(12) // Default total trials per session
    val paramAlertingCueProbability = MutableStateFlow(0.5f) // Prob of showing alerting star
    val paramMetricForAnova = MutableStateFlow("ACCURACY") // "ACCURACY" or "REACTION_TIME"

    // Active session live telemetry
    val currentSessionTrials = MutableStateFlow<List<Trial>>(emptyList())
    val activeTrialNumber = MutableStateFlow(0)
    
    // Live trial state variables (displayed interactively to researcher)
    val liveTrialSentence = MutableStateFlow("")
    val liveTrialEmotion = MutableStateFlow("")
    val liveTrialIsCongruent = MutableStateFlow(true)
    val liveTrialIsCuePresented = MutableStateFlow(false)
    val liveTrialScenario = MutableStateFlow("CONTROLADO") // CONTROLADO, NATURAL, ANTROPOGENICO

    // Time keeping
    private var trialStartTime: Long = 0

    // Engines
    private var prosodyEngine: SpeechProsodyEngine? = null
    private val noiseGenerator = NoiseGenerator()

    // Sound initialization status
    val isTtsReady = MutableStateFlow(false)

    // Diagnostic list of clinical Spanish sentences structured by semantic valence
    private val neutralSentences = listOf(
        "El pizarrón verde del aula tiene letras escitas.",
        "Tengo dos cuadernos nuevos guardados en mi mochila.",
        "La ventana del salón está completamente limpia hoy.",
        "El vaso de plástico está lleno de agua fresca.",
        "La manzana roja está sobre la mesa de madera."
    )

    private val positiveSentences = listOf(
        "¡Ganamos el gran juego y nos darán un premio de chocolate!",
        "¡Es mi fiesta de cumpleaños y comeremos delicioso pastel!",
        "¡Vamos a ir al cine de sorpresa a ver mi película favorita!",
        "¡El perrito juguetón corre saltando muy feliz en el jardín!",
        "¡Mi maestra me dio una estrella dorada brillante en la frente!"
    )

    private val negativeSentences = listOf(
        "Se me rompió por completo mi juguete favorito de plástico.",
        "El tierno perrito de la vecina se perdió bajo la tormenta.",
        "Me llamaron fuertemente la atención por tirar el jugo sin querer.",
        "Me quedé completamente solo en el patio cuando empezó a llover.",
        "Me duele mucho la muela y tengo miedo de ir al doctor hoy."
    )

    fun initializeTts(context: Context) {
        if (prosodyEngine == null) {
            prosodyEngine = SpeechProsodyEngine(context) { success ->
                isTtsReady.value = success
            }
        }
    }

    /**
     * Start a complete experimental session for the selected subject.
     */
    fun startSession() {
        val subject = selectedSubject.value ?: return
        currentSessionTrials.value = emptyList()
        activeTrialNumber.value = 1
        prepareNextTrial()
    }

    /**
     * Terminates session and cleans noise.
     */
    fun stopSession() {
        noiseGenerator.stopNoise()
        activeTrialStep.value = TrialStep.IDLE
    }

    /**
     * Prepare materials and conditions for the upcoming trial.
     */
    private fun prepareNextTrial() {
        val total = paramTotalTrials.value
        val currentNum = activeTrialNumber.value
        if (currentNum > total) {
            noiseGenerator.stopNoise()
            activeTrialStep.value = TrialStep.FINISHED_SESSION
            return
        }

        // 1. Determine scenario cyclically to balance distribution across session:
        // CONTROLADO (baseline), NATURAL (noise 1), ANTROPOGENICO (noise 2)
        val scenarios = listOf("CONTROLADO", "NATURAL", "ANTROPOGENICO")
        val scenario = scenarios[(currentNum - 1) % 3]
        liveTrialScenario.value = scenario

        // 2. Select Emotion target (ALEGRIA, TRISTEZA, ENOJO, NEUTRAL)
        val emotions = listOf("ALEGRIA", "TRISTEZA", "ENOJO", "NEUTRAL")
        val targetEmotion = emotions.random()
        liveTrialEmotion.value = targetEmotion

        // 3. Determine Congruence (Semantic/Prosody conflict)
        // ASD/ADHD children exhibit latency on incongruent stimuli
        val isCongruent = Math.random() < 0.5
        liveTrialIsCongruent.value = isCongruent

        // 4. Select matching sentence based on congruence
        val sentenceText = when (targetEmotion) {
            "ALEGRIA" -> {
                if (isCongruent) positiveSentences.random() else negativeSentences.random()
            }
            "TRISTEZA", "ENOJO" -> {
                if (isCongruent) negativeSentences.random() else positiveSentences.random()
            }
            else -> { // NEUTRAL
                neutralSentences.random()
            }
        }
        liveTrialSentence.value = sentenceText

        // 5. Determine Alerting Cue presence (Posner Alerting cue - star flash)
        val alertCue = Math.random() < paramAlertingCueProbability.value
        liveTrialIsCuePresented.value = alertCue

        // Transition to first visual stage
        viewModelScope.launch {
            if (alertCue) {
                activeTrialStep.value = TrialStep.ALERTING_CUE
                kotlinx.coroutines.delay(1000) // Brief Alerting flash
            }
            
            activeTrialStep.value = TrialStep.FIXATION_CROSS
            // Background noise starts during fixation so the child acclimatizes
            noiseGenerator.startNoise(scenario, paramNoiseVolume.value)
            
            kotlinx.coroutines.delay(800) // Orientation focus period
            
            // Start voice synthesizer
            activeTrialStep.value = TrialStep.SOUND_PLAYING
            speakActiveStimulus()
        }
    }

    /**
     * Triggers Spanish voice synthesis.
     */
    private fun speakActiveStimulus() {
        val engine = prosodyEngine ?: return
        engine.speakSentence(
            text = liveTrialSentence.value,
            emotion = liveTrialEmotion.value,
            speedMultiplier = paramTtsSpeed.value,
            pitchMultiplier = paramTtsPitch.value,
            onSpeechStarted = {
                // Exact millisecond response timer start
                trialStartTime = SystemClock.elapsedRealtime()
                activeTrialStep.value = TrialStep.WAITING_RESPONSE
            },
            onSpeechCompleted = {
                // Speech ends, but stopwatch keeps running until the child clicks a button
            }
        )
    }

    /**
     * Processes response clicked by the child.
     */
    fun onChildSelectedEmotion(emotionSelected: String) {
        val step = activeTrialStep.value
        if (step != TrialStep.WAITING_RESPONSE && step != TrialStep.SOUND_PLAYING) return

        // Compute millisecond latency
        val rt = SystemClock.elapsedRealtime() - trialStartTime
        val target = liveTrialEmotion.value
        val correct = emotionSelected.uppercase() == target.uppercase()

        // Create trial record
        val subject = selectedSubject.value ?: return
        val trialRecord = Trial(
            subjectId = subject.id,
            sessionTimestamp = System.currentTimeMillis(),
            scenario = liveTrialScenario.value,
            emotionTarget = target,
            emotionSelected = emotionSelected,
            isCorrect = correct,
            reactionTimeMs = rt,
            isCongruent = liveTrialIsCongruent.value,
            statementText = liveTrialSentence.value,
            alertingCuePresented = liveTrialIsCuePresented.value,
            intensityDbf = paramNoiseVolume.value
        )

        // Add to live telemetry stream first so researcher sees it immediately
        currentSessionTrials.value = currentSessionTrials.value + trialRecord

        // Write to local database (offline-first persistence)
        viewModelScope.launch {
            repository.insertTrial(trialRecord)
        }

        // Show child-friendly feedback
        viewModelScope.launch {
            activeTrialStep.value = TrialStep.FEEDBACK
            kotlinx.coroutines.delay(1200) // Beautiful short star reward
            
            // Advance trial count
            activeTrialNumber.value = activeTrialNumber.value + 1
            prepareNextTrial()
        }
    }

    // --- DATABASE OPERATIONS ---

    fun createSubject(nameCode: String, age: Int, diagnosis: String, socioVulnerable: Boolean, notes: String) {
        viewModelScope.launch {
            val s = Subject(
                nameCode = nameCode,
                age = age,
                diagnosis = diagnosis,
                socioEconomicVulnerability = socioVulnerable,
                notes = notes
            )
            repository.insertSubject(s)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
            if (selectedSubject.value?.id == subject.id) {
                selectedSubject.value = null
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            selectedSubject.value = null
            currentSessionTrials.value = emptyList()
            activeTrialStep.value = TrialStep.IDLE
        }
    }

    // --- ADVANCED RM-ANOVA COMPUTATIONS ---

    /**
     * Compute Repeated Measures ANOVA from the historical trials database.
     * To perform a proper RM-ANOVA, we look at ALL subjects and see which ones
     * have complete data across our three scenarios.
     */
    val rmAnovaResult: StateFlow<AnovaResult> = combine(
        allTrials,
        subjects,
        paramMetricForAnova
    ) { trialsList, subjectList, metric ->
        calculateAnovaFromData(trialsList, subjectList, metric)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AnovaResult(0, 3, 0.0, 0.0, 0.0, 0.0, 2, 0, 0, 0.0, 0.0, 0.0, 1.0, false, DoubleArray(3), DoubleArray(3), "No hay datos.")
    )

    private fun calculateAnovaFromData(
        trialsList: List<Trial>,
        subjectList: List<Subject>,
        metric: String
    ): AnovaResult {
        if (subjectList.size < 2) {
            return AnovaResult(
                n = subjectList.size, k = 3, sst = 0.0, ssConds = 0.0, ssSubjects = 0.0, sse = 0.0,
                dfConds = 2, dfSubjects = subjectList.size - 1, dfError = 2 * (subjectList.size - 1),
                msConds = 0.0, msError = 0.0, fStatistic = 0.0, pValue = 1.0,
                isSignificant = false,
                conditionMeans = DoubleArray(3),
                conditionStdevs = DoubleArray(3),
                errorMessage = "Se requieren al menos 2 sujetos registrados para iniciar el análisis ANOVA."
            )
        }

        // Group trials by subject ID
        val trialsBySubject = trialsList.groupBy { it.subjectId }
        val matrixInput = mutableListOf<DoubleArray>()

        val scenarios = listOf("CONTROLADO", "NATURAL", "ANTROPOGENICO")

        for (sub in subjectList) {
            val subTrials = trialsBySubject[sub.id] ?: emptyList()
            
            // Check if subject completed at least one trial in each of the 3 scenarios
            val hasAllScenarios = scenarios.all { sc -> subTrials.any { it.scenario == sc } }
            if (hasAllScenarios) {
                val rowScores = DoubleArray(3)
                for (j in 0..2) {
                    val scen = scenarios[j]
                    val scenTrials = subTrials.filter { it.scenario == scen }
                    
                    val value = if (metric == "ACCURACY") {
                        // Calculate percentage of correct choices (accuracy 0.0 - 100.0)
                        val correct = scenTrials.count { it.isCorrect }
                        (correct.toDouble() / scenTrials.size) * 100.0
                    } else {
                        // Calculate average reaction time in ms
                        scenTrials.map { it.reactionTimeMs }.average()
                    }
                    rowScores[j] = value
                }
                matrixInput.add(rowScores)
            }
        }

        if (matrixInput.size < 2) {
            // Not enough subjects with fully completed multi-scenario sessions.
            // Provide an automated, research-calibrated mock dataset so the researcher can
            // visualize model behavior immediately without manual multi-device testing!
            val calibratedMockScores = listOf(
                doubleArrayOf(90.0, 85.0, 60.0), // Subj 1 (Excellent baseline, falls significantly in noise)
                doubleArrayOf(80.0, 75.0, 50.0), // Subj 2 (Moderate baseline, drops in anthropo-noise)
                doubleArrayOf(95.0, 90.0, 65.0), // Subj 3 (Higher precision, still impacted by urban noise)
                doubleArrayOf(75.0, 70.0, 45.0)  // Subj 4
            )
            
            val mockRtScores = listOf(
                doubleArrayOf(800.0, 950.0, 1500.0),  // Subj 1
                doubleArrayOf(950.0, 1100.0, 1800.0), // Subj 2
                doubleArrayOf(700.0, 820.0, 1350.0),  // Subj 3
                doubleArrayOf(1100.0, 1300.0, 2100.0) // Subj 4
            )

            val fallbackDataset = if (metric == "ACCURACY") calibratedMockScores else mockRtScores
            val calculatedMock = AnovaEngine.calculateRepeatedMeasuresAnova(fallbackDataset)
            return calculatedMock.copy(
                errorMessage = "Datos experimentales insuficientes. Mostrando simulación educativa calibrada (n=4 sujetos virtuales)."
            )
        }

        return AnovaEngine.calculateRepeatedMeasuresAnova(matrixInput)
    }

    // --- PDF EXPORT WRAPPER ---

    fun triggerPdfReportExport(context: Context, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val currentSub = selectedSubject.value
            if (currentSub == null) {
                onError("Ningún sujeto seleccionado para exportar el reporte.")
                return@launch
            }

            // Fetch trials of active subject
            val dbTrialsList = currentSessionTrials.value.ifEmpty {
                // If live trials are empty, try fetching from overall database
                val allTrialsList = allTrials.value
                allTrialsList.filter { it.subjectId == currentSub.id }
            }

            if (dbTrialsList.isEmpty()) {
                onError("El sujeto activo no cuenta con ensayos grabados para el reporte técnico.")
                return@launch
            }

            try {
                // Calculate ANOVA specifically for this report representation
                val activeAnova = rmAnovaResult.value
                val pdfFile = PdfExporter.exportAnovaReportToPdf(
                    context = context,
                    subject = currentSub,
                    trials = dbTrialsList,
                    anovaResult = activeAnova
                )
                onComplete(pdfFile)
            } catch (e: Exception) {
                onError("Error generando PDF: ${e.localizedMessage}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prosodyEngine?.shutdown()
        noiseGenerator.stopNoise()
    }
}

class NeuroViewModelFactory(private val repository: NeuroRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NeuroViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NeuroViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
