package dev.calendarquiet.automation

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.calendarquiet.data.Store
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

class QuietNotificationListener : NotificationListenerService() {
    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate() { super.onCreate(); store = Store(this) }
    override fun onListenerConnected() {
        connected = this
        quietApp.scope.launch { quietApp.automation.reconcile() }
    }
    override fun onListenerDisconnected() { if (connected === this) connected = null }
    override fun onDestroy() { if (connected === this) connected = null; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { snoozeIfSelected(sbn) }

    private fun snoozeIfSelected(sbn: StatusBarNotification) {
        if (!store.enabled || sbn.packageName == packageName) return
        val flags = sbn.notification.flags
        // Ongoing services, calls, alarms and media controls must remain visible.
        if (sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            flags and Notification.FLAG_NO_CLEAR != 0 ||
            flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            sbn.notification.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
                Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SYSTEM)) return
        val until = store.snoozeWindows()[sbn.packageName] ?: return
        val duration = until - System.currentTimeMillis()
        if (duration > 0) runCatching { snoozeNotification(sbn.key, duration) }
    }

    private fun snoozeCurrent() {
        handler.post { runCatching { activeNotifications?.forEach(::snoozeIfSelected) } }
    }
    companion object {
        @Volatile private var connected: QuietNotificationListener? = null
        fun snoozeExisting() { connected?.snoozeCurrent() }
    }
}
