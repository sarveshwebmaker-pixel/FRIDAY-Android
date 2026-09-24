package com.example.actions.tools

import android.content.Context
import com.example.FridayApplication
import com.example.actions.ActionResult
import com.example.ai.GeminiAiProvider
import com.example.service.ScreenVisionManager

class ScreenVisionTool(override val id: String = "SCREEN_VISION") : FridayTool {
    override val name = "Screen Vision & AI Analysis"
    override val description = "Captures and analyzes what is currently displayed on screen using Gemini Vision"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        if (!ScreenVisionManager.isProjectionAvailable()) {
            return ActionResult.PermissionRequired(
                "MEDIA_PROJECTION",
                "Boss, screen capture permission has not been initialized. Open FRIDAY settings or start a session to grant screen analysis."
            )
        }

        var capturedBase64: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        ScreenVisionManager.captureScreen(context) { bitmap ->
            if (bitmap != null) {
                capturedBase64 = ScreenVisionManager.bitmapToBase64(bitmap)
            }
            latch.countDown()
        }

        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}

        if (capturedBase64 == null) {
            return ActionResult.Failure(
                error = "Failed capturing screen frame",
                userMessage = "I couldn't capture the screen frame right now, Boss."
            )
        }

        val question = parameters["prompt"] ?: parameters["query"] ?: parameters["question"] ?: ""

        // If user wants screen capture only without deep analysis
        if (id == "SCREEN_CAPTURE" && question.isBlank()) {
            return ActionResult.Success(
                message = "Screen captured successfully",
                spokenDetail = "I've captured your screen, Boss.",
                outputData = mapOf("screenBase64" to capturedBase64!!)
            )
        }

        // Deep AI analysis with Gemini
        val aiProvider = GeminiAiProvider(
            customApiKeyProvider = {
                try {
                    FridayApplication.instance.settingsRepo.settings.value.customApiKey
                } catch (_: Exception) { "" }
            }
        )

        val analysis = aiProvider.analyzeScreenImage(capturedBase64!!, question)

        return ActionResult.Success(
            message = analysis,
            spokenDetail = analysis,
            outputData = mapOf("screenBase64" to capturedBase64!!, "analysis" to analysis)
        )
    }
}

