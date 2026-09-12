package com.example.actions.tools

import android.content.Context
import android.util.Log
import com.example.actions.ActionResult
import com.example.core.FridayTelemetry
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Universal Tool Registry for FRIDAY.
 * All phone capabilities, system settings, communication actions, and UI automation
 * are registered here with uniform execution, permission checks, and telemetry tracking.
 */
object ToolRegistry {

    private const val TAG = "ToolRegistry"
    private val tools = ConcurrentHashMap<String, FridayTool>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        // Hardware & Device Controls
        register(FlashlightTool())
        register(VolumeTool())
        register(MediaControlTool())
        register(BrightnessTool())
        register(BatteryInfoTool())
        register(DateTimeTool())

        // Apps & Web
        register(AppLaunchTool())
        register(AppCloseTool())
        register(SearchWebTool())
        register(OpenUrlTool())

        // Time & Calendar
        register(TimerTool())
        register(AlarmTool())
        register(ShowTimersAlarmsTool())
        register(CalendarTool())

        // Communication
        register(WhatsAppCallTool())
        register(WhatsAppChatTool())
        register(WhatsAppMessageTool())
        register(NativePhoneCallTool())
        register(SendSmsTool())
        register(ContactSearchTool())
        register(SendEmailTool())
        register(ShareContentTool())
        register(ClipboardTool())

        // Maps & Navigation
        register(MapSearchTool())
        register(NavigationTool())

        // Camera & Files & Music
        register(CameraTool())
        register(GalleryTool())
        register(FileTool())
        register(PlayMusicTool())

        // Settings & System Panels
        register(SettingsActionTool())
        register(SystemPanelTool())

        // UI Automation via Accessibility
        register(UIAutomationTool())
    }

    fun register(tool: FridayTool) {
        tools[tool.id] = tool
        Log.d(TAG, "Registered tool: ${tool.id} (${tool.name})")
    }

    fun getTool(id: String): FridayTool? {
        return tools[id]
    }

    fun getAllTools(): List<FridayTool> {
        return tools.values.toList()
    }

    suspend fun executeTool(toolId: String, parameters: Map<String, String>, context: Context): ActionResult {
        val tool = tools[toolId] ?: run {
            Log.e(TAG, "Tool not found for id: $toolId")
            return ActionResult.NotSupported(toolId, "I don't have a tool configured for '$toolId'.")
        }

        FridayTelemetry.recordActionStarted(toolId)
        val startTime = System.currentTimeMillis()

        return try {
            val result = tool.execute(parameters, context)
            val duration = System.currentTimeMillis() - startTime
            val statusStr = when (result) {
                is ActionResult.Success -> "SUCCESS"
                is ActionResult.Failure -> "FAILURE"
                is ActionResult.NotSupported -> "NOT_SUPPORTED"
                is ActionResult.PermissionRequired -> "PERMISSION_REQUIRED"
                is ActionResult.MissingParameter -> "MISSING_PARAMETER"
                is ActionResult.NotFound -> "NOT_FOUND"
                is ActionResult.Cancelled -> "CANCELLED"
                is ActionResult.Timeout -> "TIMEOUT"
                is ActionResult.DisambiguationRequired -> "DISAMBIGUATION"
                is ActionResult.NeedsConfirmation -> "NEEDS_CONFIRMATION"
            }

            val msg = when (result) {
                is ActionResult.Success -> result.message
                is ActionResult.Failure -> result.error
                is ActionResult.NotSupported -> result.explanation
                is ActionResult.PermissionRequired -> result.explanation
                is ActionResult.MissingParameter -> result.prompt
                is ActionResult.NotFound -> result.message
                is ActionResult.Cancelled -> result.reason
                is ActionResult.Timeout -> result.action
                is ActionResult.DisambiguationRequired -> result.prompt
                is ActionResult.NeedsConfirmation -> result.action.description
            }

            FridayTelemetry.recordActionResult(toolId, statusStr, "$msg (${duration}ms)")
            val verified = tool.verifyResult(result)
            FridayTelemetry.recordVerificationResult(toolId, verified, statusStr)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing tool $toolId", e)
            val failure = ActionResult.Failure("Unexpected tool execution error: ${e.localizedMessage}")
            FridayTelemetry.recordActionResult(toolId, "EXCEPTION", e.message ?: "error")
            FridayTelemetry.recordVerificationResult(toolId, false, "Exception thrown")
            failure
        }
    }
}
