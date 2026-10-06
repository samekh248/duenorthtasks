package app.duenorth.tasks.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.ListSummary
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.HeldLists
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.PendingAdds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.TickLinger
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.templates.TaskTemplatePick
import app.duenorth.tasks.ui.templates.taskTemplatePicks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
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
data class ListRowUi(
    val id: String,
    val title: String,
    val openCount: Int,
    val next: String?,
    val sharing: ListSharing = ListSharing.PRIVATE
)

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
    val importance: Boolean = false,
    /** Tasks added on this screen, which a touch hold lets in at once. */
    val added: Set<String> = emptySet(),
    /** Offered under the "add a task" box (spec 004 FR-324). */
    val taskTemplates: List<TaskTemplatePick> = emptyList()
)

/**
 * The home panorama's three sections, read straight from Room (constitution Principle IV), with
 * ticks shown before the write lands (FR-006). Mapping runs off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val templates: TemplateRepository,
    accounts: AccountRepository,
    features: ServiceFeatures,
    holds: ListHolds,
    listOrder: ListOrder,
    clock: Clock
) : ViewModel() {
    private val overrides = CompletionOverrides()
    private val linger = TickLinger(viewModelScope)
    private val adds = PendingAdds()

    /** The lists as last read, for the list name on a task shown before its write lands. */
    @Volatile private var knownLists: List<ListSummary> = emptyList()

    /** The sections as last shown, which [TickLinger] keeps a just-ticked row in. */
    private var shown = HomeUiState()
    private val heldLists = HeldLists(holds)

    private val today = todayFlow(clock)

    private val due = today.flatMapLatest { day -> tasks.tasksDueBy(day.plusDays(1)).map { day to it } }

    val state: StateFlow<HomeUiState> = combine(
        due,
        tasks.recentlyCompleted(),
        combine(tasks.listSummaries(), listOrder.rank) { lists, rank -> ListOrder.sort(lists, rank) { it.localId } },
        combine(accounts.account, features.importance, taskTemplatePicks(templates, features.importance), ::Triple),
        combine(overrides.overrides, linger.ticked, adds.pending, ::Triple)
    ) { (day, dueRows), doneRows, lists, (account, importance, picks), (pending, ticked, added) ->
        knownLists = lists
        overrides.settle((dueRows + doneRows).associate { it.task.localId to it.task.completed })
        adds.settle(dueRows.mapTo(HashSet()) { it.task.localId })
        val (soon, later) = dueRows.partition { it.task.dueDate?.isAfter(day) == false }
        val (addedSoon, addedLater) = added.rows.partition { it.due?.isAfter(day) == false }
        HomeUiState(
            loading = false,
            today = day,
            dueToday = TickLinger.keep(
                shown.dueToday,
                PendingAdds.merge(
                    soon.map { it.toRow(day, pending[it.task.localId], importance) },
                    addedSoon,
                    ::dueFirst
                ),
                ticked
            ),
            tomorrow = TickLinger.keep(
                shown.tomorrow,
                PendingAdds.merge(
                    later.map { it.toRow(day, pending[it.task.localId], importance) },
                    addedLater,
                    ::dueFirst
                ),
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
            importance = importance,
            added = added.added,
            taskTemplates = picks
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

    /**
     * Adds from the "add a task" box: due today in the default list unless details say otherwise.
     * The row shows at once, before the write, and the stored row replaces it under the same id.
     */
    fun addTask(title: String, details: String? = null, due: LocalDate? = state.value.today, listId: String? = null) {
        if (title.isBlank()) return
        add(title, details, due, listId) { list, id ->
            tasks.createTask(list, title, notes = details, dueDate = due, id = id)
        }
    }

    /** Adds task template [pick] like a typed task, due today plus its offset (spec 004 US2). */
    fun addFromTemplate(pick: TaskTemplatePick, listId: String? = null) {
        val today = state.value.today
        val due = pick.dueOffset?.let { today.plusDays(it.toLong()) }
        add(pick.title, pick.details, due, listId) { list, id -> templates.useTaskTemplate(pick.id, list, today, id) }
    }

    /** Saves task [id] as a task template and hands the template's id to [then] (spec 004 US3). */
    fun saveAsTemplate(id: String, then: (String) -> Unit) {
        viewModelScope.launch { runCatching { templates.saveTaskAsTemplate(id) }.onSuccess(then) }
    }

    private fun add(
        title: String,
        details: String?,
        due: LocalDate?,
        listId: String?,
        write: suspend (listId: String, id: String) -> Unit
    ) {
        val id = UUID.randomUUID().toString()
        val today = state.value.today
        val list = knownLists.firstOrNull { if (listId != null) it.localId == listId else it.isDefault }
        // Only today and tomorrow are on this page; a later task just goes to its list.
        val shownHere = due != null && today != LocalDate.MIN && !due.isAfter(today.plusDays(1))
        val row = if (list != null && due != null && shownHere) {
            TaskRowUi(
                id = id,
                listId = list.localId,
                title = title.trim(),
                details = details?.trimEnd()?.takeIf { it.isNotBlank() },
                caption = DueText.caption(list.title, due, today, completed = false),
                overdue = due.isBefore(today),
                completed = false,
                due = due
            )
        } else {
            null
        }
        adds.add(id, row)
        viewModelScope.launch {
            runCatching { write(listId ?: tasks.defaultListIdOrCreate(), id) }.onFailure { adds.clear(id) }
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
        adds.clear(id)
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

/** Today's order: by due date, a new task above the others due the same day (its order key is the top). */
private fun dueFirst(added: TaskRowUi, row: TaskRowUi): Boolean =
    row.due == null || added.due == null || !row.due.isBefore(added.due)

internal fun ListSummary.toUi() = ListRowUi(
    id = localId,
    title = title,
    openCount = openCount,
    next = nextTaskTitle,
    sharing = ListSharing.of(isShared, isOwner)
)
