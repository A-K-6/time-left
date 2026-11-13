package dev.calendarquiet

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.os.Bundle
import android.os.SystemClock
import dev.calendarquiet.core.Countdown
import dev.calendarquiet.core.MINUTE
import dev.calendarquiet.core.TimerKind
import dev.calendarquiet.core.TimerState
import dev.calendarquiet.timer.TimerCoordinator
import dev.calendarquiet.timer.TimerReceiver
import dev.calendarquiet.timer.TimerStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Runs only on a disposable emulator: exercises actual preferences, notifications and PendingIntents. */
class TimerSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val context = targetContext
        val prefs = context.getSharedPreferences("timers", 0)
        val sounds = context.getSharedPreferences("timer_sounds", 0)
        val saved = prefs.getString("state", null)
        val startSound = sounds.getBoolean("on_start", true)
        val finishSound = sounds.getBoolean("on_finish", true)
        var passed = 0
        val messages = StringBuilder()
        var failure: Throwable? = null
        try {
            sounds.edit().putBoolean("on_start", false).putBoolean("on_finish", true).commit()
            runBlocking {
                var timers = context.quietApp.timers
                timers.clear()
                timers.saveSettings(25, 5, 15, 4)
                val originalHistory = timers.state.value.focusFinishedAt
                val initialRevision = timers.state.value.revision
                timers.handleAction(TimerReceiver.START_FOCUS, initialRevision)
                val first = timers.state.value.active!!
                check(first.kind == TimerKind.FOCUS && first.durationMillis == 25 * MINUTE)
                timers.handleAction(TimerReceiver.START_FOCUS, initialRevision)
                check(timers.state.value.active!!.id == first.id)
                messages.append("PASS: repeated start does not replace active timer\n"); passed++

                val runningRevision = timers.state.value.revision
                TimerReceiver.action(context, TimerReceiver.PAUSE, runningRevision).send()
                repeat(30) {
                    if (TimerStore(context).read().active?.isPaused == true) return@repeat
                    delay(50)
                }
                check(TimerStore(context).read().active?.isPaused == true) { "Real Pause PendingIntent did not reach the receiver" }
                val paused = timers.state.value.active!!
                check(paused.isPaused)
                val left = paused.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), timers.bootCount())
                delay(150)
                timers.refresh()
                check(timers.state.value.active!!.pausedRemaining == left)
                check(TimerStore(context).read().active == paused)
                messages.append("PASS: pause freezes and persists remaining time\n"); passed++

                val activeNotification = context.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id == 700 }.notification
                check(!activeNotification.extras.getBoolean("android.showChronometer"))
                check(activeNotification.actions.first().title.toString() == "Resume")
                messages.append("PASS: paused notification is static and offers Resume\n"); passed++

                timers = TimerCoordinator(context)
                timers.refresh()
                check(timers.state.value.active!!.pausedRemaining == left)
                timers.handleAction(TimerReceiver.RESUME, timers.state.value.revision)
                check(!timers.state.value.active!!.isPaused)
                timers.handleAction(TimerReceiver.PAUSE, runningRevision)
                check(!timers.state.value.active!!.isPaused)
                timers.extend(first.id, 1)
                check(timers.state.value.active!!.durationMillis == 26 * MINUTE)
                messages.append("PASS: reload/resume/extend preserve identity and reject stale Pause\n"); passed++

                val oldStop = timers.state.value.revision
                timers.handleAction(TimerReceiver.STOP, oldStop)
                check(timers.state.value.active == null && timers.state.value.focusFinishedAt == originalHistory)
                timers.startDuration(TimerKind.BREAK, 5)
                val replacement = timers.state.value.active!!.id
                timers.handleAction(TimerReceiver.STOP, oldStop)
                check(timers.state.value.active!!.id == replacement)
                timers.clear()
                timers.handleAction(TimerReceiver.START_FOCUS, initialRevision)
                check(timers.state.value.active == null)
                messages.append("PASS: stop does not count focus; stale Stop/Start cannot affect replacement\n"); passed++

                val now = System.currentTimeMillis()
                timers.startUntil(TimerKind.EVENT, "Calendar deadline", now + 5 * MINUTE)
                val event = timers.state.value.active!!
                timers.pauseOrResume(event.id)
                check(timers.state.value.active == event && !event.canPause)
                check(runCatching { timers.extend(event.id, 1) }.isFailure)
                check(timers.state.value.active!!.endsAt == event.endsAt)
                timers.clear()
                messages.append("PASS: calendar deadlines cannot be paused or extended\n"); passed++

                val finished = Countdown.duration(TimerKind.FOCUS, 1, now, SystemClock.elapsedRealtime(), timers.bootCount())
                TimerStore(context).write(TimerState(completed = finished, focusesInCycle = 4, revision = 1000))
                timers = TimerCoordinator(context)
                timers.handleAction(TimerReceiver.START_NEXT, 1000)
                check(timers.state.value.active!!.kind == TimerKind.LONG_BREAK)
                check(timers.state.value.active!!.durationMillis == 15 * MINUTE)
                timers.clear()
                messages.append("PASS: next-session widget action selects long break after fourth focus\n"); passed++

                // Old v0.2.2 schema: migration keeps settings and history, adds safe defaults.
                prefs.edit().putString("state", """{"focusMinutes":35,"breakMinutes":7,"focusFinishedAt":[$now]}""").commit()
                val migrated = TimerStore(context).read()
                check(migrated.focusMinutes == 35 && migrated.breakMinutes == 7)
                check(migrated.focusFinishedAt == listOf(now) && migrated.longBreakMinutes == 15 && migrated.longBreakEvery == 4)
                messages.append("PASS: v0.2.2 settings and history migrate without reset\n"); passed++

                TimerStore(context).write(TimerState(revision = 2000))
                timers = TimerCoordinator(context)
                timers.startDuration(TimerKind.FOCUS, 1)
                val timer = timers.state.value.active!!
                // Set an actually due persisted deadline, then let the Android coordinator settle it.
                val due = timer.copy(startedAt = now - 2 * MINUTE, endsAt = now - MINUTE,
                    elapsedDeadline = SystemClock.elapsedRealtime() - MINUTE)
                TimerStore(context).write(timers.state.value.copy(active = due))
                timers = TimerCoordinator(context)
                timers.refresh()
                val settled = timers.state.value
                check(settled.active == null && settled.focusFinishedAt.size == 1 && settled.focusesInCycle == 1)
                timers.refresh()
                check(timers.state.value.focusFinishedAt.size == 1)
                val finish = context.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id == 701 }.notification
                check(finish.actions.single().title.toString() == "Start short break")
                messages.append("PASS: due timer completes once and finish notification offers next break\n"); passed++
                timers.clear()
            }
        } catch (exception: Throwable) {
            failure = exception
        } finally {
            prefs.edit().putString("state", saved).commit()
            sounds.edit().putBoolean("on_start", startSound).putBoolean("on_finish", finishSound).commit()
        }
        val error = failure
        finish(if (error == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (error == null) "\n$messages\nOK ($passed checks)\n" else "\n$messages\nFAILED: ${error.stackTraceToString()}")
            putInt("passed", passed)
        })
    }
}
