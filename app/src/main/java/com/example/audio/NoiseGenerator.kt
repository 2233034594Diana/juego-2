package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

class NoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val random = Random()

    @Volatile
    private var currentVolume: Float = 0.5f

    /**
     * Updates volume in real-time.
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.0f, 1.0f)
        try {
            audioTrack?.setVolume(currentVolume)
        } catch (_: Exception) {}
    }

    /**
     * Starts looping procedural noise depending on the chosen scenario.
     */
    fun startNoise(type: String, initialVolume: Float = 0.5f) {
        stopNoise()
        currentVolume = initialVolume

        if (type == "CONTROLADO") {
            // Controlled baseline means complete quietness
            return
        }

        playJob = scope.launch {
            try {
                val sampleRate = 22050 // Optimized sample rate for CPU and sound synthesis
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
                
                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioTrack(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        0
                    )
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack = track
                try {
                    track.setVolume(currentVolume)
                    track.play()
                } catch (e: Exception) {
                    return@launch
                }

                val buffer = ShortArray(1024)
                var phase = 0.0
                var windPhase = 0.0

                while (isActive) {
                    val isPlaying = try {
                        track.playState == AudioTrack.PLAYSTATE_PLAYING
                    } catch (_: Exception) {
                        false
                    }
                    if (!isPlaying) break

                    for (i in buffer.indices) {
                        val sample = when (type) {
                            "NATURAL" -> {
                                // Synthesize a calming, whistling nature breeze
                                // Gentle whistling wind component: a 280Hz tone modulated slowly
                                windPhase += 2.0 * java.lang.Math.PI * 0.1 / sampleRate // Gust modulation
                                val gustFreq = 280.0 + 80.0 * sin(windPhase)
                                phase += 2.0 * java.lang.Math.PI * gustFreq / sampleRate
                                val tone = sin(phase)

                                // Soft rustling pink-ish static
                                val staticNoise = random.nextFloat() * 2.0f - 1.0f
                                
                                // Combine tone and gentle crackling to simulate rustling forest leaves
                                ((tone * 0.25f + staticNoise * 0.15f) * 32767.0).toInt().toShort()
                            }
                            "ANTROPOGENICO" -> {
                                // Synthesize harsh, rumbling heavy urban environmental traffic/construction noise
                                // Deep heavy hum: 65Hz rumbling exhaust motors
                                phase += 2.0 * java.lang.Math.PI * 65.0 / sampleRate
                                val engineTone = sin(phase)

                                // Harsh industrial mechanical grating sound modulated dynamically
                                windPhase += 2.0 * java.lang.Math.PI * 1.5 / sampleRate 
                                val rattleFreq = 120.0 + 60.0 * sin(windPhase)
                                val rattleTone = sin(rattleFreq * phase) * 0.2

                                // Heavy grey/white construction exhaust static
                                val frictionNoise = random.nextFloat() * 2.0f - 1.0f

                                ((engineTone * 0.40f + rattleTone * 0.15f + frictionNoise * 0.35f) * 32767.0).toInt().toShort()
                            }
                            else -> {
                                0.toShort()
                            }
                        }
                        buffer[i] = sample
                    }
                    
                    try {
                        track.write(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                // Prevent crash if AudioTrack is stopped/released concurrently
            }
        }
    }

    /**
     * Halts synthesizer.
     */
    fun stopNoise() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
            audioTrack = null
        } catch (_: Exception) {}
    }
}
