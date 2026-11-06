package app.duenorth.tasks.badge

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.ui.common.todayFlow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Keeps the app icon badge at the today section's count: open tasks that are overdue or due today,
 * in every list. It reads Room, like every screen, so ticks, edits, syncs and midnight all move it.
 * It runs off the main thread and only posts when the count or the titles change.
 */
class TodayBadge(
    private val context: Context,
    private val tasks: TaskRepository,
    private val settings: TodayBadgeSettings,
    private val notifier: TodayNotifier,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    private var started = false

    /** Bumped when notifications are allowed again, which no flow here can see. */
    private val refreshes = MutableStateFlow(0)

    /** Call once from `Application.onCreate`. Cheap: it only subscribes. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        val due: Flow<TodayCount> = todayFlow(clock).flatMapLatest { day ->
            tasks.tasksDueBy(day).map(TodayCount::of)
        }
        combine(settings.enabled, due, refreshes) { enabled, today, refresh ->
            (if (enabled) today else TodayCount.None) to refresh
        }
            .distinctUntilChanged()
            // A sync can land many rows in a row; post once it settles.
            .debounce(DEBOUNCE_MS)
            .onEach { (today, _) -> notifier.show(today) }
            .launchIn(scope)
        scheduleMidnight()
    }

    /** Posts again now, after notifications were allowed or turned back on. */
    fun refresh() = refreshes.update { it + 1 }

    /** One update with no collector, for [TodayBadgeWorker] when the app is not running. */
    suspend fun updateNow() {
        val today = if (settings.enabled.first()) {
            TodayCount.of(tasks.tasksDueBy(LocalDate.now(clock)).first())
        } else {
            TodayCount.None
        }
        notifier.show(today)
    }

    /**
     * Tomorrow's tasks join today at midnight. A running app sees that through [todayFlow]; this
     * daily job catches it when the app is not running.
     */
    private fun scheduleMidnight() {
        val now = LocalDateTime.now(clock)
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).plusMinutes(1)
        val request = PeriodicWorkRequestBuilder<TodayBadgeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(untilMidnight.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(TodayBadgeWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
