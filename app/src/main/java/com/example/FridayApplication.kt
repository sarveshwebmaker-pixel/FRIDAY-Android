package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class FridayApplication : Application() {

    companion object {
        const val CHANNEL_ID_STANDBY = "friday_standby_channel"
        const val CHANNEL_ID_ALERTS = "friday_alerts_channel"
        lateinit var instance: FridayApplication
            private set
    }

    lateinit var settingsRepo: com.example.settings.FridaySettingsRepository
        private set

    lateinit var fridayCore: com.example.core.FridayCore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepo = com.example.settings.FridaySettingsRepository(applicationContext)
        fridayCore = com.example.core.FridayCore(applicationContext, settingsRepo)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val standbyChannel = NotificationChannel(
                CHANNEL_ID_STANDBY,
                "FRIDAY Standby Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows FRIDAY active background standby state"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "FRIDAY Actions & Security",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for security confirmations and action results"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(standbyChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }
}
