package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.identity.FridayMood

data class SecurityRiskSignal(
    val title: String,
    val description: String,
    val severity: RiskSeverity
)

enum class RiskSeverity {
    LOW,
    MEDIUM,
    HIGH
}

data class SecurityWatcherReport(
    val hasSuspiciousAnomaly: Boolean,
    val summary: String,
    val mood: FridayMood,
    val signals: List<SecurityRiskSignal>,
    val suggestedPrompt: String? = null,
    val isDeviceLocked: Boolean = false
)

/**
 * Lightweight, privacy-first Security Watcher for FRIDAY.
 *
 * Compliance Guarantees:
 * - Zero screen recording.
 * - Zero unauthorized audio capture.
 * - Zero continuous screenshot sending.
 * - Uses only official, battery-efficient Android system APIs.
 */
class FridaySecurityWatcher(private val context: Context) {

    companion object {
        private const val TAG = "FridaySecurityWatcher"
        private const val MAX_SUSPICIOUS_FAILURES = 3
    }

    private var consecutiveSecurityFailures = 0
    private var lastAnomalyTimestamp: Long = 0L

    /**
     * Inspects current device signals for security-relevant events using official Android APIs.
     */
    fun inspectCurrentState(): SecurityWatcherReport {
        val signals = mutableListOf<SecurityRiskSignal>()

        // 1. Device Lock & Keyguard status
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isDeviceLocked == true
        val isSecure = keyguardManager?.isKeyguardSecure == true

        if (!isSecure) {
            signals.add(
                SecurityRiskSignal(
                    title = "Device Lock Inactive",
                    description = "Phone has no PIN, pattern, or biometric lock screen configured.",
                    severity = RiskSeverity.MEDIUM
                )
            )
        }

        // 2. Developer Mode / USB Debugging status
        try {
            val isAdbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            if (isAdbEnabled) {
                signals.add(
                    SecurityRiskSignal(
                        title = "ADB Debugging Active",
                        description = "USB debugging bridge is enabled on this device.",
                        severity = RiskSeverity.LOW
                    )
                )
            }
        } catch (_: Exception) {
            // Permission or setting restricted
        }

        // 3. Network Security inspection
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
        if (caps != null) {
            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!isValidated) {
                signals.add(
                    SecurityRiskSignal(
                        title = "Unvalidated Network Connection",
                        description = "Active network connectivity has not completed secure gateway validation.",
                        severity = RiskSeverity.LOW
                    )
                )
            }
        }

        // 4. Critical Battery / Thermal Strain
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (batteryLevel in 0..5) {
            signals.add(
                SecurityRiskSignal(
                    title = "Critical Power State",
                    description = "Battery level critically depleted ($batteryLevel%).",
                    severity = RiskSeverity.LOW
                )
            )
        }

        // 5. Consecutive Authentication / Policy Failures
        if (consecutiveSecurityFailures >= MAX_SUSPICIOUS_FAILURES) {
            signals.add(
                SecurityRiskSignal(
                    title = "Repeated Security Policy Violations",
                    description = "$consecutiveSecurityFailures unauthorized or blocked actions detected in recent sequence.",
                    severity = RiskSeverity.HIGH
                )
            )
        }

        val highRisk = signals.any { it.severity == RiskSeverity.HIGH }
        val mediumRisk = signals.any { it.severity == RiskSeverity.MEDIUM }

        val hasSuspiciousAnomaly = highRisk || (mediumRisk && consecutiveSecurityFailures > 0)
        val mood = when {
            highRisk -> FridayMood.SERIOUS
            mediumRisk -> FridayMood.CONCERNED
            else -> FridayMood.CALM
        }

        val summary = when {
            highRisk -> "High security risk detected."
            mediumRisk -> "Something seems unusual with device security."
            else -> "Device state secure and normal."
        }

        val prompt = if (hasSuspiciousAnomaly) {
            "Something doesn't look right. Want me to secure the phone?"
        } else null

        return SecurityWatcherReport(
            hasSuspiciousAnomaly = hasSuspiciousAnomaly,
            summary = summary,
            mood = mood,
            signals = signals,
            suggestedPrompt = prompt,
            isDeviceLocked = isLocked
        )
    }

    fun recordSecurityFailure() {
        consecutiveSecurityFailures++
        lastAnomalyTimestamp = System.currentTimeMillis()
        Log.w(TAG, "Recorded security event. Consecutive count: $consecutiveSecurityFailures")
    }

    fun resetSecurityFailures() {
        consecutiveSecurityFailures = 0
    }
}
