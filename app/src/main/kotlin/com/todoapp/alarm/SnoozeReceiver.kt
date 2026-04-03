package com.todoapp.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SnoozeReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    companion object {
        const val ACTION_SNOOZE = "com.todoapp.ACTION_SNOOZE"
        const val SNOOZE_MINUTES = 10L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(AlarmReceiver.EXTRA_ITEM_ID, -1L)
        val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: return
        val description = intent.getStringExtra(AlarmReceiver.EXTRA_DESCRIPTION) ?: ""
        val type = intent.getStringExtra(AlarmReceiver.EXTRA_TYPE) ?: "Item"

        // Dismiss the existing notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(itemId.toInt())

        // Reschedule alarm SNOOZE_MINUTES from now
        alarmScheduler.scheduleSnooze(itemId, title, description, type, SNOOZE_MINUTES)
    }
}
