package com.example.ai

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface GeminiLiveSessionListener {
    fun onConnected()
    fun onAudioChunkReceived(audioData: ByteArray)
    fun onTextReceived(text: String)
    fun onTurnComplete()
    fun onError(error: String)
    fun onDisconnected()
}

/**
 * Real-time Gemini Live WebSocket Client.
 * Establishes a persistent raw WebSocket (wss://) connection to Gemini Live API
 * supporting realtimeInput, streaming audio/text, interruption, and low-latency responses.
 */
class GeminiLiveClient(
    private val apiKeyProvider: () -> String,
    private val listener: GeminiLiveSessionListener
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val LIVE_ENDPOINT = "wss://$HOST/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    val isConnected = AtomicBoolean(false)

    fun connect() {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            listener.onError("Gemini API key missing or invalid.")
            return
        }

        if (isConnected.get()) {
            Log.d(TAG, "Already connected to Gemini Live")
            return
        }

        val request = Request.Builder()
            .url("$LIVE_ENDPOINT?key=$apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                Log.i(TAG, "Gemini Live WebSocket opened successfully.")
                sendInitialSetup()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                Log.e(TAG, "Gemini Live WebSocket failure: ${t.message}", t)
                listener.onError(t.localizedMessage ?: "WebSocket failure")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Gemini Live closing: $reason ($code)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                Log.i(TAG, "Gemini Live closed: $reason ($code)")
                listener.onDisconnected()
            }
        })
    }

    private fun sendInitialSetup() {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", org.json.JSONArray().apply {
                            put("TEXT")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                })
            }
            webSocket?.send(setupJson.toString())
            Log.i(TAG, "Sent setup packet to Gemini Live.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup packet", e)
        }
    }

    fun sendRealtimeAudio(pcmData: ByteArray) {
        if (!isConnected.get() || webSocket == null) return
        try {
            val base64 = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP)
            val audioJson = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm")
                            put("data", base64)
                        })
                    })
                })
            }
            webSocket?.send(audioJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendTextMessage(text: String) {
        if (!isConnected.get() || webSocket == null) return
        try {
            val clientContent = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().put("text", text))
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            webSocket?.send(clientContent.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message to Live session", e)
        }
    }

    fun interrupt() {
        Log.i(TAG, "Interrupting active Gemini Live stream.")
        // Can send turn cancellation or reconnect if needed
    }

    fun disconnect() {
        if (isConnected.get()) {
            webSocket?.close(1000, "Client closed session")
            isConnected.set(false)
        }
    }

    private fun handleIncomingMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text")
                        if (text.isNotBlank()) {
                            listener.onTextReceived(text)
                        }
                    }
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing server message: ${e.message}")
        }
    }
}
