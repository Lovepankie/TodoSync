package com.todoapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.todoapp.alarm.AlarmReceiver
import com.todoapp.data.remote.google.GoogleAuthManager
import com.todoapp.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TodoApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var authManager: GoogleAuthManager
    @Inject lateinit var syncManager: SyncManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Restore last Google sign-in session
        authManager.restoreSignIn()
        // Re-schedule periodic sync if already signed in
        if (authManager.isSignedIn) {
            syncManager.schedulePeriodicSync()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                AlarmReceiver.NOTIFICATION_CHANNEL_ID,
                "Due Date Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm sound when a task, event, or reminder is due"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(alarmSound, audioAttrs)
            }
            // Delete old channel first so sound settings take effect
            val nm = getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(AlarmReceiver.NOTIFICATION_CHANNEL_ID)
            nm.createNotificationChannel(channel)
        }
    }
}
