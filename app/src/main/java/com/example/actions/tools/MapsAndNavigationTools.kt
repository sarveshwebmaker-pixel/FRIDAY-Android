package com.example.actions.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.actions.ActionResult

class MapSearchTool : FridayTool {
    override val id = "MAP_SEARCH"
    override val name = "Map Location Search"
    override val description = "Searches for a location, address, or place on Maps"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = parameters["location"] ?: parameters["query"] ?: parameters["place"] ?: ""
        if (query.isBlank()) {
            return ActionResult.MissingParameter("location", "Where would you like to search on the map, Boss?")
        }

        val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(mapIntent)
            ActionResult.Success("Searching for $query on Maps", spokenDetail = "Looking up $query on Maps, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open maps: ${e.message}")
        }
    }
}

class NavigationTool : FridayTool {
    override val id = "START_NAVIGATION"
    override val name = "Turn-by-Turn Navigator"
    override val description = "Starts turn-by-turn driving navigation to a destination"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val destination = parameters["destination"] ?: parameters["location"] ?: ""
        if (destination.isBlank()) {
            return ActionResult.MissingParameter("destination", "Where are we heading, Boss?")
        }

        val navUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(navIntent)
            ActionResult.Success("Starting navigation to $destination", spokenDetail = "Routing to $destination now, Boss.")
        } catch (e: Exception) {
            // Fallback to geo query
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                ActionResult.Success("Opening directions to $destination", spokenDetail = "Opening directions to $destination, Boss.")
            } catch (err: Exception) {
                ActionResult.Failure("Failed to start navigation: ${err.message}")
            }
        }
    }
}
