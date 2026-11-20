package dev.calendarquiet.automation

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.calendarquiet.quietApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuietNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var refreshOnConnect: Job? = null

    override fun onListenerConnected() {
        ready = false
        refreshOnConnect?.cancel()
        connected = this
        refreshOnConnect = quietApp.scope.launch(Dispatchers.Main.immediate) {
            withContext(Dispatchers.IO) { quietApp.automation.reconcile() }
            ready = true
            snoozeCurrent()
        }
    }

    override fun onListenerDisconnected() = disconnect()

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        ready = false
        refreshOnConnect?.cancel()
        if (connected === this) connected = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { snoozeIfSelected(sbn) }

    private fun snoozeIfSelected(sbn: StatusBarNotification) {
        if (!ready || sbn.packageName == packageName) return
        val flags = sbn.notification.flags
        // Ongoing services, calls, alarms and media controls must remain visible.
        if (sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            flags and Notification.FLAG_NO_CLEAR != 0 ||
            flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            sbn.notification.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
                Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SYSTEM)) return
        val until = quietApp.automation.snoozeDeadline(sbn.packageName) ?: return
        val duration = until - System.currentTimeMillis()
        if (duration > 0) runCatching { snoozeNotification(sbn.key, duration) }
    }

    private fun snoozeCurrent() {
        handler.post {
            if (ready && quietApp.automation.canRunRules) {
                runCatching { activeNotifications?.forEach(::snoozeIfSelected) }
            }
        }
    }
    companion object {
        @Volatile private var connected: QuietNotificationListener? = null
        fun snoozeExisting() { connected?.snoozeCurrent() }
    }
}
