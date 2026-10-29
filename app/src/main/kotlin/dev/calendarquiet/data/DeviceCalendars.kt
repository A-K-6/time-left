package dev.calendarquiet.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import dev.calendarquiet.core.CalendarEvent
import dev.calendarquiet.core.HOUR

data class DeviceCalendar(val id: Long, val name: String, val account: String)

class DeviceCalendars(private val context: Context) {
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        val columns = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        return buildList {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns,
                "${CalendarContract.Calendars.SYNC_EVENTS}=1 AND ${CalendarContract.Calendars.VISIBLE}=1", null, null)?.use { cursor ->
                while (cursor.moveToNext()) add(DeviceCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty()))
            }
        }
    }

    fun events(now: Long): List<CalendarEvent> = events(now - 24 * HOUR, now + 7 * 24 * HOUR).filterNot { it.declined }

    fun events(from: Long, until: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val ids = calendars().map { it.id }.toSet()
        if (ids.isEmpty()) return emptyList()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, until)
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.AVAILABILITY)
        // Instances expands recurrence and respects moved/deleted occurrence exceptions.
        return buildList {
            context.contentResolver.query(builder.build(), columns,
                "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ?)",
                arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()),
                "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendar = cursor.getLong(1)
                    if (calendar in ids) add(CalendarEvent(cursor.getLong(0), calendar, cursor.getString(2).orEmpty(),
                        cursor.getString(3).orEmpty(), cursor.getLong(4), cursor.getLong(5), cursor.getInt(6) != 0,
                        cursor.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                        cursor.isNull(8) || cursor.getInt(8) != CalendarContract.Events.AVAILABILITY_FREE))
                }
            }
        }
    }
}
