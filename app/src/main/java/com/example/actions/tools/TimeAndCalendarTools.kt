package com.example.actions.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.example.actions.ActionResult
import java.util.Calendar

class TimerTool : FridayTool {
    override val id = "SET_TIMER"
    override val name = "Countdown Timer"
    override val description = "Sets and starts a countdown timer"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val secondsStr = parameters["durationSeconds"] ?: parameters["seconds"] ?: parameters["length"] ?: "60"
        val seconds = secondsStr.toIntOrNull() ?: 60
        val label = parameters["label"] ?: parameters["message"] ?: "FRIDAY Timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            val durationText = when {
                seconds >= 3600 -> "${seconds / 3600} hour${if (seconds / 3600 > 1) "s" else ""}"
                seconds >= 60 -> "${seconds / 60} minute${if (seconds / 60 > 1) "s" else ""}"
                else -> "$seconds seconds"
            }
            ActionResult.Success(
                message = "Timer set for $durationText",
                spokenDetail = "Timer set for $durationText, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Could not set timer: ${e.message}")
        }
    }
}

class AlarmTool : FridayTool {
    override val id = "SET_ALARM"
    override val name = "Alarm Clock"
    override val description = "Sets an alarm for a specific hour and minute"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val hour = parameters["hour"]?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = parameters["minute"]?.toIntOrNull() ?: 0
        val message = parameters["message"] ?: parameters["label"] ?: "FRIDAY Alarm"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour >= 12) "PM" else "AM"
            val formattedTime = String.format("%d:%02d %s", displayHour, minute, amPm)
            ActionResult.Success(
                message = "Alarm set for $formattedTime",
                spokenDetail = "Alarm set for $formattedTime, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Could not set alarm: ${e.message}")
        }
    }
}

class ShowTimersAlarmsTool : FridayTool {
    override val id = "SHOW_TIMERS_ALARMS"
    override val name = "Alarms & Timers Viewer"
    override val description = "Shows all current alarms and active timers"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val mode = parameters["mode"] ?: "alarm"
        val action = if (mode.contains("timer", ignoreCase = true)) {
            AlarmClock.ACTION_SHOW_TIMERS
        } else {
            AlarmClock.ACTION_SHOW_ALARMS
        }

        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened clock app", spokenDetail = "Here are your alarms and timers, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open clock app: ${e.message}")
        }
    }
}

class CalendarTool : FridayTool {
    override val id = "CALENDAR_EVENT"
    override val name = "Calendar Event Planner"
    override val description = "Adds or opens a calendar reminder / event"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val title = parameters["title"] ?: parameters["event"] ?: parameters["reminder"] ?: ""
        val desc = parameters["description"] ?: ""

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            if (title.isNotBlank()) putExtra(CalendarContract.Events.TITLE, title)
            if (desc.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, desc)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success(
                message = "Opened calendar event",
                spokenDetail = if (title.isNotBlank()) "Adding '$title' to your calendar, Boss." else "Opening your calendar, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open calendar: ${e.message}")
        }
    }
}
