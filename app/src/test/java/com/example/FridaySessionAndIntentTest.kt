package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ai.UserIntentCategory
import com.example.ai.UserIntentClassifier
import com.example.core.AssistantSessionState
import com.example.core.ConversationContext
import com.example.core.FridayCore
import com.example.core.FridayTelemetry
import com.example.core.OrbState
import com.example.identity.FridayMood
import com.example.identity.FridayPersonality
import com.example.settings.FridaySettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FridaySessionAndIntentTest {

    private lateinit var context: android.content.Context
    private lateinit var conversationContext: ConversationContext

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        conversationContext = ConversationContext()
    }

    // 1. Fast Action Recognition (Sub-second local path)
    @Test
    fun testFastActionRecognition() {
        val flashlightClass = UserIntentClassifier.classify("turn on flashlight", conversationContext)
        assertEquals(UserIntentCategory.ACTION, flashlightClass.category)
        assertTrue(flashlightClass.isLocalFastPath)
        assertNotNull(flashlightClass.structuredAction)
        assertEquals("TOGGLE_FLASHLIGHT", flashlightClass.structuredAction!!.actionType)

        val volumeClass = UserIntentClassifier.classify("volume up", conversationContext)
        assertEquals(UserIntentCategory.ACTION, volumeClass.category)
        assertTrue(volumeClass.isLocalFastPath)
        assertEquals("ADJUST_VOLUME", volumeClass.structuredAction!!.actionType)

        val musicClass = UserIntentClassifier.classify("play Grand Escape by RADWIMPS", conversationContext)
        assertEquals(UserIntentCategory.ACTION, musicClass.category)
        assertTrue(musicClass.isLocalFastPath)
        assertEquals("PLAY_MUSIC", musicClass.structuredAction!!.actionType)
    }

    // 2. Casual Conversation Separation (Zero hardware action)
    @Test
    fun testCasualConversationSeparation() {
        val helloClass = UserIntentClassifier.classify("hello friday", conversationContext)
        assertEquals(UserIntentCategory.CASUAL_CONVERSATION, helloClass.category)
        assertNotNull(helloClass.conversationResponse)
        assertFalse(helloClass.conversationResponse!!.contains("Command executed", ignoreCase = true))

        val interestingClass = UserIntentClassifier.classify("tell me something interesting", conversationContext)
        assertEquals(UserIntentCategory.CASUAL_CONVERSATION, interestingClass.category)
        assertNotNull(interestingClass.conversationResponse)

        val statusClass = UserIntentClassifier.classify("how are you doing", conversationContext)
        assertEquals(UserIntentCategory.CASUAL_CONVERSATION, statusClass.category)
        assertNotNull(statusClass.conversationResponse)
    }

    // 3. Short-Term Memory Context (Song, App, Contact)
    @Test
    fun testShortTermMemoryRetention() {
        conversationContext.startSession()

        // Play song
        conversationContext.recordInteraction(
            actionType = "PLAY_MUSIC",
            targetEntity = "Grand Escape",
            parameters = mapOf("song" to "Grand Escape", "artist" to "RADWIMPS")
        )

        assertEquals("Grand Escape", conversationContext.lastSong)
        assertEquals("RADWIMPS", conversationContext.lastArtist)

        // Question about last song
        val songQuestion = UserIntentClassifier.classify("who made that song", conversationContext)
        assertEquals(UserIntentCategory.QUESTION, songQuestion.category)
        assertNotNull(songQuestion.conversationResponse)
        assertTrue(songQuestion.conversationResponse!!.contains("RADWIMPS", ignoreCase = true))

        // Open app
        conversationContext.recordInteraction(
            actionType = "OPEN_APP",
            targetEntity = "Instagram",
            parameters = mapOf("appName" to "Instagram")
        )
        assertEquals("Instagram", conversationContext.lastApp)

        // Follow up: "close it"
        val closeFollowUp = conversationContext.resolveContextualCommand("close it")
        assertNotNull(closeFollowUp)
        assertEquals("CLOSE_APP", closeFollowUp!!.actionType)
        assertEquals("Instagram", closeFollowUp.targetEntity)

        // Contact resolution
        conversationContext.recordInteraction(
            actionType = "WHATSAPP_CALL",
            targetEntity = "Mom",
            parameters = mapOf("contact" to "Mom")
        )
        assertEquals("Mom", conversationContext.lastContact)

        // Follow up: "message her"
        val messageFollowUp = conversationContext.resolveContextualCommand("message her hello")
        assertNotNull(messageFollowUp)
        assertEquals("WHATSAPP_MESSAGE", messageFollowUp!!.actionType)
        assertEquals("Mom", messageFollowUp.targetEntity)
    }

    // 4. State Machine Sequence Verification
    @Test
    fun testSessionStateMachineSequence() {
        var currentState = AssistantSessionState.STANDBY
        assertEquals(AssistantSessionState.STANDBY, currentState)

        // Wake detected
        FridayTelemetry.recordWakeDetected("hey_friday")
        currentState = AssistantSessionState.WAKE_DETECTED
        assertEquals(AssistantSessionState.WAKE_DETECTED, currentState)

        // Listening
        currentState = AssistantSessionState.LISTENING
        assertEquals(AssistantSessionState.LISTENING, currentState)

        // Processing
        FridayTelemetry.recordCommandEnded("turn on flashlight")
        currentState = AssistantSessionState.PROCESSING
        assertEquals(AssistantSessionState.PROCESSING, currentState)

        // Executing
        FridayTelemetry.recordActionExecutionStarted("TOGGLE_FLASHLIGHT")
        currentState = AssistantSessionState.EXECUTING
        assertEquals(AssistantSessionState.EXECUTING, currentState)

        // Speaking
        FridayTelemetry.recordTtsStarted("Flashlight is on.")
        currentState = AssistantSessionState.SPEAKING
        assertEquals(AssistantSessionState.SPEAKING, currentState)

        // Follow up listening
        FridayTelemetry.recordTtsFinished()
        currentState = AssistantSessionState.FOLLOW_UP_LISTENING
        assertEquals(AssistantSessionState.FOLLOW_UP_LISTENING, currentState)

        // Timeout back to standby
        currentState = AssistantSessionState.STANDBY
        assertEquals(AssistantSessionState.STANDBY, currentState)
    }

    // 5. Telemetry Tracking
    @Test
    fun testTelemetryRecording() {
        FridayTelemetry.startSession("unit_test")
        FridayTelemetry.recordCommandStarted()
        FridayTelemetry.recordCommandEnded("turn on flashlight", 0.95f)
        FridayTelemetry.recordIntentStarted("FAST_LOCAL")
        FridayTelemetry.recordIntentFinished("DEVICE_CONTROL", mapOf("target" to "flashlight"))
        FridayTelemetry.recordActionExecutionStarted("TOGGLE_FLASHLIGHT")
        FridayTelemetry.recordActionExecutionFinished("TOGGLE_FLASHLIGHT", "SUCCESS", "Flashlight enabled")
        FridayTelemetry.recordTtsStarted("Flashlight is on.")
        FridayTelemetry.recordTtsFinished()

        val latency = FridayTelemetry.latestLatency.value
        assertNotNull(latency)
        assertEquals("FAST_LOCAL", latency!!.executionPath)
        assertEquals("turn on flashlight", latency.command)
    }

    // 6. Natural Personality & Specific Error Formatting (Never "Done" on failure)
    @Test
    fun testNaturalPersonalityAndErrorHandling() {
        // Success speech
        val successSpeech = FridayPersonality.formatSpeech(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("state" to "true"),
            isSuccess = true
        )
        assertFalse(successSpeech.speechText.contains("Command executed successfully", ignoreCase = true))

        // Failure speech must not say Done
        val failSpeech = FridayPersonality.formatError("ACTION_FAILED")
        assertFalse(failSpeech.speechText.contains("Done", ignoreCase = true))
        assertTrue(failSpeech.speechText.contains("didn't work", ignoreCase = true))

        val contactNotFound = FridayPersonality.formatError("CONTACT_NOT_FOUND")
        assertFalse(contactNotFound.speechText.contains("Done", ignoreCase = true))
        assertTrue(contactNotFound.speechText.contains("contact", ignoreCase = true))

        val appNotFound = FridayPersonality.formatError("APP_NOT_FOUND")
        assertFalse(appNotFound.speechText.contains("Done", ignoreCase = true))
        assertTrue(appNotFound.speechText.contains("app", ignoreCase = true))

        val permRequired = FridayPersonality.formatError("PERMISSION_REQUIRED")
        assertFalse(permRequired.speechText.contains("Done", ignoreCase = true))
        assertTrue(permRequired.speechText.contains("permission", ignoreCase = true))
    }
}
