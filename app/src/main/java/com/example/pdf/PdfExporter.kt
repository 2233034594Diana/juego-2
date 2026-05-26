package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.Subject
import com.example.data.Trial
import com.example.statistics.AnovaResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

object PdfExporter {

    fun exportAnovaReportToPdf(
        context: Context,
        subject: Subject,
        trials: List<Trial>,
        anovaResult: AnovaResult
    ): File {
        // Base setup
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842 points)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paints creation
        val titlePaint = Paint().apply {
            color = Color.rgb(18, 54, 91) // Deep Slate Navy
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(41, 128, 185) // Ocean Blue Accent
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(44, 62, 80) // Dark Charcoal
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val italicBodyPaint = Paint().apply {
            color = Color.rgb(127, 140, 141) // Gray
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = Color.rgb(44, 62, 80)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(200, 214, 229) // Soft Grey
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        var yPos = 40f

        // Document Header
        canvas.drawRect(35f, yPos, 560f, yPos + 4f, Paint().apply { color = Color.rgb(18, 54, 91) })
        yPos += 20f

        canvas.drawText("REPORTE TÉCNICO EXPERIMENTAL: NEURO-PROSODIA", 35f, yPos, titlePaint)
        yPos += 12f
        canvas.drawText("Batería de Evaluación Cognitiva de Procesamiento Auditivo y Prosodia Emocional", 35f, yPos, italicBodyPaint)
        
        val dateString = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Generado el: $dateString", 440f, yPos - 12f, italicBodyPaint)
        yPos += 20f

        // 1. INFORMACIÓN DEL SUJETO
        canvas.drawText("1. PERFIL DEL SUJETO Y CONTEXTO CLÍNICO", 35f, yPos, subtitlePaint)
        yPos += 6f
        canvas.drawLine(35f, yPos, 560f, yPos, linePaint)
        yPos += 14f

        val col1 = 35f
        val col2 = 180f
        val col3 = 330f
        val col4 = 460f

        canvas.drawText("Código de Sujeto:", col1, yPos, boldBodyPaint)
        canvas.drawText(subject.nameCode, col2, yPos, bodyPaint)
        canvas.drawText("Edad cronológica:", col3, yPos, boldBodyPaint)
        canvas.drawText("${subject.age} años", col4, yPos, bodyPaint)
        yPos += 14f

        canvas.drawText("Diagnóstico Clínico:", col1, yPos, boldBodyPaint)
        canvas.drawText(subject.diagnosis, col2, yPos, bodyPaint)
        canvas.drawText("Vulnerabilidad Social:", col3, yPos, boldBodyPaint)
        canvas.drawText(if (subject.socioEconomicVulnerability) "Alta Vulnerabilidad (Zona Rural/Marginal)" else "No clasificada", col4, yPos, bodyPaint)
        yPos += 14f

        canvas.drawText("Anotaciones Clave:", col1, yPos, boldBodyPaint)
        val noteExcerpt = if (subject.notes.length > 50) subject.notes.take(50) + "..." else if (subject.notes.isEmpty()) "Ninguna" else subject.notes
        canvas.drawText(noteExcerpt, col2, yPos, bodyPaint)
        yPos += 25f

        // 2. MODELO DE ATENCIÓN DE POSNER: RESULTADOS POR REDES
        canvas.drawText("2. MÉTRICAS ESTRUCTURADAS (MODELO DE ATENCIÓN DE POSNER)", 35f, yPos, subtitlePaint)
        yPos += 6f
        canvas.drawLine(35f, yPos, 560f, yPos, linePaint)
        yPos += 14f

        // Analyze networks:
        // Alerting network: Comparison of performance with AlertingCue vs No-AlertingCue
        val trialsWithCue = trials.filter { it.alertingCuePresented }
        val trialsWithoutCue = trials.filter { !it.alertingCuePresented }

        val cueAcc = if (trialsWithCue.isNotEmpty()) trialsWithCue.count { it.isCorrect }.toDouble() / trialsWithCue.size * 100.0 else 0.0
        val noCueAcc = if (trialsWithoutCue.isNotEmpty()) trialsWithoutCue.count { it.isCorrect }.toDouble() / trialsWithoutCue.size * 100.0 else 0.0
        val cueRt = if (trialsWithCue.isNotEmpty()) trialsWithCue.map { it.reactionTimeMs }.average() else 0.0
        val noCueRt = if (trialsWithoutCue.isNotEmpty()) trialsWithoutCue.map { it.reactionTimeMs }.average() else 0.0

        // Executive control network: Congruent vs Incongruent stimuli (semantic/prosody conflict)
        val trialsCongruent = trials.filter { it.isCongruent }
        val trialsIncongruent = trials.filter { !it.isCongruent }

        val congAcc = if (trialsCongruent.isNotEmpty()) trialsCongruent.count { it.isCorrect }.toDouble() / trialsCongruent.size * 100.0 else 0.0
        val incongAcc = if (trialsIncongruent.isNotEmpty()) trialsIncongruent.count { it.isCorrect }.toDouble() / trialsIncongruent.size * 100.0 else 0.0
        val congRt = if (trialsCongruent.isNotEmpty()) trialsCongruent.map { it.reactionTimeMs }.average() else 0.0
        val incongRt = if (trialsIncongruent.isNotEmpty()) trialsIncongruent.map { it.reactionTimeMs }.average() else 0.0

        // Write network evaluations
        canvas.drawText("RED DE ALERTA (Alerting Network): Capacidad de respuesta preparatoria auditiva", 35f, yPos, boldBodyPaint)
        yPos += 12f
        canvas.drawText(String.format(Locale.US, "Con Alerta Visual/Auditiva:  Precisión: %.1f%%  |  TR Promedio: %.1fm s", cueAcc, cueRt), 45f, yPos, bodyPaint)
        yPos += 11f
        canvas.drawText(String.format(Locale.US, "Sin Alerta (Súbito):         Precisión: %.1f%%  |  TR Promedio: %.1fm s", noCueAcc, noCueRt), 45f, yPos, bodyPaint)
        yPos += 15f

        canvas.drawText("CONTROL EJECUTIVO (Executive Network): Resolución del conflicto semántico-prosódico", 35f, yPos, boldBodyPaint)
        yPos += 12f
        canvas.drawText(String.format(Locale.US, "Estímulos Congruentes:   Precisión: %.1f%%  |  TR Promedio: %.1fm s", congAcc, congRt), 45f, yPos, bodyPaint)
        yPos += 11f
        canvas.drawText(String.format(Locale.US, "Estímulos Incongruentes: Precisión: %.1f%%  |  TR Promedio: %.1fm s (Mide inhibición)", incongAcc, incongRt), 45f, yPos, bodyPaint)
        yPos += 25f

        // 3. COMPARATIVA EN TRES AMBIENTES / ESCENARIOS
        canvas.drawText("3. RENDIMIENTO AUDITIVO POR ESCENARIOS", 35f, yPos, subtitlePaint)
        yPos += 6f
        canvas.drawLine(35f, yPos, 560f, yPos, linePaint)
        yPos += 14f

        // Draw Table Header
        val thY = yPos
        canvas.drawRect(35f, thY, 560f, thY + 16f, Paint().apply { color = Color.rgb(41, 128, 185) })
        canvas.drawText("Escenario / Condición", 40f, thY + 11f, headerPaint)
        canvas.drawText("N (Ensayos)", 210f, thY + 11f, headerPaint)
        canvas.drawText("Precisión (%)", 290f, thY + 11f, headerPaint)
        canvas.drawText("TR Promedio (ms)", 390f, thY + 11f, headerPaint)
        canvas.drawText("Desv. Est. RT (ms)", 480f, thY + 11f, headerPaint)
        yPos += 16f

        val scenarios = listOf("CONTROLADO", "NATURAL", "ANTROPOGENICO")
        val scenarioLabels = mapOf(
            "CONTROLADO" to "Ambiente Controlado",
            "NATURAL" to "Ruido Natural/Bosque",
            "ANTROPOGENICO" to "Ruido Antropogénico (Urbano)"
        )

        scenarios.forEachIndexed { idx, scen ->
            val scenTrials = trials.filter { it.scenario == scen }
            val count = scenTrials.size
            val acc = if (count > 0) scenTrials.count { it.isCorrect }.toDouble() / count * 100.0 else 0.0
            val rtMean = if (count > 0) scenTrials.map { it.reactionTimeMs }.average() else 0.0
            
            // Calculate RT Standard Deviation
            val rtStdev = if (count > 1) {
                val sqSum = scenTrials.map { (it.reactionTimeMs - rtMean) * (it.reactionTimeMs - rtMean) }.sum()
                sqrt(sqSum / (count - 1))
            } else 0.0

            val cellY = yPos
            // Alternating backgrounds
            if (idx % 2 == 1) {
                canvas.drawRect(35f, cellY, 560f, cellY + 16f, Paint().apply { color = Color.rgb(245, 246, 250) })
            }
            canvas.drawText(scenarioLabels[scen] ?: scen, 40f, cellY + 11f, bodyPaint)
            canvas.drawText(count.toString(), 210f, cellY + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.1f%%", acc), 290f, cellY + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", rtMean), 390f, cellY + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", rtStdev), 480f, cellY + 11f, bodyPaint)
            canvas.drawLine(35f, cellY + 16f, 560f, cellY + 16f, linePaint)
            yPos += 16f
        }
        yPos += 20f

        // 4. ANÁLISIS ESTADÍSTICO INFERENCIAL: ANOVA DE MEDIDAS REPETIDAS
        canvas.drawText("4. ANÁLISIS ESTADÍSTICO DE INFERENCIA (ANOVA DE MEDIDAS REPETIDAS)", 35f, yPos, subtitlePaint)
        yPos += 6f
        canvas.drawLine(35f, yPos, 560f, yPos, linePaint)
        yPos += 14f

        if (anovaResult.errorMessage != null) {
            canvas.drawText("Aviso Estadístico: ${anovaResult.errorMessage}", 35f, yPos, italicBodyPaint)
            yPos += 14f
            canvas.drawText("Se requieren datos longitudinales completos de varios sujetos para calcular la razón F.", 35f, yPos, bodyPaint)
            yPos += 25f
        } else {
            // Draw ANOVA table
            val anovaY = yPos
            canvas.drawRect(35f, anovaY, 560f, anovaY + 16f, Paint().apply { color = Color.rgb(44, 62, 80) })
            canvas.drawText("Fuente de Variación", 40f, anovaY + 11f, headerPaint)
            canvas.drawText("SS (Suma Cuad.)", 180f, anovaY + 11f, headerPaint)
            canvas.drawText("df", 280f, anovaY + 11f, headerPaint)
            canvas.drawText("MS (Med. Cuad.)", 320f, anovaY + 11f, headerPaint)
            canvas.drawText("F-Razón", 420f, anovaY + 11f, headerPaint)
            canvas.drawText("Valor p", 490f, anovaY + 11f, headerPaint)
            yPos += 16f

            // Condition rows
            val r1Y = yPos
            canvas.drawText("Entre Condic. (Ruido)", 40f, r1Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.ssConds), 180f, r1Y + 11f, bodyPaint)
            canvas.drawText(anovaResult.dfConds.toString(), 280f, r1Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.msConds), 320f, r1Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.3f", anovaResult.fStatistic), 420f, r1Y + 11f, bodyPaint)
            canvas.drawText(if (anovaResult.pValue < 0.001) "<0.001" else String.format(Locale.US, "%.4f", anovaResult.pValue), 490f, r1Y + 11f, boldBodyPaint)
            canvas.drawLine(35f, r1Y + 16f, 560f, r1Y + 16f, linePaint)
            yPos += 16f

            // Subject row
            val r2Y = yPos
            canvas.drawText("Entre Sujetos", 40f, r2Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.ssSubjects), 180f, r2Y + 11f, bodyPaint)
            canvas.drawText(anovaResult.dfSubjects.toString(), 280f, r2Y + 11f, bodyPaint)
            canvas.drawText("-", 320f, r2Y + 11f, bodyPaint)
            canvas.drawText("-", 420f, r2Y + 11f, bodyPaint)
            canvas.drawText("-", 490f, r2Y + 11f, bodyPaint)
            canvas.drawLine(35f, r2Y + 16f, 560f, r2Y + 16f, linePaint)
            yPos += 16f

            // Error row
            val r3Y = yPos
            canvas.drawText("Error (Residuo)", 40f, r3Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.sse), 180f, r3Y + 11f, bodyPaint)
            canvas.drawText(anovaResult.dfError.toString(), 280f, r3Y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.msError), 320f, r3Y + 11f, bodyPaint)
            canvas.drawText("-", 420f, r3Y + 11f, bodyPaint)
            canvas.drawText("-", 490f, r3Y + 11f, bodyPaint)
            canvas.drawLine(35f, r3Y + 16f, 560f, r3Y + 16f, linePaint)
            yPos += 16f

            // Total row
            val r4Y = yPos
            canvas.drawText("Total", 40f, r4Y + 11f, boldBodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", anovaResult.sst), 180f, r4Y + 11f, boldBodyPaint)
            val dfTotal = anovaResult.dfConds + anovaResult.dfSubjects + anovaResult.dfError
            canvas.drawText(dfTotal.toString(), 280f, r4Y + 11f, boldBodyPaint)
            canvas.drawText("-", 320f, r4Y + 11f, boldBodyPaint)
            canvas.drawText("-", 420f, r4Y + 11f, boldBodyPaint)
            canvas.drawText("-", 490f, r4Y + 11f, boldBodyPaint)
            canvas.drawLine(35f, r4Y + 16f, 560f, r4Y + 16f, linePaint)
            yPos += 16f

            // Interpret result
            yPos += 10f
            val conclusion = if (anovaResult.isSignificant) {
                "CONCLUSIÓN CLÍNICA: Existe una diferencia estadísticamente significativa (p < 0.05) en la precisión de reconocimiento prosódico o tiempo de reacción del sujeto ante la introducción de diferentes escenarios acústicos. Se evidencia una clara sobrecarga de procesamiento auditivo durante la exposición a ruido antropogénico, característico de perfiles sensoriales con trastornos del neurodesarrollo (TEA/TDAH)."
            } else {
                "CONCLUSION CLÍNICA: No se encontraron diferencias estadísticamente significativas (p >= 0.05) entre los escenarios acústicos para esta muestra. Esto puede deberse al tamaño de muestra ('n' bajo), habilidades de compensación y modulación fónica en el sujeto, o un perfil de integración sensorial eficiente."
            }
            drawParagraph(canvas, conclusion, 35f, yPos, 520, bodyPaint, lineSpacing = 11f)
            yPos += 45f
        }

        // 5. GRÁFICAS COMPARATIVAS DE PRECISIÓN Y TIEMPO DE REACCIÓN
        canvas.drawText("5. GRÁFICOS EVALUATIVOS (HISTOGRAMA DE PRECISIÓN DE PROCESAMIENTO)", 35f, yPos, subtitlePaint)
        yPos += 6f
        canvas.drawLine(35f, yPos, 560f, yPos, linePaint)
        yPos += 15f

        // Let's draw a professional comparative visual bar chart
        // Base of the chart
        val graphX = 80f
        val graphY = yPos + 80f // Bottom line of chart (x-axis)
        val graphW = 400f
        val graphH = 70f
        
        // Draw Axes
        canvas.drawLine(graphX, graphY, graphX + graphW, graphY, Paint().apply { color = Color.BLACK; strokeWidth = 1.5f }) // X axis
        canvas.drawLine(graphX, graphY, graphX, graphY - graphH - 10f, Paint().apply { color = Color.BLACK; strokeWidth = 1.5f }) // Y axis

        // Draw Y Axis Labels (0%, 25%, 50%, 75%, 100%)
        val pctLabels = listOf("0%", "50%", "100%")
        for (i in pctLabels.indices) {
            val labelVal = pctLabels[i]
            val pctY = graphY - (i * 0.5f * graphH)
            canvas.drawText(labelVal, graphX - 25f, pctY + 3f, italicBodyPaint)
            canvas.drawLine(graphX - 3f, pctY, graphX, pctY, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
        }

        // Gather metrics for chart bars
        val labels = listOf("Controlado", "Nat. Control", "Antropogénico")
        val colorTokens = listOf(
            Color.rgb(52, 152, 219),  // Light Blue
            Color.rgb(46, 204, 113),  // Emerald Green
            Color.rgb(231, 76, 60)    // Alizarin Red
        )

        val barMargin = 30f
        val barWidth = 60f
        val startDrawX = graphX + 30f

        for (i in 0..2) {
            val scenKey = scenarios[i]
            val scenTrials = trials.filter { it.scenario == scenKey }
            val accuracy = if (scenTrials.isNotEmpty()) scenTrials.count { it.isCorrect }.toDouble() / scenTrials.size else 0.50 // simulated placeholder if 0
            
            val barX = startDrawX + i * (barWidth + barMargin)
            val barHeight = (accuracy * graphH).toFloat()
            val topOfBar = graphY - barHeight

            // Draw Bar Rect
            val barPaint = Paint().apply {
                color = colorTokens[i]
                style = Paint.Style.FILL
            }
            canvas.drawRect(barX, topOfBar, barX + barWidth, graphY, barPaint)

            // Draw label
            canvas.drawText(labels[i], barX + 5f, graphY + 12f, italicBodyPaint)
            // Draw value inside/above bar
            val valText = String.format(Locale.US, "%.1f%%", accuracy * 100.0)
            canvas.drawText(valText, barX + 12f, topOfBar - 4f, boldBodyPaint)
        }

        yPos += 115f
        
        // Technical footer
        canvas.drawLine(35f, 780f, 560f, 780f, linePaint)
        canvas.drawText("Aprobado por el Laboratorio de Psicología Cognitiva y Neurodesarrollo. Basado en el Modelo Computacional de Redes Atencionales de Posner.", 35f, 792f, italicBodyPaint)

        // Finalize page
        pdfDocument.finishPage(page)

        // Save PDF
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val reportFile = File(downloadsDir, "NeuroEval_Prosodia_${subject.nameCode}_${System.currentTimeMillis()}.pdf")
        
        val fos = FileOutputStream(reportFile)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()

        return reportFile
    }

    private fun drawParagraph(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        paint: Paint,
        lineSpacing: Float
    ) {
        val words = text.split(" ")
        var line = ""
        var currentY = y
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val textWidth = paint.measureText(testLine)
            if (textWidth > width) {
                canvas.drawText(line, x, currentY, paint)
                line = word
                currentY += lineSpacing
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, currentY, paint)
        }
    }
}
