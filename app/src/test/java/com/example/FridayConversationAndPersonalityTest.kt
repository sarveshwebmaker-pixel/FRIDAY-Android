package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.core.ConversationContext
import com.example.identity.FridayMood
import com.example.identity.FridayPersonality
import com.example.security.FridaySecurityWatcher
import com.example.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FridayConversationAndPersonalityTest {

    @Test
    fun testConversationSessionLifecycle() {
        val context = ConversationContext()
        assertFalse(context.isSessionActive)

        context.startSession()
        assertTrue(context.isSessionActive)
        assertFalse(context.isSessionExpired(timeoutSeconds = 8))

        context.endSession()
        assertFalse(context.isSessionActive)
        assertTrue(context.isSessionExpired(timeoutSeconds = 8))
    }

    @Test
    fun testContextualFlashlightFollowUps() {
        val context = ConversationContext()
        context.startSession()

        // 1. Initial turn: Turn on flashlight
        context.recordInteraction(
            actionType = "TOGGLE_FLASHLIGHT",
            targetEntity = "flashlight",
            parameters = mapOf("target" to "flashlight", "state" to "true"),
            mood = FridayMood.HAPPY
        )

        // 2. Follow-up: "turn it off"
        val offResolved = context.resolveContextualCommand("turn it off")
        assertNotNull(offResolved)
        assertEquals("TOGGLE_FLASHLIGHT", offResolved!!.actionType)
        assertEquals("false", offResolved.parameters["state"])
        assertEquals("flashlight", offResolved.targetEntity)

        // 3. Short follow-up: "off"
        val shortOff = context.resolveContextualCommand("off")
        assertNotNull(shortOff)
        assertEquals("false", shortOff!!.parameters["state"])

        // 4. Follow-up: "again" or "on"
        val onResolved = context.resolveContextualCommand("turn it on again")
        assertNotNull(onResolved)
        assertEquals("true", onResolved!!.parameters["state"])

        val shortAgain = context.resolveContextualCommand("again")
        assertNotNull(shortAgain)
        assertEquals("true", shortAgain!!.parameters["state"])
    }

    @Test
    fun testContextualAppAndSearchFollowUps() {
        val context = ConversationContext()
        context.startSession()

        // 1. Initial turn: Open YouTube
        context.recordInteraction(
            actionType = "OPEN_APP",
            targetEntity = "YouTube",
            parameters = mapOf("appName" to "YouTube"),
            mood = FridayMood.FOCUSED
        )

        // 2. Follow-up: "Search for AI news"
        val searchResolved = context.resolveContextualCommand("Search for AI news")
        assertNotNull(searchResolved)
        assertEquals("SEARCH_WEB", searchResolved!!.actionType)
        assertEquals("AI news", searchResolved.parameters["query"])
        assertEquals("YouTube", searchResolved.parameters["targetApp"])

        // 3. Follow-up: "close it"
        val closeResolved = context.resolveContextualCommand("close it")
        assertNotNull(closeResolved)
        assertEquals("CLOSE_APP", closeResolved!!.actionType)
        assertEquals("YouTube", closeResolved.targetEntity)
    }

    @Test
    fun testContextualVolumeFollowUps() {
        val context = ConversationContext()
        context.startSession()

        context.recordInteraction(
            actionType = "ADJUST_VOLUME",
            targetEntity = "volume",
            parameters = mapOf("target" to "volume", "direction" to "up")
        )

        val quieter = context.resolveContextualCommand("quieter")
        assertNotNull(quieter)
        assertEquals("ADJUST_VOLUME", quieter!!.actionType)
        assertEquals("down", quieter.parameters["direction"])

        val louder = context.resolveContextualCommand("louder")
        assertNotNull(louder)
        assertEquals("up", louder!!.parameters["direction"])
    }

    @Test
    fun testAmbiguousCommandReturnsNullForClarification() {
        val context = ConversationContext()
        context.startSession()

        context.recordInteraction(
            actionType = "GET_BATTERY_INFO",
            targetEntity = "battery",
            parameters = emptyMap()
        )

        // "off" is meaningless for battery info context -> should return null
        val unresolved = context.resolveContextualCommand("off")
        assertNull(unresolved)
    }

    @Test
    fun testPersonalityFormattingAvoidsRoboticLogs() {
        val flashlightSpeech = FridayPersonality.formatSpeech(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("state" to "true"),
            isSuccess = true
        )
        assertFalse(flashlightSpeech.speechText.contains("Command executed successfully", ignoreCase = true))
        assertFalse(flashlightSpeech.speechText.contains("Action classifier", ignoreCase = true))
        assertTrue(flashlightSpeech.speechText.isNotBlank())

        val offSpeech = FridayPersonality.formatSpeech(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("state" to "false"),
            isSuccess = true,
            isFollowUp = true
        )
        assertTrue(offSpeech.speechText.contains("off", ignoreCase = true) || offSpeech.speechText.contains("yep", ignoreCase = true))
    }

    @Test
    fun testSecurityWatcherAnomalyReporting() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val watcher = FridaySecurityWatcher(appContext)

        // Simulate security policy failure threshold
        watcher.recordSecurityFailure()
        watcher.recordSecurityFailure()
        watcher.recordSecurityFailure()

        val report = watcher.inspectCurrentState()
        assertTrue(report.hasSuspiciousAnomaly)
        assertEquals(FridayMood.SERIOUS, report.mood)
        assertNotNull(report.suggestedPrompt)
        assertTrue(report.suggestedPrompt!!.contains("something doesn't look right", ignoreCase = true))

        watcher.resetSecurityFailures()
        val resetReport = watcher.inspectCurrentState()
        assertFalse(resetReport.hasSuspiciousAnomaly)
    }

    @Test
    fun testOfflineAiProviderCallCommandMatching() = kotlinx.coroutines.runBlocking {
        val provider = com.example.ai.OfflineAiProvider()
        val mode = com.example.core.BatteryMode.NORMAL

        // WhatsApp call matching
        val waResult = provider.processCommand("Call John on WhatsApp", mode)
        assertTrue(waResult is com.example.ai.FridayAiResult.Success)
        val waAction = (waResult as com.example.ai.FridayAiResult.Success).action
        assertEquals("WHATSAPP_CALL", waAction.intent)
        assertEquals("John", waAction.parameters["contact"])

        // Phone call matching
        val phoneResult = provider.processCommand("Call Mom", mode)
        assertTrue(phoneResult is com.example.ai.FridayAiResult.Success)
        val phoneAction = (phoneResult as com.example.ai.FridayAiResult.Success).action
        assertEquals("CALL_CONTACT", phoneAction.intent)
        assertEquals("Mom", phoneAction.parameters["contact"])
    }

    @Test
    fun testCentralAudioControllerLifecycle() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = com.example.voice.CentralAudioController(appContext)

        assertEquals(com.example.voice.MicrophoneState.MIC_IDLE, controller.micState.value)

        // Acquire for command listening
        var wakeWordStopped = false
        var voiceStartedWithSession: String? = null

        val sessionId = controller.acquireForCommandListening(
            reason = "Test session",
            stopWakeWordAction = { wakeWordStopped = true },
            startVoiceAction = { sid -> voiceStartedWithSession = sid }
        )

        assertTrue(wakeWordStopped)
        assertNotNull(sessionId)
    }

    @Test
    fun testCallPersonalityResponses() {
        val waSpeech = FridayPersonality.formatSpeech(
            actionType = "WHATSAPP_CALL",
            parameters = mapOf("contact" to "Sarah"),
            isSuccess = true
        )
        assertTrue(waSpeech.speechText.contains("WhatsApp", ignoreCase = true))
        assertTrue(waSpeech.speechText.contains("Sarah", ignoreCase = true))

        val phoneSpeech = FridayPersonality.formatSpeech(
            actionType = "CALL_CONTACT",
            parameters = mapOf("contact" to "Alex"),
            isSuccess = true
        )
        assertTrue(phoneSpeech.speechText.contains("Alex", ignoreCase = true))
    }

    @Test
    fun testWhatsAppChatAndMessageSeparation() = kotlinx.coroutines.runBlocking {
        val provider = com.example.ai.OfflineAiProvider()
        val mode = com.example.core.BatteryMode.NORMAL

        // WhatsApp Chat
        val chatResult = provider.processCommand("Chat with Alice on WhatsApp", mode)
        assertTrue(chatResult is com.example.ai.FridayAiResult.Success)
        val chatAction = (chatResult as com.example.ai.FridayAiResult.Success).action
        assertEquals("WHATSAPP_CHAT", chatAction.intent)
        assertEquals("Alice", chatAction.parameters["contact"])

        // WhatsApp Message
        val msgResult = provider.processCommand("Message Bob on WhatsApp", mode)
        assertTrue(msgResult is com.example.ai.FridayAiResult.Success)
        val msgAction = (msgResult as com.example.ai.FridayAiResult.Success).action
        assertEquals("WHATSAPP_MESSAGE", msgAction.intent)
        assertEquals("Bob", msgAction.parameters["contact"])

        // WhatsApp Call
        val callResult = provider.processCommand("Call Charlie on WhatsApp", mode)
        assertTrue(callResult is com.example.ai.FridayAiResult.Success)
        val callAction = (callResult as com.example.ai.FridayAiResult.Success).action
        assertEquals("WHATSAPP_CALL", callAction.intent)
        assertEquals("Charlie", callAction.parameters["contact"])
    }

    @Test
    fun testFourteenStepRealHumanConversationSequence() {
        val context = ConversationContext()
        context.startSession()

        // Turn 1: "FRIDAY."
        val turn1 = com.example.ai.UserIntentClassifier.classify("FRIDAY.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn1.category)
        assertTrue(turn1.conversationResponse!!.isNotBlank())
        context.recordTurn("FRIDAY.", turn1.conversationResponse!!, "GREETING")

        // Turn 2: "How are you?"
        val turn2 = com.example.ai.UserIntentClassifier.classify("How are you?", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn2.category)
        assertTrue(turn2.conversationResponse!!.contains("great", ignoreCase = true) || turn2.conversationResponse!!.contains("doing", ignoreCase = true))
        context.recordTurn("How are you?", turn2.conversationResponse!!, "CASUAL_CONVERSATION")

        // Turn 3: "Tell me something interesting."
        val turn3 = com.example.ai.UserIntentClassifier.classify("Tell me something interesting.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn3.category)
        assertTrue(turn3.conversationResponse!!.isNotBlank())
        context.recordTurn("Tell me something interesting.", turn3.conversationResponse!!, "CASUAL_CONVERSATION")
        assertNotNull(context.activeTopic)

        // Turn 4: "Tell me more."
        val turn4 = context.resolveContextualCommand("Tell me more.")
        assertNotNull(turn4)
        val turn4Classified = com.example.ai.UserIntentClassifier.classify("Tell me more.", context)
        assertTrue(turn4Classified.conversationResponse!!.isNotBlank())
        context.recordTurn("Tell me more.", turn4Classified.conversationResponse!!, "CASUAL_CONVERSATION")

        // Turn 5: "Why is that?"
        val turn5Classified = com.example.ai.UserIntentClassifier.classify("Why is that?", context)
        assertTrue(turn5Classified.conversationResponse!!.isNotBlank())
        context.recordTurn("Why is that?", turn5Classified.conversationResponse!!, "CASUAL_CONVERSATION")

        // Turn 6: "Okay, forget that."
        val turn6Resolved = context.resolveContextualCommand("Okay, forget that.")
        assertNotNull(turn6Resolved)
        assertEquals("CANCEL_CURRENT_TOPIC", turn6Resolved!!.actionType)

        // Turn 7: "What's the latest AI news?"
        val turn7 = com.example.ai.UserIntentClassifier.classify("What's the latest AI news?", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, turn7.category)
        assertEquals("SEARCH_WEB", turn7.structuredAction!!.actionType)
        context.recordTurn("What's the latest AI news?", turn7.structuredAction!!.speechResponse, "SEARCH_WEB")

        // Turn 8: "Open YouTube."
        val turn8 = com.example.ai.UserIntentClassifier.classify("Open YouTube.", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, turn8.category)
        assertEquals("OPEN_APP", turn8.structuredAction!!.actionType)
        assertTrue(turn8.structuredAction!!.parameters["appName"].equals("youtube", ignoreCase = true))
        context.recordInteraction("OPEN_APP", "YouTube", turn8.structuredAction!!.parameters)
        context.recordTurn("Open YouTube.", "Opening YouTube.", "OPEN_APP")

        // Turn 9: "Actually, go back."
        val turn9Resolved = context.resolveContextualCommand("Actually, go back.")
        assertNotNull(turn9Resolved)
        assertEquals("UI_AUTOMATION", turn9Resolved!!.actionType)
        assertEquals("back", turn9Resolved.parameters["operation"])

        // Turn 10: "Turn on the flashlight."
        val turn10 = com.example.ai.UserIntentClassifier.classify("Turn on the flashlight.", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, turn10.category)
        assertEquals("TOGGLE_FLASHLIGHT", turn10.structuredAction!!.actionType)
        assertEquals("true", turn10.structuredAction!!.parameters["state"])
        context.recordInteraction("TOGGLE_FLASHLIGHT", "flashlight", turn10.structuredAction!!.parameters)
        context.recordTurn("Turn on the flashlight.", "Done. Flashlight is on.", "TOGGLE_FLASHLIGHT")

        // Turn 11: "Turn it off."
        val turn11Resolved = context.resolveContextualCommand("Turn it off.")
        assertNotNull(turn11Resolved)
        assertEquals("TOGGLE_FLASHLIGHT", turn11Resolved!!.actionType)
        assertEquals("false", turn11Resolved.parameters["state"])
        context.recordInteraction("TOGGLE_FLASHLIGHT", "flashlight", turn11Resolved.parameters)
        context.recordTurn("Turn it off.", "Flashlight is off.", "TOGGLE_FLASHLIGHT")

        // Turn 12: "What were we talking about?"
        val turn12 = com.example.ai.UserIntentClassifier.classify("What were we talking about?", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn12.category)
        assertTrue(turn12.conversationResponse!!.contains("talking about", ignoreCase = true))

        // Turn 13: "Thanks."
        val turn13 = com.example.ai.UserIntentClassifier.classify("Thanks.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn13.category)
        assertTrue(turn13.conversationResponse!!.contains("problem", ignoreCase = true) || turn13.conversationResponse!!.contains("welcome", ignoreCase = true))

        // Turn 14: "Bye."
        val turn14 = com.example.ai.UserIntentClassifier.classify("Bye.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn14.category)
        assertTrue(turn14.conversationResponse!!.contains("goodbye", ignoreCase = true))
    }

    @Test
    fun testNaturalMultiTurnSpaceFollowUps() {
        val context = ConversationContext()
        context.startSession()

        // Turn 1: "Tell me about space."
        val turn1 = com.example.ai.UserIntentClassifier.classify("Tell me about space.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, turn1.category)
        assertTrue(turn1.conversationResponse!!.contains("frontier", ignoreCase = true) || turn1.conversationResponse!!.contains("space", ignoreCase = true))
        context.recordTurn("Tell me about space.", turn1.conversationResponse!!, "CASUAL_CONVERSATION")
        assertEquals("space", context.activeTopic)

        // Turn 2: "How far is the Moon?"
        val turn2 = com.example.ai.UserIntentClassifier.classify("How far is the Moon?", context)
        assertEquals(com.example.ai.UserIntentCategory.QUESTION, turn2.category)
        assertTrue(turn2.conversationResponse!!.contains("384,400", ignoreCase = true))
        context.recordTurn("How far is the Moon?", turn2.conversationResponse!!, "QUESTION")

        // Turn 3: "What about Mars?"
        val turn3 = com.example.ai.UserIntentClassifier.classify("What about Mars?", context)
        assertEquals(com.example.ai.UserIntentCategory.QUESTION, turn3.category)
        assertTrue(turn3.conversationResponse!!.contains("Mars", ignoreCase = true))
        assertTrue(turn3.conversationResponse!!.contains("million kilometers", ignoreCase = true))
    }

    @Test
    fun testSessionEntityStorageAndRecall() {
        val context = ConversationContext()
        context.startSession()

        // User shares a fact
        val statement = com.example.ai.UserIntentClassifier.classify("My favorite game is Minecraft.", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, statement.category)
        assertEquals("Minecraft", context.getSessionEntity("game"))

        // User queries the stored fact
        val query = com.example.ai.UserIntentClassifier.classify("What game did I mention?", context)
        assertEquals(com.example.ai.UserIntentCategory.CASUAL_CONVERSATION, query.category)
        assertTrue(query.conversationResponse!!.contains("Minecraft", ignoreCase = true))
    }

    @Test
    fun testNaturalTopicSwitchingAndFollowUps() {
        val context = ConversationContext()
        context.startSession()

        // 1. Tell me about black holes
        val step1 = com.example.ai.UserIntentClassifier.classify("Tell me about black holes.", context)
        assertEquals(com.example.ai.UserIntentCategory.QUESTION, step1.category)
        assertTrue(step1.conversationResponse!!.contains("event horizon", ignoreCase = true))
        context.recordTurn("Tell me about black holes.", step1.conversationResponse!!, "QUESTION")
        assertEquals("black holes", context.activeTopic)

        // 2. What happened in AI today? -> Live search
        val step2 = com.example.ai.UserIntentClassifier.classify("Okay, what happened in AI today?", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, step2.category)
        assertEquals("SEARCH_WEB", step2.structuredAction!!.actionType)
        context.recordInteraction("SEARCH_WEB", "AI news", step2.structuredAction!!.parameters)

        // 3. Open YouTube
        val step3 = com.example.ai.UserIntentClassifier.classify("Open YouTube.", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, step3.category)
        assertEquals("OPEN_APP", step3.structuredAction!!.actionType)
        context.recordInteraction("OPEN_APP", "YouTube", step3.structuredAction!!.parameters)

        // 4. "Wait, no—go back." -> Back navigation
        val step4 = context.resolveContextualCommand("Wait, no—go back.")
        assertNotNull(step4)
        assertEquals("UI_AUTOMATION", step4!!.actionType)
        assertEquals("back", step4.parameters["operation"])

        // 5. Turn on the flashlight
        val step5 = com.example.ai.IntentUnderstandingEngine.parseCommand("Turn on the flashlight.")
        assertNotNull(step5)
        assertEquals("TOGGLE_FLASHLIGHT", step5!!.actionType)
        context.recordInteraction("TOGGLE_FLASHLIGHT", "flashlight", step5.parameters)

        // 6. "Could you turn that off?" -> Follow-up resolved
        val step6 = context.resolveContextualCommand("Could you turn that off?")
        assertNotNull(step6)
        assertEquals("TOGGLE_FLASHLIGHT", step6!!.actionType)
        assertEquals("false", step6.parameters["state"])

        // 7. "Do that again" / "The same thing"
        val step7 = context.resolveContextualCommand("The same thing")
        assertNotNull(step7)
        assertEquals("TOGGLE_FLASHLIGHT", step7!!.actionType)
    }

    @Test
    fun testStableKnowledgeVsLiveInfo() {
        val context = ConversationContext()
        context.startSession()

        // Stable knowledge does not invoke SEARCH_WEB
        val blackHole = com.example.ai.UserIntentClassifier.classify("What is a black hole?", context)
        assertEquals(com.example.ai.UserIntentCategory.QUESTION, blackHole.category)
        assertNull(blackHole.structuredAction)
        assertTrue(blackHole.isLocalFastPath)

        val gravity = com.example.ai.UserIntentClassifier.classify("Explain gravity.", context)
        assertEquals(com.example.ai.UserIntentCategory.QUESTION, gravity.category)
        assertNull(gravity.structuredAction)
        assertTrue(gravity.isLocalFastPath)

        // Live info triggers web search
        val news = com.example.ai.UserIntentClassifier.classify("What's the latest AI news?", context)
        assertEquals(com.example.ai.UserIntentCategory.ACTION, news.category)
        assertEquals("SEARCH_WEB", news.structuredAction!!.actionType)
    }

    @Test
    fun testComprehensiveDeviceControls() {
        // 1. Screenshot
        val screenshot = com.example.ai.IntentUnderstandingEngine.parseCommand("Take a screenshot")
        assertNotNull(screenshot)
        assertEquals("TAKE_SCREENSHOT", screenshot!!.actionType)

        // 2. Lock screen
        val lock = com.example.ai.IntentUnderstandingEngine.parseCommand("Lock phone")
        assertNotNull(lock)
        assertEquals("LOCK_SCREEN", lock!!.actionType)

        // 3. Power menu
        val power = com.example.ai.IntentUnderstandingEngine.parseCommand("Open power menu")
        assertNotNull(power)
        assertEquals("POWER_MENU", power!!.actionType)

        // 4. Split screen
        val split = com.example.ai.IntentUnderstandingEngine.parseCommand("Toggle split screen")
        assertNotNull(split)
        assertEquals("SPLIT_SCREEN", split!!.actionType)

        // 5. Ringer modes & DND
        val silent = com.example.ai.IntentUnderstandingEngine.parseCommand("Put phone on silent")
        assertNotNull(silent)
        assertEquals("RINGER_MODE", silent!!.actionType)
        assertEquals("silent", silent.parameters["mode"])

        val vibrate = com.example.ai.IntentUnderstandingEngine.parseCommand("Set phone to vibrate")
        assertNotNull(vibrate)
        assertEquals("RINGER_MODE", vibrate!!.actionType)
        assertEquals("vibrate", vibrate.parameters["mode"])

        val dnd = com.example.ai.IntentUnderstandingEngine.parseCommand("Turn on do not disturb")
        assertNotNull(dnd)
        assertEquals("RINGER_MODE", dnd!!.actionType)
        assertEquals("dnd", dnd.parameters["mode"])

        // 6. Auto-rotate
        val rotateOn = com.example.ai.IntentUnderstandingEngine.parseCommand("Turn on auto rotate")
        assertNotNull(rotateOn)
        assertEquals("SCREEN_ORIENTATION", rotateOn!!.actionType)
        assertEquals("on", rotateOn.parameters["state"])

        val rotateOff = com.example.ai.IntentUnderstandingEngine.parseCommand("Lock screen rotation")
        assertNotNull(rotateOff)
        assertEquals("SCREEN_ORIENTATION", rotateOff!!.actionType)
        assertEquals("off", rotateOff.parameters["state"])

        // 7. Universal Audio Streams
        val ringVol = com.example.ai.IntentUnderstandingEngine.parseCommand("Set ringtone volume to 80 percent")
        assertNotNull(ringVol)
        assertEquals("ADJUST_VOLUME", ringVol!!.actionType)
        assertEquals("ring", ringVol.parameters["stream"])
        assertEquals("80", ringVol.parameters["level"])

        val alarmVol = com.example.ai.IntentUnderstandingEngine.parseCommand("Turn up alarm volume")
        assertNotNull(alarmVol)
        assertEquals("ADJUST_VOLUME", alarmVol!!.actionType)
        assertEquals("alarm", alarmVol.parameters["stream"])
        assertEquals("up", alarmVol.parameters["direction"])

        // 8. Flashlight strobe
        val strobe = com.example.ai.IntentUnderstandingEngine.parseCommand("Emergency strobe light")
        assertNotNull(strobe)
        assertEquals("FLASHLIGHT_STROBE", strobe!!.actionType)

        // 9. Vibrate device / haptic motor
        val vib = com.example.ai.IntentUnderstandingEngine.parseCommand("Buzz phone")
        assertNotNull(vib)
        assertEquals("VIBRATE_DEVICE", vib!!.actionType)
    }
}
