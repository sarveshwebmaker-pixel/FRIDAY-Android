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
}
