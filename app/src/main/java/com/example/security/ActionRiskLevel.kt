package com.example.security

/**
 * Security tiers for FRIDAY phone operations.
 *
 * SAFE: Can execute automatically without interrupting the user (e.g. read time, battery check, flashlight).
 * CONFIRM: Requires explicit user approval before execution (e.g. wipe data, modify sensitive settings).
 * BLOCKED: Security violation; FRIDAY is strictly prohibited from performing (e.g. arbitrary code execution, security bypass).
 */
enum class ActionRiskLevel {
    SAFE,
    CONFIRM,
    BLOCKED
}

data class SecurityEvaluationResult(
    val riskLevel: ActionRiskLevel,
    val isPermitted: Boolean,
    val reason: String,
    val requiresBiometricOrConfirm: Boolean = false
)
