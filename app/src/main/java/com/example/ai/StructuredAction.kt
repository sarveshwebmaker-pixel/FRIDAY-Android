package com.example.ai

import com.example.security.ActionRiskLevel

/**
 * Structured phone action representation returned by the AI brain.
 * Instead of arbitrary code execution, FRIDAY acts strictly on validated structured actions.
 */
data class StructuredAction(
    val intent: String,
    val actionType: String,
    val parameters: Map<String, String> = emptyMap(),
    val speechResponse: String,
    val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE,
    val confirmationPrompt: String? = null,
    val steps: List<StructuredAction> = emptyList()
)

sealed class FridayAiResult {
    data class Success(val action: StructuredAction) : FridayAiResult()
    data class Error(val message: String, val isRecoverable: Boolean = true) : FridayAiResult()
}
