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
        if (_recentTurns.size >= 4) {
            _recentTurns.removeFirst()
        }
        _recentTurns.addLast(ConversationTurn(userSpeech, assistantReply, category))
        touchInteraction()
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
    }

    /**
     * Compact summary for Gemini contextual grounding. Bounded and lightweight.
     */
    fun getRecentContextSummary(): String {
        val parts = mutableListOf<String>()
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

        val last = lastAction

        // 1. Flashlight context
        if (last != null && (last.targetEntity == "flashlight" || last.actionType == "TOGGLE_FLASHLIGHT")) {
            val isOff = lower == "off" ||
                    lower == "turn it off" ||
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
                    lower == "turn on" ||
                    lower == "turn it back on" ||
                    lower == "turn back on" ||
                    lower == "turn it on again" ||
                    lower == "turn on again" ||
                    lower == "switch it on" ||
                    lower == "switch on" ||
                    lower == "put it on" ||
                    lower == "back on" ||
                    lower == "again" ||
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
            if (lower == "louder" || lower == "up" || lower == "more" || lower == "turn it up" || lower == "increase" || lower == "higher") {
                return ResolvedContextCommand(
                    resolvedText = "turn volume up",
                    actionType = "ADJUST_VOLUME",
                    parameters = mapOf("target" to "volume", "direction" to "up"),
                    targetEntity = "volume"
                )
            }
            if (lower == "quieter" || lower == "down" || lower == "less" || lower == "turn it down" || lower == "lower" || lower == "decrease") {
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

            // Contextual search: "Search for AI news" after "Open YouTube"
            if (lower.startsWith("search ") || lower.startsWith("search for ")) {
                val query = if (lower.startsWith("search for ")) {
                    trimmed.substring(11).trim()
                } else {
                    trimmed.substring(7).trim()
                }
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

        if (lower == "go back" || lower == "back" || lower == "return") {
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
        if (last != null && (lower == "again" || lower == "do that again" || lower == "repeat" || lower == "try again")) {
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
