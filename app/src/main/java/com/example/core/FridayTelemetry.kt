package com.example.core

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommandLatency(
    val wakeDetectionMs: Long = 0L,
    val speechRecognitionMs: Long = 0L,
    val intentProcessingMs: Long = 0L,
    val actionExecutionMs: Long = 0L,
    val ttsStartMs: Long = 0L,
    val totalCommandMs: Long = 0L,
    val command: String = "",
    val executionPath: String = "FAST_LOCAL"
)

data class TelemetryEvent(
    val stage: String,
    val detail: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val stepDurationMs: Long = 0L
)

/**
 * Developer Debug Mode & Real-time Latency Tracker for FRIDAY.
 * Logs precise timing across the audio, speech recognition, intent parsing,
 * tool planning, execution, verification, and TTS pipelines.
 */
object FridayTelemetry {

    private const val TAG = "FridayTelemetry"

    private var sessionStartTimeMs: Long = 0L
    private var lastStageTimeMs: Long = 0L
    private var currentSessionActive = false

    // Precision timestamp markers for requested latency metrics
    private var wakeDetectedTimeMs: Long = 0L
    private var speechStartTimeMs: Long = 0L
    private var speechEndTimeMs: Long = 0L
    private var intentStartTimeMs: Long = 0L
    private var intentEndTimeMs: Long = 0L
    private var actionStartTimeMs: Long = 0L
    private var actionEndTimeMs: Long = 0L
    private var ttsStartTimeMs: Long = 0L
    private var currentCommandText: String = ""
    private var currentExecutionPath: String = "FAST_LOCAL"

    private val _latestLatency = MutableStateFlow<CommandLatency?>(null)
    val latestLatency: StateFlow<CommandLatency?> = _latestLatency.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<TelemetryEvent>>(emptyList())
    val recentLogs: StateFlow<List<TelemetryEvent>> = _recentLogs.asStateFlow()

    fun startSession(trigger: String) {
        val now = SystemClock.elapsedRealtime()
        sessionStartTimeMs = now
        lastStageTimeMs = now
        wakeDetectedTimeMs = now
        speechStartTimeMs = 0L
        speechEndTimeMs = 0L
        intentStartTimeMs = 0L
        intentEndTimeMs = 0L
        actionStartTimeMs = 0L
        actionEndTimeMs = 0L
        ttsStartTimeMs = 0L
        currentCommandText = ""
        currentExecutionPath = "FAST_LOCAL"
        currentSessionActive = true
        logEvent("WAKE_DETECTED", "Trigger: $trigger")
    }

    fun recordWakeDetected(trigger: String = "wake_word") {
        startSession(trigger)
    }

    fun recordMicAcquired(component: String) {
        logEvent("MIC_ACQUIRED", "Owner: $component")
    }

    fun recordMicReleased(component: String) {
        logEvent("MIC_RELEASED", "Released by: $component")
    }

    fun recordCommandStarted() {
        speechStartTimeMs = SystemClock.elapsedRealtime()
        logEvent("COMMAND_STARTED", "Speech recognizer listening")
    }

    fun recordCommandEnded(transcript: String, confidence: Float = 1.0f) {
        speechEndTimeMs = SystemClock.elapsedRealtime()
        currentCommandText = transcript
        logEvent("COMMAND_ENDED", "Speech ended. Confidence: ${(confidence * 100).toInt()}%")
        logEvent("TRANSCRIPT", "\"$transcript\"")
    }

    fun recordSpeechRecognized(transcript: String, confidence: Float = 1.0f) {
        recordCommandEnded(transcript, confidence)
    }

    fun recordFastPathExecution() {
        currentExecutionPath = "FAST_LOCAL"
        logEvent("FAST_PATH", "Local fast path routing engaged")
    }

    fun recordIntentStarted(executionPath: String = "FAST_LOCAL") {
        intentStartTimeMs = SystemClock.elapsedRealtime()
        currentExecutionPath = executionPath
        logEvent("INTENT_STARTED", "Path: $executionPath")
    }

    fun recordIntentFinished(intent: String, parameters: Map<String, String>) {
        intentEndTimeMs = SystemClock.elapsedRealtime()
        recordIntent(intent, parameters)
    }

    fun recordIntent(intent: String, parameters: Map<String, String>) {
        logEvent("INTENT", intent)
        logEvent("PARAMETERS", parameters.toString())
    }

    fun recordActionExecutionStarted(action: String = "ACTION") {
        actionStartTimeMs = SystemClock.elapsedRealtime()
        recordActionStarted(action)
    }

    fun recordActionExecutionFinished(action: String, status: String, detail: String) {
        actionEndTimeMs = SystemClock.elapsedRealtime()
        recordActionResult(action, status, detail)
    }

    fun recordActionSelected(action: String) {
        logEvent("ACTION_SELECTED", action)
    }

    fun recordPermissionCheck(permission: String, granted: Boolean) {
        logEvent("PERMISSION_CHECK", "$permission -> granted: $granted")
    }

    fun recordActionStarted(action: String) {
        logEvent("ACTION_STARTED", "Executing: $action")
    }

    fun recordActionResult(action: String, status: String, detail: String) {
        logEvent("ACTION_RESULT", "[$status] $action: $detail")
    }

    fun recordVerificationResult(action: String, verified: Boolean, message: String) {
        logEvent("VERIFICATION_RESULT", "$action -> verified: $verified ($message)")
    }

    fun recordTtsStarted(text: String = "") {
        ttsStartTimeMs = SystemClock.elapsedRealtime()
        logEvent("TTS_STARTED", "Speaking: \"$text\"")
    }

    fun recordCommandComplete(isSuccess: Boolean = true, errorMessage: String? = null) {
        val now = SystemClock.elapsedRealtime()
        if (!isSuccess) {
            logEvent("COMMAND_FAILED", errorMessage ?: "Unknown error")
        } else {
            logEvent("COMMAND_SUCCESS", "Execution verified successfully")
        }
        val wakeDuration = if (wakeDetectedTimeMs > 0 && speechStartTimeMs > wakeDetectedTimeMs) {
            speechStartTimeMs - wakeDetectedTimeMs
        } else 0L

        val speechDuration = if (speechStartTimeMs > 0 && speechEndTimeMs > speechStartTimeMs) {
            speechEndTimeMs - speechStartTimeMs
        } else 0L

        val intentDuration = if (intentStartTimeMs > 0 && intentEndTimeMs > intentStartTimeMs) {
            intentEndTimeMs - intentStartTimeMs
        } else 0L

        val actionDuration = if (actionStartTimeMs > 0 && actionEndTimeMs > actionStartTimeMs) {
            actionEndTimeMs - actionStartTimeMs
        } else 0L

        val ttsStartDelay = if (speechEndTimeMs > 0 && ttsStartTimeMs > speechEndTimeMs) {
            ttsStartTimeMs - speechEndTimeMs
        } else 0L

        val totalMs = if (sessionStartTimeMs > 0) now - sessionStartTimeMs else 0L

        val latency = CommandLatency(
            wakeDetectionMs = wakeDuration,
            speechRecognitionMs = speechDuration,
            intentProcessingMs = intentDuration,
            actionExecutionMs = actionDuration,
            ttsStartMs = ttsStartDelay,
            totalCommandMs = totalMs,
            command = currentCommandText,
            executionPath = currentExecutionPath
        )
        _latestLatency.value = latency
        Log.i(TAG, "[LATENCY_METRICS] $latency")
    }

    fun recordTtsFinished() {
        val totalMs = if (sessionStartTimeMs > 0) SystemClock.elapsedRealtime() - sessionStartTimeMs else 0L
        logEvent("TTS_FINISHED", "Speech playback complete")
        logEvent("TOTAL_LATENCY", "${totalMs}ms total pipeline duration")
        recordCommandComplete()
        currentSessionActive = false
    }

    private fun logEvent(stage: String, detail: String) {
        val now = SystemClock.elapsedRealtime()
        val durationFromLast = if (lastStageTimeMs > 0) now - lastStageTimeMs else 0L
        lastStageTimeMs = now

        val totalFromStart = if (sessionStartTimeMs > 0) now - sessionStartTimeMs else 0L

        Log.i(TAG, "[FRIDAY_TIMING] $stage: $detail (step: +${durationFromLast}ms, total: ${totalFromStart}ms)")

        val event = TelemetryEvent(
            stage = stage,
            detail = detail,
            stepDurationMs = durationFromLast
        )

        val updated = (_recentLogs.value + event).takeLast(40)
        _recentLogs.value = updated
    }
}
