package dev.calendarquiet.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import dev.calendarquiet.core.HOUR

class Scheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val jobs = context.getSystemService(JobScheduler::class.java)
    private fun alarmIntent() = PendingIntent.getBroadcast(context, 1,
        Intent(context, AutomationReceiver::class.java).setAction("dev.calendarquiet.RECONCILE"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(at: Long) {
        val pending = alarmIntent()
        try {
            if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            // Access can be revoked between checking and scheduling.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        val component = ComponentName(context, CalendarJobService::class.java)
        if (jobs.getPendingJob(PERIODIC_JOB) == null) {
            jobs.schedule(JobInfo.Builder(PERIODIC_JOB, component).setPeriodic(6 * HOUR).setPersisted(true).build())
        }
        armCalendarChanges()
    }

    fun armCalendarChanges(replaceRunning: Boolean = false) {
        if (!replaceRunning && jobs.getPendingJob(CALENDAR_JOB) != null) return
        jobs.schedule(JobInfo.Builder(CALENDAR_JOB, ComponentName(context, CalendarJobService::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(5_000).setTriggerContentMaxDelay(60_000).build())
    }

    fun cancel() {
        alarms.cancel(alarmIntent())
        jobs.cancel(CALENDAR_JOB); jobs.cancel(PERIODIC_JOB)
    }

    companion object { const val CALENDAR_JOB = 10; const val PERIODIC_JOB = 11 }
}
