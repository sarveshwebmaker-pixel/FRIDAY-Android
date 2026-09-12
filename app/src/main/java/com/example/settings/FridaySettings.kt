package com.example.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.core.BatteryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FridaySettingsData(
    val batteryMode: BatteryMode = BatteryMode.NORMAL,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val wakeWordEnabled: Boolean = true,
    val floatingBubbleEnabled: Boolean = false,
    val lockScreenEnabled: Boolean = true,
    val voiceIdentityThreshold: Float = 0.80f,
    val notificationEnabled: Boolean = true,
    val firstRunCompleted: Boolean = false,
    val conversationTimeoutSeconds: Int = 8,
    val customApiKey: String = ""
)

class FridaySettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FridaySettingsData> = _settings.asStateFlow()

    private fun loadSettings(): FridaySettingsData {
        val batteryModeName = prefs.getString("battery_mode", BatteryMode.NORMAL.name) ?: BatteryMode.NORMAL.name
        val mode = try {
            BatteryMode.valueOf(batteryModeName)
        } catch (_: Exception) {
            BatteryMode.NORMAL
        }

        return FridaySettingsData(
            batteryMode = mode,
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            speechPitch = prefs.getFloat("speech_pitch", 1.0f),
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", true),
            floatingBubbleEnabled = prefs.getBoolean("floating_bubble_enabled", false),
            lockScreenEnabled = prefs.getBoolean("lock_screen_enabled", true),
            voiceIdentityThreshold = prefs.getFloat("voice_identity_threshold", 0.80f),
            notificationEnabled = prefs.getBoolean("notification_enabled", true),
            firstRunCompleted = prefs.getBoolean("first_run_completed", false),
            conversationTimeoutSeconds = prefs.getInt("conv_timeout_sec", 8),
            customApiKey = prefs.getString("custom_api_key", "") ?: ""
        )
    }

    fun updateConversationTimeout(seconds: Int) {
        val clamped = seconds.coerceIn(4, 30)
        prefs.edit().putInt("conv_timeout_sec", clamped).apply()
        _settings.value = _settings.value.copy(conversationTimeoutSeconds = clamped)
    }

    fun updateBatteryMode(mode: BatteryMode) {
        prefs.edit().putString("battery_mode", mode.name).apply()
        _settings.value = _settings.value.copy(batteryMode = mode)
    }

    fun updateSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
        _settings.value = _settings.value.copy(speechRate = rate)
    }

    fun updateSpeechPitch(pitch: Float) {
        prefs.edit().putFloat("speech_pitch", pitch).apply()
        _settings.value = _settings.value.copy(speechPitch = pitch)
    }

    fun updateWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
        _settings.value = _settings.value.copy(wakeWordEnabled = enabled)
    }

    fun updateFloatingBubbleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("floating_bubble_enabled", enabled).apply()
        _settings.value = _settings.value.copy(floatingBubbleEnabled = enabled)
    }

    fun updateLockScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("lock_screen_enabled", enabled).apply()
        _settings.value = _settings.value.copy(lockScreenEnabled = enabled)
    }

    fun updateVoiceIdentityThreshold(threshold: Float) {
        prefs.edit().putFloat("voice_identity_threshold", threshold).apply()
        _settings.value = _settings.value.copy(voiceIdentityThreshold = threshold)
    }

    fun updateNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notification_enabled", enabled).apply()
        _settings.value = _settings.value.copy(notificationEnabled = enabled)
    }

    fun updateFirstRunCompleted(completed: Boolean) {
        prefs.edit().putBoolean("first_run_completed", completed).apply()
        _settings.value = _settings.value.copy(firstRunCompleted = completed)
    }

    fun updateCustomApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key).apply()
        _settings.value = _settings.value.copy(customApiKey = key)
    }
}
