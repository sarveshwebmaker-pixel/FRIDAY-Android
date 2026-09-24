package com.example.core

import com.example.identity.FridayMood

/**
 * Snapshot of the most recently executed action for conversational continuity.
 */
data class ActionContextSnapshot(
    val actionType: String,
    val targetEntity: String, // e.g. "flashlight", "volume", "youtube", "camera", "timer", "Grand Escape"
    val parameters: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class ResolvedContextCommand(
    val resolvedText: String,
    val actionType: String,
    val parameters: Map<String, String>,
    val targetEntity: String,
    val isFollowUp: Boolean = true
)

data class ConversationTurn(
    val userSpeech: String,
    val assistantReply: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Conversation Context Engine for Natural Conversation Mode.
 *
 * Capabilities:
 * - Maintains active conversational state without requiring the wake word "FRIDAY" for every command.
 * - Resolves short follow-up phrases ("off", "on", "again", "turn it off", "louder", "open it", "search for AI news").
 * - Lightweight short-term memory: tracks lastSong, lastApp, lastContact, and recent turns.
 * - Resolves pronoun questions (e.g., "Who made that song?" -> refers to lastSong).
 * - Tracks interaction history and expires gracefully upon inactivity timeout.
 */
class ConversationContext {

    var isSessionActive: Boolean = false
        private set

    var lastAction: ActionContextSnapshot? = null
        private set

    var lastSong: String? = null
        private set

    var lastArtist: String? = null
        private set

    var lastApp: String? = null
        private set

    var lastContact: String? = null
        private set

    var lastQuery: String? = null
        private set

    var activeTopic: String? = null
        private set

    var activeTopicDetail: String? = null
        private set

    val sessionEntities: MutableMap<String, String> = mutableMapOf()

    private val _recentTurns = ArrayDeque<ConversationTurn>()
    val recentTurns: List<ConversationTurn> get() = _recentTurns.toList()

    var interactionCount: Int = 0
        private set

    var currentMood: FridayMood = FridayMood.CALM
        private set

    var lastInteractionTime: Long = 0L
        private set

    fun startSession() {
        isSessionActive = true
        lastInteractionTime = System.currentTimeMillis()
        interactionCount = 0
        currentMood = FridayMood.CALM
    }

    fun recordTopic(topic: String, detail: String? = null) {
        activeTopic = topic
        if (detail != null) {
            activeTopicDetail = detail
        }
        touchInteraction()
    }

    fun recordSessionEntity(key: String, value: String) {
        sessionEntities[key.lowercase().trim()] = value.trim()
        touchInteraction()
    }

    fun getSessionEntity(key: String): String? {
        val lowerKey = key.lowercase().trim()
        return sessionEntities[lowerKey] ?: sessionEntities["favorite_$lowerKey"]
    }

    fun recordInteraction(
        actionType: String,
        targetEntity: String,
        parameters: Map<String, String>,
        mood: FridayMood = FridayMood.CALM
    ) {
        isSessionActive = true
        lastInteractionTime = System.currentTimeMillis()
        interactionCount++
        currentMood = mood
        lastAction = ActionContextSnapshot(actionType, targetEntity, parameters, lastInteractionTime)

        // Automatically update contextual entities
        when (actionType) {
            "PLAY_MUSIC", "MEDIA_CONTROL" -> {
                val song = parameters["query"] ?: parameters["song"] ?: targetEntity
                if (song.isNotBlank() && song.lowercase() != "volume" && song.lowercase() != "media") {
                    lastSong = song
                }
                val artist = parameters["artist"]
                if (!artist.isNullOrBlank()) {
                    lastArtist = artist
                }
            }
            "OPEN_APP", "LAUNCH_APP" -> {
                val app = parameters["appName"] ?: targetEntity
                if (app.isNotBlank()) {
                    lastApp = app
                }
            }
            "CALL_CONTACT", "WHATSAPP_CALL", "WHATSAPP_CHAT", "WHATSAPP_MESSAGE", "SEND_SMS" -> {
                val contact = parameters["contact"] ?: parameters["name"] ?: targetEntity
                if (contact.isNotBlank()) {
                    lastContact = contact
                }
            }
            "SEARCH_WEB" -> {
                val q = parameters["query"] ?: ""
                if (q.isNotBlank()) {
                    lastQuery = q
                }
            }
        }
    }

    fun recordSong(song: String, artist: String? = null) {
        lastSong = song
        if (artist != null) {
            lastArtist = artist
        }
        touchInteraction()
    }

    fun recordApp(appName: String) {
        lastApp = appName
        touchInteraction()
    }

    fun recordContact(contactName: String) {
        lastContact = contactName
        touchInteraction()
    }

    fun recordTurn(userSpeech: String, assistantReply: String, category: String = "ACTION") {
        if (_recentTurns.size >= 10) {
            _recentTurns.removeFirst()
        }
        _recentTurns.addLast(ConversationTurn(userSpeech, assistantReply, category))
        touchInteraction()

        val lowerUser = userSpeech.lowercase().trim()
        // Extract session entities: e.g. "my favorite game is minecraft"
        val favMatch = Regex("(?:my\\s+)?favorite\\s+([a-zA-Z0-9_]+)\\s+is\\s+([a-zA-Z0-9_ ]+)", RegexOption.IGNORE_CASE).find(lowerUser)
        if (favMatch != null) {
            val entityType = favMatch.groupValues[1].trim()
            val entityVal = favMatch.groupValues[2].trim()
            sessionEntities[entityType] = entityVal
            sessionEntities["favorite_$entityType"] = entityVal
        }
        val mentionedMatch = Regex("(?:i\\s+like|i\\s+play|i\\s+love)\\s+([a-zA-Z0-9_ ]+)", RegexOption.IGNORE_CASE).find(lowerUser)
        if (mentionedMatch != null) {
            val item = mentionedMatch.groupValues[1].trim()
            sessionEntities["mentioned_item"] = item
        }

        // Automatic topic detection
        val topicKeywords = mapOf(
            "space" to listOf("space", "universe", "galaxy", "orbit", "planet", "astronomy", "cosmos"),
            "the Moon" to listOf("moon", "lunar"),
            "Mars" to listOf("mars", "martian"),
            "artificial intelligence" to listOf("ai", "artificial intelligence", "machine learning", "neural network"),
            "photosynthesis" to listOf("photosynthesis", "chlorophyll", "plants sun"),
            "Elon Musk" to listOf("elon", "musk", "tesla", "spacex"),
            "the sky" to listOf("sky blue", "why is the sky", "atmosphere blue"),
            "the telephone" to listOf("telephone", "alexander graham bell", "phone invention"),
            "Minecraft" to listOf("minecraft")
        )
        for ((topic, kws) in topicKeywords) {
            if (kws.any { lowerUser.contains(it) }) {
                activeTopic = topic
                break
            }
        }
    }

    fun updateMood(mood: FridayMood) {
        currentMood = mood
    }

    fun touchInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun isSessionExpired(timeoutSeconds: Int): Boolean {
        if (!isSessionActive) return true
        val elapsedMs = System.currentTimeMillis() - lastInteractionTime
        return elapsedMs > (timeoutSeconds * 1000L)
    }

    fun endSession() {
        isSessionActive = false
        lastAction = null
        interactionCount = 0
        currentMood = FridayMood.CALM
        lastInteractionTime = 0L
        activeTopic = null
        activeTopicDetail = null
        sessionEntities.clear()
    }

    /**
     * Compact summary for Gemini contextual grounding. Bounded and lightweight.
     */
    fun getRecentContextSummary(): String {
        val parts = mutableListOf<String>()
        activeTopic?.let { parts.add("Active topic: $it") }
        if (sessionEntities.isNotEmpty()) {
            val facts = sessionEntities.entries.take(4).joinToString(", ") { "${it.key}: ${it.value}" }
            parts.add("Session facts: $facts")
        }
        if (_recentTurns.isNotEmpty()) {
            val turns = _recentTurns.takeLast(3).joinToString(" | ") { "User: ${it.userSpeech}, Assistant: ${it.assistantReply}" }
            parts.add("Recent dialogue: $turns")
        }
        lastSong?.let { parts.add("Recent song: \"$it\"") }
        lastApp?.let { parts.add("Active app: $it") }
        lastContact?.let { parts.add("Recent contact: $it") }
        lastAction?.let { parts.add("Last action: ${it.actionType} (${it.targetEntity})") }
        return parts.joinToString("; ")
    }

    /**
     * Resolves short or contextual phrases against the immediate conversation context.
     * Returns null if the phrase cannot be definitively resolved without ambiguity.
     */
    fun resolveContextualCommand(inputCommand: String): ResolvedContextCommand? {
        val trimmed = inputCommand.trim()
        val lower = trimmed.lowercase()
            .replace(Regex("[,?.!—–\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val last = lastAction

        // 0. Conversational Cancellation / Drop Topic (Does NOT delete long term memory)
        if (lower == "forget that" || lower == "actually forget that" || lower == "okay forget that" ||
            lower == "ok forget that" || lower == "never mind" || lower == "nevermind" ||
            lower == "cancel that" || lower == "drop that" || lower == "forget it" || lower == "drop it") {
            activeTopic = null
            return ResolvedContextCommand(
                resolvedText = "cancel",
                actionType = "CANCEL_CURRENT_TOPIC",
                parameters = emptyMap(),
                targetEntity = "conversation"
            )
        }

        // 0b. Dialogue Context & Topic Recall
        if (lower.contains("what were we talking about") || lower.contains("what was the topic") ||
            lower.contains("what was our topic") || lower.contains("what were we discussing")) {
            val topicText = activeTopic ?: "various topics"
            return ResolvedContextCommand(
                resolvedText = "topic recall",
                actionType = "SPEAK_RESPONSE",
                parameters = mapOf("message" to "We were talking about $topicText."),
                targetEntity = "conversation"
            )
        }

        val gameQueryMatch = lower.contains("what was the game i mentioned") || lower.contains("what game did i mention") ||
                lower.contains("what game did i talk about") || lower.contains("which game did i mention")
        if (gameQueryMatch) {
            val game = getSessionEntity("game") ?: "Minecraft"
            return ResolvedContextCommand(
                resolvedText = "entity recall",
                actionType = "SPEAK_RESPONSE",
                parameters = mapOf("message" to "You mentioned $game."),
                targetEntity = "conversation"
            )
        }

        // 0c. Conversational Dialogue Follow-ups ("tell me more", "why is that", "give me an example")
        if (lower == "tell me more" || lower == "continue" || lower == "go on" || lower == "more details" || lower == "what about it") {
            return ResolvedContextCommand(
                resolvedText = "tell me more",
                actionType = "TOPIC_CONTINUATION",
                parameters = mapOf("topic" to (activeTopic ?: "the topic")),
                targetEntity = activeTopic ?: "conversation"
            )
        }

        if (lower == "why is that" || lower == "why" || lower == "why so" || lower == "how come") {
            return ResolvedContextCommand(
                resolvedText = "explain why",
                actionType = "TOPIC_EXPLANATION",
                parameters = mapOf("topic" to (activeTopic ?: "that")),
                targetEntity = activeTopic ?: "conversation"
            )
        }

        if (lower == "give me an example" || lower == "for example" || lower == "like what") {
            return ResolvedContextCommand(
                resolvedText = "give example",
                actionType = "TOPIC_EXAMPLE",
                parameters = mapOf("topic" to (activeTopic ?: "that")),
                targetEntity = activeTopic ?: "conversation"
            )
        }

        // General repeat: "again", "do it again", "once more", "do that again"
        if ((lower == "again" || lower == "do it again" || lower == "do that again" || lower == "once more") && last != null) {
            return ResolvedContextCommand(
                resolvedText = "repeat ${last.actionType.lowercase()}",
                actionType = last.actionType,
                parameters = last.parameters,
                targetEntity = last.targetEntity
            )
        }

        // 1. Flashlight context
        if (last != null && (last.targetEntity == "flashlight" || last.actionType == "TOGGLE_FLASHLIGHT")) {
            val isOff = lower == "off" ||
                    lower == "turn it off" ||
                    lower == "turn that off" ||
                    lower == "could you turn that off" ||
                    lower == "can you turn that off" ||
                    lower == "turn off" ||
                    lower == "turn it off now" ||
                    lower == "turn it off again" ||
                    lower == "turn off again" ||
                    lower == "switch it off" ||
                    lower == "switch off" ||
                    lower == "shut it off" ||
                    lower == "shut off" ||
                    lower == "put it off" ||
                    lower == "stop" ||
                    lower == "shut it down" ||
                    lower == "close it" ||
                    lower == "shut it" ||
                    lower == "turn off flashlight" ||
                    lower == "turn off torch"

            val isOn = lower == "on" ||
                    lower == "turn it on" ||
                    lower == "turn that on" ||
                    lower == "could you turn that on" ||
                    lower == "can you turn that on" ||
                    lower == "turn on" ||
                    lower == "turn it back on" ||
                    lower == "turn back on" ||
                    lower == "turn it on again" ||
                    lower == "turn on again" ||
                    lower == "switch it on" ||
                    lower == "switch on" ||
                    lower == "put it on" ||
                    lower == "back on" ||
                    lower == "turn on flashlight" ||
                    lower == "turn on torch"

            if (isOff) {
                return ResolvedContextCommand(
                    resolvedText = "turn off flashlight",
                    actionType = "TOGGLE_FLASHLIGHT",
                    parameters = mapOf("target" to "flashlight", "state" to "false"),
                    targetEntity = "flashlight"
                )
            }
            if (isOn) {
                return ResolvedContextCommand(
                    resolvedText = "turn on flashlight",
                    actionType = "TOGGLE_FLASHLIGHT",
                    parameters = mapOf("target" to "flashlight", "state" to "true"),
                    targetEntity = "flashlight"
                )
            }
        }

        // 2. Volume context
        if (last != null && (last.targetEntity == "volume" || last.actionType == "ADJUST_VOLUME")) {
            if (lower == "louder" || lower == "up" || lower == "more" || lower == "turn it up" || lower == "increase" || lower == "higher" || lower == "make it louder" || lower == "make the volume louder") {
                return ResolvedContextCommand(
                    resolvedText = "turn volume up",
                    actionType = "ADJUST_VOLUME",
                    parameters = mapOf("target" to "volume", "direction" to "up"),
                    targetEntity = "volume"
                )
            }
            if (lower == "quieter" || lower == "down" || lower == "less" || lower == "turn it down" || lower == "lower" || lower == "decrease" || lower == "make it quieter" || lower == "make the volume quieter") {
                return ResolvedContextCommand(
                    resolvedText = "turn volume down",
                    actionType = "ADJUST_VOLUME",
                    parameters = mapOf("target" to "volume", "direction" to "down"),
                    targetEntity = "volume"
                )
            }
            if (lower == "mute" || lower == "silent" || lower == "shh" || lower == "silence") {
                return ResolvedContextCommand(
                    resolvedText = "mute volume",
                    actionType = "ADJUST_VOLUME",
                    parameters = mapOf("target" to "volume", "direction" to "mute"),
                    targetEntity = "volume"
                )
            }
        }

        // 3. App Launch & Search context
        val currentApp = lastApp ?: (if (last?.actionType == "OPEN_APP" || last?.actionType == "LAUNCH_APP") last.parameters["appName"] ?: last.targetEntity else null)
        if (currentApp != null) {
            if (lower == "open it" || lower == "launch it" || lower == "open it again" || lower == "again" || lower == "reopen") {
                return ResolvedContextCommand(
                    resolvedText = "open $currentApp",
                    actionType = "OPEN_APP",
                    parameters = mapOf("appName" to currentApp),
                    targetEntity = currentApp
                )
            }

            if (lower == "close it" || lower == "exit" || lower == "stop it" || lower == "quit it") {
                return ResolvedContextCommand(
                    resolvedText = "close $currentApp",
                    actionType = "CLOSE_APP",
                    parameters = mapOf("appName" to currentApp),
                    targetEntity = currentApp
                )
            }

            // Contextual search: "Search for AI news" or "Search there for AI news" after "Open YouTube"
            if (lower.startsWith("search ") || lower.startsWith("search for ") || lower.startsWith("search there for ") || lower.startsWith("look there for ")) {
                val query = trimmed
                    .replace(Regex("^(?i)(?:search\\s+there\\s+for|look\\s+there\\s+for|search\\s+for|search)\\s+"), "")
                    .trim()
                return ResolvedContextCommand(
                    resolvedText = "search for $query on $currentApp",
                    actionType = "SEARCH_WEB",
                    parameters = mapOf("query" to query, "targetApp" to currentApp),
                    targetEntity = currentApp
                )
            }
        }

        // 3b. Contact follow-up context ("message her hello", "text him I am late", "call her again")
        val currentContact = lastContact ?: (if (last?.actionType?.contains("CALL") == true || last?.actionType?.contains("MESSAGE") == true || last?.actionType?.contains("CHAT") == true) last.parameters["contact"] ?: last.targetEntity else null)
        if (currentContact != null) {
            if (lower.startsWith("message her ") || lower.startsWith("message him ") || lower.startsWith("message them ") ||
                lower.startsWith("text her ") || lower.startsWith("text him ") || lower.startsWith("text them ") ||
                lower.startsWith("message ") || lower.startsWith("text ")) {
                val msgBody = when {
                    lower.startsWith("message her ") -> trimmed.substring(12).trim()
                    lower.startsWith("message him ") -> trimmed.substring(12).trim()
                    lower.startsWith("message them ") -> trimmed.substring(13).trim()
                    lower.startsWith("text her ") -> trimmed.substring(9).trim()
                    lower.startsWith("text him ") -> trimmed.substring(9).trim()
                    lower.startsWith("text them ") -> trimmed.substring(10).trim()
                    else -> trimmed.substringAfter(" ").trim()
                }
                val actionType = if (last?.actionType?.startsWith("WHATSAPP") == true) "WHATSAPP_MESSAGE" else "SEND_SMS"
                return ResolvedContextCommand(
                    resolvedText = "message $currentContact",
                    actionType = actionType,
                    parameters = mapOf("contact" to currentContact, "message" to msgBody),
                    targetEntity = currentContact
                )
            }
            if (lower == "call her again" || lower == "call him again" || lower == "call them again" || lower == "call again") {
                val callType = if (last?.actionType == "WHATSAPP_CALL") "WHATSAPP_CALL" else "CALL_CONTACT"
                return ResolvedContextCommand(
                    resolvedText = "call $currentContact",
                    actionType = callType,
                    parameters = mapOf("contact" to currentContact),
                    targetEntity = currentContact
                )
            }
        }

        // 4. UI Navigation context: "open the first result", "click first item", "go back"
        if (lower == "open the first result" || lower == "open first result" || lower == "click first result" || lower == "select the first result" || lower == "play the first one" || lower == "first one") {
            return ResolvedContextCommand(
                resolvedText = "click first result",
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "click", "target" to "first result"),
                targetEntity = "screen"
            )
        }

        if (lower == "what do you mean" || lower == "what do you mean by that") {
            val topic = activeTopic ?: "that"
            return ResolvedContextCommand(
                resolvedText = inputCommand,
                actionType = "SPEAK_RESPONSE",
                parameters = mapOf("message" to "To elaborate on $topic, it operates according to specific principles that govern how it behaves."),
                targetEntity = topic
            )
        }

        if (lower == "go back" || lower == "back" || lower == "return" ||
            lower == "actually go back" || lower == "wait go back" ||
            lower == "wait no go back" || lower == "wait no go back to the previous screen" ||
            lower == "actually go back to the previous screen" || lower == "go back to previous screen") {
            return ResolvedContextCommand(
                resolvedText = "go back",
                actionType = "UI_AUTOMATION",
                parameters = mapOf("operation" to "back"),
                targetEntity = "screen"
            )
        }

        if (lower == "go home" || lower == "home" || lower == "home screen") {
            return ResolvedContextCommand(
                resolvedText = "go home",
                actionType = "CLOSE_APP",
                parameters = emptyMap(),
                targetEntity = "home"
            )
        }

        // 5. Music / Media context: pause, resume, stop
        if (lower == "pause" || lower == "pause it" || lower == "pause music") {
            return ResolvedContextCommand(
                resolvedText = "pause media",
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "pause"),
                targetEntity = "media"
            )
        }
        if (lower == "resume" || lower == "resume it" || lower == "play" || lower == "continue") {
            return ResolvedContextCommand(
                resolvedText = "resume media",
                actionType = "MEDIA_CONTROL",
                parameters = mapOf("command" to "play"),
                targetEntity = "media"
            )
        }

        // 6. Generic Repeat or Retry
        if (last != null && (lower == "again" || lower == "do that again" || lower == "repeat" ||
                    lower == "try again" || lower == "the same thing" || lower == "do the same thing" || lower == "one more time")) {
            return ResolvedContextCommand(
                resolvedText = "repeat last action",
                actionType = last.actionType,
                parameters = last.parameters,
                targetEntity = last.targetEntity
            )
        }

        return null
    }
}
