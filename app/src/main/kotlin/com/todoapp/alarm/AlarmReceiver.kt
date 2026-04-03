package com.todoapp.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.todoapp.MainActivity
import com.todoapp.data.remote.google.GmailApiClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var gmailClient: GmailApiClient

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_TYPE = "extra_type"
        const val NOTIFICATION_CHANNEL_ID = "todo_alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "Item"

        showNotification(context, itemId, title, description, type)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                gmailClient.sendReminderEmail(title, description, type)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        itemId: Long,
        title: String,
        description: String,
        type: String
    ) {
        // Tap action — opens the specific item in the app
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val tapPending = PendingIntent.getActivity(
            context, itemId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action — fires SnoozeReceiver
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_TYPE, type)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, (itemId + 10_000).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val contentText = description.ifBlank { "Your $type is due now." }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDD14 $type Due: $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .addAction(
                android.R.drawable.ic_media_next,
                "Snooze 10 min",
                snoozePending
            )
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(itemId.toInt(), notification)
    }
}
