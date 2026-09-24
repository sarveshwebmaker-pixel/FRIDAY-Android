package com.example.actions.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.actions.ActionResult
import com.example.service.FridayAccessibilityService

class AppLaunchTool(override val id: String = "OPEN_APP") : FridayTool {
    override val name = "Application Launcher"
    override val description = "Finds and launches any installed Android application"

    companion object {
        private const val TAG = "AppLaunchTool"
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val appName = parameters["appName"] ?: parameters["app"] ?: ""
        if (appName.isBlank()) {
            return ActionResult.MissingParameter("appName", "Which app would you like me to open, Boss?")
        }

        val pm = context.packageManager
        val normalized = appName.lowercase().trim()

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "camera" to "com.android.camera",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "map" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "setting" to "com.android.settings",
            "settings" to "com.android.settings",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos"
        )

        val targetPackage = knownPackages.entries.firstOrNull { normalized.contains(it.key) }?.value
        var launchIntent: Intent? = null

        if (targetPackage != null) {
            launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        }

        if (launchIntent == null) {
            try {
                val installed = pm.getInstalledApplications(0)
                for (app in installed) {
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    if (label.contains(normalized) || normalized.contains(label)) {
                        launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying installed applications", e)
            }
        }

        // Web fallback for YouTube
        if (launchIntent == null && normalized.contains("youtube")) {
            val webYtIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (webYtIntent.resolveActivity(pm) != null) {
                context.startActivity(webYtIntent)
                ActionResult.Success("Opening YouTube in browser", spokenDetail = "Opening YouTube for you, Boss.")
            } else {
                ActionResult.NotFound("YouTube", "YouTube is not available on this device, Boss.")
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                ActionResult.Success("Opening $appName", spokenDetail = "Opening $appName, Boss.")
            } catch (e: Exception) {
                ActionResult.Failure("Failed to launch $appName: ${e.message}")
            }
        } else {
            ActionResult.NotFound("app", "I couldn't find $appName installed on your phone, Boss.")
        }
    }
}

class AppCloseTool(override val id: String = "CLOSE_APP") : FridayTool {
    override val name = "App Closer & Home Navigator"
    override val description = "Closes the current app by returning to Home or pressing Back"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        // If Accessibility Service is available, use Global Home or Back
        val accessibility = FridayAccessibilityService.getService()
        if (accessibility != null) {
            val success = accessibility.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            if (success) {
                return ActionResult.Success("Returned to home screen", spokenDetail = "Heading back home, Boss.")
            }
        }

        // Standard Home Intent fallback
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            ActionResult.Success("Returning to home", spokenDetail = "Heading back home, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Could not return to home: ${e.message}")
        }
    }
}

class SearchWebTool(override val id: String = "SEARCH_WEB") : FridayTool {
    override val name = "Web & Media Search"
    override val description = "Searches the web, Google, or YouTube for a query"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = parameters["query"] ?: parameters["q"] ?: ""
        val targetApp = parameters["targetApp"] ?: parameters["app"]

        if (query.isBlank()) {
            return ActionResult.MissingParameter("query", "What would you like me to search for, Boss?")
        }

        if (targetApp != null && targetApp.contains("youtube", ignoreCase = true)) {
            val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(ytIntent)
                return ActionResult.Success("Searching for '$query' on YouTube", spokenDetail = "Searching YouTube for '$query', Boss.")
            } catch (_: Exception) {
                // Fallback to web query
            }
        }

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Searching for '$query'", spokenDetail = "Searching for '$query', Boss.")
        } catch (e: Exception) {
            // Fallback to browser URL
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                ActionResult.Success("Searching Google for '$query'", spokenDetail = "Searching for '$query', Boss.")
            } catch (err: Exception) {
                ActionResult.Failure("Could not search web: ${err.message}")
            }
        }
    }
}

class OpenUrlTool : FridayTool {
    override val id = "OPEN_URL"
    override val name = "URL Navigator"
    override val description = "Navigates directly to any web link in the browser"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        var url = parameters["url"] ?: parameters["link"] ?: ""
        if (url.isBlank()) {
            return ActionResult.MissingParameter("url", "Which website or URL should I open, Boss?")
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Opening $url", spokenDetail = "Opening the web page, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open URL: ${e.message}")
        }
    }
}
