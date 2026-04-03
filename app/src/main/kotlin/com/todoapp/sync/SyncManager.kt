package com.todoapp.sync

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager = WorkManager.getInstance(context)

    /** Trigger an immediate one-off sync (e.g. after creating/editing an item). */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME + "_immediate",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Schedule periodic background sync every 30 minutes when network is available. */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME + "_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Emits true whenever the last immediate sync ended in FAILED state,
     * false when it succeeds or is running.
     */
    fun observeSyncFailed(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME + "_immediate")
            .asFlow()
            .map { infos -> infos.any { it.state == WorkInfo.State.FAILED } }

    /** Cancel all scheduled sync work. */
    fun cancelAll() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME + "_periodic")
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME + "_immediate")
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
