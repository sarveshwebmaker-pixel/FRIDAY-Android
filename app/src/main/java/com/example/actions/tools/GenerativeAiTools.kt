package com.example.actions.tools

import android.content.Context
import com.example.actions.ActionResult
import com.example.ai.gemini.GeminiGenerativeEngine
import com.example.ai.gemini.GenerativeResult
import com.example.security.ActionRiskLevel

/**
 * Search Grounding Tool using gemini-3.5-flash with googleSearch tool.
 * Provides real-time live search for current events, news, sports scores, and web facts.
 */
class SearchGroundingTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "SEARCH_GROUNDING"
) : FridayTool {
    override val name: String = "Live Google Search Grounding"
    override val description: String = "Searches the live web using Google Search grounding for real-time facts and current news."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = parameters["query"] ?: parameters["prompt"] ?: return ActionResult.MissingParameter(
            parameterName = "query",
            prompt = "What would you like me to look up on Google, Boss?"
        )

        val result = generativeEngine.queryWithSearchGrounding(query)
        return when (result) {
            is GenerativeResult.TextSuccess -> ActionResult.Success(
                message = result.text,
                spokenDetail = result.text,
                outputData = mapOf("query" to query)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Could not complete live search: ${result.message}")
            else -> ActionResult.Failure("Unexpected search response.")
        }
    }
}

/**
 * Maps Grounding Tool using gemini-3.5-flash with googleMaps tool.
 * Provides location-aware directions, places, and local establishment search.
 */
class MapsGroundingTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "MAPS_GROUNDING"
) : FridayTool {
    override val name: String = "Google Maps Grounding"
    override val description: String = "Finds locations, businesses, and routes using Google Maps grounding."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val location = parameters["location"] ?: parameters["query"] ?: parameters["prompt"] ?: return ActionResult.MissingParameter(
            parameterName = "location",
            prompt = "Which place or destination would you like me to search for on Google Maps?"
        )

        val result = generativeEngine.queryWithMapsGrounding(location)
        return when (result) {
            is GenerativeResult.TextSuccess -> ActionResult.Success(
                message = result.text,
                spokenDetail = result.text,
                outputData = mapOf("location" to location)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Maps grounding query failed: ${result.message}")
            else -> ActionResult.Failure("Unexpected maps grounding response.")
        }
    }
}

/**
 * Image Generation & Editing Tool using gemini-3.1-flash-image-preview.
 */
class ImageGenerationTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "GENERATE_IMAGE"
) : FridayTool {
    override val name: String = "AI Image Creation & Editing"
    override val description: String = "Generates or edits visual images from natural text prompts using Gemini 3.1 Flash Image Preview."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val prompt = parameters["prompt"] ?: parameters["description"] ?: return ActionResult.MissingParameter(
            parameterName = "prompt",
            prompt = "What image would you like me to generate for you, Boss?"
        )
        val aspectRatio = parameters["aspectRatio"] ?: "1:1"

        val result = generativeEngine.generateImage(prompt, aspectRatio)
        return when (result) {
            is GenerativeResult.ImageSuccess -> ActionResult.Success(
                message = "I have generated the image: '$prompt'.",
                spokenDetail = "Image created successfully.",
                outputData = mapOf("prompt" to prompt, "base64" to result.base64Data)
            )
            is GenerativeResult.TextSuccess -> ActionResult.Success(
                message = result.text,
                spokenDetail = result.text,
                outputData = mapOf("prompt" to prompt)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Image generation failed: ${result.message}")
            else -> ActionResult.Failure("Unexpected image generation response.")
        }
    }
}

/**
 * Video Generation Tool using veo-3.1-fast-generate-preview.
 * Supports aspect ratio (16:9 or 9:16) and resolution (720p or 1080p).
 */
class VideoGenerationTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "GENERATE_VIDEO"
) : FridayTool {
    override val name: String = "AI Video Generation (Veo)"
    override val description: String = "Generates high-definition video from text prompts using Veo 3.1 Fast Generate Preview."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val prompt = parameters["prompt"] ?: parameters["description"] ?: return ActionResult.MissingParameter(
            parameterName = "prompt",
            prompt = "What video scene would you like me to create, Boss?"
        )
        val aspectRatio = parameters["aspectRatio"] ?: "16:9"
        val resolution = parameters["resolution"] ?: "720p"

        val result = generativeEngine.generateVideo(prompt, aspectRatio, resolution)
        return when (result) {
            is GenerativeResult.VideoSuccess -> ActionResult.Success(
                message = result.message,
                spokenDetail = result.message,
                outputData = mapOf("prompt" to prompt, "videoUri" to result.videoUri)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Video generation failed: ${result.message}")
            else -> ActionResult.Failure("Unexpected video generation response.")
        }
    }
}

/**
 * Music Generation Tool using lyria-3-clip-preview (clips) or lyria-3-pro-preview (full).
 */
class MusicGenerationTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "GENERATE_MUSIC"
) : FridayTool {
    override val name: String = "AI Music Generation (Lyria)"
    override val description: String = "Composes audio music clips or soundtracks using Lyria 3 Preview."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val prompt = parameters["prompt"] ?: parameters["genre"] ?: parameters["description"] ?: return ActionResult.MissingParameter(
            parameterName = "prompt",
            prompt = "What kind of music or beat would you like me to compose, Boss?"
        )
        val isPro = parameters["mode"]?.lowercase() == "pro" || parameters["fullTrack"]?.toBoolean() == true

        val result = generativeEngine.generateMusic(prompt, isPro)
        return when (result) {
            is GenerativeResult.AudioSuccess -> ActionResult.Success(
                message = "Music composed successfully for: '$prompt'.",
                spokenDetail = "Music soundtrack composed.",
                outputData = mapOf("prompt" to prompt)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Music generation failed: ${result.message}")
            else -> ActionResult.Failure("Unexpected music generation response.")
        }
    }
}

/**
 * Audio Transcription Tool using gemini-3.5-transcribe.
 */
class AudioTranscriptionTool(
    private val generativeEngine: GeminiGenerativeEngine,
    override val id: String = "TRANSCRIBE_AUDIO"
) : FridayTool {
    override val name: String = "Gemini Audio Transcription"
    override val description: String = "Accurately transcribes voice recordings using Gemini 3.5 Transcribe."
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val audioData = parameters["audioData"] ?: return ActionResult.MissingParameter(
            parameterName = "audioData",
            prompt = "No audio data provided for transcription."
        )
        val mimeType = parameters["mimeType"] ?: "audio/wav"

        val result = generativeEngine.transcribeAudio(audioData, mimeType)
        return when (result) {
            is GenerativeResult.TextSuccess -> ActionResult.Success(
                message = result.text,
                spokenDetail = result.text,
                outputData = mapOf("transcription" to result.text)
            )
            is GenerativeResult.Error -> ActionResult.Failure("Transcription failed: ${result.message}")
            else -> ActionResult.Failure("Unexpected transcription response.")
        }
    }
}
