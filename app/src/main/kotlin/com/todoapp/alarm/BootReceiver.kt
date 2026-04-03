package com.todoapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.todoapp.domain.repository.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reschedules all upcoming alarms after a device reboot, since AlarmManager
 * alarms are cleared when the device powers off.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = repository.observeAll().first()
                items.filter { !it.isCompleted }
                    .forEach { alarmScheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
