package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.UserIntentCategory
import com.example.ai.UserIntentClassifier
import com.example.core.ConversationContext
import com.example.core.ConversationStatus
import com.example.core.DeviceLockState
import com.example.core.FridayCore
import com.example.core.VoiceAuthState
import com.example.core.VoiceOutputMode
import com.example.identity.SpeakerType
import com.example.identity.VoiceIdentityEngine
import com.example.security.ActionRiskLevel
import com.example.security.SecurityManager
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
class FridayLockScreenAndSilentModeTest {

    private lateinit var context: Context
    private lateinit var securityManager: SecurityManager
    private lateinit var identityEngine: VoiceIdentityEngine
    private lateinit var conversationContext: ConversationContext

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        context = app
        securityManager = SecurityManager()
        identityEngine = VoiceIdentityEngine()
        conversationContext = ConversationContext()
    }

    // --- 1. OWNER VOICE ACCESS WHILE PHONE IS LOCKED ---

    @Test
    fun testOwnerVoiceCanExecuteSafeActionWhileLocked() {
        val voiceResult = identityEngine.verifySpeaker(
            spokenText = "turn on the flashlight",
            speakerType = SpeakerType.OWNER,
            noiseLevel = 0.1f
        )
        assertTrue("Owner voice should be verified", voiceResult.isVerified)

        val eval = securityManager.evaluateAction(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("target" to "flashlight", "state" to "true"),
            voiceConfidence = voiceResult.confidence,
            minVoiceThreshold = identityEngine.getThreshold(),
            isVoiceVerifiedOwner = voiceResult.isVerified,
            isDeviceLocked = true
        )

        assertEquals("Safe action should execute hands-free when owner is verified and phone is locked", ActionRiskLevel.SAFE, eval.riskLevel)
    }

    @Test
    fun testAnotherPersonVoiceBlockedWhilePhoneIsLocked() {
        val voiceResult = identityEngine.verifySpeaker(
            spokenText = "turn on the flashlight",
            speakerType = SpeakerType.ANOTHER_PERSON,
            noiseLevel = 0.1f
        )
        assertFalse("Other person voice should NOT be verified as owner", voiceResult.isVerified)

        val eval = securityManager.evaluateAction(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("target" to "flashlight", "state" to "true"),
            voiceConfidence = voiceResult.confidence,
            minVoiceThreshold = identityEngine.getThreshold(),
            isVoiceVerifiedOwner = voiceResult.isVerified,
            isDeviceLocked = true
        )

        assertEquals("Action from unauthorized voice on locked device must be rejected/blocked", ActionRiskLevel.BLOCKED, eval.riskLevel)
        assertTrue("Reason should indicate owner authentication requirement", eval.reason.contains("Owner", ignoreCase = true) || eval.reason.contains("locked", ignoreCase = true) || eval.reason.contains("not recognized", ignoreCase = true))
    }

    @Test
    fun testProtectedActionRequiresDeviceUnlockEvenForOwnerWhileLocked() {
        val voiceResult = identityEngine.verifySpeaker(
            spokenText = "clear all data",
            speakerType = SpeakerType.OWNER,
            noiseLevel = 0.1f
        )
        assertTrue(voiceResult.isVerified)

        val eval = securityManager.evaluateAction(
            actionType = "CLEAR_DATA",
            parameters = emptyMap(),
            voiceConfidence = voiceResult.confidence,
            minVoiceThreshold = identityEngine.getThreshold(),
            isVoiceVerifiedOwner = voiceResult.isVerified,
            isDeviceLocked = true
        )

        assertEquals("Sensitive/protected action must require unlock even for owner when device is locked", ActionRiskLevel.CONFIRM, eval.riskLevel)
        assertTrue("Reason should state device must be unlocked", eval.reason.contains("unlock", ignoreCase = true))
    }

    @Test
    fun testEnvironmentalNoiseImpactOnVoiceConfidence() {
        val quietResult = identityEngine.verifySpeaker(
            spokenText = "FRIDAY, what is the battery level",
            speakerType = SpeakerType.OWNER,
            noiseLevel = 0.05f
        )

        val noisyResult = identityEngine.verifySpeaker(
            spokenText = "FRIDAY, what is the battery level",
            speakerType = SpeakerType.OWNER,
            noiseLevel = 0.85f
        )

        assertTrue("Quiet environment confidence should be higher than noisy environment", quietResult.confidence > noisyResult.confidence)
        assertTrue("Quiet environment should be verified", quietResult.isVerified)
    }

    // --- 2. "MUTE AND WORK" — SILENT EXECUTION MODE ---

    @Test
    fun testMuteAndWorkIntentClassification() {
        val commands = listOf(
            "mute and work",
            "silent mode",
            "be quiet and work",
            "work silently",
            "stop talking and just work",
            "work in silent mode"
        )

        for (cmd in commands) {
            val classified = UserIntentClassifier.classify(cmd, conversationContext)
            assertNotNull("Command '$cmd' should produce a structured action", classified.structuredAction)
            assertEquals("Action type should be SILENT_WORK_MODE for '$cmd'", "SILENT_WORK_MODE", classified.structuredAction?.actionType)
        }
    }

    @Test
    fun testUnmuteIntentClassification() {
        val commands = listOf(
            "unmute",
            "start talking",
            "turn on voice",
            "resume talking",
            "you can speak now",
            "speak again"
        )

        for (cmd in commands) {
            val classified = UserIntentClassifier.classify(cmd, conversationContext)
            assertNotNull("Command '$cmd' should produce a structured action", classified.structuredAction)
            assertEquals("Action type should be UNMUTE for '$cmd'", "UNMUTE", classified.structuredAction?.actionType)
        }
    }

    @Test
    fun testFridayCoreSilentModeToggleAndState() {
        val fridayCore = FridayCore(context)

        // Default state
        assertEquals(VoiceOutputMode.ON, fridayCore.state.value.voiceOutputMode)
        assertFalse(fridayCore.state.value.isSilentWorkMode)

        // Enable Silent Work Mode
        fridayCore.setSilentWorkMode(true)
        assertEquals(VoiceOutputMode.MUTED, fridayCore.state.value.voiceOutputMode)
        assertTrue(fridayCore.state.value.isSilentWorkMode)

        // Unmute
        fridayCore.setSilentWorkMode(false)
        assertEquals(VoiceOutputMode.ON, fridayCore.state.value.voiceOutputMode)
        assertFalse(fridayCore.state.value.isSilentWorkMode)
    }

    // --- 3. CENTRALIZED STATE DESIGN ---

    @Test
    fun testCentralizedStateEnumsAndProperties() {
        val fridayCore = FridayCore(context)

        // Test VoiceAuthState transitions
        fridayCore.setVoiceAuthState(VoiceAuthState.OWNER)
        assertEquals(VoiceAuthState.OWNER, fridayCore.state.value.voiceAuthState)
        assertTrue(fridayCore.state.value.isOwnerAuthenticated)

        fridayCore.setVoiceAuthState(VoiceAuthState.UNKNOWN)
        assertEquals(VoiceAuthState.UNKNOWN, fridayCore.state.value.voiceAuthState)
        assertFalse(fridayCore.state.value.isOwnerAuthenticated)

        // Test DeviceLockState transitions
        fridayCore.setDeviceLockState(DeviceLockState.LOCKED)
        assertEquals(DeviceLockState.LOCKED, fridayCore.state.value.deviceLockState)
        assertTrue(fridayCore.state.value.isDeviceLocked)

        fridayCore.setDeviceLockState(DeviceLockState.UNLOCKED)
        assertEquals(DeviceLockState.UNLOCKED, fridayCore.state.value.deviceLockState)
        assertFalse(fridayCore.state.value.isDeviceLocked)

        // Test ConversationStatus transitions
        fridayCore.startConversationSession()
        assertEquals(ConversationStatus.ACTIVE, fridayCore.state.value.conversationStatus)
        assertTrue(fridayCore.state.value.isConversationActive)

        fridayCore.endConversationSession()
        assertEquals(ConversationStatus.INACTIVE, fridayCore.state.value.conversationStatus)
        assertFalse(fridayCore.state.value.isConversationActive)
    }
}
