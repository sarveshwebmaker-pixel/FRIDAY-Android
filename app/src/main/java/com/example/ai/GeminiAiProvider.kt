package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.core.BatteryMode
import com.example.security.ActionRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAiProvider(
    private val customApiKeyProvider: () -> String = { "" },
    private val offlineFallback: OfflineAiProvider = OfflineAiProvider()
) : AiProvider {

    companion object {
        private const val TAG = "GeminiAiProvider"
        private const val MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        private const val SYSTEM_INSTRUCTION = """
You are FRIDAY, an advanced native Android AI control assistant.
Your job is to translate user voice commands strictly into structured phone actions and tool executions.
Speak naturally and conversationally, never like a robotic system log. Address user as Boss occasionally.

Supported Action Types & Tools:
1. HARDWARE & DEVICE:
   - TOGGLE_FLASHLIGHT: {"state": "on"|"off"}
   - ADJUST_VOLUME: {"direction": "up"|"down"|"mute"|"unmute", "level": "0-100"}
   - MEDIA_CONTROL: {"command": "play"|"pause"|"next"|"previous"|"stop"|"toggle"}
   - SET_BRIGHTNESS: {"level": "0-100", "openSettings": "true"|"false"}
   - GET_BATTERY_INFO: {}
   - GET_DATE_TIME: {}

2. APPS & BROWSING:
   - OPEN_APP: {"appName": "WhatsApp"|"YouTube"|"Settings"|...}
   - CLOSE_APP: {"appName": "optional"}
   - SEARCH_WEB: {"query": "...", "targetApp": "YouTube"|"browser"}
   - OPEN_URL: {"url": "..."}

3. TIME & CALENDAR:
   - SET_TIMER: {"seconds": "300", "label": "..."}
   - SET_ALARM: {"hour": "7", "minute": "30", "message": "..."}
   - SHOW_TIMERS_ALARMS: {"mode": "alarm"|"timer"}
   - CALENDAR_EVENT: {"title": "...", "description": "..."}

4. COMMUNICATION (STRICT RULES):
   - WHATSAPP_CALL: {"contact": "Rahul"} (User explicitly asks to CALL on WhatsApp / voice call on WhatsApp. NEVER silently convert to chat!)
   - WHATSAPP_CHAT: {"contact": "Rahul"} (User asks to WhatsApp / open chat with someone)
   - WHATSAPP_MESSAGE: {"contact": "Rahul", "message": "Hello"}
   - CALL_CONTACT: {"contact": "Rahul"} (Standard phone call via dialer / tel)
   - SEND_SMS: {"contact": "Rahul", "message": "..."}
   - SEARCH_CONTACT: {"query": "Rahul"}
   - SEND_EMAIL: {"recipient": "...", "subject": "...", "body": "..."}
   - SHARE_CONTENT: {"content": "...", "title": "..."}
   - CLIPBOARD_ACTION: {"mode": "copy"|"read", "text": "..."}

5. NAVIGATION & MAPS:
   - MAP_SEARCH: {"location": "..."}
   - START_NAVIGATION: {"destination": "..."}

6. CAMERA & MEDIA:
   - CAMERA_ACTION: {"mode": "photo"|"video"|"open"}
   - GALLERY_ACTION: {}
   - FILE_ACTION: {"mode": "open"|"downloads"}

7. SETTINGS & SYSTEM SHADES:
   - SETTINGS_ACTION: {"setting": "wifi"|"bluetooth"|"display"|"sound"|"battery"|"apps"|"general"}
   - SYSTEM_PANEL_ACTION: {"panel": "notifications"|"quick_settings"}

8. UI AUTOMATION (Where requested):
   - UI_AUTOMATION: {"operation": "click"|"type"|"scroll_down"|"scroll_up"|"back"|"home"|"recents"|"read_screen", "target": "button label or id", "inputText": "..."}

9. CONVERSATION, QUESTIONS & KNOWLEDGE:
   - SPEAK_RESPONSE: {"message": "..."} (For questions, explanations, math, trivia, general AI dialogue, advice, or greeting. Answer clearly and naturally in 1-3 conversational sentences under 30 words.)

10. MULTI-STEP COMMANDS:
   - If user asks a sequence like "find Rahul in contacts and call him on WhatsApp", return intent "MULTI_STEP_PLAN", actionType "MULTI_STEP_ACTION", and populate the "steps" array with each child action in chronological order.

Return ONLY a valid JSON object with:
{
  "intent": "<INTENT_NAME>",
  "actionType": "<ACTION_TYPE>",
  "parameters": { ... },
  "speechResponse": "<Short, natural spoken response under 15 words>",
  "riskLevel": "SAFE" | "CONFIRM" | "BLOCKED",
  "confirmationPrompt": null | "prompt string",
  "steps": [ ... optional list of StructuredAction JSONs for multi-step ... ]
}
"""
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    override val name: String = "Gemini 2.5 Flash"

    override val isCloudConnected: Boolean
        get() {
            val key = getEffectiveApiKey()
            return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        }

    private fun getEffectiveApiKey(): String {
        val custom = customApiKeyProvider()
        if (custom.isNotBlank()) return custom
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun processCommand(command: String, batteryMode: BatteryMode): FridayAiResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        // If in battery saver or no valid API key configured, use local engine
        if (batteryMode == BatteryMode.BATTERY_SAVER || apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.i(TAG, "Using offline local brain (BatterySaver or no API key)")
            return@withContext offlineFallback.processCommand(command, batteryMode)
        }

        try {
            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().put("text", command))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_INSTRUCTION))
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini API failed with code ${response.code}, falling back to local engine")
                return@withContext offlineFallback.processCommand(command, batteryMode)
            }

            val responseBodyString = response.body?.string() ?: return@withContext offlineFallback.processCommand(command, batteryMode)
            val jsonRoot = JSONObject(responseBodyString)
            val candidates = jsonRoot.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")

            if (rawText.isNullOrBlank()) {
                return@withContext offlineFallback.processCommand(command, batteryMode)
            }

            val actionJson = JSONObject(rawText)
            val parsedAction = parseStructuredActionJson(actionJson)
            FridayAiResult.Success(parsedAction)
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini API: ${e.message}", e)
            offlineFallback.processCommand(command, batteryMode)
        }
    }

    private fun parseStructuredActionJson(actionJson: JSONObject): StructuredAction {
        val intent = actionJson.optString("intent", "GENERAL_QUERY")
        val actionType = actionJson.optString("actionType", "SPEAK_RESPONSE")
        val speechResponse = actionJson.optString("speechResponse", "On it, Boss.")
        val riskLevelStr = actionJson.optString("riskLevel", "SAFE")
        val confirmationPrompt = if (actionJson.has("confirmationPrompt")) actionJson.optString("confirmationPrompt") else null

        val paramsMap = mutableMapOf<String, String>()
        val paramsObj = actionJson.optJSONObject("parameters")
        if (paramsObj != null) {
            val keys = paramsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                paramsMap[k] = paramsObj.optString(k, "")
            }
        }

        val childSteps = mutableListOf<StructuredAction>()
        val stepsArray = actionJson.optJSONArray("steps")
        if (stepsArray != null) {
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.optJSONObject(i)
                if (stepObj != null) {
                    childSteps.add(parseStructuredActionJson(stepObj))
                }
            }
        }

        val riskLevel = try {
            ActionRiskLevel.valueOf(riskLevelStr.uppercase())
        } catch (_: Exception) {
            ActionRiskLevel.SAFE
        }

        return StructuredAction(
            intent = intent,
            actionType = actionType,
            parameters = paramsMap,
            speechResponse = speechResponse,
            riskLevel = riskLevel,
            confirmationPrompt = confirmationPrompt,
            steps = childSteps
        )
    }
}
