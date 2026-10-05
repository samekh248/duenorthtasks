package app.duenorth.tasks.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.ListSummary
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.HeldLists
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.TickLinger
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ListRowUi(val id: String, val title: String, val openCount: Int, val next: String?)

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    /** Overdue first, then due today. */
    val dueToday: List<TaskRowUi> = emptyList(),
    val tomorrow: List<TaskRowUi> = emptyList(),
    val lists: List<ListRowUi> = emptyList(),
    val done: List<TaskRowUi> = emptyList(),
    val serviceName: String = "",
    /** The connected service has an importance star (To Do); false hides it everywhere. */
    val importance: Boolean = false
)

/**
 * The home panorama's three sections, read straight from Room (constitution Principle IV), with
 * ticks shown before the write lands (FR-006). Mapping runs off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tasks: TaskRepository,
    accounts: AccountRepository,
    features: ServiceFeatures,
    holds: ListHolds,
    clock: Clock
) : ViewModel() {
    private val overrides = CompletionOverrides()
    private val linger = TickLinger(viewModelScope)

    /** The sections as last shown, which [TickLinger] keeps a just-ticked row in. */
    private var shown = HomeUiState()
    private val heldLists = HeldLists(holds)

    private val today = todayFlow(clock)

    private val due = today.flatMapLatest { day -> tasks.tasksDueBy(day.plusDays(1)).map { day to it } }

    val state: StateFlow<HomeUiState> = combine(
        due,
        tasks.recentlyCompleted(),
        tasks.listSummaries(),
        combine(accounts.account, features.importance, ::Pair),
        combine(overrides.overrides, linger.ticked, ::Pair)
    ) { (day, dueRows), doneRows, lists, (account, importance), (pending, ticked) ->
        overrides.settle((dueRows + doneRows).associate { it.task.localId to it.task.completed })
        val (soon, later) = dueRows.partition { it.task.dueDate?.isAfter(day) == false }
        HomeUiState(
            loading = false,
            today = day,
            dueToday = TickLinger.keep(
                shown.dueToday,
                soon.map {
                    it.toRow(day, pending[it.task.localId], importance)
                },
                ticked
            ),
            tomorrow = TickLinger.keep(
                shown.tomorrow,
                later.map {
                    it.toRow(day, pending[it.task.localId], importance)
                },
                ticked
            ),
            lists = lists.map(ListSummary::toUi),
            done = TickLinger.keep(
                shown.done,
                doneRows.map {
                    it.toRow(day, pending[it.task.localId], importance)
                },
                ticked
            ),
            serviceName = serviceName(account?.provider),
            importance = importance
        ).also { shown = it }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setCompleted(id: String, completed: Boolean) {
        linger.ticked(id, completed)
        overrides.set(id, completed)
        viewModelScope.launch {
            runCatching { tasks.setCompleted(id, completed) }.onFailure { overrides.clear(id) }
        }
    }

    /** Adds from the "add a task" box: due today in the default list unless details say otherwise. */
    fun addTask(title: String, details: String? = null, due: LocalDate? = state.value.today, listId: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val list = listId ?: tasks.defaultListIdOrCreate()
            tasks.createTask(list, title, notes = details, dueDate = due)
        }
    }

    fun setImportant(id: String, important: Boolean) {
        viewModelScope.launch { runCatching { tasks.editTask(id, TaskEdit(important = important)) } }
    }

    /**
     * Today and done mix every list, so a finger on either holds sync for all of them until it
     * lifts and the fling settles.
     */
    fun holdSync(active: Boolean) = heldLists.set(active) { state.value.lists.map { it.id } }

    override fun onCleared() = heldLists.releaseAll()

    fun deleteTask(id: String) {
        viewModelScope.launch { tasks.deleteTask(id) }
    }

    fun moveTask(id: String, listId: String) {
        viewModelScope.launch { tasks.editTask(id, TaskEdit(listId = listId)) }
    }

    fun createList(title: String) {
        viewModelScope.launch { runCatching { tasks.createList(title) } }
    }

    fun renameList(id: String, title: String) {
        viewModelScope.launch { runCatching { tasks.renameList(id, title) } }
    }

    fun deleteList(id: String) {
        viewModelScope.launch { tasks.deleteList(id) }
    }
}

private fun ListSummary.toUi() = ListRowUi(id = localId, title = title, openCount = openCount, next = nextTaskTitle)
