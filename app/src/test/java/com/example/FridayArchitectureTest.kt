package com.example

import com.example.ai.OfflineAiProvider
import com.example.core.BatteryMode
import com.example.security.ActionRiskLevel
import com.example.security.SecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FridayArchitectureTest {

    private val securityManager = SecurityManager()
    private val offlineAi = OfflineAiProvider()

    @Test
    fun testSafeActionEvaluation() {
        val eval = securityManager.evaluateAction(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = mapOf("target" to "flashlight", "state" to "true"),
            voiceConfidence = 0.90f,
            minVoiceThreshold = 0.80f
        )
        assertEquals(ActionRiskLevel.SAFE, eval.riskLevel)
    }

    @Test
    fun testSensitiveActionEvaluationRequiresConfirm() {
        val eval = securityManager.evaluateAction(
            actionType = "CLEAR_DATA",
            parameters = emptyMap(),
            voiceConfidence = 0.90f,
            minVoiceThreshold = 0.80f
        )
        assertEquals(ActionRiskLevel.CONFIRM, eval.riskLevel)
    }

    @Test
    fun testLowConfidenceDemotesToConfirm() {
        val eval = securityManager.evaluateAction(
            actionType = "MODIFY_SYSTEM_SETTINGS",
            parameters = emptyMap(),
            voiceConfidence = 0.65f,
            minVoiceThreshold = 0.80f
        )
        assertEquals(ActionRiskLevel.CONFIRM, eval.riskLevel)
    }

    @Test
    fun testOfflineAiFlashlight() = runBlocking {
        val result = offlineAi.processCommand("turn on the flashlight", BatteryMode.NORMAL)
        assertTrue(result is com.example.ai.FridayAiResult.Success)
        val action = (result as com.example.ai.FridayAiResult.Success).action
        assertEquals("TOGGLE_FLASHLIGHT", action.actionType)
        assertEquals("true", action.parameters["state"])
    }

    @Test
    fun testOfflineAiOnTheFlashlightColloquial() = runBlocking {
        // Test colloquial "on the flashlight" / "she on the flashlight"
        val result = offlineAi.processCommand("on the flashlight", BatteryMode.NORMAL)
        assertTrue(result is com.example.ai.FridayAiResult.Success)
        val action = (result as com.example.ai.FridayAiResult.Success).action
        assertEquals("TOGGLE_FLASHLIGHT", action.actionType)
        assertEquals("true", action.parameters["state"])
    }

    @Test
    fun testOfflineAiOffTheFlashlight() = runBlocking {
        val result = offlineAi.processCommand("turn off flashlight", BatteryMode.NORMAL)
        assertTrue(result is com.example.ai.FridayAiResult.Success)
        val action = (result as com.example.ai.FridayAiResult.Success).action
        assertEquals("TOGGLE_FLASHLIGHT", action.actionType)
        assertEquals("false", action.parameters["state"])
    }

    @Test
    fun testCanHandleLocallyFastPath() {
        assertTrue(offlineAi.canHandleLocally("turn on the flashlight"))
        assertTrue(offlineAi.canHandleLocally("on the flashlight"))
        assertTrue(offlineAi.canHandleLocally("volume up"))
        assertTrue(offlineAi.canHandleLocally("how much battery left"))
        assertTrue(offlineAi.canHandleLocally("what time is it"))
        assertTrue(offlineAi.canHandleLocally("open camera"))
    }

    @Test
    fun testOfflineAiBatteryQuery() = runBlocking {
        val result = offlineAi.processCommand("how much battery do I have left?", BatteryMode.BATTERY_SAVER)
        assertTrue(result is com.example.ai.FridayAiResult.Success)
        val action = (result as com.example.ai.FridayAiResult.Success).action
        assertEquals("GET_BATTERY_INFO", action.actionType)
    }

    @Test
    fun testNormalCommandsAreCategorizedAsSafe() {
        assertTrue(SecurityManager.isSafeAction("TOGGLE_FLASHLIGHT"))
        assertTrue(SecurityManager.isSafeAction("CHANGE_VOLUME"))
        assertTrue(SecurityManager.isSafeAction("GET_BATTERY_INFO"))
        assertTrue(SecurityManager.isSafeAction("GET_TIME"))
        assertTrue(SecurityManager.isSafeAction("LAUNCH_APP"))
        assertTrue(SecurityManager.isSafeAction("MEDIA_CONTROL"))

        // When voiceConfidence is null (Requirement 7: no voice identity check for normal commands)
        val eval = securityManager.evaluateAction(
            actionType = "TOGGLE_FLASHLIGHT",
            parameters = emptyMap(),
            voiceConfidence = null,
            minVoiceThreshold = 0.80f
        )
        assertEquals(ActionRiskLevel.SAFE, eval.riskLevel)
    }

    @Test
    fun testStripWakeWordPrefix() {
        assertEquals("turn on the flashlight", com.example.core.FridayCore.stripWakeWordPrefix("Friday, turn on the flashlight"))
        assertEquals("what is the time", com.example.core.FridayCore.stripWakeWordPrefix("Hey Friday what is the time"))
        assertEquals("volume up", com.example.core.FridayCore.stripWakeWordPrefix("FRIDAY: volume up"))
        assertEquals("", com.example.core.FridayCore.stripWakeWordPrefix("FRIDAY"))
        assertEquals("", com.example.core.FridayCore.stripWakeWordPrefix("Hey Friday"))
        assertEquals("turn off torch", com.example.core.FridayCore.stripWakeWordPrefix("turn off torch"))
    }
}
