package com.example.ai

import com.example.core.BatteryMode
import com.example.identity.FridayMood
import com.example.identity.FridayPersonality
import com.example.security.ActionRiskLevel

/**
 * High-speed, zero-battery, offline rule-based NLU engine with FridayPersonality integration.
 * Ensures FRIDAY functions even without internet or when an API key is not configured.
 */
class OfflineAiProvider : AiProvider {

    override val name: String = "FRIDAY Local Edge Brain (Offline)"
    override val isCloudConnected: Boolean = false

    override suspend fun processCommand(command: String, batteryMode: BatteryMode): FridayAiResult {
        val lower = command.lowercase().trim()

        // 1. Blocked commands check
        if (lower.contains("format phone") || lower.contains("delete system") || lower.contains("run script") || lower.contains("root")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "BLOCKED_ACTION",
                isSuccess = false,
                explicitMood = FridayMood.SERIOUS
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SECURITY_VIOLATION",
                    actionType = "BLOCKED_ACTION",
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.BLOCKED
                )
            )
        }

        // 2. Sensitive commands check
        if (lower.contains("factory reset") || lower.contains("wipe") || lower.contains("erase all")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "CONFIRM_REQUEST",
                parameters = mapOf("actionName" to "data reset"),
                explicitMood = FridayMood.FOCUSED
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SENSITIVE_OPERATION",
                    actionType = "CLEAR_DATA",
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.CONFIRM,
                    confirmationPrompt = "Are you sure you want to execute this sensitive operation?"
                )
            )
        }

        // 3. Centralized IntentUnderstandingEngine (Natural variations, verbs, capabilities)
        val parsed = IntentUnderstandingEngine.parseCommand(command)
        if (parsed != null) {
            return FridayAiResult.Success(parsed)
        }

        // 4. Flashlight & Torch
        val hasFlashlightWord = (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash") || lower.contains("light")) &&
                !lower.contains("highlight") && !lower.contains("flight") && !lower.contains("daylight") && !lower.contains("traffic light")

        val isToggleFlashlightPhrase = lower == "turn on" || lower == "turn off" || lower == "on" || lower == "off" ||
                lower == "turn it off" || lower == "turn it on" || lower == "turn it back on" ||
                lower == "turn it on again" || lower == "turn it off again" || lower == "turn off again" ||
                lower == "turn on again" || lower == "switch it off" || lower == "switch it on"

        if (hasFlashlightWord || isToggleFlashlightPhrase) {
            val isOff = lower.contains("off") || lower.contains("disable") || lower.contains("stop") || lower.contains("close") || lower.contains("shut")
            val state = if (isOff) "false" else "true"
            val isFollowUp = isToggleFlashlightPhrase
            val speech = FridayPersonality.formatSpeech(
                actionType = "TOGGLE_FLASHLIGHT",
                parameters = mapOf("target" to "flashlight", "state" to state),
                isSuccess = true,
                isFollowUp = isFollowUp
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "DEVICE_CONTROL",
                    actionType = "TOGGLE_FLASHLIGHT",
                    parameters = mapOf("target" to "flashlight", "state" to state),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 4. Volume
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("mute") ||
            lower.contains("louder") || lower.contains("quieter") || lower == "up" || lower == "down") {
            val direction = when {
                lower.contains("down") || lower.contains("lower") || lower.contains("quieter") || lower.contains("decrease") -> "down"
                lower.contains("mute") || lower.contains("silent") -> "mute"
                else -> "up"
            }
            val speech = FridayPersonality.formatSpeech(
                actionType = "ADJUST_VOLUME",
                parameters = mapOf("direction" to direction),
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "DEVICE_CONTROL",
                    actionType = "ADJUST_VOLUME",
                    parameters = mapOf("target" to "volume", "direction" to direction),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 5. Battery
        if (lower.contains("battery") || lower.contains("power") || lower.contains("charge") || lower.contains("percentage")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "GET_BATTERY_INFO",
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SYSTEM_INFO",
                    actionType = "GET_BATTERY_INFO",
                    parameters = mapOf("query" to "battery"),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 6. Time & Date
        if (lower.contains("time") || lower.contains("date") || lower.contains("day is it") || lower.contains("clock")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "GET_DATE_TIME",
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SYSTEM_INFO",
                    actionType = "GET_DATE_TIME",
                    parameters = mapOf("query" to "time"),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 7. Search Command: "Search for AI news" / "Search AI news"
        if (lower.startsWith("search ") || lower.startsWith("search for ")) {
            val query = lower.removePrefix("search for ").removePrefix("search ").trim()
            val speech = FridayPersonality.formatSpeech(
                actionType = "SEARCH_WEB",
                parameters = mapOf("query" to query),
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SEARCH_WEB",
                    actionType = "SEARCH_WEB",
                    parameters = mapOf("query" to query),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 8. Open / Launch App
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val appName = lower.substringAfter(" ").trim()
            val isFollowUp = appName == "it" || appName.isBlank()
            val speech = FridayPersonality.formatSpeech(
                actionType = "OPEN_APP",
                parameters = mapOf("appName" to appName),
                isSuccess = true,
                isFollowUp = isFollowUp
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "LAUNCH_APP",
                    actionType = "OPEN_APP",
                    parameters = mapOf("appName" to appName),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 9. Close App
        if (lower == "close it" || lower == "close app" || lower == "close" || lower == "exit") {
            val speech = FridayPersonality.formatSpeech(
                actionType = "CLOSE_APP",
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "CLOSE_APP",
                    actionType = "CLOSE_APP",
                    parameters = emptyMap(),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 10. Timer
        if (lower.contains("timer") || lower.contains("countdown")) {
            val minutes = Regex("(\\d+)\\s*(?:min|minute)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val seconds = minutes * 60
            val speech = FridayPersonality.formatSpeech(
                actionType = "SET_TIMER",
                parameters = mapOf("seconds" to seconds.toString()),
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SET_TIMER",
                    actionType = "SET_TIMER",
                    parameters = mapOf("seconds" to seconds.toString(), "label" to "FRIDAY Timer"),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 11. WhatsApp: Voice Call vs Chat vs Message
        if (lower.contains("whatsapp")) {
            val isCall = lower.contains("call") || lower.contains("voice call") || lower.contains("dial")
            val isMessage = lower.contains("message") || lower.contains("text") || lower.contains("send a message")

            if (isCall) {
                val rawContact = when {
                    lower.startsWith("voice call ") -> lower.removePrefix("voice call ").substringBefore(" on whatsapp").substringBefore(" using whatsapp").trim()
                    lower.startsWith("call ") && lower.contains(" on whatsapp") -> lower.removePrefix("call ").substringBefore(" on whatsapp").trim()
                    lower.startsWith("call ") && lower.contains(" using whatsapp") -> lower.removePrefix("call ").substringBefore(" using whatsapp").trim()
                    lower.startsWith("make a whatsapp call to ") -> lower.removePrefix("make a whatsapp call to ").trim()
                    lower.startsWith("whatsapp call ") -> lower.removePrefix("whatsapp call ").trim()
                    lower.contains("call ") -> lower.substringAfter("call ").removeSuffix(" on whatsapp").removeSuffix(" using whatsapp").trim()
                    else -> lower.removePrefix("whatsapp call").removePrefix("whatsapp").trim()
                }.removeSuffix("on whatsapp").removeSuffix("using whatsapp").trim()

                val contact = rawContact.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val speech = FridayPersonality.formatSpeech(
                    actionType = "WHATSAPP_CALL",
                    parameters = mapOf("contact" to contact),
                    isSuccess = true
                )
                return FridayAiResult.Success(
                    StructuredAction(
                        intent = "WHATSAPP_CALL",
                        actionType = "WHATSAPP_CALL",
                        parameters = mapOf("contact" to contact),
                        speechResponse = speech.speechText,
                        riskLevel = ActionRiskLevel.SAFE
                    )
                )
            } else if (isMessage) {
                val rawContact = when {
                    lower.startsWith("message ") -> lower.removePrefix("message ").substringBefore(" on whatsapp").substringBefore(" that ").trim()
                    lower.startsWith("send a message to ") -> lower.removePrefix("send a message to ").substringBefore(" on whatsapp").substringBefore(" that ").trim()
                    lower.startsWith("text ") -> lower.removePrefix("text ").substringBefore(" on whatsapp").substringBefore(" that ").trim()
                    else -> lower.substringAfter("message ").substringBefore(" on whatsapp").trim()
                }.removeSuffix("on whatsapp").trim()

                val contact = rawContact.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val speech = FridayPersonality.formatSpeech(
                    actionType = "WHATSAPP_MESSAGE",
                    parameters = mapOf("contact" to contact),
                    isSuccess = true
                )
                return FridayAiResult.Success(
                    StructuredAction(
                        intent = "WHATSAPP_MESSAGE",
                        actionType = "WHATSAPP_MESSAGE",
                        parameters = mapOf("contact" to contact),
                        speechResponse = speech.speechText,
                        riskLevel = ActionRiskLevel.SAFE
                    )
                )
            } else {
                // "WhatsApp Rahul", "Open WhatsApp chat with Rahul"
                val rawContact = when {
                    lower.startsWith("whatsapp ") -> lower.removePrefix("whatsapp ").trim()
                    lower.startsWith("open whatsapp chat with ") -> lower.removePrefix("open whatsapp chat with ").trim()
                    lower.startsWith("chat with ") -> lower.removePrefix("chat with ").substringBefore(" on whatsapp").trim()
                    else -> lower.substringAfter("whatsapp ").trim()
                }.removeSuffix("on whatsapp").trim()

                val contact = rawContact.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val speech = FridayPersonality.formatSpeech(
                    actionType = "WHATSAPP_CHAT",
                    parameters = mapOf("contact" to contact),
                    isSuccess = true
                )
                return FridayAiResult.Success(
                    StructuredAction(
                        intent = "WHATSAPP_CHAT",
                        actionType = "WHATSAPP_CHAT",
                        parameters = mapOf("contact" to contact),
                        speechResponse = speech.speechText,
                        riskLevel = ActionRiskLevel.SAFE
                    )
                )
            }
        }

        // 12. Phone Call Command
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") || lower.startsWith("make a call to ")) {
            val rawContact = when {
                lower.startsWith("make a call to ") -> lower.removePrefix("make a call to ").trim()
                lower.startsWith("call ") -> lower.removePrefix("call ").trim()
                lower.startsWith("dial ") -> lower.removePrefix("dial ").trim()
                lower.startsWith("phone ") -> lower.removePrefix("phone ").trim()
                else -> ""
            }

            val contact = rawContact.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            val speech = FridayPersonality.formatSpeech(
                actionType = "CALL_CONTACT",
                parameters = mapOf("contact" to contact),
                isSuccess = true
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "CALL_CONTACT",
                    actionType = "CALL_CONTACT",
                    parameters = mapOf("contact" to contact),
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 13. Media Controls
        if (lower == "pause" || lower == "pause music" || lower == "pause media" || lower == "stop music" ||
            lower == "play" || lower == "play music" || lower == "resume" || lower == "resume music" ||
            lower == "next song" || lower == "next track" || lower == "skip" || lower == "skip song" ||
            lower == "previous song" || lower == "previous track") {
            val cmd = when {
                lower.contains("pause") || lower.contains("stop") -> "pause"
                lower.contains("play") || lower.contains("resume") -> "play"
                lower.contains("next") || lower.contains("skip") -> "next"
                lower.contains("previous") -> "previous"
                else -> "toggle"
            }
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "DEVICE_CONTROL",
                    actionType = "MEDIA_CONTROL",
                    parameters = mapOf("command" to cmd),
                    speechResponse = "Media updated, Boss.",
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 14. Settings Shortcut
        if (lower.startsWith("open settings") || lower.contains("wifi settings") || lower.contains("bluetooth settings") ||
            lower.contains("display settings") || lower.contains("sound settings")) {
            val cat = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("display") || lower.contains("brightness") -> "display"
                lower.contains("sound") -> "sound"
                else -> "general"
            }
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "SETTINGS_ACTION",
                    actionType = "SETTINGS_ACTION",
                    parameters = mapOf("setting" to cat),
                    speechResponse = "Opening $cat settings, Boss.",
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 15. UI Automation Commands
        if (lower == "go back" || lower == "back" || lower.startsWith("tap ") || lower.startsWith("click ") ||
            lower == "scroll down" || lower == "scroll up" || lower == "go home") {
            val (op, target) = when {
                lower == "go back" || lower == "back" -> Pair("back", null)
                lower == "go home" -> Pair("home", null)
                lower == "scroll down" -> Pair("scroll_down", null)
                lower == "scroll up" -> Pair("scroll_up", null)
                lower.startsWith("tap ") -> Pair("click", lower.removePrefix("tap ").trim())
                lower.startsWith("click ") -> Pair("click", lower.removePrefix("click ").trim())
                else -> Pair("click", null)
            }
            val params = mutableMapOf("operation" to op)
            if (target != null) params["target"] = target
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "UI_AUTOMATION",
                    actionType = "UI_AUTOMATION",
                    parameters = params,
                    speechResponse = "On it, Boss.",
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 16. Greetings & Identity
        if (lower.contains("who are you") || lower.contains("what is your name")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "SPEAK_RESPONSE",
                parameters = mapOf("message" to "I am FRIDAY, your personal native Android voice assistant."),
                isSuccess = true,
                explicitMood = FridayMood.HAPPY
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "GENERAL_QUERY",
                    actionType = "SPEAK_RESPONSE",
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        if (lower.contains("hello") || lower.contains("hi friday") || lower.contains("hey friday")) {
            val speech = FridayPersonality.formatSpeech(
                actionType = "SPEAK_RESPONSE",
                parameters = mapOf("message" to "Online and ready. What can I do for you?"),
                isSuccess = true,
                explicitMood = FridayMood.CALM
            )
            return FridayAiResult.Success(
                StructuredAction(
                    intent = "GENERAL_QUERY",
                    actionType = "SPEAK_RESPONSE",
                    speechResponse = speech.speechText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 17. Offline Question / Info & Natural Dialogue handling
        val isQuestion = lower.startsWith("what") || lower.startsWith("who") || lower.startsWith("where") ||
                lower.startsWith("when") || lower.startsWith("why") || lower.startsWith("how") || lower.startsWith("is ") ||
                lower.startsWith("can you explain") || lower.startsWith("tell me about") || lower.startsWith("explain ") ||
                lower == "tell me more" || lower == "why is that" || lower == "why" || lower == "give me an example"

        if (isQuestion) {
            val responseText = when {
                // Space & Astronomy
                lower.contains("how far is the moon") || (lower.contains("moon") && lower.contains("how far")) ->
                    "The Moon is about 384,400 kilometers away from Earth."
                lower.contains("what about mars") || (lower.contains("mars") && (lower.contains("distance") || lower.contains("how far"))) ->
                    "Mars is on average about 225 million kilometers from Earth, ranging from 54 million to 400 million kilometers depending on their orbits."
                lower.contains("tell me about space") || lower == "what do you think about space" || lower.contains("about space") ->
                    "Space is an endless frontier filled with billions of galaxies, black holes, and uncharted worlds waiting to be explored."
                // Science & Nature
                lower.contains("why is the sky blue") || (lower.contains("sky") && lower.contains("blue")) ->
                    "The sky is blue because gases in Earth's atmosphere scatter sunlight in all directions, and blue light waves scatter more easily than longer wavelengths."
                lower.contains("photosynthesis") ->
                    "Photosynthesis is the process where green plants convert sunlight, water, and carbon dioxide into oxygen and glucose for energy."
                // People & Inventions
                lower.contains("elon musk") ->
                    "Elon Musk is a technology entrepreneur who leads Tesla, founded SpaceX, and owns X."
                lower.contains("telephone") && (lower.contains("who") || lower.contains("invent")) ->
                    "Alexander Graham Bell is credited with patenting the first practical telephone in 1876."
                // Artificial Intelligence
                lower.contains("explain ai") || lower.contains("what is ai") || lower.contains("artificial intelligence") ->
                    "Artificial intelligence is computer systems designed to learn patterns from data, recognize speech, solve problems, and make decisions."
                // Conversational continuations & follow-ups
                lower == "tell me more" ->
                    "Astronomers estimate there are more than two trillion galaxies across the observable universe, each with billions of stars."
                lower == "why is that" || lower == "why" ->
                    "It comes down to fundamental laws of physics and nature, such as light scattering and gravitational forces."
                lower == "give me an example" ->
                    "For example, recommendation engines, speech recognition, and autonomous navigation are real-world applications of AI."
                // Math
                lower.contains("2 + 2") || lower.contains("2 plus 2") -> "2 plus 2 is 4."
                // Weather & News
                lower.contains("weather") -> "I don't have internet access right now to check the live weather."
                lower.contains("latest ai news") || (lower.contains("ai") && lower.contains("news")) ->
                    "Recent AI developments focus on multimodal reasoning, on-device intelligence, and autonomous agents."
                lower.contains("news") -> "I'm offline right now, so I can't fetch the latest live headlines."
                // Capitals
                lower.contains("capital of japan") -> "The capital of Japan is Tokyo."
                lower.contains("capital of france") -> "The capital of France is Paris."
                lower.contains("capital of usa") || lower.contains("capital of the united states") || lower.contains("capital of america") -> "The capital of the United States is Washington, D.C."
                lower.contains("capital of india") -> "The capital of India is New Delhi."
                lower.contains("time") -> "You can ask me for the current time and I will check your device clock."
                else -> "I'm running in local offline mode right now, so I can handle phone actions and basic queries. Connect to the internet for full AI answers."
            }

            return FridayAiResult.Success(
                StructuredAction(
                    intent = "GENERAL_QUERY",
                    actionType = "SPEAK_RESPONSE",
                    speechResponse = responseText,
                    riskLevel = ActionRiskLevel.SAFE
                )
            )
        }

        // 14. Unknown speech fallback: conversational apology instead of robotic system log
        val fallback = FridayPersonality.formatSpeech(
            actionType = "UNKNOWN_INTENT",
            parameters = emptyMap(),
            isSuccess = true,
            explicitMood = FridayMood.APOLOGETIC
        )
        return FridayAiResult.Success(
            StructuredAction(
                intent = "UNKNOWN",
                actionType = "UNKNOWN_INTENT",
                speechResponse = fallback.speechText,
                riskLevel = ActionRiskLevel.SAFE
            )
        )
    }

    /**
     * Fast-path check: returns true if the command is a recognized local device action,
     * allowing instant execution (< 1ms) without network round-trips.
     */
    fun canHandleLocally(command: String): Boolean {
        val lower = command.lowercase().trim()
        if (lower.isBlank()) return false

        if (IntentUnderstandingEngine.parseCommand(command) != null) return true

        // Flashlight & Torch
        val hasFlashlightWord = (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash") || lower.contains("light")) &&
                !lower.contains("highlight") && !lower.contains("flight") && !lower.contains("daylight") && !lower.contains("traffic light")
        if (hasFlashlightWord || lower == "turn on" || lower == "turn off" || lower == "on" || lower == "off" ||
            lower == "turn it off" || lower == "turn it on" || lower == "turn it back on" ||
            lower == "turn it on again" || lower == "turn it off again" || lower == "turn off again" ||
            lower == "turn on again" || lower == "switch it off" || lower == "switch it on") return true

        // Volume
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("mute") ||
            lower.contains("louder") || lower.contains("quieter") || lower == "up" || lower == "down") {
            return true
        }

        // WhatsApp and Phone Calls
        if (lower.contains("whatsapp") || lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ")) {
            return true
        }

        // Battery
        if (lower.contains("battery") || lower.contains("power") || lower.contains("charge") || lower.contains("percentage") || lower.contains("percent")) {
            return true
        }

        // Time & Date
        if (lower.contains("time") || lower.contains("date") || lower.contains("day is it") || lower.contains("clock") || lower.contains("today")) {
            return true
        }

        // App Launch & Close
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") ||
            lower == "close it" || lower == "close app" || lower == "close" || lower == "exit") {
            return true
        }

        // Media & Playback (e.g. play Grand Escape, pause, resume)
        if (lower.startsWith("play ") || lower == "play" || lower == "pause" || lower == "resume" ||
            lower.contains("music") || lower.contains("song") || lower.contains("track")) {
            return true
        }

        // UI Navigation (e.g. go back, back, go home)
        if (lower == "go back" || lower == "back" || lower == "go home" || lower == "scroll down" || lower == "scroll up") {
            return true
        }

        // Search
        if (lower.startsWith("search ") || lower.startsWith("search for ")) {
            return true
        }

        // Timer
        if (lower.contains("timer") || lower.contains("countdown") || lower.contains("alarm") || lower.contains("stopwatch")) {
            return true
        }

        // Sensitive / Security
        if (lower.contains("format") || lower.contains("wipe") || lower.contains("erase all") || lower.contains("factory reset") || lower.contains("root")) {
            return true
        }

        // Assistant Core Identity
        if (lower.contains("who are you") || lower.contains("what is your name") || lower.contains("hello") || lower.contains("hi friday") || lower.contains("hey friday")) {
            return true
        }

        return false
    }
}
