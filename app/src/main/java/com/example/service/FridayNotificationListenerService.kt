package com.example.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RemoteInputResult {
    SUCCESS,
    SERVICE_DISABLED,
    NOTIFICATION_NOT_FOUND,
    UNSUPPORTED_NO_REMOTE_INPUT,
    SEND_FAILED
}

data class ReceivedNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isMessagingApp: Boolean = false,
    val hasReplyAction: Boolean = false
)

/**
 * FRIDAY Notification Listener Service.
 * Intercepts incoming notifications (e.g. WhatsApp, SMS, Telegram),
 * extracts sender & message text, and enables direct voice replying via RemoteInput.
 */
class FridayNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "FridayNotificationSvc"

        private var instance: FridayNotificationListenerService? = null

        private val _latestNotification = MutableStateFlow<ReceivedNotification?>(null)
        val latestNotification: StateFlow<ReceivedNotification?> = _latestNotification.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<ReceivedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<ReceivedNotification>> = _recentNotifications.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun getService(): FridayNotificationListenerService? = instance

        fun replyToTarget(context: Context, contactOrQuery: String?, replyText: String): RemoteInputResult {
            val service = instance ?: return RemoteInputResult.SERVICE_DISABLED
            val activeNotifs = try {
                service.activeNotifications
            } catch (e: Exception) {
                null
            } ?: return RemoteInputResult.SERVICE_DISABLED

            if (activeNotifs.isEmpty()) {
                return RemoteInputResult.NOTIFICATION_NOT_FOUND
            }

            // If contact specified, search notifications matching contact name or title
            val targetSbn: StatusBarNotification? = if (!contactOrQuery.isNullOrBlank()) {
                val queryLower = contactOrQuery.lowercase().trim()
                activeNotifs.firstOrNull { sbn ->
                    val title = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
                    val text = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
                    title.contains(queryLower) || text.contains(queryLower)
                }
            } else {
                val latest = _latestNotification.value
                if (latest != null) {
                    activeNotifs.firstOrNull { it.key == latest.key }
                        ?: activeNotifs.firstOrNull { it.packageName == latest.packageName }
                } else {
                    activeNotifs.firstOrNull()
                }
            }

            if (targetSbn == null) {
                return RemoteInputResult.NOTIFICATION_NOT_FOUND
            }

            val actions = targetSbn.notification.actions
            if (actions.isNullOrEmpty()) {
                return RemoteInputResult.UNSUPPORTED_NO_REMOTE_INPUT
            }

            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                    RemoteInput.addResultsToIntent(
                        arrayOf(
                            RemoteInput.Builder(remoteInput.resultKey).build()
                        ),
                        intent,
                        bundle
                    )
                    try {
                        action.actionIntent.send(context, 0, intent)
                        Log.i(TAG, "Successfully sent RemoteInput quick reply to ${targetSbn.packageName}")
                        return RemoteInputResult.SUCCESS
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed sending remote input reply", e)
                        return RemoteInputResult.SEND_FAILED
                    }
                }
            }

            return RemoteInputResult.UNSUPPORTED_NO_REMOTE_INPUT
        }

        fun replyToLatest(context: Context, replyText: String): Boolean {
            return replyToTarget(context, null, replyText) == RemoteInputResult.SUCCESS
        }

        fun replyToNotification(context: Context, sbn: StatusBarNotification, replyText: String): Boolean {
            val actions = sbn.notification.actions ?: return false
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                    RemoteInput.addResultsToIntent(
                        arrayOf(
                            RemoteInput.Builder(remoteInput.resultKey).build()
                        ),
                        intent,
                        bundle
                    )
                    try {
                        action.actionIntent.send(context, 0, intent)
                        Log.i(TAG, "Sent RemoteInput quick reply to ${sbn.packageName}")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed sending remote input reply", e)
                    }
                }
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "NotificationListener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "NotificationListener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Ignore self

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        val isMessaging = pkg.contains("whatsapp") || pkg.contains("messaging") ||
                pkg.contains("telegram") || pkg.contains("signal") || pkg.contains("sms")

        val hasReply = sbn.notification.actions?.any { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        } ?: false

        val notif = ReceivedNotification(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            isMessagingApp = isMessaging,
            hasReplyAction = hasReply
        )

        _latestNotification.value = notif
        val updatedList = _recentNotifications.value.toMutableList().apply {
            add(0, notif)
            if (size > 30) removeAt(size - 1)
        }
        _recentNotifications.value = updatedList
        Log.i(TAG, "Captured notification from $appName: $title - $text (Replyable: $hasReply)")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val updated = _recentNotifications.value.filter { it.key != sbn.key }
        _recentNotifications.value = updated
        if (_latestNotification.value?.key == sbn.key) {
            _latestNotification.value = updated.firstOrNull()
        }
    }
}
