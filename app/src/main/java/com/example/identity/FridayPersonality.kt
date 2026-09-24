package com.example.identity

import kotlin.random.Random

data class FormattedSpeech(
    val speechText: String,
    val mood: FridayMood
)

/**
 * Natural Conversational Persona Engine for FRIDAY.
 *
 * Replaces dry, robotic system logs ("Command executed successfully", "Intent score: 0.83")
 * with human-like, conversational speech that sounds calm, intelligent, confident,
 * helpful, and appropriately expressive.
 */
object FridayPersonality {

    private var bossOccurrenceCounter = 0

    /**
     * FRIDAY uses natural, modern assistant phrasing with occasional, natural "Boss" (~every 6 turns).
     */
    fun shouldIncludeBoss(): Boolean {
        bossOccurrenceCounter++
        return (bossOccurrenceCounter % 6 == 0)
    }

    fun resetBossCounter() {
        bossOccurrenceCounter = 0
    }

    /**
     * Formats speech output naturally with appropriate mood and conversational cadence.
     */
    fun formatSpeech(
        actionType: String,
        parameters: Map<String, String> = emptyMap(),
        isSuccess: Boolean = true,
        isFollowUp: Boolean = false,
        errorDetail: String? = null,
        targetApp: String? = null,
        explicitMood: FridayMood? = null
    ): FormattedSpeech {
        val useBoss = shouldIncludeBoss()
        val bossSuffix = if (useBoss) ", Boss" else ""
        val bossPrefix = if (useBoss) "Boss, " else ""

        if (!isSuccess) {
            val mood = explicitMood ?: FridayMood.APOLOGETIC
            val text = when {
                errorDetail?.contains("security", ignoreCase = true) == true ||
                errorDetail?.contains("blocked", ignoreCase = true) == true -> {
                    "${bossPrefix}I'm not comfortable letting that continue."
                }
                Random.nextBoolean() -> {
                    "Hmm... that didn't work. Want me to try again?"
                }
                else -> {
                    "Sorry${bossSuffix}, I couldn't complete that."
                }
            }
            return FormattedSpeech(text, mood)
        }

        val resultingMood: FridayMood
        val text: String

        when (actionType.uppercase()) {
            "TOGGLE_FLASHLIGHT" -> {
                val isOn = parameters["state"] == "true"
                if (isOn) {
                    resultingMood = explicitMood ?: FridayMood.HAPPY
                    text = if (isFollowUp) {
                        listOf(
                            "Back on.",
                            "Flashlight's on.",
                            "Turned it on${bossSuffix}.",
                            "On."
                        ).random()
                    } else {
                        listOf(
                            "Done. Flashlight's on${bossSuffix}.",
                            "Flashlight is on.",
                            "Got it${bossSuffix}, light's on.",
                            "Nice. That's done."
                        ).random()
                    }
                } else {
                    resultingMood = explicitMood ?: FridayMood.CALM
                    text = if (isFollowUp) {
                        listOf(
                            "Yep, turning it off.",
                            "Off.",
                            "Turning it off.",
                            "Yep."
                        ).random()
                    } else {
                        listOf(
                            "Flashlight's off.",
                            "Yep, turning it off${bossSuffix}.",
                            "Done. Light is off.",
                            "Got it."
                        ).random()
                    }
                }
            }

            "ADJUST_VOLUME" -> {
                val direction = parameters["direction"]?.lowercase() ?: "up"
                resultingMood = explicitMood ?: FridayMood.CALM
                text = when (direction) {
                    "up" -> listOf(
                        "Got it.",
                        "Volume's up${bossSuffix}.",
                        "Turning it up.",
                        "Louder."
                    ).random()
                    "down" -> listOf(
                        "Got it.",
                        "Volume lowered${bossSuffix}.",
                        "Turning it down.",
                        "Quieter."
                    ).random()
                    "mute" -> listOf(
                        "Muted.",
                        "Silenced${bossSuffix}.",
                        "Done, sound is off."
                    ).random()
                    else -> "Got it${bossSuffix}."
                }
            }

            "OPEN_APP", "LAUNCH_APP" -> {
                val app = parameters["appName"] ?: targetApp ?: "it"
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (isFollowUp || app.equals("it", ignoreCase = true)) {
                    listOf(
                        "Opening it.",
                        "Sure${bossSuffix}.",
                        "Right away.",
                        "Opening it now."
                    ).random()
                } else {
                    listOf(
                        "Opening $app.",
                        "Launching $app${bossSuffix}.",
                        "Sure, opening $app.",
                        "Opening $app now."
                    ).random()
                }
            }

            "SEARCH_WEB" -> {
                val query = parameters["query"] ?: "that"
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = listOf(
                    "Sure${bossSuffix}.",
                    "Searching for $query.",
                    "Looking that up for you.",
                    "On it."
                ).random()
            }

            "CLOSE_APP" -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                text = listOf("Done.", "Closing it.", "Done${bossSuffix}.").random()
            }

            "GET_BATTERY_INFO" -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                val rawInfo = parameters["info"]
                text = if (rawInfo != null) {
                    rawInfo
                } else {
                    listOf(
                        "Checking battery status${bossSuffix}.",
                        "Battery levels coming right up.",
                        "Checking your battery."
                    ).random()
                }
            }

            "GET_DATE_TIME" -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                val rawTime = parameters["time"]
                text = rawTime ?: "Checking the time."
            }

            "SET_TIMER" -> {
                val seconds = parameters["seconds"]?.toIntOrNull() ?: 60
                val minutes = seconds / 60
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (minutes > 0) {
                    "Setting a timer for $minutes minute${if (minutes > 1) "s" else ""}${bossSuffix}."
                } else {
                    "Timer set for $seconds seconds${bossSuffix}."
                }
            }

            "SPEAK_RESPONSE", "GENERAL_QUERY" -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                text = parameters["message"] ?: parameters["response"] ?: "Online and ready${bossSuffix}."
            }

            "CANCEL_CURRENT_TOPIC" -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                text = "No problem."
            }

            "SECURITY_WARNING" -> {
                resultingMood = FridayMood.CONCERNED
                text = "${bossPrefix}something doesn't look right. Want me to secure the phone?"
            }

            "SECURITY_CONCERNED" -> {
                resultingMood = FridayMood.CONCERNED
                text = "${bossPrefix}something seems unusual."
            }

            "BLOCKED_ACTION" -> {
                resultingMood = FridayMood.SERIOUS
                text = "${bossPrefix}I'm not comfortable letting that continue."
            }

            "UNCLEAR_COMMAND" -> {
                resultingMood = FridayMood.APOLOGETIC
                text = listOf(
                    "Sorry${bossSuffix}, I didn't catch that.",
                    "I didn't quite hear you. Say again?",
                    "Say that once more${bossSuffix}?"
                ).random()
            }

            "CONFIRM_REQUEST" -> {
                resultingMood = FridayMood.FOCUSED
                val actionName = parameters["actionName"] ?: "this action"
                text = "${bossPrefix}that's a sensitive operation. Are you sure you want to proceed with $actionName?"
            }

            "WHATSAPP_CALL" -> {
                val contact = parameters["contact"] ?: ""
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (contact.isNotBlank()) {
                    "Calling $contact on WhatsApp."
                } else {
                    "Who should I call on WhatsApp?"
                }
            }

            "WHATSAPP_CHAT" -> {
                val contact = parameters["contact"] ?: ""
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (contact.isNotBlank()) {
                    "Opening chat with $contact on WhatsApp."
                } else {
                    "Who should I chat with on WhatsApp?"
                }
            }

            "WHATSAPP_MESSAGE" -> {
                val contact = parameters["contact"] ?: ""
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (contact.isNotBlank()) {
                    "Opening WhatsApp to message $contact."
                } else {
                    "Who should I message on WhatsApp?"
                }
            }

            "CALL_CONTACT" -> {
                val contact = parameters["contact"] ?: ""
                resultingMood = explicitMood ?: FridayMood.FOCUSED
                text = if (contact.isNotBlank()) {
                    listOf(
                        "Calling $contact now.",
                        "Placing a call to $contact.",
                        "Dialing $contact."
                    ).random()
                } else {
                    "Who should I call?"
                }
            }

            "PLAY_MUSIC" -> {
                val song = parameters["query"] ?: parameters["song"] ?: "music"
                resultingMood = explicitMood ?: FridayMood.HAPPY
                text = listOf(
                    "Playing $song${bossSuffix}.",
                    "Sure, playing $song.",
                    "On it.",
                    "Playing $song."
                ).random()
            }

            "UNKNOWN_INTENT" -> {
                resultingMood = explicitMood ?: FridayMood.APOLOGETIC
                text = listOf(
                    "Sorry, what did you want me to do?",
                    "I didn't quite catch that.",
                    "Sorry${bossSuffix}, what would you like me to do?"
                ).random()
            }

            else -> {
                resultingMood = explicitMood ?: FridayMood.CALM
                text = listOf(
                    "Done${bossSuffix}.",
                    "Done.",
                    "Got it${bossSuffix}.",
                    "Sure."
                ).random()
            }
        }

        return FormattedSpeech(text, resultingMood)
    }

    /**
     * Formats failure or specific error conditions with accurate, human explanations.
     * Never claims "Done" when an action failed.
     */
    fun formatError(errorType: String, detail: String? = null): FormattedSpeech {
        val useBoss = shouldIncludeBoss()
        val bossSuffix = if (useBoss) ", Boss" else ""

        val text = when (errorType.uppercase()) {
            "CONTACT_NOT_FOUND", "NOT_FOUND_CONTACT" -> "I couldn't find that contact."
            "APP_NOT_FOUND", "NOT_FOUND_APP" -> "I can't find that app."
            "DISAMBIGUATION_REQUIRED", "MULTIPLE_CONTACTS" -> detail ?: "I found multiple contacts with that name. Which one do you mean?"
            "SPEECH_RECOGNITION_FAILED", "NO_SPEECH" -> "I didn't catch that."
            "UNCLEAR", "UNKNOWN_INTENT" -> "Sorry, what did you want me to do?"
            "PERMISSION_REQUIRED" -> detail ?: "I need permission for that${bossSuffix}."
            "BLOCKED_SECURITY" -> "I'm not comfortable letting that continue."
            else -> detail ?: "That didn't work."
        }

        return FormattedSpeech(text, FridayMood.APOLOGETIC)
    }
}
