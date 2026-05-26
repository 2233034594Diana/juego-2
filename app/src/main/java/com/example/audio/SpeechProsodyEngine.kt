package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeechProsodyEngine(
    private val context: Context,
    private val onInitComplete: (success: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set locale to Spanish (try Mexico first, fallback to generic Spanish)
            val spanishMx = Locale("es", "MX")
            val result = tts?.setLanguage(spanishMx)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to standard Spanish
                tts?.setLanguage(Locale("es", "ES"))
            }
            
            isInitialized = true
            onInitComplete(true)
        } else {
            isInitialized = false
            onInitComplete(false)
        }
    }

    /**
     * Set vocal features based on the emotional prosody target.
     */
    private fun applyProsodyParameters(emotion: String, speedAdjustment: Float = 1.0f, pitchAdjustment: Float = 1.0f) {
        val ttsEngine = tts ?: return

        when (emotion) {
            "ALEGRIA" -> { // Excitement/Happy: High pitch, elevated speech rate
                ttsEngine.setPitch(1.45f * pitchAdjustment)
                ttsEngine.setSpeechRate(1.15f * speedAdjustment)
            }
            "TRISTEZA" -> { // Sadness: Low pitch, compressed sluggish speech rate
                ttsEngine.setPitch(0.70f * pitchAdjustment)
                ttsEngine.setSpeechRate(0.65f * speedAdjustment)
            }
            "ENOJO" -> { // Angry: Highest tone, rapid agitated speech rate
                ttsEngine.setPitch(1.60f * pitchAdjustment)
                ttsEngine.setSpeechRate(1.30f * speedAdjustment)
            }
            "NEUTRAL" -> { // Control Baseline: Normal voice attributes
                ttsEngine.setPitch(1.00f * pitchAdjustment)
                ttsEngine.setSpeechRate(1.00f * speedAdjustment)
            }
            else -> {
                ttsEngine.setPitch(1.00f)
                ttsEngine.setSpeechRate(1.00f)
            }
        }
    }

    /**
     * Speaks the target sentence, configuring the prosody parameters and triggering progress listeners.
     */
    fun speakSentence(
        text: String,
        emotion: String,
        speedMultiplier: Float = 1.0f,
        pitchMultiplier: Float = 1.0f,
        onSpeechStarted: () -> Unit,
        onSpeechCompleted: () -> Unit
    ) {
        if (!isInitialized) {
            onInitComplete(false)
            return
        }

        applyProsodyParameters(emotion, speedMultiplier, pitchMultiplier)

        val utteranceId = "PROSODY_TRIAL_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) {
                    onSpeechStarted()
                }
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    onSpeechCompleted()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                onSpeechCompleted()
            }

            override fun onError(id: String?, errorCode: Int) {
                onSpeechCompleted()
            }
        })

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Shutdown engine resources.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
