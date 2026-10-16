package com.example.agent

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class NotificationMessageInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val hasReplyAction: Boolean
)

class MayaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MayaNotificationListener"

        @Volatile
        var instance: MayaNotificationListenerService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        var onMessageReceived: ((NotificationMessageInfo) -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Maya Notification Listener Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        Log.w(TAG, "Maya Notification Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // Focus on messaging packages
        val isMessagingApp = pkg.contains("whatsapp", ignoreCase = true) ||
                pkg.contains("telegram", ignoreCase = true) ||
                pkg.contains("messaging", ignoreCase = true) ||
                pkg.contains("signal", ignoreCase = true) ||
                pkg.contains("mms", ignoreCase = true)

        if (!isMessagingApp) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isNotEmpty() && text.isNotEmpty()) {
            val hasReply = findWearReplyAction(sbn.notification) != null
            val info = NotificationMessageInfo(
                key = sbn.key,
                packageName = pkg,
                appName = getPackageAppName(pkg),
                sender = title,
                text = text,
                timestamp = sbn.postTime,
                hasReplyAction = hasReply
            )
            onMessageReceived?.invoke(info)
        }
    }

    private fun getPackageAppName(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    fun getRecentMessages(): List<NotificationMessageInfo> {
        val active = activeNotifications ?: return emptyList()
        val list = mutableListOf<NotificationMessageInfo>()

        for (sbn in active) {
            val extras = sbn.notification?.extras ?: continue
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isNotEmpty() && text.isNotEmpty()) {
                val hasReply = findWearReplyAction(sbn.notification) != null
                list.add(
                    NotificationMessageInfo(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = getPackageAppName(sbn.packageName),
                        sender = title,
                        text = text,
                        timestamp = sbn.postTime,
                        hasReplyAction = hasReply
                    )
                )
            }
        }
        return list
    }

    fun sendReply(key: String, replyText: String): Boolean {
        val sbn = activeNotifications?.find { it.key == key } ?: return false
        val action = findWearReplyAction(sbn.notification) ?: return false

        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        for (remoteInput in remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(this, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification reply: ${e.message}")
            false
        }
    }

    private fun findWearReplyAction(notification: Notification?): Notification.Action? {
        if (notification == null) return null
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) {
                    return action
                }
            }
        }
        return null
    }
}
