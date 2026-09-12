package com.example.actions.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.actions.ActionResult
import com.example.service.FridayAccessibilityService

class SettingsActionTool : FridayTool {
    override val id = "SETTINGS_ACTION"
    override val name = "Android System Settings"
    override val description = "Navigates directly to any Android system settings screen"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val settingType = (parameters["setting"] ?: parameters["type"] ?: parameters["category"] ?: "general").lowercase().trim()

        val (action, spoken) = when {
            settingType.contains("wifi") || settingType.contains("wi-fi") || settingType.contains("internet") || settingType.contains("network") -> {
                Pair(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings, Boss.")
            }
            settingType.contains("bluetooth") || settingType.contains("bt") -> {
                Pair(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings, Boss.")
            }
            settingType.contains("display") || settingType.contains("screen") || settingType.contains("brightness") -> {
                Pair(Settings.ACTION_DISPLAY_SETTINGS, "Opening Display settings, Boss.")
            }
            settingType.contains("sound") || settingType.contains("volume") || settingType.contains("audio") || settingType.contains("ringtone") -> {
                Pair(Settings.ACTION_SOUND_SETTINGS, "Opening Sound settings, Boss.")
            }
            settingType.contains("battery") || settingType.contains("power") -> {
                Pair(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Opening Battery settings, Boss.")
            }
            settingType.contains("notification") -> {
                Pair(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, "Opening Notification settings, Boss.")
            }
            settingType.contains("accessibility") -> {
                Pair(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Opening Accessibility settings, Boss.")
            }
            settingType.contains("airplane") || settingType.contains("flight") -> {
                Pair(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Opening Airplane Mode settings, Boss.")
            }
            settingType.contains("app") || settingType.contains("permission") -> {
                Pair(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "Opening Application settings, Boss.")
            }
            settingType.contains("storage") || settingType.contains("space") -> {
                Pair(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Opening Storage settings, Boss.")
            }
            settingType.contains("date") || settingType.contains("time") -> {
                Pair(Settings.ACTION_DATE_SETTINGS, "Opening Date and Time settings, Boss.")
            }
            settingType.contains("security") || settingType.contains("lock") -> {
                Pair(Settings.ACTION_SECURITY_SETTINGS, "Opening Security settings, Boss.")
            }
            settingType.contains("hotspot") || settingType.contains("tether") -> {
                Pair(Settings.ACTION_WIRELESS_SETTINGS, "Opening Hotspot and Tethering settings, Boss.")
            }
            settingType.contains("location") || settingType.contains("gps") -> {
                Pair(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Opening Location settings, Boss.")
            }
            else -> {
                Pair(Settings.ACTION_SETTINGS, "Opening Settings, Boss.")
            }
        }

        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened $settingType settings", spokenDetail = spoken)
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(fallback)
                ActionResult.Success("Opened main settings", spokenDetail = "Opening device settings for you, Boss.")
            } catch (err: Exception) {
                ActionResult.Failure("Failed to open settings: ${err.message}")
            }
        }
    }
}

class SystemPanelTool : FridayTool {
    override val id = "SYSTEM_PANEL_ACTION"
    override val name = "System Shade & Quick Settings"
    override val description = "Opens notification drawer or quick settings panel via Accessibility"
    override val requiresAccessibility = true

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, please enable the FRIDAY Accessibility Service in Android Settings to open system panels."
            )

        val panel = (parameters["panel"] ?: parameters["type"] ?: "notifications").lowercase()
        val globalAction = if (panel.contains("quick") || panel.contains("tile")) {
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        } else {
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }

        val success = service.performGlobal(globalAction)
        return if (success) {
            val spoken = if (globalAction == AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) {
                "Here are your quick settings, Boss."
            } else {
                "Opening your notifications, Boss."
            }
            ActionResult.Success("Opened $panel panel", spokenDetail = spoken)
        } else {
            ActionResult.Failure("Failed to expand $panel shade")
        }
    }
}
