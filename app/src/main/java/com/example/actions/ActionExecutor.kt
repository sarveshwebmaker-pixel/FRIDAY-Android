package com.example.actions

import android.content.Context
import android.util.Log
import com.example.actions.planner.ActionPlanner
import com.example.actions.planner.ExecutionPlan
import com.example.actions.planner.PlannedStep
import com.example.actions.tools.FlashlightTool
import com.example.actions.tools.ToolRegistry
import com.example.core.FridayTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Action Executor for FRIDAY.
 * Directs incoming phone actions and multi-step plans to the Universal Tool Registry,
 * runs pre-flight permission and safety checks, and returns verified action results.
 */
class ActionExecutor(val context: Context) {

    companion object {
        private const val TAG = "ActionExecutor"

        var isTorchOn: Boolean
            get() = FlashlightTool.isTorchOn
            set(value) { FlashlightTool.isTorchOn = value }
    }

    suspend fun execute(action: PhoneAction): ActionResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "[ACTION_EXECUTION] Executing ${action.actionType}")
        FridayTelemetry.recordActionSelected(action.actionType)

        try {
            when (action) {
                is PhoneAction.ToggleFlashlight -> {
                    val params = mapOf("state" to if (action.enable) "on" else "off")
                    ToolRegistry.executeTool("TOGGLE_FLASHLIGHT", params, context)
                }

                is PhoneAction.AdjustVolume -> {
                    val params = mutableMapOf("direction" to action.direction)
                    if (action.level != null) params["level"] = action.level.toString()
                    ToolRegistry.executeTool("ADJUST_VOLUME", params, context)
                }

                is PhoneAction.MediaControl -> {
                    ToolRegistry.executeTool("MEDIA_CONTROL", mapOf("command" to action.command), context)
                }

                is PhoneAction.PlayMusic -> {
                    val params = mutableMapOf("query" to action.query)
                    if (action.artist != null) params["artist"] = action.artist
                    if (action.targetApp != null) params["targetApp"] = action.targetApp
                    ToolRegistry.executeTool("PLAY_MUSIC", params, context)
                }

                is PhoneAction.SetBrightness -> {
                    val params = mutableMapOf<String, String>()
                    if (action.level != null) params["level"] = action.level.toString()
                    if (action.openSettings) params["openSettings"] = "true"
                    ToolRegistry.executeTool("SET_BRIGHTNESS", params, context)
                }

                is PhoneAction.GetBatteryInfo -> {
                    ToolRegistry.executeTool("GET_BATTERY_INFO", emptyMap(), context)
                }

                is PhoneAction.GetDateTime -> {
                    ToolRegistry.executeTool("GET_DATE_TIME", emptyMap(), context)
                }

                is PhoneAction.OpenApp -> {
                    ToolRegistry.executeTool("OPEN_APP", mapOf("appName" to action.appName), context)
                }

                is PhoneAction.CloseApp -> {
                    val params = if (action.appName != null) mapOf("appName" to action.appName) else emptyMap()
                    ToolRegistry.executeTool("CLOSE_APP", params, context)
                }

                is PhoneAction.NavigateApp -> {
                    ToolRegistry.executeTool("OPEN_APP", mapOf("appName" to action.target), context)
                }

                is PhoneAction.SearchWeb -> {
                    val params = mutableMapOf("query" to action.query)
                    if (action.targetApp != null) params["targetApp"] = action.targetApp
                    ToolRegistry.executeTool("SEARCH_WEB", params, context)
                }

                is PhoneAction.OpenUrl -> {
                    ToolRegistry.executeTool("OPEN_URL", mapOf("url" to action.url), context)
                }

                is PhoneAction.SetTimer -> {
                    val params = mapOf(
                        "seconds" to action.durationSeconds.toString(),
                        "label" to action.label
                    )
                    ToolRegistry.executeTool("SET_TIMER", params, context)
                }

                is PhoneAction.SetAlarm -> {
                    val params = mapOf(
                        "hour" to action.hour.toString(),
                        "minute" to action.minute.toString(),
                        "message" to action.label
                    )
                    ToolRegistry.executeTool("SET_ALARM", params, context)
                }

                is PhoneAction.ShowTimersAlarms -> {
                    ToolRegistry.executeTool("SHOW_TIMERS_ALARMS", mapOf("mode" to action.mode), context)
                }

                is PhoneAction.CalendarEvent -> {
                    val params = mutableMapOf("title" to action.title)
                    if (action.description != null) params["description"] = action.description
                    ToolRegistry.executeTool("CALENDAR_EVENT", params, context)
                }

                is PhoneAction.WhatsAppCall -> {
                    ToolRegistry.executeTool("WHATSAPP_CALL", mapOf("contact" to action.contactName), context)
                }

                is PhoneAction.WhatsAppChat -> {
                    ToolRegistry.executeTool("WHATSAPP_CHAT", mapOf("contact" to action.contactName), context)
                }

                is PhoneAction.WhatsAppMessage -> {
                    val params = mutableMapOf("contact" to action.contactName)
                    if (action.message != null) params["message"] = action.message
                    ToolRegistry.executeTool("WHATSAPP_MESSAGE", params, context)
                }

                is PhoneAction.CallContact -> {
                    ToolRegistry.executeTool("CALL_CONTACT", mapOf("contact" to action.contactName), context)
                }

                is PhoneAction.SendSms -> {
                    val params = mutableMapOf("contact" to action.contactName)
                    if (action.message != null) params["message"] = action.message
                    ToolRegistry.executeTool("SEND_SMS", params, context)
                }

                is PhoneAction.SearchContact -> {
                    ToolRegistry.executeTool("SEARCH_CONTACT", mapOf("query" to action.query), context)
                }

                is PhoneAction.SendEmail -> {
                    val params = mutableMapOf("recipient" to action.recipient)
                    if (action.subject != null) params["subject"] = action.subject
                    if (action.body != null) params["body"] = action.body
                    ToolRegistry.executeTool("SEND_EMAIL", params, context)
                }

                is PhoneAction.ShareContent -> {
                    val params = mutableMapOf("content" to action.content)
                    if (action.title != null) params["title"] = action.title
                    ToolRegistry.executeTool("SHARE_CONTENT", params, context)
                }

                is PhoneAction.ClipboardAction -> {
                    val params = mutableMapOf("mode" to action.mode)
                    if (action.text != null) params["text"] = action.text
                    ToolRegistry.executeTool("CLIPBOARD_ACTION", params, context)
                }

                is PhoneAction.MapSearch -> {
                    ToolRegistry.executeTool("MAP_SEARCH", mapOf("location" to action.location), context)
                }

                is PhoneAction.StartNavigation -> {
                    ToolRegistry.executeTool("START_NAVIGATION", mapOf("destination" to action.destination), context)
                }

                is PhoneAction.CameraAction -> {
                    ToolRegistry.executeTool("CAMERA_ACTION", mapOf("mode" to action.mode), context)
                }

                is PhoneAction.GalleryAction -> {
                    ToolRegistry.executeTool("GALLERY_ACTION", emptyMap(), context)
                }

                is PhoneAction.FileAction -> {
                    val params = mutableMapOf("mode" to action.mode)
                    if (action.query != null) params["query"] = action.query
                    ToolRegistry.executeTool("FILE_ACTION", params, context)
                }

                is PhoneAction.SettingsAction -> {
                    ToolRegistry.executeTool("SETTINGS_ACTION", mapOf("setting" to action.settingType), context)
                }

                is PhoneAction.SystemPanelAction -> {
                    ToolRegistry.executeTool("SYSTEM_PANEL_ACTION", mapOf("panel" to action.panelType), context)
                }

                is PhoneAction.UIAutomationAction -> {
                    val params = mutableMapOf("operation" to action.operation)
                    if (action.target != null) params["target"] = action.target
                    if (action.text != null) params["inputText"] = action.text
                    if (action.x != null) params["x"] = action.x.toString()
                    if (action.y != null) params["y"] = action.y.toString()
                    ToolRegistry.executeTool("UI_AUTOMATION", params, context)
                }

                is PhoneAction.MultiStepAction -> {
                    val plannedSteps = action.steps.mapNotNull { step ->
                        when (step) {
                            is PhoneAction.SearchContact -> PlannedStep("SEARCH_CONTACT", mapOf("query" to step.query))
                            is PhoneAction.WhatsAppCall -> PlannedStep("WHATSAPP_CALL", mapOf("contact" to step.contactName))
                            is PhoneAction.CallContact -> PlannedStep("CALL_CONTACT", mapOf("contact" to step.contactName))
                            is PhoneAction.OpenApp -> PlannedStep("OPEN_APP", mapOf("appName" to step.appName))
                            is PhoneAction.SearchWeb -> PlannedStep("SEARCH_WEB", mapOf("query" to step.query))
                            is PhoneAction.ToggleFlashlight -> PlannedStep("TOGGLE_FLASHLIGHT", mapOf("state" to if (step.enable) "on" else "off"))
                            is PhoneAction.SetTimer -> PlannedStep("SET_TIMER", mapOf("seconds" to step.durationSeconds.toString(), "label" to step.label))
                            is PhoneAction.SettingsAction -> PlannedStep("SETTINGS_ACTION", mapOf("setting" to step.settingType))
                            is PhoneAction.UIAutomationAction -> PlannedStep("UI_AUTOMATION", mapOf("operation" to step.operation, "target" to (step.target ?: "")))
                            else -> null
                        }
                    }
                    ActionPlanner.executePlan(ExecutionPlan(plannedSteps), context)
                }

                is PhoneAction.SpeakResponse -> {
                    ActionResult.Success(action.message, spokenDetail = action.message)
                }

                is PhoneAction.SensitiveAction -> {
                    ActionResult.NeedsConfirmation(action)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action ${action.actionType}", e)
            ActionResult.Failure("Failed to execute ${action.actionType}: ${e.localizedMessage}")
        }
    }
}
