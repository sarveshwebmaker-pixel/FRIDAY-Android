package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Pluggable TTS (Text-to-Speech) / Voice Output abstraction.
 * Allows FRIDAY to run with Android System TTS today, and seamlessly
 * plug in a cloud TTS provider in the future without modifying core business logic.
 */
interface VoiceOutputProvider {
    fun speak(text: String, pitch: Float = 1.0f, rate: Float = 1.0f)
    fun stop()
    fun isReady(): Boolean
    fun shutdown()
}

/**
 * Standard on-device Android Text-to-Speech implementation.
 * Zero-dependency, lightweight, offline-capable, and preserves device battery life.
 */
class AndroidSystemTtsProvider(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) : VoiceOutputProvider {

    companion object {
        private const val TAG = "AndroidSystemTts"
        private const val UTTERANCE_ID = "friday_speech_id"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { onStart() }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { onDone() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { onError("TTS synthesis error") }
                    }
                })
                Log.d(TAG, "Android TextToSpeech initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize Android TextToSpeech (status $status)")
            }
        }
    }

    override fun speak(text: String, pitch: Float, rate: Float) {
        if (!isInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not ready to speak: $text")
            onDone()
            return
        }

        try {
            textToSpeech?.setPitch(pitch)
            textToSpeech?.setSpeechRate(rate)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            onError(e.message ?: "TTS error")
        }
    }

    override fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    override fun isReady(): Boolean = isInitialized && textToSpeech != null

    override fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
}
