package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.FridayApplication
import com.example.MainActivity
import com.example.R

class FridayForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.friday.service.START"
        const val ACTION_STOP = "com.example.friday.service.STOP"
        const val ACTION_ACTIVATE_VOICE = "com.example.friday.service.ACTIVATE_VOICE"

        var isRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, FridayForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FridayForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    FridayApplication.instance.fridayCore.stopListening()
                } catch (_: Exception) {}
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_ACTIVATE_VOICE -> {
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("TRIGGER_VOICE", true)
                }
                startActivity(openIntent)
            }
            else -> {
                startForegroundWithNotification("FRIDAY — Ready in Standby")
                try {
                    FridayApplication.instance.fridayCore.checkAndStartStandby()
                } catch (_: Exception) {}
            }
        }
        return START_STICKY
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true

        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Friday:StandbyWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hour max safety timeout
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activateVoiceIntent = Intent(this, FridayForegroundService::class.java).apply {
            action = ACTION_ACTIVATE_VOICE
        }
        val pendingActivateIntent = PendingIntent.getService(
            this, 1, activateVoiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FridayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, FridayApplication.CHANNEL_ID_STANDBY)
            .setContentTitle("FRIDAY AI Assistant")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_friday_logo, "Activate", pendingActivateIntent)
            .addAction(R.drawable.ic_friday_logo, "Standby Off", pendingStopIntent)
            .build()
    }

    private fun stopForegroundService() {
        isRunning = false
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }
}
