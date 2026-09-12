package com.example.core

enum class BatteryMode(
    val title: String,
    val description: String,
    val badgeLabel: String
) {
    NORMAL(
        title = "Normal",
        description = "Standard standby with intermittent low-power wake check.",
        badgeLabel = "NORMAL"
    ),
    BATTERY_SAVER(
        title = "Battery Saver",
        description = "Conserves battery. Background wake word disabled; tap orb to speak.",
        badgeLabel = "SAVER"
    ),
    PERFORMANCE(
        title = "Charging / Performance",
        description = "Maximum responsiveness and continuous standby listening.",
        badgeLabel = "PERF"
    )
}
