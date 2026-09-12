package com.example.actions.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.example.actions.ActionResult
import com.example.security.ActionRiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlashlightTool : FridayTool {
    override val id = "TOGGLE_FLASHLIGHT"
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

class VolumeTool : FridayTool {
    override val id = "ADJUST_VOLUME"
    override val name = "Volume Controller"
    override val description = "Adjusts device music / media stream volume (up, down, mute, level)"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Audio service unavailable", "Audio controls aren't responding right now.")

        val direction = (parameters["direction"] ?: parameters["action"] ?: "up").lowercase()
        val levelStr = parameters["level"]

        return try {
            if (levelStr != null) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetPercent = levelStr.toIntOrNull()?.coerceIn(0, 100) ?: 50
                val targetVol = (maxVol * (targetPercent / 100.0)).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                return ActionResult.Success("Volume set to $targetPercent%", spokenDetail = "Volume set to $targetPercent%.")
            }

            when (direction) {
                "up", "raise", "higher", "louder" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ActionResult.Success("Volume raised", spokenDetail = "Volume turned up.")
                }
                "down", "lower", "quieter", "soft" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    ActionResult.Success("Volume lowered", spokenDetail = "Volume turned down.")
                }
                "mute", "silent" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    }
                    ActionResult.Success("Volume muted", spokenDetail = "Muted.")
                }
                "unmute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    }
                    ActionResult.Success("Volume unmuted", spokenDetail = "Unmuted.")
                }
                else -> ActionResult.Failure("Unknown volume direction: $direction")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Volume adjustment failed: ${e.message}")
        }
    }
}

class MediaControlTool : FridayTool {
    override val id = "MEDIA_CONTROL"
    override val name = "Media Playback Controller"
    override val description = "Controls media playback (play, pause, next, previous, toggle)"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Audio manager unavailable")

        val command = (parameters["command"] ?: parameters["action"] ?: "play").lowercase()

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
                "pause", "stop" -> "Media paused."
                "play", "resume", "start" -> "Playing media."
                "next", "skip" -> "Skipping to next track."
                "previous", "prev" -> "Playing previous track."
                else -> "Media updated."
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

        val levelStr = parameters["level"]
        if (canWrite && levelStr != null) {
            val percent = levelStr.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val targetBrightness = (255 * (percent / 100.0)).toInt()
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
            return ActionResult.Success("Brightness adjusted to $percent%", spokenDetail = "Brightness set to $percent%.")
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
