package com.example.security

import android.util.Log
import com.example.identity.FridayMood
import com.example.identity.FridayPersonality

/**
 * Enforces security constraints on all phone actions and AI requests.
 *
 * Core Guarantees:
 * - SAFE commands (flashlight, volume, time, battery, app launches) execute without voice identity friction.
 * - Sensitive commands and operations during security anomalies require explicit user confirmation.
 * - Malicious or disallowed actions are strictly BLOCKED.
 * - Expresses security situations through natural, respectful speech rather than clinical error logs.
 */
class SecurityManager {

    companion object {
        private const val TAG = "FridaySecurity"

        // Disallowed action types that are always blocked by policy
        private val BLOCKED_ACTION_TYPES = setOf(
            "EXECUTE_SHELL",
            "RUN_SCRIPT",
            "INSTALL_APK",
            "BYPASS_LOCK",
            "SECRET_RECORD",
            "EXPOSE_CREDENTIALS",
            "ROOT_ACCESS"
        )

        // Actions requiring explicit user confirmation
        private val CONFIRM_ACTION_TYPES = setOf(
            "CLEAR_DATA",
            "DELETE_FILE",
            "SEND_SMS",
            "MAKE_CALL",
            "MODIFY_SYSTEM_SETTINGS",
            "FACTORY_RESET",
            "DISABLE_SECURITY"
        )

        // Safe normal action types (Requirement 7: No voice identity check for normal commands)
        val SAFE_ACTION_TYPES = setOf(
            "TOGGLE_FLASHLIGHT",
            "CHANGE_VOLUME",
            "ADJUST_VOLUME",
            "LAUNCH_APP",
            "OPEN_APP",
            "CLOSE_APP",
            "SEARCH_WEB",
            "GET_BATTERY_INFO",
            "GET_DATE_TIME",
            "GET_TIME",
            "SET_TIMER",
            "MEDIA_CONTROL",
            "SPEAK_RESPONSE",
            "GENERAL_QUERY"
        )

        fun isSafeAction(actionType: String): Boolean {
            return SAFE_ACTION_TYPES.contains(actionType.uppercase().trim())
        }
    }

    /**
     * Evaluates a requested action against security policies, voice identity confidence,
     * and device security watcher signals.
     */
    fun evaluateAction(
        actionType: String,
        parameters: Map<String, String>,
        voiceConfidence: Float?,
        minVoiceThreshold: Float,
        watcherReport: SecurityWatcherReport? = null
    ): SecurityEvaluationResult {
        val normalizedType = actionType.uppercase().trim()

        // 1. Strict check: Disallowed actions
        if (BLOCKED_ACTION_TYPES.contains(normalizedType)) {
            Log.w(TAG, "Security policy BLOCKED requested action: $normalizedType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.BLOCKED,
                isPermitted = false,
                reason = "Boss, I'm not comfortable letting that continue. That action is blocked by FRIDAY security policy."
            )
        }

        // 2. Security Watcher Anomaly detected: elevate to CONFIRM
        if (watcherReport?.hasSuspiciousAnomaly == true && !isSafeAction(normalizedType)) {
            Log.w(TAG, "Security Watcher active anomaly elevated risk for: $normalizedType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = "Boss, something doesn't look right. Want me to secure the phone?",
                requiresBiometricOrConfirm = true
            )
        }

        // 3. Sensitive actions: Require explicit user confirmation
        if (CONFIRM_ACTION_TYPES.contains(normalizedType)) {
            Log.i(TAG, "Action requires CONFIRMATION: $normalizedType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = "Boss, that's a sensitive action. Are you sure you want to proceed?",
                requiresBiometricOrConfirm = true
            )
        }

        // 4. Voice Identity Confidence verification for sensitive operations
        if (voiceConfidence != null && voiceConfidence < minVoiceThreshold && !isSafeAction(normalizedType)) {
            Log.w(TAG, "Voice identity confidence ($voiceConfidence) is below threshold ($minVoiceThreshold) for action: $actionType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = "Boss, I'm not certain of your voice match. Please confirm manually to proceed.",
                requiresBiometricOrConfirm = true
            )
        }

        // 5. Safe normal actions (Flashlight, Volume, Battery, Time, App Launch, Timer)
        return SecurityEvaluationResult(
            riskLevel = ActionRiskLevel.SAFE,
            isPermitted = true,
            reason = "Action is categorized as SAFE for automatic execution."
        )
    }
}
