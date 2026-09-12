package com.example.ai

import com.example.identity.FridayPersonality
import com.example.security.ActionRiskLevel

/**
 * Standard Capability IDs supported natively by FRIDAY.
 */
object FridayCapabilities {
    const val FLASHLIGHT = "FLASHLIGHT"
    const val VOLUME = "VOLUME"
    const val MEDIA = "MEDIA"
    const val PLAY_MUSIC = "PLAY_MUSIC"
    const val BRIGHTNESS = "BRIGHTNESS"
    const val WIFI = "WIFI"
    const val BLUETOOTH = "BLUETOOTH"
    const val BATTERY = "BATTERY"
    const val SYSTEM_INFO = "SYSTEM_INFO"
    const val APP_OPEN = "APP_OPEN"
    const val APP_ACTION = "APP_ACTION"
    const val WEB_SEARCH = "WEB_SEARCH"
    const val MAP_SEARCH = "MAP_SEARCH"
    const val NAVIGATION = "NAVIGATION"
    const val CAMERA = "CAMERA"
    const val ALARM = "ALARM"
    const val TIMER = "TIMER"
    const val NOTIFICATIONS = "NOTIFICATIONS"
    const val SETTINGS = "SETTINGS"
    const val CLIPBOARD = "CLIPBOARD"
    const val FILES = "FILES"
    const val SHARE = "SHARE"
    const val UI_AUTOMATION = "UI_AUTOMATION"
    const val CONTACT_SEARCH = "CONTACT_SEARCH"
    const val PHONE_CALL = "CALL_CONTACT"
    const val WHATSAPP_CALL = "WHATSAPP_CALL"
    const val WHATSAPP_MESSAGE = "WHATSAPP_MESSAGE"
    const val WHATSAPP_CHAT = "WHATSAPP_CHAT"
    const val SMS = "SEND_SMS"
}

/**
 * Scalable Intent Understanding and Entity Extraction Engine.
 * Translates arbitrary natural-language phrasing into structured, validated actions
 * using semantic synonym matching, verb-noun matrices, and entity extraction.
 */
object IntentUnderstandingEngine {

    fun parseCommand(rawText: String): StructuredAction? {
        val lower = rawText.lowercase().trim()
            .replace(Regex("[?,.!]"), "")
            .replace(Regex("\\s+"), " ")

        if (lower.isBlank()) return null

        // 1. FLASHLIGHT (ON / OFF with all natural variations)
        val flashOnPatterns = listOf(
            "turn on flashlight", "turn the flashlight on", "turn on the flashlight",
            "switch on the torch", "switch on torch", "switch the torch on",
            "enable flashlight", "flashlight on", "torch on",
            "turn on torch", "turn torch on", "turn on the torch", "turn the torch on",
            "turn on light", "turn light on", "turn on the light", "turn the light on"
        )
        if (flashOnPatterns.any { lower == it || lower.startsWith("$it ") }) {
            val speech = FridayPersonality.formatSpeech("TOGGLE_FLASHLIGHT", mapOf("state" to "true"), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.FLASHLIGHT,
                actionType = "TOGGLE_FLASHLIGHT",
                parameters = mapOf("target" to "flashlight", "state" to "true"),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        val flashOffPatterns = listOf(
            "turn off flashlight", "turn the flashlight off", "turn off the flashlight",
            "switch off the torch", "switch off torch", "switch the torch off",
            "disable flashlight", "flashlight off", "torch off",
            "turn off torch", "turn torch off", "turn off the torch", "turn the torch off",
            "turn off light", "turn light off", "turn off the light", "turn the light off",
            "kill the light", "shut off flashlight", "shut off torch"
        )
        if (flashOffPatterns.any { lower == it || lower.startsWith("$it ") }) {
            val speech = FridayPersonality.formatSpeech("TOGGLE_FLASHLIGHT", mapOf("state" to "false"), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.FLASHLIGHT,
                actionType = "TOGGLE_FLASHLIGHT",
                parameters = mapOf("target" to "flashlight", "state" to "false"),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 2. WHATSAPP: STRICT CALL VS MESSAGE VS CHAT
        if (lower.contains("whatsapp")) {
            // A. WhatsApp Voice Call
            val isWhatsAppCall = lower.startsWith("call ") || lower.startsWith("voice call ") ||
                    lower.startsWith("make a whatsapp call") || lower.startsWith("whatsapp call ") ||
                    lower.contains("call ") && (lower.contains("on whatsapp") || lower.contains("using whatsapp") || lower.contains("via whatsapp"))

            if (isWhatsAppCall) {
                var contact = lower
                    .replace(Regex("^(?:make a\\s+)?(?:voice\\s+)?call\\s+"), "")
                    .replace(Regex("^whatsapp\\s+call\\s+"), "")
                    .replace(Regex("\\s+(?:on|using|via)\\s+whatsapp.*"), "")
                    .replace(Regex("whatsapp.*"), "")
                    .trim()

                contact = capitalizeWords(contact)
                val speech = FridayPersonality.formatSpeech("WHATSAPP_CALL", mapOf("contact" to contact), isSuccess = true)
                return StructuredAction(
                    intent = FridayCapabilities.WHATSAPP_CALL,
                    actionType = "WHATSAPP_CALL",
                    parameters = mapOf("contact" to contact, "contactName" to contact),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            }

            // B. WhatsApp Message
            val isWhatsAppMsg = lower.startsWith("message ") || lower.startsWith("send a message to ") ||
                    lower.startsWith("text ") || lower.startsWith("send text to ") || lower.startsWith("whatsapp message ") ||
                    (lower.contains("message ") && (lower.contains("on whatsapp") || lower.contains("via whatsapp")))

            if (isWhatsAppMsg) {
                var rest = lower
                    .replace(Regex("^(?:send\\s+a\\s+)?message\\s+(?:to\\s+)?"), "")
                    .replace(Regex("^whatsapp\\s+message\\s+"), "")
                    .replace(Regex("^text\\s+(?:to\\s+)?"), "")
                    .trim()

                var message = ""
                if (rest.contains(" saying ")) {
                    message = rest.substringAfter(" saying ").trim()
                    rest = rest.substringBefore(" saying ").trim()
                } else if (rest.contains(" that ")) {
                    message = rest.substringAfter(" that ").trim()
                    rest = rest.substringBefore(" that ").trim()
                }

                var contact = rest
                    .replace(Regex("\\s+(?:on|using|via)\\s+whatsapp.*"), "")
                    .trim()

                contact = capitalizeWords(contact)
                message = message.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val speech = FridayPersonality.formatSpeech("WHATSAPP_MESSAGE", mapOf("contact" to contact), isSuccess = true)
                return StructuredAction(
                    intent = FridayCapabilities.WHATSAPP_MESSAGE,
                    actionType = "WHATSAPP_MESSAGE",
                    parameters = mapOf("contact" to contact, "contactName" to contact, "message" to message),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            }

            // C. WhatsApp Chat (open conversation)
            var contact = lower
                .replace(Regex("^open\\s+whatsapp\\s+chat\\s+(?:with\\s+)?"), "")
                .replace(Regex("^open\\s+(?:the\\s+)?chat\\s+(?:with\\s+)?"), "")
                .replace(Regex("^whatsapp\\s+chat\\s+(?:with\\s+)?"), "")
                .replace(Regex("^chat\\s+(?:with\\s+)?"), "")
                .replace(Regex("^whatsapp\\s+"), "")
                .replace(Regex("\\s+(?:on|via)\\s+whatsapp.*"), "")
                .trim()

            contact = capitalizeWords(contact)
            val speech = FridayPersonality.formatSpeech("WHATSAPP_CHAT", mapOf("contact" to contact), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.WHATSAPP_CHAT,
                actionType = "WHATSAPP_CHAT",
                parameters = mapOf("contact" to contact, "contactName" to contact),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 3. NATIVE PHONE CALL (Strict: NEVER silently convert to chat or whatsapp)
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") || lower.startsWith("make a call to ")) {
            var contact = lower
                .replace(Regex("^(?:make\\s+a\\s+call\\s+to|call|dial|phone)\\s+"), "")
                .trim()

            contact = capitalizeWords(contact)
            val speech = FridayPersonality.formatSpeech("CALL_CONTACT", mapOf("contact" to contact), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.PHONE_CALL,
                actionType = "CALL_CONTACT",
                parameters = mapOf("contact" to contact, "contactName" to contact),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 4. SMS / TEXT
        if (lower.startsWith("send sms to ") || lower.startsWith("sms ") || lower.startsWith("send a text to ") || lower.startsWith("text ")) {
            var contact = lower
                .replace(Regex("^(?:send\\s+sms\\s+to|sms|send\\s+a\\s+text\\s+to|text)\\s+"), "")
                .trim()

            var message = ""
            if (contact.contains(" that ")) {
                message = contact.substringAfter(" that ").trim()
                contact = contact.substringBefore(" that ").trim()
            }

            contact = capitalizeWords(contact)
            return StructuredAction(
                intent = FridayCapabilities.SMS,
                actionType = "SEND_SMS",
                parameters = mapOf("contact" to contact, "contactName" to contact, "message" to message),
                speechResponse = "Preparing SMS to $contact, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 5. CONTACT SEARCH
        if (lower.startsWith("search contact ") || lower.startsWith("find contact ") || lower.startsWith("look up contact ") ||
            lower.startsWith("find ") && lower.contains(" in contacts")) {
            val query = lower
                .replace(Regex("^(?:search|find|look up)\\s+contact\\s+"), "")
                .replace(Regex("^find\\s+"), "")
                .replace(" in contacts", "")
                .trim()

            return StructuredAction(
                intent = FridayCapabilities.CONTACT_SEARCH,
                actionType = "SEARCH_CONTACT",
                parameters = mapOf("contact" to query, "query" to query),
                speechResponse = "Searching contacts for $query, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6. VOLUME (UP, DOWN, MUTE, LEVEL)
        val volumeUpWords = listOf("volume up", "turn up the volume", "turn up volume", "turn volume up", "increase volume", "louder", "make it louder", "boost sound", "increase sound", "raise volume")
        if (volumeUpWords.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.VOLUME,
                actionType = "ADJUST_VOLUME",
                parameters = mapOf("direction" to "up"),
                speechResponse = "Volume increased, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        val volumeDownWords = listOf("volume down", "turn down the volume", "turn down volume", "turn volume down", "decrease volume", "quieter", "make it quieter", "lower the volume", "lower volume", "reduce volume", "decrease sound")
        if (volumeDownWords.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.VOLUME,
                actionType = "ADJUST_VOLUME",
                parameters = mapOf("direction" to "down"),
                speechResponse = "Volume decreased, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        val volumeMuteWords = listOf("mute", "mute volume", "mute the phone", "mute audio", "silence", "be quiet")
        if (volumeMuteWords.any { lower == it }) {
            return StructuredAction(
                intent = FridayCapabilities.VOLUME,
                actionType = "ADJUST_VOLUME",
                parameters = mapOf("direction" to "mute"),
                speechResponse = "Muted, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 7. MEDIA CONTROLS (PLAY, PAUSE, NEXT, PREV, PLAY SONG/TRACK)
        if (lower.startsWith("play ")) {
            val query = lower.removePrefix("play ").trim()
            if (query.isNotBlank() && query != "music" && query != "song") {
                val speech = FridayPersonality.formatSpeech("PLAY_MUSIC", mapOf("query" to query, "song" to query), isSuccess = true)
                return StructuredAction(
                    intent = FridayCapabilities.PLAY_MUSIC,
                    actionType = "PLAY_MUSIC",
                    parameters = mapOf("query" to query, "song" to query, "targetApp" to "YouTube"),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            }
        }

        if (lower == "play" || lower == "play music" || lower == "resume" || lower == "resume music" || lower == "start playing") {
            return StructuredAction(
                intent = FridayCapabilities.MEDIA,
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "play"),
                speechResponse = "Resuming playback, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "pause" || lower == "pause music" || lower == "stop music" || lower == "pause playback" || lower == "stop song") {
            return StructuredAction(
                intent = FridayCapabilities.MEDIA,
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "pause"),
                speechResponse = "Playback paused, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "next song" || lower == "next track" || lower == "skip song" || lower == "skip track" || lower == "skip") {
            return StructuredAction(
                intent = FridayCapabilities.MEDIA,
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "next"),
                speechResponse = "Skipping to next track, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "previous song" || lower == "previous track" || lower == "previous" || lower == "last song") {
            return StructuredAction(
                intent = FridayCapabilities.MEDIA,
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "previous"),
                speechResponse = "Going to previous track, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 8. BATTERY & SYSTEM INFO
        if (lower.contains("battery") || lower == "what's my battery" || lower == "battery level" || lower == "battery percentage" || lower == "check battery") {
            return StructuredAction(
                intent = FridayCapabilities.BATTERY,
                actionType = "GET_BATTERY_INFO",
                parameters = emptyMap(),
                speechResponse = "Checking battery level, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower == "what time is it" || lower == "what's the time" || lower == "current time" || lower == "tell me the time") {
            return StructuredAction(
                intent = FridayCapabilities.SYSTEM_INFO,
                actionType = "GET_DATE_TIME",
                parameters = mapOf("type" to "time"),
                speechResponse = "Checking current time, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower == "what is today's date" || lower == "what's today's date" || lower == "what date is it" || lower == "today's date") {
            return StructuredAction(
                intent = FridayCapabilities.SYSTEM_INFO,
                actionType = "GET_DATE_TIME",
                parameters = mapOf("type" to "date"),
                speechResponse = "Checking today's date, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 9. APP ACTION: YOUTUBE SEARCH & PLAY
        if (lower.startsWith("play ") && lower.endsWith(" on youtube")) {
            val query = lower.removePrefix("play ").removeSuffix(" on youtube").trim()
            val speech = FridayPersonality.formatSpeech("PLAY_MUSIC", mapOf("query" to query, "song" to query), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.PLAY_MUSIC,
                actionType = "PLAY_MUSIC",
                parameters = mapOf("query" to query, "song" to query, "targetApp" to "YouTube"),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.startsWith("search youtube for ") || (lower.startsWith("search ") && lower.endsWith(" on youtube"))) {
            val query = lower
                .replace(Regex("^search\\s+youtube\\s+for\\s+"), "")
                .replace(Regex("^search\\s+"), "")
                .replace(Regex("\\s+on\\s+youtube$"), "")
                .trim()

            return StructuredAction(
                intent = FridayCapabilities.APP_ACTION,
                actionType = "SEARCH_WEB",
                parameters = mapOf("query" to query, "targetApp" to "YouTube"),
                speechResponse = "Searching YouTube for $query, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 10. APP OPEN / LAUNCH
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val appName = lower
                .replace(Regex("^(?:open|launch|start)\\s+"), "")
                .trim()

            val speech = FridayPersonality.formatSpeech("OPEN_APP", mapOf("appName" to appName), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.APP_OPEN,
                actionType = "OPEN_APP",
                parameters = mapOf("appName" to appName),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 11. WEB SEARCH (News, weather, internet search, info)
        if (lower.startsWith("search google for ") || lower.startsWith("search web for ") ||
            lower.startsWith("google ") || lower.startsWith("search for ") || lower.startsWith("search ") ||
            lower == "latest news" || lower.startsWith("latest news ") || lower == "news" ||
            lower.startsWith("news about ") || lower.startsWith("weather in ") || lower == "what is the weather" ||
            lower == "weather today") {
            val query = when {
                lower == "latest news" || lower == "news" -> "latest news"
                lower == "what is the weather" || lower == "weather today" -> "current weather"
                else -> lower
                    .replace(Regex("^(?:search\\s+google\\s+for|search\\s+web\\s+for|google|search\\s+for|search)\\s+"), "")
                    .trim()
            }

            val speech = if (query.contains("news")) "Searching for the latest news, Boss." else "Searching the web for $query, Boss."
            return StructuredAction(
                intent = FridayCapabilities.WEB_SEARCH,
                actionType = "SEARCH_WEB",
                parameters = mapOf("query" to query),
                speechResponse = speech,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 12. MAP SEARCH & NAVIGATION
        if (lower.startsWith("navigate to ") || lower.startsWith("take me to ") || lower.startsWith("directions to ") || lower.startsWith("drive to ")) {
            val destination = lower
                .replace(Regex("^(?:navigate\\s+to|take\\s+me\\s+to|directions\\s+to|drive\\s+to)\\s+"), "")
                .trim()

            return StructuredAction(
                intent = FridayCapabilities.NAVIGATION,
                actionType = "START_NAVIGATION",
                parameters = mapOf("destination" to destination),
                speechResponse = "Starting navigation to $destination, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 13. CAMERA
        if (lower == "open camera" || lower == "launch camera" || lower == "take a picture" || lower == "take a photo") {
            val mode = if (lower.contains("picture") || lower.contains("photo")) "photo" else "open"
            return StructuredAction(
                intent = FridayCapabilities.CAMERA,
                actionType = "CAMERA_ACTION",
                parameters = mapOf("mode" to mode),
                speechResponse = "Opening camera, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 14. TIMER & ALARM
        if (lower.contains("timer") || lower.contains("countdown")) {
            val minutes = Regex("(\\d+)\\s*(?:min|minute)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val seconds = minutes * 60
            return StructuredAction(
                intent = FridayCapabilities.TIMER,
                actionType = "SET_TIMER",
                parameters = mapOf("seconds" to seconds.toString(), "label" to "FRIDAY Timer"),
                speechResponse = "Setting a timer for $minutes minutes, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.contains("alarm")) {
            if (lower.contains("show") || lower.contains("view")) {
                return StructuredAction(
                    intent = FridayCapabilities.ALARM,
                    actionType = "SHOW_TIMERS_ALARMS",
                    parameters = mapOf("mode" to "alarm"),
                    speechResponse = "Opening alarms, Boss.",
                    riskLevel = ActionRiskLevel.SAFE
                )
            }
            val hour = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 7
            return StructuredAction(
                intent = FridayCapabilities.ALARM,
                actionType = "SET_ALARM",
                parameters = mapOf("hour" to hour.toString(), "minute" to "0", "message" to "FRIDAY Alarm"),
                speechResponse = "Setting alarm for $hour:00, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 15. WIFI & BLUETOOTH
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return StructuredAction(
                intent = FridayCapabilities.WIFI,
                actionType = "SETTINGS_ACTION",
                parameters = mapOf("setting" to "wifi"),
                speechResponse = "Opening Wi-Fi settings, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("bluetooth")) {
            return StructuredAction(
                intent = FridayCapabilities.BLUETOOTH,
                actionType = "SETTINGS_ACTION",
                parameters = mapOf("setting" to "bluetooth"),
                speechResponse = "Opening Bluetooth settings, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 16. NOTIFICATIONS & QUICK SETTINGS
        if (lower.contains("notifications") || lower.contains("notification shade")) {
            return StructuredAction(
                intent = FridayCapabilities.NOTIFICATIONS,
                actionType = "SYSTEM_PANEL_ACTION",
                parameters = mapOf("panel" to "notifications"),
                speechResponse = "Opening notifications, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("quick settings") || lower.contains("control center")) {
            return StructuredAction(
                intent = FridayCapabilities.SETTINGS,
                actionType = "SYSTEM_PANEL_ACTION",
                parameters = mapOf("panel" to "quick_settings"),
                speechResponse = "Opening quick settings, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 17. UI AUTOMATION
        if (lower == "go back" || lower == "back") {
            return StructuredAction(
                intent = FridayCapabilities.UI_AUTOMATION,
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "back"),
                speechResponse = "Going back, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "go home" || lower == "home") {
            return StructuredAction(
                intent = FridayCapabilities.UI_AUTOMATION,
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "home"),
                speechResponse = "Going home, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "scroll down") {
            return StructuredAction(
                intent = FridayCapabilities.UI_AUTOMATION,
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "scroll_down"),
                speechResponse = "Scrolling down, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "scroll up") {
            return StructuredAction(
                intent = FridayCapabilities.UI_AUTOMATION,
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "scroll_up"),
                speechResponse = "Scrolling up, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 18. CLIPBOARD
        if (lower.contains("clipboard")) {
            val mode = if (lower.contains("copy")) "copy" else "read"
            return StructuredAction(
                intent = FridayCapabilities.CLIPBOARD,
                actionType = "CLIPBOARD_ACTION",
                parameters = mapOf("mode" to mode),
                speechResponse = if (mode == "read") "Reading clipboard, Boss." else "Copied to clipboard, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        return null
    }

    private fun capitalizeWords(input: String): String {
        return input.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
