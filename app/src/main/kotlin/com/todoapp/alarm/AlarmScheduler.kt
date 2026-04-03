package com.todoapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.todoapp.domain.model.TodoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an exact alarm for [item] at its due/start time.
     * Does nothing if the item has no due date or the time has already passed.
     */
    fun schedule(item: TodoItem) {
        if (item.isCompleted) return
        val triggerAt = item.startDate ?: item.dueDate ?: return
        if (triggerAt.isBefore(LocalDateTime.now())) return
        val millis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleAt(item.id, item.title, item.description, item.type.name, millis)
    }

    /** Reschedules alarm [snoozeMinutes] from now. */
    fun scheduleSnooze(
        itemId: Long,
        title: String,
        description: String,
        type: String,
        snoozeMinutes: Long
    ) {
        val millis = System.currentTimeMillis() + snoozeMinutes * 60 * 1000
        scheduleAt(itemId, title, description, type, millis)
    }

    /** Cancels a previously scheduled alarm for the given item id. */
    fun cancel(itemId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, itemId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun scheduleAt(
        itemId: Long,
        title: String,
        description: String,
        type: String,
        triggerMillis: Long
    ) {
        val pending = buildPendingIntent(itemId, title, description, type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    private fun buildPendingIntent(
        itemId: Long,
        title: String,
        description: String,
        type: String
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_DESCRIPTION, description)
            putExtra(AlarmReceiver.EXTRA_TYPE, type)
        }
        return PendingIntent.getBroadcast(
            context, itemId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
