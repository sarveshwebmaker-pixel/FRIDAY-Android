package com.example.actions.tools

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.example.actions.ActionResult

class CameraTool : FridayTool {
    override val id = "CAMERA_ACTION"
    override val name = "Camera Controller"
    override val description = "Opens camera, takes photos, or starts video recording"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val mode = (parameters["mode"] ?: parameters["action"] ?: "open").lowercase()

        val intent = when (mode) {
            "photo", "take_photo", "capture", "picture" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "video", "record_video" -> Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            else -> {
                // Open default camera application
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            val spoken = when (mode) {
                "photo", "take_photo", "capture", "picture" -> "Camera ready to capture."
                "video", "record_video" -> "Ready to record video."
                else -> "Opening camera."
            }
            ActionResult.Success("Camera opened in $mode mode", spokenDetail = spoken)
        } catch (e: Exception) {
            ActionResult.Failure("Could not open camera: ${e.message}")
        }
    }
}

class GalleryTool : FridayTool {
    override val id = "GALLERY_ACTION"
    override val name = "Gallery & Photos Viewer"
    override val description = "Opens photos, gallery, or image picker"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Opened gallery", spokenDetail = "Opening your photos.")
        } catch (e: Exception) {
            // Fallback to Google Photos / Gallery package
            try {
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                    ?: context.packageManager.getLaunchIntentForPackage("com.android.gallery3d")
                if (fallbackIntent != null) {
                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallbackIntent)
                    return ActionResult.Success("Opened photos app", spokenDetail = "Opening your photos.")
                }
            } catch (_: Exception) {}

            ActionResult.Failure("Failed to open gallery: ${e.message}")
        }
    }
}

class FileTool : FridayTool {
    override val id = "FILE_ACTION"
    override val name = "File & Storage Browser"
    override val description = "Opens file explorer or downloads directory"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val mode = (parameters["mode"] ?: parameters["action"] ?: "open").lowercase()

        val intent = if (mode.contains("download")) {
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return try {
            context.startActivity(intent)
            val spoken = if (mode.contains("download")) "Opening downloads." else "Opening files."
            ActionResult.Success("Opened file viewer", spokenDetail = spoken)
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open files: ${e.message}")
        }
    }
}

class PlayMusicTool(override val id: String = "PLAY_MUSIC") : FridayTool {
    override val name = "Music & Song Player"
    override val description = "Plays songs, artists, or audio streams via media apps or YouTube"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = parameters["query"] ?: parameters["song"] ?: parameters["track"] ?: "music"
        val artist = parameters["artist"]
        val targetApp = parameters["targetApp"]?.lowercase()

        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putExtra(android.app.SearchManager.QUERY, query)
            if (!artist.isNullOrBlank()) {
                putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist)
            }
        }

        if (targetApp == "spotify") {
            intent.setPackage("com.spotify.music")
        } else if (targetApp == "youtube" || targetApp == "ytmusic" || targetApp == "youtube music") {
            intent.setPackage("com.google.android.apps.youtube.music")
        }

        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val spoken = if (query.isNotBlank() && query != "music") "Playing $query." else "Playing music."
                ActionResult.Success("Playing $query", spokenDetail = spoken)
            } else {
                val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (ytIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(ytIntent)
                    ActionResult.Success("Playing $query on YouTube", spokenDetail = "Playing $query on YouTube.")
                } else {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    ActionResult.Success("Playing $query", spokenDetail = "Playing $query.")
                }
            }
        } catch (e: Exception) {
            ActionResult.Failure("Could not play music: ${e.message}")
        }
    }
}
