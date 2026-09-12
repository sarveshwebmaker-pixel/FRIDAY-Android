package com.example.ai

import com.example.core.ConversationContext
import com.example.identity.FridayMood
import com.example.security.ActionRiskLevel

enum class UserIntentCategory {
    ACTION,
    QUESTION,
    CASUAL_CONVERSATION,
    FOLLOW_UP,
    UNKNOWN
}

data class ClassifiedIntent(
    val category: UserIntentCategory,
    val structuredAction: StructuredAction? = null,
    val conversationResponse: String? = null,
    val isLocalFastPath: Boolean = false,
    val contextEntity: String? = null,
    val queryText: String = "",
    val mood: FridayMood = FridayMood.CALM
)

/**
 * High-accuracy intent classification engine.
 *
 * Distinguishes between:
 * 1. ACTION: Direct Android phone/hardware/app execution (fast path).
 * 2. QUESTION: General knowledge, factual inquiries, or questions about recent context.
 * 3. CASUAL_CONVERSATION: Social interaction, capability questions, greetings, jokes, facts.
 * 4. FOLLOW_UP: Context-dependent short commands ("turn it off", "louder", "who made that song").
 * 5. UNKNOWN: Unintelligible or unsupported speech.
 */
object UserIntentClassifier {

    private val interestingFacts = listOf(
        "Honey never spoils. Archaeologists have found 3,000-year-old honey in ancient Egyptian tombs that is still perfectly edible.",
        "A day on Venus is longer than a full year on Venus.",
        "Octopuses have three hearts, and their blood is copper-based and blue.",
        "Bananas share approximately 50 percent of their DNA with humans.",
        "Water can boil and freeze at the exact same time under special conditions, known as the triple point."
    )

    private val assistantJokes = listOf(
        "Why don't scientists trust atoms? Because they make up everything.",
        "I asked my phone if it loves me. It said: 'I'm connected to everything, but you're my favorite hotspot.'",
        "There are 10 types of people in the world: those who understand binary, and those who don't."
    )

    fun classify(rawText: String, context: ConversationContext): ClassifiedIntent {
        val trimmed = rawText.trim()
        val lower = trimmed.lowercase()
            .replace(Regex("[?,.!]"), "")
            .replace(Regex("\\s+"), " ")

        if (lower.isBlank()) {
            return ClassifiedIntent(
                category = UserIntentCategory.UNKNOWN,
                conversationResponse = "I didn't catch that.",
                mood = FridayMood.APOLOGETIC
            )
        }

        // 1. FOLLOW-UP CHECK (Context-dependent)
        if (context.isSessionActive) {
            // Check contextual song questions: "Who made that song?", "Who sings that song?", "Who wrote that?"
            val isSongQuestion = lower.contains("that song") || lower.contains("this song") ||
                    ((lower.startsWith("who made") || lower.startsWith("who wrote") || lower.startsWith("who sings") || lower.startsWith("who sang")) && context.lastSong != null)

            if (isSongQuestion && context.lastSong != null) {
                val song = context.lastSong ?: ""
                val response = if (song.contains("grand escape", ignoreCase = true)) {
                    "\"Grand Escape\" was composed by RADWIMPS featuring Toko Miura for the film Weathering with You."
                } else {
                    "That song is \"$song\"."
                }
                return ClassifiedIntent(
                    category = UserIntentCategory.QUESTION,
                    conversationResponse = response,
                    isLocalFastPath = true,
                    contextEntity = song,
                    queryText = rawText,
                    mood = FridayMood.HAPPY
                )
            }

            // Check standard contextual action follow-ups ("turn it off", "louder", "open it", "search for AI news")
            val resolved = context.resolveContextualCommand(rawText)
            if (resolved != null) {
                val action = StructuredAction(
                    intent = resolved.actionType,
                    actionType = resolved.actionType,
                    parameters = resolved.parameters,
                    speechResponse = "Done.",
                    riskLevel = ActionRiskLevel.SAFE
                )
                return ClassifiedIntent(
                    category = UserIntentCategory.FOLLOW_UP,
                    structuredAction = action,
                    isLocalFastPath = true,
                    contextEntity = resolved.targetEntity,
                    queryText = resolved.resolvedText,
                    mood = FridayMood.CALM
                )
            }
        }

        // 2. CASUAL CONVERSATION (Social, check-ins, capabilities, facts, humor)
        if (lower == "how are you" || lower == "how are you doing" || lower == "how's it going" || lower == "how are you today" ||
            lower == "hey friday how are you" || lower == "friday how are you") {
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = "I'm doing great. Ready whenever you are.",
                isLocalFastPath = true,
                mood = FridayMood.HAPPY
            )
        }

        if (lower == "what can you do" || lower == "what are you capable of" || lower == "what are your skills" ||
            lower == "what can i say" || lower == "help" || lower == "help me" || lower == "what do you do") {
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = "I can control device hardware like flashlight and volume, launch and close apps, make phone and WhatsApp calls, send messages, play music, search the web, set timers, and answer your questions.",
                isLocalFastPath = true,
                mood = FridayMood.CALM
            )
        }

        if (lower.contains("tell me something interesting") || lower == "tell me an interesting fact" ||
            lower == "interesting fact" || lower == "fun fact" || lower == "tell me a fact" || lower.contains("something interesting")) {
            val fact = interestingFacts.random()
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = fact,
                isLocalFastPath = true,
                mood = FridayMood.HAPPY
            )
        }

        if (lower == "tell me a joke" || lower == "make me laugh" || lower == "say a joke") {
            val joke = assistantJokes.random()
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = joke,
                isLocalFastPath = true,
                mood = FridayMood.HAPPY
            )
        }

        if (lower == "who are you" || lower == "what is your name" || lower == "what's your name" || lower == "who made you") {
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = "I'm FRIDAY, your personal AI assistant.",
                isLocalFastPath = true,
                mood = FridayMood.CALM
            )
        }

        if (lower == "hello" || lower == "hi" || lower == "hey" || lower == "hey friday" || lower == "hello friday" || lower == "hi friday" ||
            lower.startsWith("hello") || lower.startsWith("hi ") || lower.startsWith("hey ") ||
            lower == "good morning" || lower == "good evening" || lower == "good afternoon") {
            val greeting = when {
                lower.contains("morning") -> "Good morning. How can I help you today?"
                lower.contains("evening") -> "Good evening. What can I do for you?"
                lower.contains("afternoon") -> "Good afternoon. Ready when you are."
                else -> "Hello. Ready whenever you are."
            }
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = greeting,
                isLocalFastPath = true,
                mood = FridayMood.HAPPY
            )
        }

        if (lower == "thank you" || lower == "thanks" || lower == "thanks friday" || lower == "good job" || lower == "great job") {
            return ClassifiedIntent(
                category = UserIntentCategory.CASUAL_CONVERSATION,
                conversationResponse = "You're welcome.",
                isLocalFastPath = true,
                mood = FridayMood.HAPPY
            )
        }

        // 3. FACTUAL QUESTIONS (Common Knowledge Fast Path)
        if (lower == "whats the capital of japan" || lower == "what is the capital of japan" || lower == "capital of japan") {
            return ClassifiedIntent(
                category = UserIntentCategory.QUESTION,
                conversationResponse = "The capital of Japan is Tokyo.",
                isLocalFastPath = true,
                queryText = rawText,
                mood = FridayMood.CALM
            )
        }

        if (lower == "whats the capital of france" || lower == "what is the capital of france" || lower == "capital of france") {
            return ClassifiedIntent(
                category = UserIntentCategory.QUESTION,
                conversationResponse = "The capital of France is Paris.",
                isLocalFastPath = true,
                queryText = rawText,
                mood = FridayMood.CALM
            )
        }

        // Math calculation queries: e.g. "what is 25 times 4", "what is 10 plus 5"
        val mathMatch = Regex("what(?:'s| is)\\s+(\\d+)\\s*(times|\\*|plus|\\+|minus|-|divided by|/)\\s*(\\d+)").find(lower)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toLongOrNull() ?: 0L
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toLongOrNull() ?: 0L
            val res = when (op) {
                "times", "*" -> a * b
                "plus", "+" -> a + b
                "minus", "-" -> a - b
                "divided by", "/" -> if (b != 0L) a / b else null
                else -> null
            }
            if (res != null) {
                return ClassifiedIntent(
                    category = UserIntentCategory.QUESTION,
                    conversationResponse = "$a $op $b is $res.",
                    isLocalFastPath = true,
                    queryText = rawText,
                    mood = FridayMood.CALM
                )
            }
        }

        // 4. ACTION CHECK (Hardware, Apps, Device, Media, Tools)
        val parsedAction = IntentUnderstandingEngine.parseCommand(rawText)
        if (parsedAction != null) {
            return ClassifiedIntent(
                category = UserIntentCategory.ACTION,
                structuredAction = parsedAction,
                isLocalFastPath = true,
                contextEntity = parsedAction.parameters["target"] ?: parsedAction.parameters["appName"] ?: parsedAction.parameters["contact"],
                queryText = rawText,
                mood = FridayMood.CALM
            )
        }

        // 5. GENERAL QUESTION (Pass to Gemini AI with short context)
        val isQuestionPattern = lower.startsWith("what") || lower.startsWith("who") || lower.startsWith("where") ||
                lower.startsWith("when") || lower.startsWith("why") || lower.startsWith("how") || lower.startsWith("is ") ||
                lower.startsWith("can you explain") || lower.startsWith("tell me about")

        if (isQuestionPattern) {
            return ClassifiedIntent(
                category = UserIntentCategory.QUESTION,
                isLocalFastPath = false,
                queryText = rawText,
                mood = FridayMood.CALM
            )
        }

        // 6. UNKNOWN
        return ClassifiedIntent(
            category = UserIntentCategory.UNKNOWN,
            conversationResponse = "Sorry, what did you want me to do?",
            isLocalFastPath = true,
            queryText = rawText,
            mood = FridayMood.APOLOGETIC
        )
    }
}
