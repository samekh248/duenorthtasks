package app.duenorth.tasks.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs one [SyncEngine.sync] in the background. WorkManager builds it with its default factory;
 * the engine comes from Hilt through [SyncEntryPoint], so the app needs no custom WorkManager setup.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java)
        val engine = entry.syncEngine()
        val result = engine.sync()
        if (engine.backfillPending) entry.syncScheduler().backfillSoon()
        return result.toWorkResult(runAttemptCount)
    }
}

/** Runs [SyncEngine.backfill], the history load a first sync leaves behind, as its own job. */
class BackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncEngine()
        return engine.backfill().toWorkResult(runAttemptCount)
    }
}

/**
 * One sync for a 5 or 10 minute interval, shorter than WorkManager's periodic minimum. It always
 * schedules the next one, even after a failure, so the chain never stops; [SyncWorker] retries.
 */
class SyncTickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java)
        val engine = entry.syncEngine()
        try {
            engine.sync()
            if (engine.backfillPending) entry.syncScheduler().backfillSoon()
        } finally {
            entry.syncScheduler().nextTick()
        }
        return Result.success()
    }
}

private const val MAX_RETRIES = 8

private fun SyncResult.toWorkResult(runAttemptCount: Int): ListenableWorker.Result = when (this) {
    SyncResult.Success, SyncResult.NoAccount, SyncResult.NeedsSignIn -> ListenableWorker.Result.success()
    is SyncResult.Retry -> if (runAttemptCount < MAX_RETRIES) {
        ListenableWorker.Result.retry()
    } else {
        ListenableWorker.Result.failure()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncEngine(): SyncEngine

    fun syncScheduler(): SyncScheduler
}
