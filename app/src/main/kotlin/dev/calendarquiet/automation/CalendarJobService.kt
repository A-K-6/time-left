package dev.calendarquiet.automation

import android.app.job.JobParameters
import android.app.job.JobService
import dev.calendarquiet.quietApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarJobService : JobService() {
    private val running = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = quietApp.scope.launch {
            quietApp.automation.reconcile()
            withContext(Dispatchers.Main) {
                running.remove(params.jobId)
                if (params.jobId == Scheduler.CALENDAR_JOB && quietApp.automation.store.enabled) {
                    // Replace before finishing so changes during execution are carried forward.
                    Scheduler(this@CalendarJobService).armCalendarChanges(replaceRunning = true)
                } else jobFinished(params, false)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return quietApp.automation.store.enabled
    }
    override fun onDestroy() {
        running.values.forEach { it.cancel() }
        running.clear()
        super.onDestroy()
    }
}
