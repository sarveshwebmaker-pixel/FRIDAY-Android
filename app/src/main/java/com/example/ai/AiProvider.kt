package com.example.ai

import com.example.core.BatteryMode

interface AiProvider {
    val name: String
    val isCloudConnected: Boolean
    suspend fun processCommand(command: String, batteryMode: BatteryMode): FridayAiResult
}
