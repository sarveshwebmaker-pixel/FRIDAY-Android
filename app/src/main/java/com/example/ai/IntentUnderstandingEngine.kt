package com.example.ai

import com.example.identity.FridayPersonality
import com.example.security.ActionRiskLevel

/**
 * Standard Capability IDs supported natively by FRIDAY.
 */
object FridayCapabilities {
    const val FLASHLIGHT = "FLASHLIGHT"
    const val FLASHLIGHT_STROBE = "FLASHLIGHT_STROBE"
    const val VOLUME = "VOLUME"
    const val RINGER_MODE = "RINGER_MODE"
    const val SCREEN_ORIENTATION = "SCREEN_ORIENTATION"
    const val SCREEN_TIMEOUT = "SCREEN_TIMEOUT"
    const val SCREENSHOT = "TAKE_SCREENSHOT"
    const val LOCK_SCREEN = "LOCK_SCREEN"
    const val POWER_MENU = "POWER_MENU"
    const val SPLIT_SCREEN = "SPLIT_SCREEN"
    const val VIBRATE = "VIBRATE_DEVICE"
    const val DARK_MODE = "DARK_MODE"
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
    const val PAYMENT = "PAYMENT"
    const val APP_CLOSE = "CLOSE_APP"
    const val SILENT_WORK_MODE = "SILENT_WORK_MODE"
    const val UNMUTE = "UNMUTE"
    const val GENERATE_IMAGE = "GENERATE_IMAGE"
    const val GENERATE_VIDEO = "GENERATE_VIDEO"
    const val GENERATE_MUSIC = "GENERATE_MUSIC"
    const val SEARCH_GROUNDING = "SEARCH_GROUNDING"
    const val MAPS_GROUNDING = "MAPS_GROUNDING"
    const val TRANSCRIBE_AUDIO = "TRANSCRIBE_AUDIO"
}

/**
 * Scalable Intent Understanding and Entity Extraction Engine.
 * Translates arbitrary natural-language phrasing into structured, validated actions
 * using semantic synonym matching, verb-noun matrices, and entity extraction.
 */
object IntentUnderstandingEngine {

    fun normalizeConversationalText(rawText: String): String {
        var text = rawText.lowercase().trim()
            .replace(Regex("[,?.!]"), "")
            .replace(Regex("\\s+"), " ")

        // Strip wake word
        text = text.replace(Regex("^(?:hey\\s+friday|ok\\s+friday|okay\\s+friday|friday)[,\\s]*"), "").trim()

        // Conversational openers
        val prefixes = listOf(
            "can you please ", "could you please ", "would you please ",
            "can you ", "could you ", "would you ", "will you ",
            "can we ", "could we ", "please ", "kindly ",
            "hey can you ", "hey could you ",
            "i want to ", "i'd like to ", "i would like to ", "i need to ", "i want you to ",
            "can you tell me ", "could you tell me ", "tell me ",
            "do me a favor and ", "go ahead and ",
            "actually ", "okay so ", "wait "
        )
        for (p in prefixes) {
            if (text.startsWith(p)) {
                text = text.removePrefix(p).trim()
                break
            }
        }

        // Conversational request closers
        val suffixes = listOf(
            " for me please", " for me", " please", " right now", " now"
        )
        for (s in suffixes) {
            if (text.endsWith(s)) {
                text = text.removeSuffix(s).trim()
                break
            }
        }
        return text
    }

    fun parseCommand(rawText: String): StructuredAction? {
        val lower = rawText.lowercase().trim()
            .replace(Regex("[?,.!]"), "")
            .replace(Regex("\\s+"), " ")

        if (lower.isBlank()) return null

        val direct = parseCommandInternal(lower, rawText)
        if (direct != null) return direct

        val normalized = normalizeConversationalText(rawText)
        if (normalized.isNotBlank() && normalized != lower) {
            return parseCommandInternal(normalized, rawText)
        }
        return null
    }

    private fun parseCommandInternal(lower: String, rawText: String = lower): StructuredAction? {
        // 0. SILENT WORK MODE & UNMUTE
        val silentModePatterns = listOf(
            "mute and work", "silent work mode", "work silently", "work in silence",
            "go silent and work", "silent mode", "mute work", "mute and continue"
        )
        if (silentModePatterns.any { lower == it || lower.contains("mute and work") || lower.contains("work in silence") || lower.contains("silent work mode") }) {
            return StructuredAction(
                intent = FridayCapabilities.SILENT_WORK_MODE,
                actionType = "SILENT_WORK_MODE",
                parameters = mapOf("mode" to "muted"),
                speechResponse = "",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        val unmutePatterns = listOf(
            "unmute", "start talking", "resume voice", "voice on", "speak again", "unmute voice"
        )
        if (unmutePatterns.any { lower == it || lower.contains("start talking") || lower.contains("unmute") }) {
            return StructuredAction(
                intent = FridayCapabilities.UNMUTE,
                actionType = "UNMUTE",
                parameters = mapOf("mode" to "on"),
                speechResponse = "Voice output resumed. I'm listening.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

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

        // 1b. FLASHLIGHT STROBE / SOS
        val strobePatterns = listOf(
            "strobe flashlight", "flashlight strobe", "strobe light", "sos light",
            "sos torch", "blink flashlight", "flash torch", "emergency strobe", "strobe"
        )
        if (strobePatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.FLASHLIGHT_STROBE,
                actionType = "FLASHLIGHT_STROBE",
                parameters = mapOf("flashes" to "6"),
                speechResponse = "Activating emergency strobe light, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 1c. GENERATIVE AI: IMAGE, VIDEO, MUSIC, AND LIVE SEARCH GROUNDING
        if (lower.startsWith("generate image") || lower.startsWith("create image") || lower.startsWith("create an image") ||
            lower.startsWith("draw an image") || lower.startsWith("draw a picture") || lower.startsWith("generate a picture") ||
            lower.startsWith("generate picture") || lower.startsWith("paint an image")) {
            val prompt = lower.replace(Regex("^(?:generate|create|draw|paint)(?:\\s+an|\\s+a)?\\s+(?:image|picture)(?:\\s+of)?\\s*"), "").trim()
            return StructuredAction(
                intent = FridayCapabilities.GENERATE_IMAGE,
                actionType = "GENERATE_IMAGE",
                parameters = mapOf("prompt" to prompt),
                speechResponse = "Generating image for $prompt, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.startsWith("generate video") || lower.startsWith("create video") || lower.startsWith("create a video") ||
            lower.startsWith("make a video") || lower.startsWith("render a video")) {
            val prompt = lower.replace(Regex("^(?:generate|create|make|render)(?:\\s+a)?\\s+video(?:\\s+of)?\\s*"), "").trim()
            return StructuredAction(
                intent = FridayCapabilities.GENERATE_VIDEO,
                actionType = "GENERATE_VIDEO",
                parameters = mapOf("prompt" to prompt, "aspectRatio" to "16:9"),
                speechResponse = "Creating high-definition video for $prompt, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.startsWith("generate music") || lower.startsWith("compose music") || lower.startsWith("create music") ||
            lower.startsWith("compose a song") || lower.startsWith("make a beat") || lower.startsWith("generate a beat")) {
            val prompt = lower.replace(Regex("^(?:generate|compose|create|make)(?:\\s+a)?\\s+(?:music|song|beat)(?:\\s+for|\\s+of)?\\s*"), "").trim()
            return StructuredAction(
                intent = FridayCapabilities.GENERATE_MUSIC,
                actionType = "GENERATE_MUSIC",
                parameters = mapOf("prompt" to prompt),
                speechResponse = "Composing soundtrack for $prompt, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.startsWith("search live ") || lower.startsWith("search google for ") || lower.startsWith("google ") ||
            lower.startsWith("latest news on ") || lower.startsWith("breaking news on ")) {
            val query = lower.replace(Regex("^(?:search live|search google for|google|latest news on|breaking news on)\\s*"), "").trim()
            return StructuredAction(
                intent = FridayCapabilities.SEARCH_GROUNDING,
                actionType = "SEARCH_GROUNDING",
                parameters = mapOf("query" to query),
                speechResponse = "Searching live Google results for $query, Boss.",
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

        // 6. UNIVERSAL VOLUME & SOUND STREAMS
        val isVolumeRelated = lower.contains("volume") || lower.contains("sound") || lower.contains("louder") ||
                lower.contains("quieter") || lower == "mute" || lower == "unmute" || lower == "silence" || lower.startsWith("turn up the volume") || lower.startsWith("turn down the volume")
        if (isVolumeRelated) {
            val stream = when {
                lower.contains("ring") -> "ring"
                lower.contains("alarm") -> "alarm"
                lower.contains("notification") -> "notification"
                lower.contains("call") || lower.contains("voice") -> "voice"
                else -> "music"
            }
            val percentMatch = Regex("(\\d{1,3})\\s*%|(\\d{1,3})\\s*percent").find(lower)
            val level = when {
                percentMatch != null -> percentMatch.groupValues.let { it[1].ifEmpty { it[2] } }
                lower.contains("max") || lower.contains("full") || lower.contains("100") -> "max"
                lower.contains("min") || lower.contains("zero") -> "min"
                lower.contains("half") || lower.contains("50") -> "50"
                else -> null
            }
            val direction = when {
                level != null -> null
                lower.contains("up") || lower.contains("raise") || lower.contains("increase") || lower.contains("louder") || lower.contains("boost") -> "up"
                lower.contains("down") || lower.contains("lower") || lower.contains("decrease") || lower.contains("quieter") || lower.contains("reduce") -> "down"
                lower.contains("mute") || lower == "silence" -> "mute"
                lower.contains("unmute") -> "unmute"
                else -> "up"
            }

            val params = mutableMapOf("stream" to stream)
            if (level != null) params["level"] = level
            if (direction != null) params["direction"] = direction

            val speech = when {
                level != null -> "Setting $stream volume to $level%."
                direction == "mute" -> "Muted."
                direction == "unmute" -> "Unmuted."
                direction == "down" -> "Volume decreased."
                else -> "Volume increased."
            }

            return StructuredAction(
                intent = FridayCapabilities.VOLUME,
                actionType = "ADJUST_VOLUME",
                parameters = params,
                speechResponse = speech,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6b. RINGER MODES & DO NOT DISTURB (DND)
        val ringerTriggers = listOf(
            "vibrate", "silent", "ringer", "dnd", "do not disturb", "silence phone", "mute phone"
        )
        if (ringerTriggers.any { lower.contains(it) }) {
            val mode = when {
                lower.contains("dnd off") || lower.contains("turn off dnd") || lower.contains("disable dnd") || lower.contains("turn off do not disturb") -> "dnd_off"
                lower.contains("dnd") || lower.contains("do not disturb") -> "dnd"
                lower.contains("vibrate") -> "vibrate"
                lower.contains("silent") || lower.contains("silence") -> "silent"
                lower.contains("ring") || lower.contains("normal") || lower.contains("unmute") -> "normal"
                else -> "vibrate"
            }
            val speech = when (mode) {
                "vibrate" -> "Setting phone to vibrate, Boss."
                "silent" -> "Setting phone to silent, Boss."
                "normal" -> "Turning ringer on, Boss."
                "dnd" -> "Enabling Do Not Disturb, Boss."
                "dnd_off" -> "Turning off Do Not Disturb, Boss."
                else -> "Adjusting ringer mode, Boss."
            }
            return StructuredAction(
                intent = FridayCapabilities.RINGER_MODE,
                actionType = "RINGER_MODE",
                parameters = mapOf("mode" to mode),
                speechResponse = speech,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6c. SCREEN BRIGHTNESS
        if (lower.contains("brightness") || (lower.contains("screen") && (lower.contains("dim") || lower.contains("bright")))) {
            val percentMatch = Regex("(\\d{1,3})\\s*%|(\\d{1,3})\\s*percent").find(lower)
            val level = when {
                percentMatch != null -> percentMatch.groupValues.let { it[1].ifEmpty { it[2] } }
                lower.contains("max") || lower.contains("full") || lower.contains("100") -> "max"
                lower.contains("min") || lower.contains("minimum") || lower.contains("dim") -> "min"
                lower.contains("half") || lower.contains("50") -> "50"
                lower.contains("up") || lower.contains("raise") || lower.contains("increase") || lower.contains("higher") || lower.contains("brighter") -> "increase"
                lower.contains("down") || lower.contains("lower") || lower.contains("decrease") -> "decrease"
                else -> "50"
            }
            return StructuredAction(
                intent = FridayCapabilities.BRIGHTNESS,
                actionType = "SET_BRIGHTNESS",
                parameters = mapOf("level" to level),
                speechResponse = "Adjusting screen brightness, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6d. SCREEN AUTO-ROTATION
        if (lower.contains("auto rotate") || lower.contains("auto-rotate") || lower.contains("rotation") || lower.contains("screen orientation")) {
            val state = when {
                lower.contains("off") || lower.contains("lock") || lower.contains("disable") -> "off"
                lower.contains("on") || lower.contains("enable") || lower.contains("auto") -> "on"
                else -> "toggle"
            }
            val speech = if (state == "on") "Turning on auto-rotate, Boss." else "Locking screen rotation, Boss."
            return StructuredAction(
                intent = FridayCapabilities.SCREEN_ORIENTATION,
                actionType = "SCREEN_ORIENTATION",
                parameters = mapOf("state" to state),
                speechResponse = speech,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6e. SCREEN TIMEOUT
        if (lower.contains("screen timeout") || lower.contains("screen sleep") || lower.contains("sleep timeout")) {
            val time = lower.replace("screen timeout", "").replace("screen sleep", "").replace("to", "").trim()
            return StructuredAction(
                intent = FridayCapabilities.SCREEN_TIMEOUT,
                actionType = "SCREEN_TIMEOUT",
                parameters = mapOf("time" to time.ifBlank { "60" }),
                speechResponse = "Updating screen sleep timeout, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6f. SCREENSHOT
        val screenshotPatterns = listOf("take a screenshot", "take screenshot", "capture screen", "capture screenshot", "screenshot", "grab screenshot")
        if (screenshotPatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.SCREENSHOT,
                actionType = "TAKE_SCREENSHOT",
                parameters = emptyMap(),
                speechResponse = "Taking a screenshot now, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6g. LOCK SCREEN / SLEEP PHONE
        val lockPatterns = listOf("lock phone", "lock my phone", "lock screen", "lock the screen", "turn off screen", "turn off the screen", "sleep phone")
        if (lockPatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.LOCK_SCREEN,
                actionType = "LOCK_SCREEN",
                parameters = emptyMap(),
                speechResponse = "Locking phone, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6h. POWER MENU / RESTART DIALOG
        val powerPatterns = listOf("power menu", "open power menu", "power dialog", "restart phone", "shut down phone", "power options", "show power menu")
        if (powerPatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.POWER_MENU,
                actionType = "POWER_MENU",
                parameters = emptyMap(),
                speechResponse = "Opening power menu, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6i. SPLIT SCREEN
        val splitPatterns = listOf("split screen", "toggle split screen", "enable split screen", "open split screen", "multi window")
        if (splitPatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.SPLIT_SCREEN,
                actionType = "SPLIT_SCREEN",
                parameters = emptyMap(),
                speechResponse = "Toggling split screen, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6j. VIBRATE DEVICE / HAPTIC BUZZ
        val vibratePatterns = listOf("buzz phone", "buzz the phone", "test vibration", "trigger vibration", "buzz", "haptic test")
        if (vibratePatterns.any { lower == it || lower.startsWith("$it ") }) {
            return StructuredAction(
                intent = FridayCapabilities.VIBRATE,
                actionType = "VIBRATE_DEVICE",
                parameters = mapOf("durationMs" to "500"),
                speechResponse = "Vibrating phone, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 6k. DARK MODE
        if (lower.contains("dark mode") || lower.contains("dark theme") || lower.contains("night mode")) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("light")) "off" else "on"
            return StructuredAction(
                intent = FridayCapabilities.DARK_MODE,
                actionType = "DARK_MODE",
                parameters = mapOf("state" to state),
                speechResponse = "Opening display settings for Dark mode, Boss.",
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

        // 10b. APP CLOSE / EXIT
        if (lower.startsWith("close ") || lower.startsWith("exit ") || lower.startsWith("stop app ") || lower.startsWith("quit ")) {
            val appName = lower
                .replace(Regex("^(?:close|exit|stop app|quit)\\s+"), "")
                .trim()

            val speech = FridayPersonality.formatSpeech("CLOSE_APP", mapOf("appName" to appName), isSuccess = true)
            return StructuredAction(
                intent = FridayCapabilities.APP_CLOSE,
                actionType = "CLOSE_APP",
                parameters = mapOf("appName" to appName),
                speechResponse = speech.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 10c. MOBILE & UPI PAYMENTS
        val paymentMatch = Regex("^(?:pay|send|transfer)\\s+(?:rs\\.?|rupees\\s+|inr\\s+|\\$)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|inr|dollars)?\\s+(?:to\\s+)?([a-zA-Z0-9@._\\s]+?)(?:\\s+(?:via|on|using|through)\\s+(gpay|google pay|phonepe|paytm|bhim|upi))?$").find(lower)
        if (paymentMatch != null) {
            val amount = paymentMatch.groupValues[1]
            val payee = paymentMatch.groupValues[2].trim()
            val app = paymentMatch.groupValues.getOrNull(3)?.trim() ?: ""
            val params = mutableMapOf("amount" to amount, "payee" to payee)
            if (app.isNotBlank()) params["app"] = app
            return StructuredAction(
                intent = FridayCapabilities.PAYMENT,
                actionType = "PAYMENT",
                parameters = params,
                speechResponse = "Preparing payment of ₹$amount to $payee.",
                riskLevel = ActionRiskLevel.CONFIRM,
                confirmationPrompt = "Prepare payment of ₹$amount to $payee in your banking app?"
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

            val speech = if (query.contains("news")) "Searching for the latest news." else "Searching the web for $query."
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

        // 19. CALL CONTROL (Answer / Reject / End)
        if (lower == "answer call" || lower == "pick up" || lower == "answer the call" || lower == "accept call" || lower == "pick up the phone") {
            return StructuredAction(
                intent = "CALL_CONTROLLER",
                actionType = "CALL_CONTROLLER",
                parameters = mapOf("action" to "answer"),
                speechResponse = "Answering the call, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower == "reject call" || lower == "decline call" || lower == "end call" || lower == "hang up" || lower == "hang up the call") {
            return StructuredAction(
                intent = "CALL_CONTROLLER",
                actionType = "CALL_CONTROLLER",
                parameters = mapOf("action" to "reject"),
                speechResponse = "Ending the call, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 20. NOTIFICATIONS (Read & Reply)
        if (lower.contains("read my notification") || lower.contains("read notification") || lower.contains("read notifications") || lower.contains("what notifications do i have") || lower == "notifications") {
            return StructuredAction(
                intent = "READ_NOTIFICATIONS",
                actionType = "READ_NOTIFICATIONS",
                parameters = mapOf("count" to "3"),
                speechResponse = "Checking your notifications, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.startsWith("reply ") || lower.startsWith("reply to message ") || lower.startsWith("reply that ")) {
            val replyMsg = lower.removePrefix("reply to message ").removePrefix("reply that ").removePrefix("reply ").trim()
            return StructuredAction(
                intent = "REPLY_NOTIFICATION",
                actionType = "REPLY_NOTIFICATION",
                parameters = mapOf("message" to replyMsg),
                speechResponse = "Replying now, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 21. SCREEN VISION
        if (lower.contains("what is on my screen") || lower.contains("read my screen") || lower.contains("look at my screen") || lower.contains("analyze screen") || lower.contains("what's on my screen") || lower.contains("summarize this article") || lower.contains("explain this image")) {
            return StructuredAction(
                intent = "SCREEN_VISION",
                actionType = "SCREEN_VISION",
                parameters = mapOf("prompt" to rawText),
                speechResponse = "Analyzing your screen now, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 22. PERSONAL MEMORY
        if (lower.startsWith("remember that ") || lower.startsWith("remember ")) {
            val fact = lower.removePrefix("remember that ").removePrefix("remember ").trim()
            val topic = when {
                fact.contains("favorite song") -> "favorite_song"
                fact.contains("dark mode") || fact.contains("theme") -> "theme_preference"
                fact.contains("name is") -> "user_name"
                else -> "note"
            }
            return StructuredAction(
                intent = "MEMORY_SAVE",
                actionType = "MEMORY_SAVE",
                parameters = mapOf("key" to topic, "fact" to fact),
                speechResponse = "I'll remember that.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("what do you remember about me") || lower.contains("what do you remember") || lower.contains("recall my") || lower.contains("what is my favorite song") || lower.contains("what did i tell you") || lower.contains("what did i say") || lower.contains("what's the thing i told you") || lower.contains("what was the note") || lower.contains("what is the note")) {
            val query = if (lower.contains("favorite song")) "favorite_song" else "everything"
            return StructuredAction(
                intent = "MEMORY_READ",
                actionType = "MEMORY_READ",
                parameters = mapOf("query" to query),
                speechResponse = "Checking my memory.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        // Conversational cancellation / drop topic (does not wipe memory)
        if (lower == "forget that" || lower == "okay forget that" || lower == "actually forget that" ||
            lower == "never mind" || lower == "nevermind" || lower == "cancel that" || lower == "drop that") {
            return StructuredAction(
                intent = "CANCEL_CURRENT_TOPIC",
                actionType = "CANCEL_CURRENT_TOPIC",
                parameters = emptyMap(),
                speechResponse = "No problem.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        if (lower.startsWith("forget my ") || lower == "forget everything" || lower.startsWith("delete memory ") || lower.contains("delete that memory")) {
            val query = if (lower == "forget everything") "everything" else lower.removePrefix("forget ").removePrefix("delete memory ").trim()
            return StructuredAction(
                intent = "MEMORY_DELETE",
                actionType = "MEMORY_DELETE",
                parameters = mapOf("query" to query),
                speechResponse = "Clearing that memory.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 23. CONNECTIVITY (WIFI, BLUETOOTH, HOTSPOT)
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val state = when {
                lower.contains("on") || lower.contains("enable") -> "on"
                lower.contains("off") || lower.contains("disable") -> "off"
                else -> "status"
            }
            return StructuredAction(
                intent = "WIFI_CONTROL",
                actionType = "WIFI_CONTROL",
                parameters = mapOf("action" to state),
                speechResponse = "Managing Wi-Fi, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("bluetooth")) {
            val state = when {
                lower.contains("on") || lower.contains("enable") -> "on"
                lower.contains("off") || lower.contains("disable") -> "off"
                else -> "status"
            }
            return StructuredAction(
                intent = "BLUETOOTH_CONTROL",
                actionType = "BLUETOOTH_CONTROL",
                parameters = mapOf("action" to state),
                speechResponse = "Checking Bluetooth, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("hotspot") || lower.contains("tethering")) {
            return StructuredAction(
                intent = "HOTSPOT_CONTROL",
                actionType = "HOTSPOT_CONTROL",
                parameters = emptyMap(),
                speechResponse = "Opening hotspot settings, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 24. LIVE LOCATION
        if (lower.contains("where am i") || lower.contains("what is my location") || lower.contains("what's my location") || lower.contains("current location") || lower.contains("my coordinates")) {
            return StructuredAction(
                intent = "LOCATION",
                actionType = "LOCATION",
                parameters = emptyMap(),
                speechResponse = "Checking your live location, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 25. CALENDAR (READ & CREATE)
        if (lower.contains("what's on my calendar") || lower.contains("what is on my calendar") || lower.contains("do i have a meeting") || lower.contains("check my calendar") || lower.contains("my schedule")) {
            val day = if (lower.contains("tomorrow")) "tomorrow" else "today"
            return StructuredAction(
                intent = "CALENDAR_READ",
                actionType = "CALENDAR_READ",
                parameters = mapOf("day" to day),
                speechResponse = "Checking your calendar for $day, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.startsWith("create a meeting") || lower.startsWith("schedule a meeting") || lower.startsWith("schedule meeting") || lower.startsWith("new meeting")) {
            val title = if (lower.contains("with")) {
                "Meeting with " + capitalizeWords(lower.substringAfter("with").trim())
            } else {
                "Meeting"
            }
            val day = if (lower.contains("tomorrow")) "tomorrow" else "today"
            return StructuredAction(
                intent = "CALENDAR_CREATE",
                actionType = "CALENDAR_CREATE",
                parameters = mapOf("title" to title, "day" to day, "hour" to "16", "minute" to "0"),
                speechResponse = "Scheduling $title for $day, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }

        // 26. DEVICE DIAGNOSTICS
        if (lower.contains("what's my battery") || lower.contains("battery level") || lower.contains("battery status") || lower.contains("how much battery")) {
            return StructuredAction(
                intent = "DEVICE_STATUS",
                actionType = "DEVICE_STATUS",
                parameters = mapOf("query" to "battery"),
                speechResponse = "Checking battery level, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("what phone am i using") || lower.contains("device model") || lower.contains("phone model")) {
            return StructuredAction(
                intent = "DEVICE_STATUS",
                actionType = "DEVICE_STATUS",
                parameters = mapOf("query" to "phone"),
                speechResponse = "Checking device model, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("android version") || lower.contains("what os")) {
            return StructuredAction(
                intent = "DEVICE_STATUS",
                actionType = "DEVICE_STATUS",
                parameters = mapOf("query" to "android"),
                speechResponse = "Checking Android version, Boss.",
                riskLevel = ActionRiskLevel.SAFE
            )
        }
        if (lower.contains("storage") || lower.contains("how much space") || lower.contains("storage space")) {
            return StructuredAction(
                intent = "DEVICE_STATUS",
                actionType = "DEVICE_STATUS",
                parameters = mapOf("query" to "storage"),
                speechResponse = "Checking storage space, Boss.",
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
