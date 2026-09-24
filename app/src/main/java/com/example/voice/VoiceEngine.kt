package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlin.math.max

interface VoiceEngineListener {
    fun onSpeechStart()
    fun onSpeechRmsChanged(rmsdB: Float)
    fun onSpeechPartialResult(partialText: String) {}
    fun onSpeechResult(recognizedText: String, confidence: Float)
    fun onSpeechResult(recognizedText: String) {
        onSpeechResult(recognizedText, 1.0f)
    }
    fun onSpeechError(errorCode: Int, message: String)
    fun onTtsStart()
    fun onTtsDone()
    fun onTtsError(error: String)
}

/**
 * FRIDAY Voice Engine V2: High-responsiveness speech recognizer with real-time VAD,
 * acoustic noise handling, and multi-factor confidence scoring.
 */
class VoiceEngine(
    private val context: Context,
    private val listener: VoiceEngineListener
) {

    companion object {
        private const val TAG = "VoiceEngineV2"
        private const val UTTERANCE_ID = "friday_speech_id"

        // Silence timeout in milliseconds after user stops speaking before forcing finish
        // Relaxed to 1500ms to allow natural human pauses between words
        private const val VAD_TRAILING_SILENCE_MS = 1500L

        // Confidence thresholds for decision making
        const val CONFIDENCE_HIGH_THRESHOLD = 0.50f
        const val CONFIDENCE_MEDIUM_THRESHOLD = 0.35f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentSessionId: String? = null
    private var busyRetryCount = 0

    // Standby Listening Mode flag
    var isStandbyMode: Boolean = false

    // Modular TTS Voice Output layer
    private var ttsProvider: VoiceOutputProvider = AndroidSystemTtsProvider(
        context = context,
        onStart = { listener.onTtsStart() },
        onDone = { listener.onTtsDone() },
        onError = { err -> listener.onTtsError(err) }
    )

    // Real-time Voice Activity Detection (VAD) state
    private var speechStarted = false
    private var peakRmsDuringSpeech = 0f
    private var lastSpeechTimestamp = 0L
    private var vadWatchdogRunnable: Runnable? = null

    /**
     * Allows plugging in an alternative VoiceOutputProvider (e.g. Cloud TTS)
     * without modifying speech recognition or conversational state machines.
     */
    fun setTtsProvider(provider: VoiceOutputProvider) {
        ttsProvider.shutdown()
        ttsProvider = provider
    }

    fun startListening(sessionId: String? = null) {
        mainHandler.post {
            if (isListening) {
                stopListening()
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                listener.onSpeechError(-1, "Speech recognition not available on device")
                return@post
            }

            currentSessionId = sessionId
            val boundSession = sessionId
            Log.i(TAG, "[SPEECH_STATE] Starting SpeechRecognizer (Session: ${boundSession ?: "legacy"})")

            try {
                // Reset VAD state for fresh utterance
                speechStarted = false
                peakRmsDuringSpeech = 0f
                lastSpeechTimestamp = 0L
                stopVadWatchdog()

                // Destroy old instance to prevent resource leaks
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                val isOfflineAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

                speechRecognizer = if (isOfflineAvailable) {
                    try {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } catch (_: Exception) {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (boundSession != null && boundSession != currentSessionId) return
                            isListening = true
                            busyRetryCount = 0
                            Log.i(TAG, "[SPEECH_STATE] Ready for speech (Session: $boundSession, Standby: $isStandbyMode)")
                            listener.onSpeechStart()
                            startVadWatchdog()
                        }

                        override fun onBeginningOfSpeech() {
                            if (boundSession != null && boundSession != currentSessionId) return
                            speechStarted = true
                            lastSpeechTimestamp = System.currentTimeMillis()
                            Log.i(TAG, "[SPEECH_STATE] Beginning of speech (Session: $boundSession)")
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            if (boundSession != null && boundSession != currentSessionId) return
                            // Normalize rmsdB (-2 to 10 typical) to 0.0 .. 1.0
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            if (normalized > 0.18f) {
                                speechStarted = true
                                peakRmsDuringSpeech = max(peakRmsDuringSpeech, normalized)
                                lastSpeechTimestamp = System.currentTimeMillis()
                            }
                            listener.onSpeechRmsChanged(normalized)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            if (boundSession != null && boundSession != currentSessionId) return
                            stopVadWatchdog()
                            isListening = false
                            Log.i(TAG, "[SPEECH_STATE] End of speech (Session: $boundSession)")
                        }

                        override fun onError(error: Int) {
                            if (boundSession != null && boundSession != currentSessionId) return
                            stopVadWatchdog()
                            isListening = false

                            // In standby mode, silence / timeout is completely expected: seamlessly re-arm listening
                            if (isStandbyMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                                try {
                                    speechRecognizer?.destroy()
                                } catch (_: Exception) {}
                                speechRecognizer = null
                                mainHandler.postDelayed({
                                    if (isStandbyMode) {
                                        startListening(boundSession)
                                    }
                                }, 50L)
                                return
                            }

                            if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_AUDIO) && busyRetryCount < 2) {
                                busyRetryCount++
                                Log.w(TAG, "SpeechRecognizer recoverable error (code $error). Retrying backoff attempt $busyRetryCount/2...")
                                try {
                                    speechRecognizer?.destroy()
                                } catch (_: Exception) {}
                                speechRecognizer = null
                                mainHandler.postDelayed({
                                    if (boundSession == currentSessionId || isStandbyMode) {
                                        startListening(boundSession)
                                    }
                                }, 150L)
                                return
                            }

                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Google Speech is busy"
                                else -> "Speech recognition error ($error)"
                            }
                            Log.w(TAG, "SpeechRecognizer error: $msg ($error)")
                            try {
                                speechRecognizer?.stopListening()
                                speechRecognizer?.destroy()
                            } catch (_: Exception) {}
                            speechRecognizer = null
                            Log.i(TAG, "[MIC_OWNER] SpeechRecognizer destroyed on error ($error). Microphone released.")
                            listener.onSpeechError(error, msg)
                        }

                        override fun onResults(results: Bundle?) {
                            if (boundSession != null && boundSession != currentSessionId) {
                                Log.w(TAG, "[DROPPED_STALE_CALLBACK] Dropped speech results from session $boundSession (current: $currentSessionId)")
                                return
                            }
                            stopVadWatchdog()
                            isListening = false
                            try {
                                speechRecognizer?.stopListening()
                                speechRecognizer?.destroy()
                            } catch (_: Exception) {}
                            speechRecognizer = null
                            Log.i(TAG, "[MIC_OWNER] SpeechRecognizer stopped & destroyed. Microphone completely released.")

                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognized = matches?.firstOrNull() ?: ""
                            val rawScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                            val confidence = calculateConfidence(
                                recognizedText = recognized,
                                rawConfidenceScores = rawScores,
                                peakRms = peakRmsDuringSpeech,
                                candidates = matches
                            )

                            Log.i(TAG, "[RAW_TRANSCRIPT] \"$recognized\" [Confidence: $confidence, Session: $boundSession]")
                            listener.onSpeechResult(recognized, confidence)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            if (boundSession != null && boundSession != currentSessionId) return
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull() ?: ""
                            if (partial.isNotBlank()) {
                                speechStarted = true
                                lastSpeechTimestamp = System.currentTimeMillis()
                                listener.onSpeechRmsChanged(0.5f)
                                listener.onSpeechPartialResult(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                    // Natural conversational silence detection allowing pauses between words
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                isListening = false
                stopVadWatchdog()
                listener.onSpeechError(-2, e.message ?: "Failed to start recognition")
            }
        }
    }

    private fun startVadWatchdog() {
        stopVadWatchdog()
        vadWatchdogRunnable = object : Runnable {
            override fun run() {
                if (!isListening) return

                val now = System.currentTimeMillis()
                // If speech has occurred and silence has lasted longer than VAD_TRAILING_SILENCE_MS,
                // proactively stop listening to avoid waiting for system timeout
                if (speechStarted && lastSpeechTimestamp > 0L && (now - lastSpeechTimestamp >= VAD_TRAILING_SILENCE_MS)) {
                    Log.d(TAG, "VAD detected end of user utterance (${now - lastSpeechTimestamp}ms silence). Finalizing...")
                    try {
                        speechRecognizer?.stopListening()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping recognizer from VAD watchdog", e)
                    }
                    return
                }

                mainHandler.postDelayed(this, 150L)
            }
        }
        mainHandler.postDelayed(vadWatchdogRunnable!!, 200L)
    }

    private fun stopVadWatchdog() {
        vadWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        vadWatchdogRunnable = null
    }

    /**
     * Multi-factor confidence score calculator.
     * Computes a normalized float in [0.0 .. 1.0] using:
     * - Android SpeechRecognizer platform confidence scores
     * - Acoustic Signal Quality (peak RMS energy vs ambient floor)
     * - Linguistic Intent Clarity (presence of known action verbs and nouns)
     * - Candidate consensus / ambiguity
     */
    fun calculateConfidence(
        recognizedText: String,
        rawConfidenceScores: FloatArray?,
        peakRms: Float,
        candidates: List<String>?
    ): Float {
        if (recognizedText.isBlank()) return 0f

        // 1. Platform score (0.0 to 1.0)
        val platformScore = rawConfidenceScores?.firstOrNull() ?: -1f
        val baseScore = when {
            platformScore in 0.0f..1.0f -> platformScore
            platformScore > 1.0f -> (platformScore / 100f).coerceIn(0f, 1f)
            else -> 0.75f // Default when platform doesn't report scores
        }

        // 2. Linguistic Intent Clarity
        val lower = recognizedText.lowercase().trim()
        val knownCommandWords = setOf(
            "flashlight", "torch", "light", "flash",
            "volume", "sound", "mute", "unmute", "louder", "quieter", "down", "up",
            "battery", "power", "charge", "percentage", "percent",
            "time", "date", "clock", "today",
            "open", "launch", "start", "camera", "app", "youtube", "chrome", "whatsapp", "spotify",
            "call", "phone", "dial", "message", "search", "navigate", "directions", "map",
            "music", "song", "play", "pause", "resume", "stop", "next", "track",
            "timer", "alarm", "countdown", "stopwatch", "cancel",
            "friday", "turn", "on", "off", "set"
        )

        val words = lower.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val matchedCount = words.count { knownCommandWords.contains(it) }

        val lexicalScore = when {
            matchedCount >= 2 -> 0.96f
            matchedCount == 1 -> 0.88f
            words.size >= 2 -> 0.75f
            else -> 0.50f
        }

        // 3. Acoustic Signal Quality
        val acousticScore = when {
            peakRms >= 0.30f -> 0.92f
            peakRms >= 0.15f -> 0.80f
            else -> 0.55f
        }

        // 4. Candidate Consensus
        val consensusScore = if (candidates != null && candidates.size > 1) {
            val top = candidates[0].lowercase().trim()
            val second = candidates[1].lowercase().trim()
            if (top == second) 0.95f else 0.80f
        } else {
            0.85f
        }

        // Weighted calculation:
        val composite = (baseScore * 0.40f) + (lexicalScore * 0.35f) + (acousticScore * 0.15f) + (consensusScore * 0.10f)
        return composite.coerceIn(0.05f, 1.0f)
    }

    fun stopListening() {
        mainHandler.post {
            stopVadWatchdog()
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            } finally {
                isListening = false
            }
        }
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun speak(text: String, pitch: Float = 1.0f, rate: Float = 1.0f) {
        ttsProvider.speak(text, pitch, rate)
    }

    fun stopSpeaking() {
        ttsProvider.stop()
    }

    fun shutdown() {
        stopVadWatchdog()
        stopListening()
        ttsProvider.shutdown()
    }
}

