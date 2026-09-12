package com.example.core

import com.example.ai.StructuredAction
import com.example.identity.FridayMood
import com.example.voice.MicrophoneState

/**
 * Visual and operational states for the animated FRIDAY AI orb.
 */
enum class OrbState(val displayLabel: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    THINKING("Processing..."),
    SPEAKING("Speaking..."),
    WORKING("Executing..."),
    ERROR("Error"),
    OFFLINE("Offline")
}

/**
 * Explicit state machine for voice interaction lifecycle.
 */
enum class AssistantSessionState {
    STANDBY,
    WAKE_DETECTED,
    LISTENING,
    PROCESSING,
    EXECUTING,
    SPEAKING,
    FOLLOW_UP_LISTENING
}

/**
 * State snapshot of FRIDAY's runtime.
 */
data class FridayState(
    val orbState: OrbState = OrbState.IDLE,
    val sessionState: AssistantSessionState = AssistantSessionState.STANDBY,
    val mood: FridayMood = FridayMood.CALM,
    val isConversationActive: Boolean = false,
    val statusText: String = "FRIDAY — Ready",
    val batteryMode: BatteryMode = BatteryMode.NORMAL,
    val audioAmplitude: Float = 0f, // 0.0 to 1.0 for dynamic orb pulse
    val lastRecognizedText: String? = null,
    val currentSpeechResponse: String? = null,
    val pendingAction: StructuredAction? = null,
    val isWakeWordActive: Boolean = true,
    val isForegroundServiceRunning: Boolean = false,
    val isMicrophoneGranted: Boolean = false,
    val micState: MicrophoneState = MicrophoneState.MIC_IDLE,
    val voiceConfidence: Float? = null,
    val speechConfidence: Float? = null,
    val activeProviderName: String = "Gemini 3.5 Flash",
    val errorMessage: String? = null,
    val securityStatusMessage: String? = null
)
