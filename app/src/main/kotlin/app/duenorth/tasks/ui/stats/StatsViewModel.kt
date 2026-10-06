package app.duenorth.tasks.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.CompletedStat
import app.duenorth.tasks.data.db.OpenStat
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.sync.SyncEngine
import app.duenorth.tasks.ui.common.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

/**
 * Stats from Room, recounted off the main thread at most once a second while tasks change, as a
 * sync does (spec 005 FR-430, FR-433).
 */
class StatsSource(
    tasks: TaskRepository,
    historyLoading: Flow<Boolean>,
    clock: Clock,
    firstDayOfWeek: () -> DayOfWeek = { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
) {
    val stats: Flow<StatsUi> = combine(
        todayFlow(clock),
        tasks.completedStats(),
        tasks.openStats(),
        historyLoading
    ) { today, completed, open, partial -> Inputs(today, completed, open, partial) }
        .conflate()
        .map { Stats.compute(it.completed, it.open, it.today, clock.zone, firstDayOfWeek(), it.partial) }
        .transform {
            emit(it)
            delay(MIN_INTERVAL_MS)
        }
        .flowOn(Dispatchers.Default)

    private data class Inputs(
        val today: LocalDate,
        val completed: List<CompletedStat>,
        val open: List<OpenStat>,
        val partial: Boolean
    )

    private companion object {
        const val MIN_INTERVAL_MS = 1_000L
    }
}

/**
 * Counts nothing until the stats section first comes near the screen ([start]), so the home
 * screen's cold start never pays for it (FR-431).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(tasks: TaskRepository, engine: SyncEngine, clock: Clock) : ViewModel() {
    private val started = MutableStateFlow(false)
    private val source = StatsSource(tasks, engine.historyLoading, clock)

    val state: StateFlow<StatsUi?> = started
        .flatMapLatest { if (it) source.stats else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start() {
        started.value = true
    }
}
