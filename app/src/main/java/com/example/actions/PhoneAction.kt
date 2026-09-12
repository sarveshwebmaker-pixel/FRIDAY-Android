package com.example.actions

import com.example.security.ActionRiskLevel

sealed class PhoneAction(
    val actionType: String,
    val riskLevel: ActionRiskLevel,
    val confirmationRequired: Boolean = false
) {
    // Hardware & Device
    data class ToggleFlashlight(val enable: Boolean) : PhoneAction("TOGGLE_FLASHLIGHT", ActionRiskLevel.SAFE)
    data class AdjustVolume(val direction: String, val level: Int? = null) : PhoneAction("ADJUST_VOLUME", ActionRiskLevel.SAFE)
    data class MediaControl(val command: String) : PhoneAction("MEDIA_CONTROL", ActionRiskLevel.SAFE)
    data class PlayMusic(val query: String, val artist: String? = null, val targetApp: String? = null) : PhoneAction("PLAY_MUSIC", ActionRiskLevel.SAFE)
    data class SetBrightness(val level: Int? = null, val openSettings: Boolean = false) : PhoneAction("SET_BRIGHTNESS", ActionRiskLevel.SAFE)
    data object GetBatteryInfo : PhoneAction("GET_BATTERY_INFO", ActionRiskLevel.SAFE)
    data object GetDateTime : PhoneAction("GET_DATE_TIME", ActionRiskLevel.SAFE)

    // Apps & Web
    data class OpenApp(val appName: String) : PhoneAction("OPEN_APP", ActionRiskLevel.SAFE)
    data class CloseApp(val appName: String? = null) : PhoneAction("CLOSE_APP", ActionRiskLevel.SAFE)
    data class NavigateApp(val target: String) : PhoneAction("NAVIGATE_APP", ActionRiskLevel.SAFE)
    data class SearchWeb(val query: String, val targetApp: String? = null) : PhoneAction("SEARCH_WEB", ActionRiskLevel.SAFE)
    data class OpenUrl(val url: String) : PhoneAction("OPEN_URL", ActionRiskLevel.SAFE)

    // Time & Calendar
    data class SetTimer(val durationSeconds: Int, val label: String = "") : PhoneAction("SET_TIMER", ActionRiskLevel.SAFE)
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : PhoneAction("SET_ALARM", ActionRiskLevel.SAFE)
    data class ShowTimersAlarms(val mode: String = "alarm") : PhoneAction("SHOW_TIMERS_ALARMS", ActionRiskLevel.SAFE)
    data class CalendarEvent(val title: String, val description: String? = null) : PhoneAction("CALENDAR_EVENT", ActionRiskLevel.SAFE)

    // Communication
    data class WhatsAppCall(val contactName: String) : PhoneAction("WHATSAPP_CALL", ActionRiskLevel.SAFE)
    data class WhatsAppChat(val contactName: String) : PhoneAction("WHATSAPP_CHAT", ActionRiskLevel.SAFE)
    data class WhatsAppMessage(val contactName: String, val message: String? = null) : PhoneAction("WHATSAPP_MESSAGE", ActionRiskLevel.SAFE)
    data class CallContact(val contactName: String) : PhoneAction("CALL_CONTACT", ActionRiskLevel.SAFE)
    data class SendSms(val contactName: String, val message: String? = null) : PhoneAction("SEND_SMS", ActionRiskLevel.SAFE)
    data class SearchContact(val query: String) : PhoneAction("SEARCH_CONTACT", ActionRiskLevel.SAFE)
    data class SendEmail(val recipient: String, val subject: String? = null, val body: String? = null) : PhoneAction("SEND_EMAIL", ActionRiskLevel.SAFE)
    data class ShareContent(val content: String, val title: String? = null) : PhoneAction("SHARE_CONTENT", ActionRiskLevel.SAFE)
    data class ClipboardAction(val mode: String, val text: String? = null) : PhoneAction("CLIPBOARD_ACTION", ActionRiskLevel.SAFE)

    // Maps & Places
    data class MapSearch(val location: String) : PhoneAction("MAP_SEARCH", ActionRiskLevel.SAFE)
    data class StartNavigation(val destination: String) : PhoneAction("START_NAVIGATION", ActionRiskLevel.SAFE)

    // Camera & Media Files
    data class CameraAction(val mode: String) : PhoneAction("CAMERA_ACTION", ActionRiskLevel.SAFE) // open, photo, video
    data object GalleryAction : PhoneAction("GALLERY_ACTION", ActionRiskLevel.SAFE)
    data class FileAction(val mode: String, val query: String? = null) : PhoneAction("FILE_ACTION", ActionRiskLevel.SAFE) // open, downloads

    // Settings & System Panels
    data class SettingsAction(val settingType: String) : PhoneAction("SETTINGS_ACTION", ActionRiskLevel.SAFE)
    data class SystemPanelAction(val panelType: String) : PhoneAction("SYSTEM_PANEL_ACTION", ActionRiskLevel.SAFE) // notifications, quick_settings

    // UI Automation (Accessibility Service)
    data class UIAutomationAction(
        val operation: String, // click, type, scroll_down, scroll_up, back, home, recents, read_screen
        val target: String? = null,
        val text: String? = null,
        val x: Float? = null,
        val y: Float? = null
    ) : PhoneAction("UI_AUTOMATION", ActionRiskLevel.SAFE)

    // Multi-Step Planner Execution
    data class MultiStepAction(val steps: List<PhoneAction>) : PhoneAction("MULTI_STEP_PLAN", ActionRiskLevel.SAFE)

    // General Responses & Confirmation
    data class SpeakResponse(val message: String) : PhoneAction("SPEAK_RESPONSE", ActionRiskLevel.SAFE)
    data class SensitiveAction(
        val type: String,
        val description: String,
        val params: Map<String, String>
    ) : PhoneAction(type, ActionRiskLevel.CONFIRM, confirmationRequired = true)
}

sealed class ActionResult {
    data class Success(
        val message: String,
        val spokenDetail: String? = null,
        val outputData: Map<String, String> = emptyMap()
    ) : ActionResult()

    data class Failure(
        val error: String,
        val userMessage: String? = null
    ) : ActionResult()

    data class NotSupported(
        val feature: String,
        val explanation: String
    ) : ActionResult()

    data class PermissionRequired(
        val permission: String,
        val explanation: String
    ) : ActionResult()

    data class MissingParameter(
        val parameterName: String,
        val prompt: String
    ) : ActionResult()

    data class NotFound(
        val item: String,
        val message: String
    ) : ActionResult()

    data class Cancelled(
        val reason: String
    ) : ActionResult()

    data class Timeout(
        val action: String
    ) : ActionResult()

    data class DisambiguationRequired(
        val prompt: String,
        val candidates: List<String>
    ) : ActionResult()

    data class NeedsConfirmation(
        val action: PhoneAction.SensitiveAction
    ) : ActionResult()
}
