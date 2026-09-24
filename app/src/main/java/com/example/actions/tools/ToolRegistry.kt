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
        // 1. Hardware & Device Controls
        register(FlashlightTool("FLASHLIGHT"))
        register(FlashlightTool("TOGGLE_FLASHLIGHT"))
        register(FlashlightStrobeTool("FLASHLIGHT_STROBE"))
        register(FlashlightStrobeTool("SOS_LIGHT"))
        register(VolumeTool("VOLUME_CONTROL"))
        register(VolumeTool("ADJUST_VOLUME"))
        register(RingerModeTool("RINGER_MODE"))
        register(RingerModeTool("SET_RINGER"))
        register(ScreenOrientationTool("SCREEN_ORIENTATION"))
        register(ScreenOrientationTool("AUTO_ROTATE"))
        register(ScreenTimeoutTool("SCREEN_TIMEOUT"))
        register(ScreenTimeoutTool("SET_SCREEN_TIMEOUT"))
        register(ScreenshotTool("TAKE_SCREENSHOT"))
        register(ScreenshotTool("SCREENSHOT"))
        register(LockScreenTool("LOCK_SCREEN"))
        register(PowerMenuTool("POWER_MENU"))
        register(PowerMenuTool("POWER_DIALOG"))
        register(SplitScreenTool("SPLIT_SCREEN"))
        register(VibratorTool("VIBRATE_DEVICE"))
        register(DarkModeTool("DARK_MODE"))
        register(MediaControlTool())
        register(BrightnessTool())
        register(BatteryInfoTool())
        register(DateTimeTool())

        // 2. Connectivity & Device Diagnostics
        register(WifiControlTool())
        register(BluetoothControlTool())
        register(HotspotControlTool())
        register(DeviceStatusTool())

        // 3. Location & GPS
        register(LocationTool())

        // 4. Apps & Web
        register(AppLaunchTool("APP_LAUNCH"))
        register(AppLaunchTool("OPEN_APP"))
        register(AppCloseTool("APP_ACTION"))
        register(AppCloseTool("CLOSE_APP"))
        register(SearchWebTool("WEB_SEARCH"))
        register(SearchWebTool("SEARCH_WEB"))
        register(OpenUrlTool())

        // 5. Time & Calendar
        register(TimerTool())
        register(AlarmTool())
        register(ShowTimersAlarmsTool())
        register(CalendarTool())
        register(CalendarReadTool())
        register(CalendarCreateTool())

        // 6. Personal Encrypted Memory
        register(MemorySaveTool())
        register(MemoryReadTool())
        register(MemoryDeleteTool())

        // 7. Communication & Contacts
        register(NativePhoneCallTool("PHONE_CALL"))
        register(NativePhoneCallTool("CALL_CONTACT"))
        register(ContactSearchTool("CONTACT_SEARCH"))
        register(ContactSearchTool("SEARCH_CONTACT"))
        register(WhatsAppCallTool())
        register(WhatsAppChatTool())
        register(WhatsAppMessageTool())
        register(SendSmsTool("SMS_SEND"))
        register(SendSmsTool("SEND_SMS"))
        register(SendEmailTool())
        register(ShareContentTool())
        register(ClipboardTool())

        // 8. Maps & Navigation
        register(MapSearchTool())
        register(NavigationTool())

        // 9. Camera & Files & Music
        register(CameraTool())
        register(GalleryTool())
        register(FileTool())
        register(PlayMusicTool("PLAY_MUSIC"))
        register(PlayMusicTool("MEDIA_SEARCH"))

        // 10. Settings & System Panels
        register(SettingsActionTool())
        register(SystemPanelTool())

        // 11. UI Automation via Accessibility
        register(UIAutomationTool())

        // 12. Call Control & Notifications & Vision
        register(CallControllerTool("PHONE_CALL_CONTROL"))
        register(CallControllerTool("CALL_CONTROLLER"))
        register(ReadNotificationsTool("NOTIFICATION_READ"))
        register(ReadNotificationsTool("READ_NOTIFICATIONS"))
        register(ReplyNotificationTool("REMOTE_INPUT"))
        register(ReplyNotificationTool("REPLY_NOTIFICATION"))
        register(ScreenVisionTool("SCREEN_CAPTURE"))
        register(ScreenVisionTool("SCREEN_ANALYSIS"))
        register(ScreenVisionTool("SCREEN_VISION"))

        // 13. Mobile & UPI Payments
        val paymentTool = PaymentTool()
        register(paymentTool)
        tools["PAYMENT"] = paymentTool
        tools["UPI_PAYMENT"] = paymentTool
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
