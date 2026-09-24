package com.example.actions.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.actions.ActionResult
import com.example.service.FridayNotificationListenerService

class ReadNotificationsTool(override val id: String = "NOTIFICATION_READ") : FridayTool {
    override val name = "Notification Reader"
    override val description = "Reads recent incoming notifications or messages from WhatsApp, SMS, and apps"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        if (!FridayNotificationListenerService.isRunning()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ActionResult.PermissionRequired(
                "NOTIFICATION_LISTENER",
                "Boss, please allow Notification Access in Android Settings so I can read messages and notifications for you."
            )
        }

        val notifications = FridayNotificationListenerService.recentNotifications.value
        if (notifications.isEmpty()) {
            return ActionResult.Success(
                message = "No unread notifications",
                spokenDetail = "You have no new notifications right now, Boss."
            )
        }

        val countStr = parameters["count"] ?: "3"
        val count = countStr.toIntOrNull()?.coerceIn(1, 10) ?: 3
        val topNotifications = notifications.take(count)

        val summary = topNotifications.mapIndexed { index, notif ->
            "${notif.appName}: ${notif.title} says \"${notif.text}\""
        }.joinToString(". ")

        val spoken = if (topNotifications.size == 1) {
            val n = topNotifications[0]
            "You have one notification from ${n.appName}. ${n.title} says: ${n.text}, Boss."
        } else {
            "You have ${topNotifications.size} recent notifications, Boss. $summary."
        }

        return ActionResult.Success(
            message = summary,
            spokenDetail = spoken,
            outputData = mapOf("count" to topNotifications.size.toString())
        )
    }
}

class ReplyNotificationTool(override val id: String = "REMOTE_INPUT") : FridayTool {
    override val name = "Notification Quick Replier"
    override val description = "Replies to incoming notifications (e.g. WhatsApp, SMS) via RemoteInput"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val replyText = parameters["message"] ?: parameters["text"] ?: parameters["reply"] ?: ""
        val contactName = parameters["contact"] ?: parameters["to"] ?: parameters["name"]

        if (replyText.isBlank()) {
            return ActionResult.MissingParameter("message", "What would you like me to reply, Boss?")
        }

        if (!FridayNotificationListenerService.isRunning()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ActionResult.PermissionRequired(
                "NOTIFICATION_LISTENER",
                "Boss, please enable Notification Access so I can send quick replies for you."
            )
        }

        val result = FridayNotificationListenerService.replyToTarget(context, contactName, replyText)
        val targetLabel = contactName ?: "the latest message"

        return when (result) {
            com.example.service.RemoteInputResult.SUCCESS -> {
                ActionResult.Success(
                    message = "Quick reply sent to $targetLabel",
                    spokenDetail = "Reply sent to $targetLabel: \"$replyText\", Boss."
                )
            }
            com.example.service.RemoteInputResult.UNSUPPORTED_NO_REMOTE_INPUT -> {
                ActionResult.NotSupported(
                    feature = "REMOTE_INPUT",
                    explanation = "The app notification from $targetLabel does not support quick replies via RemoteInput, Boss."
                )
            }
            com.example.service.RemoteInputResult.NOTIFICATION_NOT_FOUND -> {
                ActionResult.NotFound(
                    item = "notification",
                    message = "I couldn't find an active notification from $targetLabel, Boss."
                )
            }
            com.example.service.RemoteInputResult.SERVICE_DISABLED -> {
                ActionResult.PermissionRequired(
                    permission = "NOTIFICATION_LISTENER",
                    explanation = "Notification access is not enabled. Please enable it in Settings, Boss."
                )
            }
            com.example.service.RemoteInputResult.SEND_FAILED -> {
                ActionResult.Failure(
                    error = "Failed to dispatch RemoteInput action",
                    userMessage = "Could not send the reply to $targetLabel."
                )
            }
        }
    }
}
