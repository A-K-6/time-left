package dev.calendarquiet.timer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.calendarquiet.MainActivity
import dev.calendarquiet.R
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.quietApp.scope.launch {
            try { context.quietApp.timers.refresh() }
            finally { pending.finish() }
        }
    }

    companion object {
        fun render(context: Context, state: TimerState, boot: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
            if (ids.isEmpty()) return
            val open = PendingIntent.getActivity(context, 700, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val views = RemoteViews(context.packageName, R.layout.timer_widget)
            val timer = state.active
            views.setTextViewText(R.id.widget_title, timer?.title ?: "Time Left")
            views.setViewVisibility(R.id.widget_countdown, if (timer == null) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_idle, if (timer == null) View.VISIBLE else View.GONE)
            if (timer != null) {
                val remaining = timer.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot).coerceAtLeast(0)
                views.setBoolean(R.id.widget_countdown, "setCountDown", true)
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + remaining, null, remaining > 0)
                views.setTextViewText(R.id.widget_detail, "Ends ${finishLabel(System.currentTimeMillis() + remaining)}")
            } else {
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.widget_idle, state.completed?.let { "${it.title} finished" } ?: context.getString(R.string.choose_timer))
                views.setTextViewText(R.id.widget_detail, "Focus sessions today: ${state.completedToday(System.currentTimeMillis())}")
            }
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
