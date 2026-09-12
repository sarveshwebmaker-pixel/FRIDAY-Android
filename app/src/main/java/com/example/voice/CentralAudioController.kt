package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Centralized Audio & Microphone Controller.
 *
 * Enforces single microphone ownership across FRIDAY:
 * Only ONE component (WakeWordEngine, VoiceEngine, or TextToSpeech)
 * may hold or access audio at any point in time.
 */
class CentralAudioController(private val context: Context) {

    companion object {
        private const val TAG = "CentralAudioController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _micState = MutableStateFlow(MicrophoneState.MIC_IDLE)
    val micState: StateFlow<MicrophoneState> = _micState.asStateFlow()

    var activeAudioOwner: String = "None"
        private set

    var activeSessionId: String? = null
        private set

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusHeld = false

    private val micLock = Any()

    fun transitionTo(newState: MicrophoneState, owner: String, sessionId: String? = activeSessionId) {
        synchronized(micLock) {
            val oldState = _micState.value
            if (oldState != newState || activeAudioOwner != owner) {
                Log.i(TAG, "[MIC_STATE] $oldState -> $newState | [ACTIVE_AUDIO_OWNER] $owner | [COMMAND_SESSION_ID] ${sessionId ?: "none"}")
                activeAudioOwner = owner
                activeSessionId = sessionId
                _micState.value = newState
            }
        }
    }

    /**
     * Prepares and starts command listening with guaranteed exclusive microphone ownership.
     * Generates a fresh unique sessionId.
     */
    fun acquireForCommandListening(
        reason: String,
        stopWakeWordAction: () -> Unit,
        startVoiceAction: (sessionId: String) -> Unit
    ): String {
        val sessionId = UUID.randomUUID().toString()
        synchronized(micLock) {
            Log.i(TAG, "[ACQUIRE_MIC] Requesting command listening: $reason (Session: $sessionId)")
            transitionTo(MicrophoneState.RELEASING_MIC, "CentralAudioController", sessionId)

            // 1. Fully stop wake word engine and release AudioRecord first
            stopWakeWordAction()
            Log.i(TAG, "[MIC_OWNER] AudioRecord released by WakeWordEngine.")
        }

        // 2. Schedule immediate handoff on next looper tick once AudioRecord is released
        mainHandler.post {
            synchronized(micLock) {
                if (activeSessionId == sessionId) {
                    Log.i(TAG, "[MIC_OWNER] Exclusive ownership granted to Google SpeechRecognizer (Session: $sessionId)")
                    transitionTo(MicrophoneState.COMMAND_LISTENING, "VoiceEngine", sessionId)
                    startVoiceAction(sessionId)
                }
            }
        }

        return sessionId
    }

    /**
     * Prepares and starts standby wake-word listening with exclusive microphone ownership.
     */
    fun acquireForWakeWord(
        stopVoiceAction: () -> Unit,
        startWakeWordAction: () -> Unit
    ) {
        synchronized(micLock) {
            abandonAudioFocus()
            transitionTo(MicrophoneState.RELEASING_MIC, "CentralAudioController", null)
            stopVoiceAction()
            Log.i(TAG, "[MIC_OWNER] VoiceEngine SpeechRecognizer stopped and destroyed.")
        }

        mainHandler.post {
            synchronized(micLock) {
                Log.i(TAG, "[MIC_OWNER] Exclusive ownership granted to WakeWordEngine AudioRecord standby.")
                transitionTo(MicrophoneState.WAKE_WORD_LISTENING, "WakeWordEngine", null)
                startWakeWordAction()
            }
        }
    }

    fun setProcessing(sessionId: String?) {
        synchronized(micLock) {
            transitionTo(MicrophoneState.PROCESSING, "FridayCore", sessionId)
        }
    }

    fun setSpeaking(onReadyToSpeak: () -> Unit) {
        synchronized(micLock) {
            transitionTo(MicrophoneState.SPEAKING, "TextToSpeech", null)
            requestAudioFocus()
        }
        onReadyToSpeak()
    }

    fun onSpeakingCompleted(afterSpeakAction: () -> Unit) {
        synchronized(micLock) {
            abandonAudioFocus()
            transitionTo(MicrophoneState.RELEASING_MIC, "CentralAudioController", null)
        }
        mainHandler.post {
            afterSpeakAction()
        }
    }

    fun releaseMic(onComplete: (() -> Unit)? = null) {
        synchronized(micLock) {
            abandonAudioFocus()
            transitionTo(MicrophoneState.RELEASING_MIC, "CentralAudioController", null)
        }
        mainHandler.post {
            synchronized(micLock) {
                transitionTo(MicrophoneState.MIC_IDLE, "None", null)
            }
            onComplete?.invoke()
        }
    }

    private fun requestAudioFocus() {
        if (isAudioFocusHeld || audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* no-op */ }
                    .build()
                audioFocusRequest = focusRequest
                val result = audioManager.requestAudioFocus(focusRequest)
                isAudioFocusHeld = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                isAudioFocusHeld = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        if (!isAudioFocusHeld || audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus", e)
        } finally {
            isAudioFocusHeld = false
            audioFocusRequest = null
        }
    }
}
