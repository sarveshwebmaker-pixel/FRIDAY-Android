package com.example.bubble

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Floating Bubble Manager.
 *
 * Phase 1 Architecture:
 * - Manages SYSTEM_ALERT_WINDOW permission verification and bubble availability state.
 * - Bridges background touch triggers to launch FRIDAY assistant overlay.
 *
 * Phase 2 Roadmap:
 * - Draggable floating micro-orb with touch gestures and audio waveform overlay.
 */
class FloatingBubbleManager(private val context: Context) {

    companion object {
        private const val TAG = "FloatingBubbleManager"
    }

    fun canEnableBubble(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun setBubbleActive(active: Boolean): Boolean {
        if (active && !canEnableBubble()) {
            Log.w(TAG, "Cannot activate bubble: SYSTEM_ALERT_WINDOW permission not granted")
            return false
        }
        Log.i(TAG, "Floating bubble state updated: active=$active")
        return true
    }
}
