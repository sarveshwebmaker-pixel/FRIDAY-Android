package com.example.actions.tools

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.actions.ActionResult

class WifiControlTool : FridayTool {
    override val id = "WIFI_CONTROL"
    override val name = "Wi-Fi Controller"
    override val description = "Checks or toggles device Wi-Fi connectivity"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val action = (parameters["action"] ?: parameters["state"] ?: "status").lowercase().trim()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ActionResult.Failure("Wi-Fi service unavailable")

        val isWifiEnabled = wifiManager.isWifiEnabled

        return when (action) {
            "status", "check", "query" -> {
                val stateStr = if (isWifiEnabled) "enabled" else "disabled"
                ActionResult.Success(
                    message = "Wi-Fi is currently $stateStr",
                    spokenDetail = "Wi-Fi is currently $stateStr, Boss."
                )
            }
            "on", "enable", "true" -> {
                if (isWifiEnabled) {
                    return ActionResult.Success("Wi-Fi is already on", spokenDetail = "Wi-Fi is already turned on, Boss.")
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    ActionResult.Success("Wi-Fi turned on", spokenDetail = "Turning Wi-Fi on, Boss.")
                } else {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(panelIntent)
                        ActionResult.Success("Opened Wi-Fi panel", spokenDetail = "Here is your Wi-Fi control panel, Boss.")
                    } catch (_: Exception) {
                        val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(settingsIntent)
                        ActionResult.Success("Opened Wi-Fi settings", spokenDetail = "Opening Wi-Fi settings, Boss.")
                    }
                }
            }
            "off", "disable", "false" -> {
                if (!isWifiEnabled) {
                    return ActionResult.Success("Wi-Fi is already off", spokenDetail = "Wi-Fi is already off, Boss.")
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = false
                    ActionResult.Success("Wi-Fi turned off", spokenDetail = "Turning Wi-Fi off, Boss.")
                } else {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(panelIntent)
                        ActionResult.Success("Opened Wi-Fi panel", spokenDetail = "Here is your Wi-Fi control panel, Boss.")
                    } catch (_: Exception) {
                        val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(settingsIntent)
                        ActionResult.Success("Opened Wi-Fi settings", spokenDetail = "Opening Wi-Fi settings, Boss.")
                    }
                }
            }
            else -> ActionResult.Failure("Unknown Wi-Fi action: $action")
        }
    }
}

class BluetoothControlTool : FridayTool {
    override val id = "BLUETOOTH_CONTROL"
    override val name = "Bluetooth Controller"
    override val description = "Checks or manages Bluetooth state"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val action = (parameters["action"] ?: parameters["state"] ?: "status").lowercase().trim()
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ActionResult.Failure("Bluetooth not available on this device", "Bluetooth hardware is not available on this device, Boss.")

        val isEnabled = bluetoothAdapter.isEnabled

        return when (action) {
            "status", "check", "query" -> {
                val stateStr = if (isEnabled) "turned on" else "turned off"
                ActionResult.Success(
                    message = "Bluetooth is $stateStr",
                    spokenDetail = "Bluetooth is currently $stateStr, Boss."
                )
            }
            "on", "enable", "true" -> {
                if (isEnabled) {
                    return ActionResult.Success("Bluetooth is already on", spokenDetail = "Bluetooth is already turned on, Boss.")
                }
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ActionResult.Success("Requested Bluetooth enable", spokenDetail = "Enabling Bluetooth, Boss.")
                } catch (_: Exception) {
                    val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    ActionResult.Success("Opened Bluetooth settings", spokenDetail = "Opening Bluetooth settings, Boss.")
                }
            }
            "off", "disable", "false" -> {
                if (!isEnabled) {
                    return ActionResult.Success("Bluetooth is already off", spokenDetail = "Bluetooth is already turned off, Boss.")
                }
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(settingsIntent)
                    ActionResult.Success("Opened Bluetooth settings", spokenDetail = "Opening Bluetooth settings to toggle off, Boss.")
                } catch (e: Exception) {
                    ActionResult.Failure("Failed to open Bluetooth settings: ${e.message}")
                }
            }
            else -> ActionResult.Failure("Unknown Bluetooth action: $action")
        }
    }
}

class HotspotControlTool : FridayTool {
    override val id = "HOTSPOT_CONTROL"
    override val name = "Mobile Hotspot Controller"
    override val description = "Opens hotspot configuration or wireless tethering settings"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened hotspot settings", spokenDetail = "Opening hotspot and tethering settings, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open hotspot settings: ${e.message}")
        }
    }
}

class DeviceStatusTool : FridayTool {
    override val id = "DEVICE_STATUS"
    override val name = "Comprehensive Device Diagnostics"
    override val description = "Queries real-time battery, network, OS version, device model, and storage"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = (parameters["query"] ?: parameters["category"] ?: "all").lowercase()

        // Battery
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } else false

        // Network
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connMgr?.activeNetwork
        val caps = connMgr?.getNetworkCapabilities(activeNetwork)
        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline"
        }

        // Device & OS
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val bytesTotal = stat.blockCountLong * stat.blockSizeLong
        val gbFree = bytesAvailable / (1024 * 1024 * 1024)
        val gbTotal = bytesTotal / (1024 * 1024 * 1024)

        if (query.contains("battery")) {
            val chargeStr = if (isCharging) " and currently charging" else ""
            return ActionResult.Success(
                message = "Battery: $batteryLevel%$chargeStr",
                spokenDetail = "Your battery is at $batteryLevel%$chargeStr, Boss."
            )
        }

        if (query.contains("storage") || query.contains("space")) {
            return ActionResult.Success(
                message = "Storage: ${gbFree}GB free out of ${gbTotal}GB",
                spokenDetail = "You have ${gbFree} gigabytes free out of ${gbTotal} gigabytes total storage, Boss."
            )
        }

        if (query.contains("android") || query.contains("os") || query.contains("version")) {
            return ActionResult.Success(
                message = "Android $androidVersion (API $sdkInt)",
                spokenDetail = "You are running Android $androidVersion on API level $sdkInt, Boss."
            )
        }

        if (query.contains("phone") || query.contains("model") || query.contains("device")) {
            return ActionResult.Success(
                message = "$manufacturer $model",
                spokenDetail = "You are using a $manufacturer $model, Boss."
            )
        }

        val chargingText = if (isCharging) "charging" else "not charging"
        val fullReport = "Battery is at $batteryLevel% ($chargingText). Connected via $networkType. Running Android $androidVersion on your $manufacturer $model with ${gbFree}GB storage free."
        return ActionResult.Success(
            message = fullReport,
            spokenDetail = fullReport
        )
    }
}
