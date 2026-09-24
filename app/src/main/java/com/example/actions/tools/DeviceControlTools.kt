package com.example.actions.tools

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.example.actions.ActionResult
import com.example.security.ActionRiskLevel
import com.example.service.FridayAccessibilityService
import com.example.service.FridayNotificationListenerService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlashlightTool(override val id: String = "TOGGLE_FLASHLIGHT") : FridayTool {
    override val name = "Flashlight Controller"
    override val description = "Turns the device flashlight / camera torch on or off"

    companion object {
        private const val TAG = "FlashlightTool"
        var isTorchOn = false
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val enableStr = parameters["state"] ?: parameters["enable"] ?: "true"
        val enable = enableStr.equals("true", ignoreCase = true) || enableStr.equals("on", ignoreCase = true)

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            } ?: cameraManager?.cameraIdList?.firstOrNull()

            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                isTorchOn = enable
                val msg = if (enable) "Flashlight turned on" else "Flashlight turned off"
                val spoken = if (enable) "Flashlight is on." else "Flashlight is off."
                ActionResult.Success(msg, spokenDetail = spoken)
            } else {
                ActionResult.Failure(
                    error = "Flashlight hardware unavailable",
                    userMessage = "Flashlight doesn't seem available on this phone."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            ActionResult.Failure(
                error = "Flashlight error: ${e.message}",
                userMessage = "Couldn't toggle the flashlight."
            )
        }
    }
}

class FlashlightStrobeTool(override val id: String = "FLASHLIGHT_STROBE") : FridayTool {
    override val name = "Emergency SOS & Strobe Light"
    override val description = "Flashes camera torch in strobe / SOS pattern for emergency signaling"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val count = parameters["flashes"]?.toIntOrNull()?.coerceIn(1, 15) ?: 5
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) { false }
            } ?: cameraManager?.cameraIdList?.firstOrNull()

            if (cameraManager != null && cameraId != null) {
                for (i in 0 until count) {
                    cameraManager.setTorchMode(cameraId, true)
                    delay(250)
                    cameraManager.setTorchMode(cameraId, false)
                    delay(200)
                }
                ActionResult.Success("SOS strobe signal completed", spokenDetail = "Flashlight strobe completed, Boss.")
            } else {
                ActionResult.Failure("Flashlight hardware unavailable", "Torch is not available.")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Strobe failed: ${e.message}", "Couldn't activate strobe.")
        }
    }
}

class VolumeTool(override val id: String = "ADJUST_VOLUME") : FridayTool {
    override val name = "Universal Volume Controller"
    override val description = "Adjusts device media, ringtone, alarm, call, and notification audio volume"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Audio service unavailable", "Audio controls aren't responding right now.")

        val direction = (parameters["direction"] ?: parameters["action"] ?: "up").lowercase()
        val levelStr = parameters["level"]?.lowercase()?.trim()
        val streamParam = parameters["stream"]?.lowercase()?.trim() ?: "music"

        val streamType = when {
            streamParam.contains("ring") -> AudioManager.STREAM_RING
            streamParam.contains("alarm") -> AudioManager.STREAM_ALARM
            streamParam.contains("notification") -> AudioManager.STREAM_NOTIFICATION
            streamParam.contains("voice") || streamParam.contains("call") -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val streamLabel = when (streamType) {
            AudioManager.STREAM_RING -> "Ringtone"
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            AudioManager.STREAM_VOICE_CALL -> "Call"
            else -> "Media"
        }

        return try {
            val maxVol = audioManager.getStreamMaxVolume(streamType)

            if (levelStr != null) {
                val targetPercent = when (levelStr) {
                    "max", "maximum", "full", "100" -> 100
                    "min", "minimum", "zero", "0" -> 0
                    "half", "50" -> 50
                    else -> levelStr.replace("%", "").toIntOrNull()?.coerceIn(0, 100) ?: 50
                }
                val targetVol = (maxVol * (targetPercent / 100.0)).toInt()
                audioManager.setStreamVolume(streamType, targetVol, AudioManager.FLAG_SHOW_UI)
                return ActionResult.Success("$streamLabel volume set to $targetPercent%", spokenDetail = "$streamLabel volume set to $targetPercent%.")
            }

            when (direction) {
                "up", "raise", "higher", "louder" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ActionResult.Success("$streamLabel volume raised", spokenDetail = "$streamLabel volume turned up.")
                }
                "down", "lower", "quieter", "soft" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    ActionResult.Success("$streamLabel volume lowered", spokenDetail = "$streamLabel volume turned down.")
                }
                "mute", "silent" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                    }
                    ActionResult.Success("$streamLabel muted", spokenDetail = "$streamLabel muted.")
                }
                "unmute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    }
                    ActionResult.Success("$streamLabel unmuted", spokenDetail = "$streamLabel unmuted.")
                }
                else -> ActionResult.Failure("Unknown volume direction: $direction")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Volume adjustment failed: ${e.message}")
        }
    }
}

class RingerModeTool(override val id: String = "RINGER_MODE") : FridayTool {
    override val name = "Ringer & Do Not Disturb (DND) Controller"
    override val description = "Sets phone to Normal / Ring, Vibrate, Silent, or Do Not Disturb mode"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Audio service unavailable")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val mode = (parameters["mode"] ?: parameters["action"] ?: "vibrate").lowercase().trim()

        return try {
            when {
                mode.contains("vibrate") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ActionResult.Success("Phone set to vibrate", spokenDetail = "Phone is now on vibrate, Boss.")
                }
                mode.contains("silent") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        ActionResult.Success("Phone set to vibrate (grant DND for full silent)", spokenDetail = "Set to vibrate. To mute completely, grant Do Not Disturb access in settings, Boss.")
                    } else {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        ActionResult.Success("Phone silenced", spokenDetail = "Phone is now silenced, Boss.")
                    }
                }
                mode.contains("normal") || mode.contains("ring") || mode.contains("sound") || mode.contains("unmute") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    ActionResult.Success("Ringer turned on", spokenDetail = "Ringer is back on, Boss.")
                }
                mode.contains("dnd") || mode.contains("do_not_disturb") || mode.contains("disturb") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            val enable = !mode.contains("off") && !mode.contains("disable")
                            val filter = if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                            notificationManager.setInterruptionFilter(filter)
                            val statusStr = if (enable) "Do Not Disturb enabled" else "Do Not Disturb turned off"
                            ActionResult.Success(statusStr, spokenDetail = if (enable) "Do Not Disturb is on, Boss." else "Do Not Disturb is off, Boss.")
                        } else {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            ActionResult.PermissionRequired("NOTIFICATION_POLICY", "Boss, please grant Do Not Disturb access in settings.")
                        }
                    } else {
                        ActionResult.NotSupported("DND", "Do Not Disturb API requires Android 6.0+")
                    }
                }
                else -> {
                    val currentMode = when (audioManager.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        else -> "Normal (Ringing)"
                    }
                    ActionResult.Success("Ringer status: $currentMode", spokenDetail = "Phone ringer is currently set to $currentMode, Boss.")
                }
            }
        } catch (e: Exception) {
            ActionResult.Failure("Failed changing ringer mode: ${e.message}")
        }
    }
}

class ScreenOrientationTool(override val id: String = "SCREEN_ORIENTATION") : FridayTool {
    override val name = "Screen Auto-Rotation Controller"
    override val description = "Turns screen auto-rotation on, off, or locks rotation"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val state = (parameters["state"] ?: parameters["action"] ?: "toggle").lowercase().trim()
        val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true

        val currentRotation = try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        } catch (_: Exception) { 1 }

        val targetRotation = when {
            state.contains("on") || state.contains("auto") || state.contains("enable") -> 1
            state.contains("off") || state.contains("lock") || state.contains("disable") -> 0
            else -> if (currentRotation == 1) 0 else 1
        }

        if (canWrite) {
            return try {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, targetRotation)
                val status = if (targetRotation == 1) "Auto-rotation enabled" else "Screen rotation locked"
                val spoken = if (targetRotation == 1) "Auto-rotate is on, Boss." else "Screen rotation locked, Boss."
                ActionResult.Success(status, spokenDetail = spoken)
            } catch (e: Exception) {
                ActionResult.Failure("Failed setting rotation: ${e.message}")
            }
        }

        // Fallback: open display settings
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened display settings for rotation", spokenDetail = "Opening display settings to toggle screen rotation, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Cannot open display settings: ${e.message}")
        }
    }
}

class ScreenTimeoutTool(override val id: String = "SCREEN_TIMEOUT") : FridayTool {
    override val name = "Screen Sleep & Timeout Controller"
    override val description = "Sets device screen sleep timeout in seconds or minutes"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val rawTime = parameters["time"] ?: parameters["duration"] ?: parameters["seconds"] ?: "60"
        val seconds = when {
            rawTime.contains("min") -> (rawTime.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1) * 60
            rawTime.contains("sec") -> rawTime.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 30
            else -> rawTime.toIntOrNull() ?: 60
        }.coerceIn(15, 600)

        val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true

        if (canWrite) {
            return try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
                val display = if (seconds >= 60) "${seconds / 60} minute(s)" else "$seconds seconds"
                ActionResult.Success("Screen timeout set to $display", spokenDetail = "Screen sleep timeout set to $display, Boss.")
            } catch (e: Exception) {
                ActionResult.Failure("Failed setting screen timeout: ${e.message}")
            }
        }

        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened display settings for screen timeout", spokenDetail = "Opening display settings for screen sleep timeout, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Cannot open display settings: ${e.message}")
        }
    }
}

class ScreenshotTool(override val id: String = "TAKE_SCREENSHOT") : FridayTool {
    override val name = "Instant Screenshot Capture"
    override val description = "Captures an instant device screenshot via Android Accessibility"
    override val requiresAccessibility = true

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, I need the FRIDAY Accessibility Service enabled to take screenshots hands-free."
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            return if (success) {
                ActionResult.Success("Screenshot captured", spokenDetail = "Screenshot captured, Boss.")
            } else {
                ActionResult.Failure("System did not accept screenshot trigger")
            }
        } else {
            return ActionResult.NotSupported("SCREENSHOT", "Voice screenshot requires Android 9.0 (Pie) or newer, Boss.")
        }
    }
}

class LockScreenTool(override val id: String = "LOCK_SCREEN") : FridayTool {
    override val name = "Device Screen Lock"
    override val description = "Locks the device screen instantly via Android Accessibility"
    override val requiresAccessibility = true

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, I need the FRIDAY Accessibility Service enabled to lock your phone hands-free."
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            return if (success) {
                ActionResult.Success("Screen locked", spokenDetail = "Locking phone, Boss.")
            } else {
                ActionResult.Failure("Failed locking screen")
            }
        } else {
            return ActionResult.NotSupported("LOCK_SCREEN", "Hands-free screen locking requires Android 9.0 or newer, Boss.")
        }
    }
}

class PowerMenuTool(override val id: String = "POWER_MENU") : FridayTool {
    override val name = "Power Dialog & Restart Menu"
    override val description = "Opens the device power off / restart menu dialog"
    override val requiresAccessibility = true

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, please enable FRIDAY Accessibility Service to open the power menu."
            )

        val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        return if (success) {
            ActionResult.Success("Power dialog opened", spokenDetail = "Here is your power menu, Boss.")
        } else {
            ActionResult.Failure("Failed to open power dialog")
        }
    }
}

class SplitScreenTool(override val id: String = "SPLIT_SCREEN") : FridayTool {
    override val name = "Split Screen & Multi-Window"
    override val description = "Toggles multi-window split screen mode for multitasking"
    override val requiresAccessibility = true

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, please enable FRIDAY Accessibility Service to toggle split screen."
            )

        val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        return if (success) {
            ActionResult.Success("Split screen toggled", spokenDetail = "Split screen toggled, Boss.")
        } else {
            ActionResult.Failure("Split screen not supported on this view")
        }
    }
}

class VibratorTool(override val id: String = "VIBRATE_DEVICE") : FridayTool {
    override val name = "Haptic Feedback & Device Vibration"
    override val description = "Triggers phone vibration motor for tactile feedback"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val durationMs = parameters["durationMs"]?.toLongOrNull() ?: 500L
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        return try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
                ActionResult.Success("Device vibrated", spokenDetail = "Vibrated.")
            } else {
                ActionResult.Failure("Vibration motor not available")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Failed vibrating device: ${e.message}")
        }
    }
}

class DarkModeTool(override val id: String = "DARK_MODE") : FridayTool {
    override val name = "Dark Mode & Night Theme Controller"
    override val description = "Guides user to Android Dark Theme / Night Mode display settings"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened display settings for Dark theme", spokenDetail = "Opening display settings for Dark mode, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed opening display settings: ${e.message}")
        }
    }
}

class MediaControlTool(override val id: String = "MEDIA_CONTROL") : FridayTool {
    override val name = "Media Playback Controller"
    override val description = "Controls media playback (play, pause, next, previous, toggle) across Spotify, YouTube Music, and players"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val command = (parameters["command"] ?: parameters["action"] ?: "play").lowercase().trim()

        // 1. Try MediaSessionManager first if NotificationListenerService is active
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val notificationListener = ComponentName(context, FridayNotificationListenerService::class.java)
            val controllers = sessionManager?.getActiveSessions(notificationListener)

            if (!controllers.isNullOrEmpty()) {
                val activeController = controllers.firstOrNull {
                    val state = it.playbackState?.state
                    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                } ?: controllers.first()

                val appName = activeController.packageName.substringAfterLast('.')
                val transport = activeController.transportControls

                when (command) {
                    "pause", "stop" -> {
                        transport.pause()
                        return ActionResult.Success("Paused playback on $appName", spokenDetail = "Paused media on $appName, Boss.")
                    }
                    "play", "resume", "start" -> {
                        transport.play()
                        return ActionResult.Success("Resumed playback on $appName", spokenDetail = "Playing media on $appName, Boss.")
                    }
                    "next", "skip" -> {
                        transport.skipToNext()
                        return ActionResult.Success("Skipped track on $appName", spokenDetail = "Skipping to next track, Boss.")
                    }
                    "previous", "prev", "back" -> {
                        transport.skipToPrevious()
                        return ActionResult.Success("Previous track on $appName", spokenDetail = "Playing previous track, Boss.")
                    }
                    "toggle" -> {
                        val isPlaying = activeController.playbackState?.state == PlaybackState.STATE_PLAYING
                        if (isPlaying) transport.pause() else transport.play()
                        val stateStr = if (isPlaying) "Paused" else "Playing"
                        return ActionResult.Success("$stateStr playback on $appName", spokenDetail = "$stateStr media, Boss.")
                    }
                }
            }
        } catch (_: Exception) {
            // Permission or service not active, fallback to AudioManager media key events
        }

        // 2. Fallback to system-wide media key events
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Audio manager unavailable")

        val keycode = when (command) {
            "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play", "resume", "start" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keycode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            val spoken = when (command) {
                "pause", "stop" -> "Media paused, Boss."
                "play", "resume", "start" -> "Playing media, Boss."
                "next", "skip" -> "Skipping to next track, Boss."
                "previous", "prev" -> "Playing previous track, Boss."
                else -> "Media updated, Boss."
            }
            ActionResult.Success("Media command '$command' dispatched", spokenDetail = spoken)
        } catch (e: Exception) {
            ActionResult.Failure("Failed to dispatch media command: ${e.message}")
        }
    }
}

class BrightnessTool : FridayTool {
    override val id = "SET_BRIGHTNESS"
    override val name = "Screen Brightness Controller"
    override val description = "Adjusts device screen brightness or opens Display settings"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true

        val levelStr = parameters["level"]?.lowercase()?.trim()
        val currentBrightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) { 128 }

        val targetPercent: Int? = when {
            levelStr == "max" || levelStr == "maximum" || levelStr == "full" -> 100
            levelStr == "min" || levelStr == "minimum" -> 10
            levelStr == "half" -> 50
            levelStr == "increase" || levelStr == "up" || levelStr == "higher" -> ((currentBrightness / 255.0) * 100).toInt() + 20
            levelStr == "decrease" || levelStr == "down" || levelStr == "lower" -> ((currentBrightness / 255.0) * 100).toInt() - 20
            levelStr != null -> levelStr.replace("%", "").toIntOrNull()
            else -> null
        }

        if (canWrite && targetPercent != null) {
            val clamped = targetPercent.coerceIn(5, 100)
            val targetBrightness = (255 * (clamped / 100.0)).toInt()
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
            return ActionResult.Success("Brightness adjusted to $clamped%", spokenDetail = "Brightness set to $clamped%.")
        }

        // If direct write not permitted by Android, open Display Settings directly
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened display brightness settings", spokenDetail = "Opening display settings.")
        } catch (e: Exception) {
            ActionResult.Failure("Cannot open display settings: ${e.message}")
        }
    }
}

class BatteryInfoTool : FridayTool {
    override val id = "GET_BATTERY_INFO"
    override val name = "Battery Information Reader"
    override val description = "Reads battery capacity percentage and charging status"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } else false

        return if (level >= 0) {
            val chargingText = if (isCharging) " and charging" else ""
            ActionResult.Success(
                message = "Battery is at $level%$chargingText",
                spokenDetail = "Battery is sitting at $level%$chargingText."
            )
        } else {
            ActionResult.Failure("Unable to read battery level", "Can't read the battery status right now.")
        }
    }
}

class DateTimeTool : FridayTool {
    override val id = "GET_DATE_TIME"
    override val name = "Date & Time Reader"
    override val description = "Provides the current time, day, and date"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val sdf = SimpleDateFormat("EEEE, MMMM d, h:mm a", Locale.getDefault())
        val formatted = sdf.format(Date())
        return ActionResult.Success("It is currently $formatted", spokenDetail = "It's $formatted.")
    }
}

