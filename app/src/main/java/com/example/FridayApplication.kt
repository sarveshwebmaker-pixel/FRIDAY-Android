package com.example

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle

class FridayApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val CHANNEL_ID_STANDBY = "friday_standby_channel"
        const val CHANNEL_ID_ALERTS = "friday_alerts_channel"
        lateinit var instance: FridayApplication
            private set
    }

    private var runningActivities = 0
    val isAppInForeground: Boolean
        get() = runningActivities > 0

    lateinit var settingsRepo: com.example.settings.FridaySettingsRepository
        private set

    lateinit var fridayCore: com.example.core.FridayCore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        settingsRepo = com.example.settings.FridaySettingsRepository(applicationContext)
        fridayCore = com.example.core.FridayCore(applicationContext, settingsRepo)
        createNotificationChannels()
    }

    override fun onActivityStarted(activity: Activity) {
        runningActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        runningActivities = (runningActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

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
