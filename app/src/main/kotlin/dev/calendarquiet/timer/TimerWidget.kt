package dev.calendarquiet.timer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.calendarquiet.MainActivity
import dev.calendarquiet.R
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.core.formatCountdown
import dev.calendarquiet.quietApp
import kotlinx.coroutines.launch

open class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.quietApp.scope.launch {
            try { context.quietApp.timers.refresh() }
            finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        fun render(context: Context, state: TimerState, boot: Int) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(TimerWidget::class.java to R.layout.timer_widget, FocusWidget::class.java to R.layout.timer_widget_small).forEach { (provider, layout) ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isNotEmpty()) {
                    val compact = provider == FocusWidget::class.java
                    val layouts = if (compact) mapOf(
                        SizeF(80f, 100f) to views(context, state, boot, R.layout.focus_widget, true),
                        SizeF(110f, 140f) to views(context, state, boot, R.layout.focus_widget, true),
                        SizeF(140f, 56f) to views(context, state, boot, layout, true),
                        SizeF(180f, 160f) to views(context, state, boot, R.layout.focus_widget, true)) else mapOf(
                        SizeF(80f, 100f) to views(context, state, boot, R.layout.focus_widget, true),
                        SizeF(110f, 140f) to views(context, state, boot, R.layout.focus_widget, true),
                        SizeF(140f, 56f) to views(context, state, boot, R.layout.timer_widget_small, true),
                        SizeF(180f, 160f) to views(context, state, boot, R.layout.focus_widget, true),
                        SizeF(180f, 180f) to views(context, state, boot, layout, false))
                    manager.updateAppWidget(ids, RemoteViews(layouts))
                }
            }
        }

        private fun views(context: Context, state: TimerState, boot: Int, layout: Int, compact: Boolean): RemoteViews {
            val open = PendingIntent.getActivity(context, 700, Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_TIMER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val views = RemoteViews(context.packageName, layout)
            val timer = state.active
            val horizontal = layout == R.layout.timer_widget_small
            val now = System.currentTimeMillis()
            val running = timer != null && !timer.isPaused
            views.setTextViewText(R.id.widget_title, timer?.let { if (it.isPaused) "${it.title} · paused" else it.title } ?: if (state.nextSessionKind == TimerKind.FOCUS) "Ready to focus" else "Time for a break")
            views.setViewVisibility(R.id.widget_countdown, if (running) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_idle, if (running) View.GONE else View.VISIBLE)
            val left = timer?.remaining(now, SystemClock.elapsedRealtime(), boot)?.coerceAtLeast(0) ?: 0
            views.setBoolean(R.id.widget_countdown, "setCountDown", true)
            views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + left, null, running && left > 0)
            if (timer != null) {
                views.setTextViewText(R.id.widget_idle, formatCountdown(left))
                views.setTextViewText(R.id.widget_detail, if (timer.isPaused) "PAUSED · your time is saved" else "Ends ${finishLabel(now + left)}")
                val primaryAction = if (timer.canPause) {
                    if (timer.isPaused) TimerReceiver.RESUME else TimerReceiver.PAUSE
                } else TimerReceiver.STOP
                views.setTextViewText(R.id.widget_primary, if (timer.canPause) { if (timer.isPaused) "Resume" else "Pause" } else "Stop")
                views.setOnClickPendingIntent(R.id.widget_primary, TimerReceiver.action(context, primaryAction, state.revision))
                views.setTextViewText(R.id.widget_secondary, "Stop")
                views.setOnClickPendingIntent(R.id.widget_secondary, TimerReceiver.action(context, TimerReceiver.STOP, state.revision))
                views.setViewVisibility(R.id.widget_secondary, if (!compact && timer.canPause) View.VISIBLE else View.GONE)
            } else {
                val next = state.nextSessionKind
                val label = when (next) { TimerKind.LONG_BREAK -> "Long break"; TimerKind.BREAK -> "Break"; else -> "Focus" }
                views.setTextViewText(R.id.widget_idle, formatCountdown(state.minutesFor(next) * dev.calendarquiet.core.MINUTE))
                views.setTextViewText(R.id.widget_detail, "${state.completedToday(now)} today  ·  ${state.focusesInCycle}/${state.longBreakEvery} focuses")
                views.setTextViewText(R.id.widget_primary, if (horizontal) "Start" else label)
                views.setContentDescription(R.id.widget_primary, "Start ${label.lowercase(java.util.Locale.ROOT)}")
                views.setOnClickPendingIntent(R.id.widget_primary, TimerReceiver.action(context, TimerReceiver.START_NEXT, state.revision))
                val secondary = if (next == TimerKind.FOCUS) TimerReceiver.START_BREAK else TimerReceiver.START_FOCUS
                views.setTextViewText(R.id.widget_secondary, if (next == TimerKind.FOCUS) "Break" else "Focus")
                views.setOnClickPendingIntent(R.id.widget_secondary, TimerReceiver.action(context, secondary, state.revision))
                views.setViewVisibility(R.id.widget_secondary, if (compact) View.GONE else View.VISIBLE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}

class FocusWidget : TimerWidget()
