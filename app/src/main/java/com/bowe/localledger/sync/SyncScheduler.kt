package com.bowe.localledger.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object SyncScheduler {
    private const val CloudSyncWorkName = "cloud-sync"

    fun enqueue(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CloudSyncWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }
}
