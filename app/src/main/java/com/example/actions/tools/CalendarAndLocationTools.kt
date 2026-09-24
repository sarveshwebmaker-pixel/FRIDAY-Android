package com.example.actions.tools

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.actions.ActionResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LocationTool : FridayTool {
    override val id = "LOCATION"
    override val name = "Live GPS Location Provider"
    override val description = "Retrieves real GPS coordinates and street address without fabrication"
    override val requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return ActionResult.PermissionRequired(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "Boss, I require location permission to determine where you are."
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ActionResult.Failure("Location services unavailable")

        var bestLocation: Location? = null
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
        } catch (e: SecurityException) {
            return ActionResult.PermissionRequired(Manifest.permission.ACCESS_FINE_LOCATION, "Location permission is required.")
        } catch (_: Exception) {}

        if (bestLocation == null) {
            return ActionResult.Failure(
                error = "No location fix acquired",
                userMessage = "I cannot determine your current location. Please verify that GPS is turned on, Boss."
            )
        }

        val lat = bestLocation.latitude
        val lng = bestLocation.longitude

        var addressText = ""
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    addressText = listOfNotNull(addr.thoroughfare, addr.locality, addr.adminArea, addr.countryName).joinToString(", ")
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    addressText = listOfNotNull(addr.thoroughfare, addr.locality, addr.adminArea, addr.countryName).joinToString(", ")
                }
            }
        } catch (_: Exception) {}

        val locationReport = if (addressText.isNotBlank()) {
            "You are at $addressText"
        } else {
            String.format(Locale.US, "You are at coordinates %.4f, %.4f", lat, lng)
        }

        return ActionResult.Success(
            message = locationReport,
            spokenDetail = "$locationReport, Boss."
        )
    }
}

class CalendarReadTool : FridayTool {
    override val id = "CALENDAR_READ"
    override val name = "Calendar Schedule Reader"
    override val description = "Queries upcoming calendar events and meetings"
    override val requiredPermissions = listOf(Manifest.permission.READ_CALENDAR)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return ActionResult.PermissionRequired(
                Manifest.permission.READ_CALENDAR,
                "Boss, I need Calendar permission to check your schedule."
            )
        }

        val timeSpan = (parameters["time"] ?: parameters["day"] ?: "today").lowercase()
        val cal = Calendar.getInstance()
        if (timeSpan.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startMillis = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endMillis = cal.timeInMillis

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val events = mutableListOf<String>()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)

                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                while (cursor.moveToNext() && events.size < 5) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Event"
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    val loc = if (locIdx >= 0) cursor.getString(locIdx) else null

                    val timeStr = if (begin > 0) timeFormat.format(Date(begin)) else ""
                    val locStr = if (!loc.isNullOrBlank()) " at $loc" else ""
                    events.add("$title at $timeStr$locStr")
                }
            }
        } catch (e: Exception) {
            return ActionResult.Failure("Error reading calendar: ${e.message}")
        }

        val dayLabel = if (timeSpan.contains("tomorrow")) "tomorrow" else "today"
        return if (events.isEmpty()) {
            ActionResult.Success(
                message = "No events scheduled for $dayLabel",
                spokenDetail = "You have no meetings or events scheduled for $dayLabel, Boss."
            )
        } else {
            val list = events.joinToString("; ")
            ActionResult.Success(
                message = "Events for $dayLabel: $list",
                spokenDetail = "Here is what's on your calendar for $dayLabel, Boss: $list."
            )
        }
    }
}

class CalendarCreateTool : FridayTool {
    override val id = "CALENDAR_CREATE"
    override val name = "Calendar Event Scheduler"
    override val description = "Schedules and creates new calendar meetings with title, time, and duration"
    override val requiredPermissions = listOf(Manifest.permission.WRITE_CALENDAR)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val title = parameters["title"] ?: parameters["event"] ?: parameters["meeting"] ?: ""
        if (title.isBlank()) {
            return ActionResult.MissingParameter("title", "What is the title of the meeting or event, Boss?")
        }

        val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val location = parameters["location"] ?: ""
        val durationMinutes = parameters["durationMinutes"]?.toIntOrNull() ?: 60

        // Parse day and hour
        val dayStr = parameters["day"] ?: parameters["date"] ?: "tomorrow"
        val hour = parameters["hour"]?.toIntOrNull() ?: 16 // default 4 PM
        val minute = parameters["minute"]?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        if (dayStr.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        val startMillis = cal.timeInMillis
        val endMillis = startMillis + (durationMinutes * 60 * 1000L)

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(Date(startMillis))
        val targetDay = if (dayStr.contains("tomorrow")) "tomorrow" else "today"

        // Direct ContentProvider insertion if WRITE_CALENDAR is granted
        if (hasWrite) {
            try {
                // Find primary calendar ID
                var calId: Long? = null
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    CalendarContract.Calendars.VISIBLE + " = 1",
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        calId = cursor.getLong(0)
                    }
                }

                if (calId != null) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, endMillis)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        if (location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
                    }
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) {
                        return ActionResult.Success(
                            message = "Scheduled '$title' for $targetDay at $formattedTime",
                            spokenDetail = "Scheduled '$title' for $targetDay at $formattedTime, Boss."
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback to system calendar intent
        val insertIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(insertIntent)
            ActionResult.Success(
                message = "Opened calendar to schedule '$title'",
                spokenDetail = "Setting up '$title' for $targetDay at $formattedTime on your calendar, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to schedule calendar event: ${e.message}")
        }
    }
}
