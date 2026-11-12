package com.quarry.congregationscheduler

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Keeps launcher widgets in sync with the schedule loaded by the app. */
object ScheduleWidgets {
    private const val PREFS = "schedule_widget_data"

    fun update(context: Context, schedule: ScheduleData?) {
        if (schedule != null) {
            val meeting = schedule.meetings.mapNotNull { item ->
                val date = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return@mapNotNull null
                if (date.isBefore(LocalDate.now())) return@mapNotNull null
                date to item
            }.minByOrNull { it.first }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (meeting == null) {
                prefs.clear().putString("congregation", schedule.settings.name).putBoolean("has_meeting", false)
            } else {
                val (date, item) = meeting
                val assignments = item.slots.filter { !it.backup }.take(3)
                prefs.putBoolean("has_meeting", true)
                    .putString("congregation", schedule.settings.name)
                    .putString("date", date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")))
                    .putString("date_iso", date.toString())
                    .putString("short_date", date.format(DateTimeFormatter.ofPattern("EEE, MMM d")))
                    .putString("time", widgetTime(item.time))
                    .putString("countdown", countdown(date))
                    .putString("title", item.displayLabel?.takeIf { it.isNotBlank() } ?: item.type)
                    .putString("assignment_1", assignmentLabel(assignments.getOrNull(0)))
                    .putString("assignment_2", assignmentLabel(assignments.getOrNull(1)))
                    .putString("assignment_3", assignmentLabel(assignments.getOrNull(2)))
            }
            prefs.apply()
        }
        refresh(context)
    }

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            CompactScheduleWidget::class.java to R.layout.widget_schedule_compact,
            WideScheduleWidget::class.java to R.layout.widget_schedule_wide
        ).forEach { (provider, layout) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, layout)) }
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        refresh(context)
    }

    private fun buildViews(context: Context, layout: Int): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasMeeting = prefs.getBoolean("has_meeting", false)
        val meetingDate = prefs.getString("date_iso", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val stillUpcoming = hasMeeting && meetingDate != null && !meetingDate.isBefore(LocalDate.now())
        val date = if (layout == R.layout.widget_schedule_wide) prefs.getString("date", null) else prefs.getString("short_date", null)
        views.setTextViewText(R.id.widget_congregation, prefs.getString("congregation", "JW Task Scheduler") ?: "JW Task Scheduler")
        views.setTextViewText(R.id.widget_date, if (stillUpcoming) date else "Open app to refresh schedule")
        views.setTextViewText(R.id.widget_time, if (stillUpcoming) prefs.getString("time", "") else "")
        views.setTextViewText(R.id.widget_countdown, if (stillUpcoming && meetingDate != null) countdown(meetingDate) else "")
        if (layout == R.layout.widget_schedule_wide) {
            views.setTextViewText(R.id.widget_meeting_title, prefs.getString("title", "NEXT MEETING") ?: "NEXT MEETING")
            views.setTextViewText(R.id.widget_assignment_1, prefs.getString("assignment_1", "") ?: "")
            views.setTextViewText(R.id.widget_assignment_2, prefs.getString("assignment_2", "") ?: "")
            views.setTextViewText(R.id.widget_assignment_3, prefs.getString("assignment_3", "") ?: "")
            listOf(
                R.id.widget_assignment_1 to "assignment_1",
                R.id.widget_assignment_2 to "assignment_2",
                R.id.widget_assignment_3 to "assignment_3"
            ).forEach { (viewId, key) ->
                val rowVisible = stillUpcoming && !prefs.getString(key, "").isNullOrBlank()
                views.setViewVisibility(viewId, if (rowVisible) android.view.View.VISIBLE else android.view.View.GONE)
            }
            val visibility = if (stillUpcoming) android.view.View.VISIBLE else android.view.View.GONE
            views.setViewVisibility(R.id.widget_assignments, visibility)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, layout, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }

    private fun assignmentLabel(slot: Slot?): String {
        if (slot == null) return ""
        val department = when (slot.department) {
            "mic" -> "Mic Men"
            "av" -> "Audio/Video"
            "platform", "attendant" -> "Platform"
            else -> slot.department.removePrefix("dept:").replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
        return "$department · ${slot.name ?: "Unassigned"}"
    }

    private fun widgetTime(value: String): String = runCatching {
        val parts = value.split(":")
        val hour = parts[0].toInt()
        val minute = parts.getOrElse(1) { "00" }
        "${if (hour % 12 == 0) 12 else hour % 12}:$minute ${if (hour < 12) "AM" else "PM"}"
    }.getOrDefault(value)

    private fun countdown(date: LocalDate): String = when (val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }
}

class CompactScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ScheduleWidgets.refresh(context)
}

class WideScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ScheduleWidgets.refresh(context)
}
