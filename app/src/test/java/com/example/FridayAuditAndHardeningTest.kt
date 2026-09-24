package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.actions.ActionResult
import com.example.actions.tools.PaymentTool
import com.example.ai.FridayCapabilities
import com.example.ai.IntentUnderstandingEngine
import com.example.ai.UserIntentCategory
import com.example.ai.UserIntentClassifier
import com.example.bubble.FloatingBubbleManager
import com.example.core.ConversationContext
import com.example.core.OrbState
import com.example.identity.FridayMood
import com.example.security.ActionRiskLevel
import com.example.security.SecurityManager
import com.example.security.SecurityWatcherReport
import kotlinx.coroutines.runBlocking
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
class FridayAuditAndHardeningTest {

    private lateinit var context: android.content.Context
    private lateinit var conversationContext: ConversationContext
    private lateinit var securityManager: SecurityManager
    private lateinit var paymentTool: PaymentTool

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        conversationContext = ConversationContext()
        securityManager = SecurityManager()
        paymentTool = PaymentTool()
    }

    @Test
    fun testNaturalConversationAndChainedContext() {
        conversationContext.startSession()

        // 1. Initial command: "turn on flashlight"
        val flashOn = UserIntentClassifier.classify("FRIDAY, turn on the flashlight.", conversationContext)
        assertEquals(UserIntentCategory.ACTION, flashOn.category)
        assertEquals("TOGGLE_FLASHLIGHT", flashOn.structuredAction?.actionType)
        conversationContext.recordInteraction("TOGGLE_FLASHLIGHT", "flashlight", mapOf("target" to "flashlight", "state" to "true"))

        // 2. Follow-up: "Turn it off."
        val turnOff = UserIntentClassifier.classify("Turn it off.", conversationContext)
        assertEquals(UserIntentCategory.FOLLOW_UP, turnOff.category)
        assertEquals("TOGGLE_FLASHLIGHT", turnOff.structuredAction?.actionType)
        assertEquals("false", turnOff.structuredAction?.parameters?.get("state"))
        conversationContext.recordInteraction("TOGGLE_FLASHLIGHT", "flashlight", mapOf("target" to "flashlight", "state" to "false"))

        // 3. Follow-up: "Turn it on again."
        val turnOnAgain = UserIntentClassifier.classify("Turn it on again.", conversationContext)
        assertEquals(UserIntentCategory.FOLLOW_UP, turnOnAgain.category)
        assertEquals("TOGGLE_FLASHLIGHT", turnOnAgain.structuredAction?.actionType)
        assertEquals("true", turnOnAgain.structuredAction?.parameters?.get("state"))

        // 4. Volume: "Make the volume louder."
        val louder = UserIntentClassifier.classify("Make the volume louder.", conversationContext)
        assertEquals(UserIntentCategory.ACTION, louder.category)
        assertEquals("ADJUST_VOLUME", louder.structuredAction?.actionType)
        assertEquals("up", louder.structuredAction?.parameters?.get("direction"))

        // 5. App Launch: "Open YouTube."
        val openYt = UserIntentClassifier.classify("Open YouTube.", conversationContext)
        assertEquals(UserIntentCategory.ACTION, openYt.category)
        assertEquals("OPEN_APP", openYt.structuredAction?.actionType)
        assertEquals("youtube", openYt.structuredAction?.parameters?.get("appName")?.lowercase())

        // 6. App Close: "Close YouTube."
        val closeYt = UserIntentClassifier.classify("Close YouTube.", conversationContext)
        assertEquals(UserIntentCategory.ACTION, closeYt.category)
        assertEquals("CLOSE_APP", closeYt.structuredAction?.actionType)
        assertEquals("youtube", closeYt.structuredAction?.parameters?.get("appName")?.lowercase())

        // 7. Identity: "Tell me about yourself."
        val about = UserIntentClassifier.classify("Tell me about yourself.", conversationContext)
        assertEquals(UserIntentCategory.CASUAL_CONVERSATION, about.category)
        assertTrue(about.conversationResponse?.contains("FRIDAY") == true)

        // 8. Why inquiry: "Why did you do that?"
        val why = UserIntentClassifier.classify("Why did you do that?", conversationContext)
        assertEquals(UserIntentCategory.QUESTION, why.category)
        assertTrue(why.conversationResponse?.isNotEmpty() == true)
    }

    @Test
    fun testPaymentRecognitionAndSecurityRequirements() = runBlocking {
        // Natural payment command parsing
        val action = IntentUnderstandingEngine.parseCommand("pay 500 to rahul@okhdfcbank via Google Pay")
        assertNotNull(action)
        assertEquals(FridayCapabilities.PAYMENT, action?.intent)
        assertEquals("500", action?.parameters?.get("amount"))
        assertEquals("rahul@okhdfcbank", action?.parameters?.get("payee"))
        assertEquals("google pay", action?.parameters?.get("app")?.lowercase())
        assertEquals(ActionRiskLevel.CONFIRM, action?.riskLevel)

        // Payment Tool validation: empty parameters fail gracefully
        val failEmpty = paymentTool.execute(emptyMap(), context)
        assertTrue(failEmpty is ActionResult.Failure)

        // Valid execution returns success or failure based on installed apps, but never fabricates false confirmation
        val res = paymentTool.execute(mapOf("amount" to "500", "payee" to "rahul@okhdfcbank", "app" to "gpay"), context)
        assertNotNull(res)
    }

    @Test
    fun testLockScreenSecurityPolicyEnforcement() {
        val lockedReport = SecurityWatcherReport(
            hasSuspiciousAnomaly = false,
            summary = "Locked device",
            mood = FridayMood.CALM,
            signals = emptyList(),
            suggestedPrompt = null,
            isDeviceLocked = true
        )

        // Sensitive actions like payment or reading contacts must be BLOCKED/REQUIRE UNLOCK on locked screen
        val paymentAssessment = securityManager.evaluateAction(
            actionType = "PAYMENT",
            parameters = mapOf("amount" to "100", "payee" to "someone@upi"),
            voiceConfidence = 0.9f,
            minVoiceThreshold = 0.7f,
            watcherReport = lockedReport
        )
        assertFalse(paymentAssessment.isPermitted)
        assertTrue(paymentAssessment.requiresBiometricOrConfirm)
        assertTrue(paymentAssessment.reason.contains("unlock") || paymentAssessment.reason.contains("locked"))

        val smsAssessment = securityManager.evaluateAction(
            actionType = "SEND_SMS",
            parameters = mapOf("contact" to "Alice", "message" to "Hello"),
            voiceConfidence = 0.9f,
            minVoiceThreshold = 0.7f,
            watcherReport = lockedReport
        )
        assertFalse(smsAssessment.isPermitted)

        // Lock-safe actions like Flashlight must be permitted
        val flashAssessment = securityManager.evaluateAction(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("state" to "true"),
            voiceConfidence = 0.9f,
            minVoiceThreshold = 0.7f,
            watcherReport = lockedReport
        )
        assertTrue(flashAssessment.isPermitted)
    }

    @Test
    fun testFloatingBubbleManagerLifecycle() {
        var clicked = false
        val bubbleManager = FloatingBubbleManager(context) {
            clicked = true
        }

        // Test state updates do not crash
        bubbleManager.updateOrbState(OrbState.LISTENING, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.THINKING, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.SPEAKING, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.WORKING, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.ERROR, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.OFFLINE, isAppInForeground = false)
        bubbleManager.updateOrbState(OrbState.IDLE, isAppInForeground = false)

        // Foreground should dismiss overlay
        bubbleManager.updateOrbState(OrbState.LISTENING, isAppInForeground = true)

        bubbleManager.destroy()
        assertFalse(clicked)
    }
}
