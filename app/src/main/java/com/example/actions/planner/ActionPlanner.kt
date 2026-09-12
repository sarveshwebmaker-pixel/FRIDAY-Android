package com.example.actions.planner

import android.content.Context
import android.util.Log
import com.example.actions.ActionResult
import com.example.actions.tools.ToolRegistry
import com.example.core.FridayTelemetry

data class PlannedStep(
    val toolId: String,
    val parameters: Map<String, String> = emptyMap(),
    val description: String = ""
)

data class ExecutionPlan(
    val steps: List<PlannedStep>
)

/**
 * Action Planner capable of executing multi-step command sequences.
 * Executes steps sequentially, carries forward context/output parameters from preceding steps,
 * halts on failures or safety prompts, and verifies completion at every milestone.
 */
object ActionPlanner {

    private const val TAG = "ActionPlanner"

    /**
     * Executes a structured multi-step plan.
     */
    suspend fun executePlan(
        plan: ExecutionPlan,
        context: Context,
        toolExecutor: suspend (String, Map<String, String>) -> ActionResult = { id, params ->
            ToolRegistry.executeTool(id, params, context)
        }
    ): ActionResult {
        Log.i(TAG, "Executing plan with ${plan.steps.size} steps")
        val accumulatedContext = mutableMapOf<String, String>()
        var lastSuccessMessage = "Plan executed successfully."
        val stepResults = mutableListOf<String>()

        for ((index, step) in plan.steps.withIndex()) {
            val stepNumber = index + 1
            Log.i(TAG, "Executing Step $stepNumber/${plan.steps.size}: ${step.toolId}")
            FridayTelemetry.recordActionStarted("STEP_$stepNumber:${step.toolId}")

            // Merge accumulated context into step parameters (e.g. resolved contact name or number from step 1)
            val effectiveParams = step.parameters.toMutableMap()
            for ((key, value) in accumulatedContext) {
                if (!effectiveParams.containsKey(key) || effectiveParams[key].isNullOrBlank()) {
                    effectiveParams[key] = value
                }
            }

            val stepResult = toolExecutor(step.toolId, effectiveParams)

            when (stepResult) {
                is ActionResult.Success -> {
                    accumulatedContext.putAll(stepResult.outputData)
                    lastSuccessMessage = stepResult.spokenDetail ?: stepResult.message
                    stepResults.add("Step $stepNumber: ${stepResult.message}")
                    Log.i(TAG, "Step $stepNumber succeeded. Context carried forward: $accumulatedContext")
                }

                is ActionResult.Failure,
                is ActionResult.NotFound,
                is ActionResult.NotSupported,
                is ActionResult.PermissionRequired,
                is ActionResult.MissingParameter,
                is ActionResult.DisambiguationRequired,
                is ActionResult.NeedsConfirmation,
                is ActionResult.Cancelled,
                is ActionResult.Timeout -> {
                    Log.w(TAG, "Step $stepNumber (${step.toolId}) halted plan with result: $stepResult")
                    return stepResult
                }
            }
        }

        val spokenSummary = if (plan.steps.size > 1) {
            "Completed all ${plan.steps.size} actions."
        } else {
            lastSuccessMessage
        }

        return ActionResult.Success(
            message = "Plan completed (${stepResults.size} steps): ${stepResults.joinToString("; ")}",
            spokenDetail = spokenSummary,
            outputData = accumulatedContext
        )
    }

    /**
     * Parses compound sentences (e.g. "turn off flashlight and set timer for 5 minutes",
     * "find Rahul in contacts and call him on WhatsApp") into a multi-step execution plan.
     */
    fun createPlanFromConjunctions(
        rawCommand: String,
        actionParser: (String) -> PlannedStep?
    ): ExecutionPlan? {
        val lower = rawCommand.lowercase().trim()
        val splitDelimiters = listOf(" and then ", " then ", " and ")

        var segments: List<String> = emptyList()
        for (delim in splitDelimiters) {
            if (lower.contains(delim)) {
                segments = rawCommand.split(Regex(Regex.escape(delim), RegexOption.IGNORE_CASE))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (segments.size > 1) break
            }
        }

        if (segments.size <= 1) return null

        val steps = mutableListOf<PlannedStep>()
        for (seg in segments) {
            val step = actionParser(seg)
            if (step != null) {
                steps.add(step)
            } else {
                // If any segment cannot be resolved into a valid step, don't guess an incomplete plan
                return null
            }
        }

        return if (steps.size > 1) ExecutionPlan(steps) else null
    }
}
