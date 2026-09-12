package com.example.actions.tools

import android.content.Context
import com.example.actions.ActionResult
import com.example.security.ActionRiskLevel

/**
 * Universal Android Tool Interface.
 * Every legitimate Android capability exposed to FRIDAY is encapsulated as a distinct,
 * validated, auditable tool with strict pre-conditions, execution logic, and post-verification.
 */
interface FridayTool {
    val id: String
    val name: String
    val description: String
    val requiredPermissions: List<String>
        get() = emptyList()
    val requiresAccessibility: Boolean
        get() = false
    val riskLevel: ActionRiskLevel
        get() = ActionRiskLevel.SAFE

    suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult

    fun verifyResult(result: ActionResult): Boolean {
        return result is ActionResult.Success
    }
}
