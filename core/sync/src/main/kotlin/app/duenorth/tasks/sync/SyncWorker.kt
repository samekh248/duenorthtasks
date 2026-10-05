package app.duenorth.tasks.sync

import android.content.Context
import androidx.work.CoroutineWorker
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
        val engine = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncEngine()
        return when (val result = engine.sync()) {
            SyncResult.Success, SyncResult.NoAccount, SyncResult.NeedsSignIn -> Result.success()
            is SyncResult.Retry -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 8
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncEngine(): SyncEngine
}
