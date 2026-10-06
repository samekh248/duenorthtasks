package app.duenorth.tasks.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * When sync runs (T040): 5 s after the last local edit, whenever the app comes to the
 * foreground, on pull-to-refresh, and every "sync every" interval (default 15 min) in the
 * background. All of it is one unique WorkManager job with a network constraint, so syncs never
 * overlap and never run offline. A first sync's history load ([SyncEngine.backfill]) is a second
 * unique job, so a sync asked for while it runs is not queued behind it.
 */
class SyncScheduler(
    private val workManager: WorkManager,
    private val settings: SyncSettingsStore,
    private val localEdits: Flow<Unit>,
    private val scope: CoroutineScope,
    private val debounce: Duration = 5.seconds
) {
    private var started = false

    /** Call once from `Application.onCreate`. Cheap: it only subscribes, it does no work. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        localEdits.debounce(debounce).onEach { syncNow() }.launchIn(scope)
        settings.settings.onEach { schedulePeriodic(it) }.launchIn(scope)
        scope.launch(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = syncNow()
                }
            )
        }
    }

    /** Sync as soon as the network allows: foreground, pull-to-refresh and debounced edits. */
    fun syncNow() {
        scope.launch {
            val settings = currentSettings()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(settings.wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            // Appending keeps a sync that is already running, then runs once more for the new edits.
            // One that is only waiting (to retry after a failure, or for its turn) is replaced, so
            // a tap on sync, or a new task, never waits out another sync's backoff.
            val running = workManager.getWorkInfosForUniqueWorkFlow(NOW).first()
                .any { it.state == WorkInfo.State.RUNNING }
            val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            workManager.enqueueUniqueWork(NOW, policy, request)
            // Brings the periodic sync back after cancelAll (sign-out, then signing in again).
            schedulePeriodic(settings)
        }
    }

    /** Starts the history load a sync left behind, unless it is already queued or running. */
    fun backfillSoon() {
        scope.launch {
            val request = OneTimeWorkRequestBuilder<BackfillWorker>()
                .setConstraints(constraints(currentSettings().wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(BACKFILL, ExistingWorkPolicy.KEEP, request)
        }
    }

    /** Stops every scheduled sync, for sign-out and switching service. */
    fun cancelAll() {
        workManager.cancelUniqueWork(NOW)
        workManager.cancelUniqueWork(BACKFILL)
        workManager.cancelUniqueWork(PERIODIC)
    }

    private fun schedulePeriodic(settings: SyncSettings) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints(settings.wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private suspend fun currentSettings(): SyncSettings = settings.settings.first()

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
        const val BACKFILL = "sync-backfill"
        private const val BACKOFF_SECONDS = 30L
    }
}
