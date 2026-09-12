package com.example.skills

import com.example.actions.ActionExecutor
import com.example.actions.ActionResult
import com.example.actions.PhoneAction
import com.example.ai.StructuredAction

class DeviceControlSkill : FridaySkill {
    override val id: String = "skill_device_control"
    override val name: String = "Device Controls"
    override val description: String = "Controls phone hardware: flashlight, volume, media playback, brightness."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "DEVICE_CONTROL" || intent == "MEDIA" || intent == "PLAY_MUSIC" ||
                lower.contains("flashlight") || lower.contains("torch") || lower.contains("light") ||
                lower.contains("volume") || lower.contains("mute") || lower.contains("unmute") ||
                lower.contains("media") || lower.contains("pause") || lower.contains("resume") ||
                lower.contains("next song") || lower.contains("previous song") || lower.contains("play song") ||
                lower.startsWith("play ") || lower.contains("brightness")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        val target = (action.parameters["target"] ?: action.actionType).lowercase()
        val state = action.parameters["state"] ?: ""

        return when {
            action.actionType == "PLAY_MUSIC" || action.intent == "PLAY_MUSIC" -> {
                val query = action.parameters["query"] ?: action.parameters["song"] ?: "music"
                val artist = action.parameters["artist"]
                val targetApp = action.parameters["targetApp"] ?: "YouTube"
                executor.execute(PhoneAction.PlayMusic(query, artist, targetApp))
            }

            action.actionType == "TOGGLE_FLASHLIGHT" || target.contains("flashlight") || target.contains("torch") || target.contains("light") -> {
                val enable = when {
                    state == "false" || state == "off" -> false
                    state == "true" || state == "on" -> true
                    else -> !ActionExecutor.isTorchOn
                }
                executor.execute(PhoneAction.ToggleFlashlight(enable))
            }

            action.actionType == "ADJUST_VOLUME" || target.contains("volume") -> {
                val direction = action.parameters["direction"] ?: "up"
                val level = action.parameters["level"]?.toIntOrNull()
                executor.execute(PhoneAction.AdjustVolume(direction, level))
            }

            action.actionType == "MEDIA_CONTROL" || target.contains("media") || target.contains("music") -> {
                val command = action.parameters["command"] ?: action.parameters["action"] ?: "play"
                executor.execute(PhoneAction.MediaControl(command))
            }

            action.actionType == "SET_BRIGHTNESS" || target.contains("brightness") -> {
                val level = action.parameters["level"]?.toIntOrNull()
                val openSettings = action.parameters["openSettings"]?.equals("true", ignoreCase = true) ?: false
                executor.execute(PhoneAction.SetBrightness(level, openSettings))
            }

            else -> ActionResult.Failure("Unsupported device control parameter")
        }
    }
}

class SystemInfoSkill : FridaySkill {
    override val id: String = "skill_system_info"
    override val name: String = "System Info"
    override val description = "Reports phone status such as battery percentage and current date/time."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "SYSTEM_INFO" ||
                lower.contains("battery") || lower.contains("percentage") ||
                lower.contains("time") || lower.contains("date") || lower.contains("day")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        val query = action.parameters["query"] ?: action.intent
        return if (query.contains("battery", ignoreCase = true) || action.actionType == "GET_BATTERY_INFO") {
            executor.execute(PhoneAction.GetBatteryInfo)
        } else {
            executor.execute(PhoneAction.GetDateTime)
        }
    }
}

class AppLauncherSkill : FridaySkill {
    override val id: String = "skill_app_launcher"
    override val name: String = "App Launcher"
    override val description = "Launches, closes, and navigates applications installed on your phone."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "LAUNCH_APP" || intent == "OPEN_APP" || intent == "CLOSE_APP" ||
                lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("close ")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        if (action.actionType == "CLOSE_APP" || action.intent == "CLOSE_APP") {
            return executor.execute(PhoneAction.CloseApp(action.parameters["appName"]))
        }
        val appName = action.parameters["appName"] ?: action.parameters["app"] ?: "Settings"
        return executor.execute(PhoneAction.OpenApp(appName))
    }
}

class SearchWebSkill : FridaySkill {
    override val id: String = "skill_search_web"
    override val name: String = "Web & URL Search"
    override val description = "Performs searches in web browsers, dedicated apps, or opens web URLs."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "SEARCH_WEB" || intent == "OPEN_URL" ||
                lower.startsWith("search ") || lower.startsWith("google ") || lower.startsWith("browse ")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        if (action.actionType == "OPEN_URL") {
            val url = action.parameters["url"] ?: ""
            return executor.execute(PhoneAction.OpenUrl(url))
        }
        val query = action.parameters["query"] ?: "news"
        val targetApp = action.parameters["targetApp"]
        return executor.execute(PhoneAction.SearchWeb(query, targetApp))
    }
}

class ClockTimerSkill : FridaySkill {
    override val id: String = "skill_timer"
    override val name: String = "Timers, Alarms & Calendar"
    override val description = "Sets countdown timers, alarms, and adds calendar events."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "SET_TIMER" || intent == "SET_ALARM" || intent == "SHOW_TIMERS_ALARMS" || intent == "CALENDAR_EVENT" ||
                lower.contains("timer") || lower.contains("alarm") || lower.contains("calendar") || lower.contains("reminder")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        return when (action.actionType) {
            "SET_ALARM" -> {
                val hour = action.parameters["hour"]?.toIntOrNull() ?: 8
                val min = action.parameters["minute"]?.toIntOrNull() ?: 0
                val label = action.parameters["label"] ?: "FRIDAY Alarm"
                executor.execute(PhoneAction.SetAlarm(hour, min, label))
            }
            "SHOW_TIMERS_ALARMS" -> {
                val mode = action.parameters["mode"] ?: "alarm"
                executor.execute(PhoneAction.ShowTimersAlarms(mode))
            }
            "CALENDAR_EVENT" -> {
                val title = action.parameters["title"] ?: "Event"
                val desc = action.parameters["description"]
                executor.execute(PhoneAction.CalendarEvent(title, desc))
            }
            else -> {
                val seconds = action.parameters["seconds"]?.toIntOrNull() ?: 60
                val label = action.parameters["label"] ?: "FRIDAY Timer"
                executor.execute(PhoneAction.SetTimer(seconds, label))
            }
        }
    }
}

class CommunicationSkill : FridaySkill {
    override val id: String = "skill_communication"
    override val name: String = "Communications & Messaging"
    override val description = "Handles WhatsApp calls, WhatsApp chat, WhatsApp messages, native phone calls, SMS, contacts, email, clipboard, and sharing."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent in listOf("WHATSAPP_CALL", "WHATSAPP_CHAT", "WHATSAPP_MESSAGE", "CALL_CONTACT", "SEND_SMS", "SEARCH_CONTACT", "SEND_EMAIL", "SHARE_CONTENT", "CLIPBOARD_ACTION") ||
                lower.contains("whatsapp") || lower.startsWith("call ") || lower.startsWith("dial ") ||
                lower.startsWith("text ") || lower.startsWith("message ") || lower.startsWith("sms ") ||
                lower.startsWith("email ") || lower.contains("contact") || lower.contains("clipboard") || lower.contains("share ")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        val contact = action.parameters["contact"] ?: action.parameters["name"] ?: ""
        val message = action.parameters["message"] ?: action.parameters["text"]

        return when (action.actionType) {
            "WHATSAPP_CALL" -> executor.execute(PhoneAction.WhatsAppCall(contact))
            "WHATSAPP_CHAT" -> executor.execute(PhoneAction.WhatsAppChat(contact))
            "WHATSAPP_MESSAGE" -> executor.execute(PhoneAction.WhatsAppMessage(contact, message))
            "CALL_CONTACT" -> executor.execute(PhoneAction.CallContact(contact))
            "SEND_SMS" -> executor.execute(PhoneAction.SendSms(contact, message))
            "SEARCH_CONTACT" -> executor.execute(PhoneAction.SearchContact(contact))
            "SEND_EMAIL" -> {
                val recipient = action.parameters["recipient"] ?: contact
                val subject = action.parameters["subject"]
                executor.execute(PhoneAction.SendEmail(recipient, subject, message))
            }
            "SHARE_CONTENT" -> {
                val content = action.parameters["content"] ?: message ?: ""
                val title = action.parameters["title"]
                executor.execute(PhoneAction.ShareContent(content, title))
            }
            "CLIPBOARD_ACTION" -> {
                val mode = action.parameters["mode"] ?: "copy"
                val text = action.parameters["text"] ?: message
                executor.execute(PhoneAction.ClipboardAction(mode, text))
            }
            else -> {
                when (action.intent) {
                    "WHATSAPP_CALL" -> executor.execute(PhoneAction.WhatsAppCall(contact))
                    "WHATSAPP_CHAT" -> executor.execute(PhoneAction.WhatsAppChat(contact))
                    "WHATSAPP_MESSAGE" -> executor.execute(PhoneAction.WhatsAppMessage(contact, message))
                    "SEND_SMS" -> executor.execute(PhoneAction.SendSms(contact, message))
                    "SEND_EMAIL" -> executor.execute(PhoneAction.SendEmail(contact, null, message))
                    else -> executor.execute(PhoneAction.CallContact(contact))
                }
            }
        }
    }
}

class MapsNavigationSkill : FridaySkill {
    override val id: String = "skill_maps_navigation"
    override val name: String = "Maps & Turn-by-Turn Navigation"
    override val description = "Searches locations on Google Maps or initiates driving navigation."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "MAP_SEARCH" || intent == "START_NAVIGATION" ||
                lower.startsWith("navigate ") || lower.startsWith("directions to ") ||
                lower.contains("where is ") || lower.contains("find on map")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        val location = action.parameters["destination"] ?: action.parameters["location"] ?: action.parameters["query"] ?: ""
        return if (action.actionType == "START_NAVIGATION" || action.intent == "START_NAVIGATION") {
            executor.execute(PhoneAction.StartNavigation(location))
        } else {
            executor.execute(PhoneAction.MapSearch(location))
        }
    }
}

class CameraMediaSkill : FridaySkill {
    override val id: String = "skill_camera_media"
    override val name = "Camera, Photos & Files"
    override val description = "Controls camera capture, video recording, gallery browsing, and file downloads."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent in listOf("CAMERA_ACTION", "GALLERY_ACTION", "FILE_ACTION") ||
                lower.contains("camera") || lower.contains("photo") || lower.contains("picture") ||
                lower.contains("gallery") || lower.contains("downloads") || lower.contains("files")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        return when (action.actionType) {
            "GALLERY_ACTION" -> executor.execute(PhoneAction.GalleryAction)
            "FILE_ACTION" -> {
                val mode = action.parameters["mode"] ?: "open"
                val query = action.parameters["query"]
                executor.execute(PhoneAction.FileAction(mode, query))
            }
            else -> {
                val mode = action.parameters["mode"] ?: "open"
                executor.execute(PhoneAction.CameraAction(mode))
            }
        }
    }
}

class SettingsSystemSkill : FridaySkill {
    override val id: String = "skill_settings_system"
    override val name = "Settings & System Panels"
    override val description = "Opens Android system settings and pulls down notification / quick settings shades."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "SETTINGS_ACTION" || intent == "SYSTEM_PANEL_ACTION" ||
                lower.contains("settings") || lower.contains("notification panel") ||
                lower.contains("quick settings")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        return if (action.actionType == "SYSTEM_PANEL_ACTION") {
            val panel = action.parameters["panel"] ?: "notifications"
            executor.execute(PhoneAction.SystemPanelAction(panel))
        } else {
            val setting = action.parameters["setting"] ?: action.parameters["type"] ?: "general"
            executor.execute(PhoneAction.SettingsAction(setting))
        }
    }
}

class UIAutomationSkill : FridaySkill {
    override val id: String = "skill_ui_automation"
    override val name = "On-Screen UI Automation"
    override val description = "Taps on-screen buttons, types text, scrolls, navigates back, and reads screen."

    override fun canHandle(intent: String, rawText: String): Boolean {
        val lower = rawText.lowercase()
        return intent == "UI_AUTOMATION" ||
                lower.startsWith("tap ") || lower.startsWith("click ") || lower.startsWith("type ") ||
                lower.contains("scroll") || lower == "go back" || lower == "back" ||
                lower.contains("what is on screen") || lower.contains("read screen")
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        val operation = action.parameters["operation"] ?: "click"
        val target = action.parameters["target"]
        val text = action.parameters["text"]
        val x = action.parameters["x"]?.toFloatOrNull()
        val y = action.parameters["y"]?.toFloatOrNull()

        return executor.execute(PhoneAction.UIAutomationAction(operation, target, text, x, y))
    }
}

class MultiStepSkill : FridaySkill {
    override val id: String = "skill_multi_step"
    override val name = "Multi-Step Action Planner"
    override val description = "Executes multi-step compound action sequences."

    override fun canHandle(intent: String, rawText: String): Boolean {
        return intent == "MULTI_STEP_PLAN"
    }

    override suspend fun execute(action: StructuredAction, executor: ActionExecutor): ActionResult {
        return executor.execute(PhoneAction.MultiStepAction(emptyList()))
    }
}
