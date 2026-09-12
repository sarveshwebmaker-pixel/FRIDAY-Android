package com.example.skills

import com.example.actions.ActionExecutor
import com.example.actions.ActionResult
import com.example.ai.StructuredAction

/**
 * Pluggable capability contract for FRIDAY.
 * New skills can be added without modifying the core orchestrator.
 */
interface FridaySkill {
    val id: String
    val name: String
    val description: String

    fun canHandle(intent: String, rawText: String): Boolean
    suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult
}
