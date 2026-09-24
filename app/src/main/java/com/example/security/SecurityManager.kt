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
            "DISABLE_SECURITY",
            "PAYMENT",
            "UPI_PAYMENT",
            "SEND_MONEY",
            "PAY_MONEY",
            "TRANSFER_MONEY",
            "MEMORY_DELETE",
            "CLEAR_MEMORY"
        )

        // Actions permitted while device is locked (hands-free safe access)
        val LOCK_SAFE_ACTION_TYPES = setOf(
            "TOGGLE_FLASHLIGHT",
            "FLASHLIGHT",
            "GET_BATTERY_INFO",
            "BATTERY",
            "GET_DATE_TIME",
            "GET_TIME",
            "SYSTEM_INFO",
            "MEDIA_CONTROL",
            "MEDIA",
            "PLAY_MUSIC",
            "VOLUME",
            "ADJUST_VOLUME",
            "CHANGE_VOLUME",
            "SILENT_WORK_MODE",
            "UNMUTE",
            "SET_TIMER",
            "TIMER",
            "ALARM",
            "SET_ALARM",
            "SPEAK_RESPONSE",
            "GENERAL_QUERY"
        )

        // Safe normal action types (Requirement 7: No voice identity check for normal commands)
        val SAFE_ACTION_TYPES = setOf(
            "TOGGLE_FLASHLIGHT",
            "FLASHLIGHT",
            "CHANGE_VOLUME",
            "ADJUST_VOLUME",
            "VOLUME",
            "LAUNCH_APP",
            "OPEN_APP",
            "CLOSE_APP",
            "SEARCH_WEB",
            "WEB_SEARCH",
            "GET_BATTERY_INFO",
            "BATTERY",
            "GET_DATE_TIME",
            "GET_TIME",
            "SYSTEM_INFO",
            "SET_TIMER",
            "TIMER",
            "ALARM",
            "SET_ALARM",
            "MEDIA_CONTROL",
            "MEDIA",
            "PLAY_MUSIC",
            "SILENT_WORK_MODE",
            "UNMUTE",
            "SPEAK_RESPONSE",
            "GENERAL_QUERY"
        )

        fun isSafeAction(actionType: String): Boolean {
            return SAFE_ACTION_TYPES.contains(actionType.uppercase().trim())
        }
    }

    /**
     * Evaluates a requested action against layered security policies:
     * OWNER VOICE AUTHENTICATION -> ACTION RISK CHECK -> IF SAFE -> EXECUTE -> IF PROTECTED -> REQUIRE UNLOCK/CONFIRMATION
     */
    fun evaluateAction(
        actionType: String,
        parameters: Map<String, String>,
        voiceConfidence: Float?,
        minVoiceThreshold: Float,
        watcherReport: SecurityWatcherReport? = null,
        isVoiceVerifiedOwner: Boolean = true,
        isDeviceLocked: Boolean = watcherReport?.isDeviceLocked == true
    ): SecurityEvaluationResult {
        val normalizedType = actionType.uppercase().trim()

        // 1. Strict check: Disallowed actions
        if (BLOCKED_ACTION_TYPES.contains(normalizedType)) {
            Log.w(TAG, "Security policy BLOCKED requested action: $normalizedType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.BLOCKED,
                isPermitted = false,
                reason = "I'm not able to perform that action as it is restricted by security policy."
            )
        }

        // 2. Layered Security: Device Locked Checks
        if (isDeviceLocked) {
            // Layer 1: Owner Voice Authentication must succeed while device is locked
            if (!isVoiceVerifiedOwner) {
                Log.w(TAG, "Device is locked and speaker is NOT verified owner. Action rejected: $normalizedType")
                return SecurityEvaluationResult(
                    riskLevel = ActionRiskLevel.BLOCKED,
                    isPermitted = false,
                    reason = "Speaker not recognized. Only the device owner can execute commands while the phone is locked."
                )
            }

            // Layer 2: Action Risk Check on locked device — protected actions require legitimate Android unlock
            if (!LOCK_SAFE_ACTION_TYPES.contains(normalizedType)) {
                Log.w(TAG, "Device is locked. Protected command requires legitimate unlock: $normalizedType")
                return SecurityEvaluationResult(
                    riskLevel = ActionRiskLevel.CONFIRM,
                    isPermitted = false,
                    reason = "Please unlock your device to perform this action. I cannot access personal data or applications while your phone is locked.",
                    requiresBiometricOrConfirm = true
                )
            }
        }

        // 3. Security Watcher Anomaly detected: elevate to CONFIRM
        if (watcherReport?.hasSuspiciousAnomaly == true && !isSafeAction(normalizedType)) {
            Log.w(TAG, "Security Watcher active anomaly elevated risk for: $normalizedType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = "Unusual device condition detected. Please confirm before proceeding.",
                requiresBiometricOrConfirm = true
            )
        }

        // 4. Sensitive actions: Require explicit user confirmation & voice verification
        if (CONFIRM_ACTION_TYPES.contains(normalizedType)) {
            Log.i(TAG, "Action requires CONFIRMATION: $normalizedType")
            if (!isVoiceVerifiedOwner && voiceConfidence != null && voiceConfidence < minVoiceThreshold) {
                Log.w(TAG, "Voice identity confidence ($voiceConfidence) is below threshold ($minVoiceThreshold) for sensitive action: $actionType")
                return SecurityEvaluationResult(
                    riskLevel = ActionRiskLevel.BLOCKED,
                    isPermitted = false,
                    reason = "Voice identity not verified for sensitive operation. Action rejected."
                )
            }

            val prompt = if (normalizedType.contains("PAYMENT") || normalizedType.contains("MONEY")) {
                "Preparing payment. Please confirm to proceed to your banking app."
            } else {
                "This is a sensitive action. Are you sure you want to proceed?"
            }
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = prompt,
                requiresBiometricOrConfirm = true
            )
        }

        // 5. Voice Identity Confidence verification for non-safe actions when unlocked
        if (voiceConfidence != null && voiceConfidence < minVoiceThreshold && !isSafeAction(normalizedType)) {
            Log.w(TAG, "Voice identity confidence ($voiceConfidence) is below threshold ($minVoiceThreshold) for action: $actionType")
            return SecurityEvaluationResult(
                riskLevel = ActionRiskLevel.CONFIRM,
                isPermitted = true,
                reason = "Boss, I'm not certain of your voice match. Please confirm manually to proceed.",
                requiresBiometricOrConfirm = true
            )
        }

        // 6. Safe normal actions (Flashlight, Volume, Battery, Time, App Launch, Timer, Silent Mode)
        return SecurityEvaluationResult(
            riskLevel = ActionRiskLevel.SAFE,
            isPermitted = true,
            reason = "Action is categorized as SAFE for automatic execution."
        )
    }
}
