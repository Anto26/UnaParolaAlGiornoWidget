package com.example.unaparoladelgiorno.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.glance.appwidget.updateAll
import androidx.work.WorkerParameters
import com.example.unaparoladelgiorno.data.WordRepository
import com.example.unaparoladelgiorno.widget.WordWidget

class WordRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = WordRepository(applicationContext).refresh()
        return if (outcome.isSuccess) {
            WordWidget().updateAll(applicationContext)
            Result.success()
        } else {
            // Transient network hiccups are common overnight; let WorkManager
            // retry with backoff instead of leaving a stale/empty widget.
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
