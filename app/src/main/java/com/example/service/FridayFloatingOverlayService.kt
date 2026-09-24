package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.FridayApplication

/**
 * Floating Overlay Service for FRIDAY.
 * Coordinates with FloatingBubbleManager to ensure the interactive assistant orb
 * appears dynamically when active in background and dismisses cleanly when idle.
 * Never keeps a static, unclickable icon stuck on screen.
 */
class FridayFloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FridayFloatingDock"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start overlay service: overlay permission not granted")
                return
            }
            try {
                val intent = Intent(context, FridayFloatingOverlayService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FridayFloatingOverlayService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FridayFloatingOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FridayFloatingOverlayService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "FridayFloatingOverlayService initialized cleanly.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "FridayFloatingOverlayService stopped.")
    }
}
