package dev.calendarquiet.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.quietApp.scope.launch {
            try {
                if (intent.hasExtra("revision")) context.quietApp.timers.handleAction(intent.action, intent.getLongExtra("revision", -1))
                else context.quietApp.timers.refresh()
            } catch (exception: Exception) {
                Log.e("TimerReceiver", "Could not update timer", exception)
            } finally { pending.finish() }
        }
    }
    companion object {
        const val REFRESH = "dev.calendarquiet.TIMER_REFRESH"
        const val PAUSE = "dev.calendarquiet.TIMER_PAUSE"
        const val RESUME = "dev.calendarquiet.TIMER_RESUME"
        const val STOP = "dev.calendarquiet.TIMER_STOP"
        const val START_FOCUS = "dev.calendarquiet.TIMER_START_FOCUS"
        const val START_BREAK = "dev.calendarquiet.TIMER_START_BREAK"
        const val START_NEXT = "dev.calendarquiet.TIMER_START_NEXT"

        fun action(context: Context, action: String, revision: Long): PendingIntent = PendingIntent.getBroadcast(context, 701,
            Intent(context, TimerReceiver::class.java).setAction(action)
                .setData(Uri.parse("timeleft://timer/$revision/$action")).putExtra("revision", revision),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
