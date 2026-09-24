package com.example.ai.gemini

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Advanced Gemini Generative AI Engine for FRIDAY.
 *
 * Implements strict model compliance:
 * - Search Grounding: gemini-3.5-flash + googleSearch
 * - Maps Grounding: gemini-3.5-flash + googleMaps
 * - Live Conversations: gemini-3.8-live
 * - Image Creation & Editing: gemini-3.1-flash-image-preview
 * - Video Generation (Text & Image): veo-3.1-fast-generate-preview
 * - Music Generation: lyria-3-clip-preview (clips) / lyria-3-pro-preview (full tracks)
 * - Audio Transcription: gemini-3.5-transcribe
 */
class GeminiGenerativeEngine(
    private val apiKeyProvider: () -> String = { "" }
) {

    companion object {
        private const val TAG = "GeminiGenerativeEngine"
        private const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

        // Strictly mapped model identifiers
        const val MODEL_TEXT_GROUNDING = "gemini-3.5-flash"
        const val MODEL_LIVE_CONVERSATION = "gemini-3.8-live"
        const val MODEL_IMAGE_GENERATION = "gemini-3.1-flash-image-preview"
        const val MODEL_VIDEO_GENERATION = "veo-3.1-fast-generate-preview"
        const val MODEL_MUSIC_CLIP = "lyria-3-clip-preview"
        const val MODEL_MUSIC_PRO = "lyria-3-pro-preview"
        const val MODEL_AUDIO_TRANSCRIBE = "gemini-3.5-transcribe"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getEffectiveKey(): String {
        val custom = apiKeyProvider().trim()
        if (custom.isNotBlank()) return custom
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
    }

    val isAvailable: Boolean
        get() {
            val key = getEffectiveKey()
            return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        }

    /**
     * Search Grounding using gemini-3.5-flash with the googleSearch tool.
     */
    suspend fun queryWithSearchGrounding(prompt: String): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().put("googleSearch", JSONObject()))
                })
            }

            val url = "$BASE_ENDPOINT/$MODEL_TEXT_GROUNDING:generateContent?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Search grounding failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val text = content?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""

            // Extract search query metadata if returned
            val groundingMetadata = candidate?.optJSONObject("groundingMetadata")
            val searchQueries = mutableListOf<String>()
            val queriesArray = groundingMetadata?.optJSONArray("webSearchQueries")
            if (queriesArray != null) {
                for (i in 0 until queriesArray.length()) {
                    searchQueries.add(queriesArray.optString(i))
                }
            }

            GenerativeResult.TextSuccess(
                text = text.trim(),
                metadata = mapOf("model" to MODEL_TEXT_GROUNDING, "searchQueries" to searchQueries.joinToString(", "))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search grounding error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Unknown search grounding error")
        }
    }

    /**
     * Maps Grounding using gemini-3.5-flash with the googleMaps tool.
     */
    suspend fun queryWithMapsGrounding(prompt: String): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().put("googleMaps", JSONObject()))
                })
            }

            val url = "$BASE_ENDPOINT/$MODEL_TEXT_GROUNDING:generateContent?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Maps grounding failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val text = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""

            GenerativeResult.TextSuccess(
                text = text.trim(),
                metadata = mapOf("model" to MODEL_TEXT_GROUNDING, "grounding" to "googleMaps")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Maps grounding error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Unknown maps grounding error")
        }
    }

    /**
     * Image Creation & Editing using gemini-3.1-flash-image-preview.
     */
    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1",
        outputMimeType: String = "image/jpeg"
    ): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        try {
            val requestJson = JSONObject().apply {
                put("prompt", prompt)
                put("numberOfImages", 1)
                put("outputMimeType", outputMimeType)
                put("aspectRatio", aspectRatio)
            }

            val url = "$BASE_ENDPOINT/$MODEL_IMAGE_GENERATION:generateImages?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Image generation failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val imagesArray = json.optJSONArray("images")
            val base64 = imagesArray?.optJSONObject(0)?.optString("imageBytes")
                ?: imagesArray?.optJSONObject(0)?.optString("data") ?: ""

            if (base64.isNotBlank()) {
                GenerativeResult.ImageSuccess(
                    base64Data = base64,
                    mimeType = outputMimeType,
                    metadata = mapOf("model" to MODEL_IMAGE_GENERATION, "aspectRatio" to aspectRatio)
                )
            } else {
                GenerativeResult.TextSuccess(
                    text = "Image generated for prompt: '$prompt'",
                    metadata = mapOf("model" to MODEL_IMAGE_GENERATION)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image generation error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Image generation error")
        }
    }

    /**
     * Video Generation using veo-3.1-fast-generate-preview.
     * Supports aspect ratio (16:9 or 9:16) and resolution (720p or 1080p).
     */
    suspend fun generateVideo(
        prompt: String,
        aspectRatio: String = "16:9",
        resolution: String = "720p"
    ): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        try {
            val requestJson = JSONObject().apply {
                put("prompt", prompt)
                put("aspectRatio", if (aspectRatio in listOf("16:9", "9:16")) aspectRatio else "16:9")
                put("resolution", resolution)
            }

            val url = "$BASE_ENDPOINT/$MODEL_VIDEO_GENERATION:generateVideos?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Video generation failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val videoUri = json.optString("videoUri")
            val message = if (videoUri.isNotBlank()) "Video created: $videoUri" else "Video generation initiated for '$prompt'."

            GenerativeResult.VideoSuccess(
                videoUri = videoUri,
                metadata = mapOf("model" to MODEL_VIDEO_GENERATION, "aspectRatio" to aspectRatio, "resolution" to resolution),
                message = message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video generation error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Video generation error")
        }
    }

    /**
     * Music Generation using lyria-3-clip-preview (clips) or lyria-3-pro-preview (full tracks).
     */
    suspend fun generateMusic(
        prompt: String,
        isPro: Boolean = false
    ): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        val model = if (isPro) MODEL_MUSIC_PRO else MODEL_MUSIC_CLIP

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                })
            }

            val url = "$BASE_ENDPOINT/$model:generateContent?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Music generation failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val part = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            val inlineData = part?.optJSONObject("inlineData")
            val base64Audio = inlineData?.optString("data") ?: ""
            val mimeType = inlineData?.optString("mimeType", "audio/mp3") ?: "audio/mp3"

            GenerativeResult.AudioSuccess(
                base64Data = base64Audio,
                mimeType = mimeType,
                metadata = mapOf("model" to model, "prompt" to prompt)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Music generation error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Music generation error")
        }
    }

    /**
     * Audio Transcription using gemini-3.5-transcribe.
     */
    suspend fun transcribeAudio(
        base64AudioData: String,
        mimeType: String = "audio/wav"
    ): GenerativeResult = withContext(Dispatchers.IO) {
        val key = getEffectiveKey()
        if (key.isBlank()) return@withContext GenerativeResult.Error("Gemini API key is not configured.")

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64AudioData)
                                })
                            })
                            put(JSONObject().put("text", "Transcribe this audio accurately."))
                        })
                    })
                })
            }

            val url = "$BASE_ENDPOINT/$MODEL_AUDIO_TRANSCRIBE:generateContent?key=$key"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext GenerativeResult.Error("Audio transcription failed: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val transcription = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""

            GenerativeResult.TextSuccess(
                text = transcription.trim(),
                metadata = mapOf("model" to MODEL_AUDIO_TRANSCRIBE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error: ${e.message}", e)
            GenerativeResult.Error(e.message ?: "Audio transcription error")
        }
    }
}

sealed class GenerativeResult {
    data class TextSuccess(val text: String, val metadata: Map<String, String> = emptyMap()) : GenerativeResult()
    data class ImageSuccess(val base64Data: String, val mimeType: String, val metadata: Map<String, String> = emptyMap()) : GenerativeResult()
    data class VideoSuccess(val videoUri: String, val metadata: Map<String, String> = emptyMap(), val message: String) : GenerativeResult()
    data class AudioSuccess(val base64Data: String, val mimeType: String, val metadata: Map<String, String> = emptyMap()) : GenerativeResult()
    data class Error(val message: String) : GenerativeResult()
}
