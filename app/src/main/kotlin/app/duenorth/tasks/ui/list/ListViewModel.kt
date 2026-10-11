package app.duenorth.tasks.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.settings.ListShades
import app.duenorth.tasks.settings.PinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.HeldLists
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.PendingAdds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.home.ListRowUi
import app.duenorth.tasks.ui.home.toUi
import app.duenorth.tasks.ui.templates.TaskTemplatePick
import app.duenorth.tasks.ui.templates.taskTemplatePicks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ListSort(val label: String) {
    MY_ORDER("my order"),
    DUE("due date"),
    TITLE("title")
}

@Immutable
data class ListUiState(
    val loading: Boolean = true,
    /** False once the list is gone (deleted here or remotely); the page closes itself. */
    val exists: Boolean = true,
    val title: String = "",
    /** Shared, and by whom (spec 002); decides the header line and whether rename/delete show. */
    val sharing: ListSharing = ListSharing.PRIVATE,
    /** Pinned to the home panorama as its own section; decides "pin" or "unpin" in the menu. */
    val pinned: Boolean = false,
    val open: List<TaskRowUi> = emptyList(),
    val completed: List<TaskRowUi> = emptyList(),
    val completedExpanded: Boolean = false,
    val sort: ListSort = ListSort.MY_ORDER,
    val lists: List<ListRowUi> = emptyList(),
    val serviceName: String = "",
    val today: LocalDate = LocalDate.MIN,
    /** The connected service has an importance star (To Do); false hides it everywhere. */
    val importance: Boolean = false,
    /** Reorder mode (specs/003-reordering US1): grippers, no add box, no completed group. */
    val reordering: Boolean = false,
    /** The row to bring into view when reorder mode opens from its long-press menu. */
    val reorderFrom: String? = null,
    /** The one-time "order stays on this phone" note (FR-231), for services that don't store order. */
    val orderNote: Boolean = false,
    /** Tasks added on this page, which a touch hold lets in at once. */
    val added: Set<String> = emptySet(),
    /** Offered under the "add a task" box (spec 004 FR-324). */
    val taskTemplates: List<TaskTemplatePick> = emptyList()
)

private data class ListPrefs(
    val sort: ListSort = ListSort.MY_ORDER,
    val completedExpanded: Boolean = false,
    val reordering: Boolean = false,
    val reorderFrom: String? = null,
    /** Completed tasks "clear completed" took away, hidden until the write that deletes them lands. */
    val cleared: Set<String> = emptySet()
)

private data class OrderFeatures(val storesOrder: Boolean, val noteShown: Boolean)

/** One list's page (T028): open tasks in the chosen order and a collapsible "completed" group. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val templates: TemplateRepository,
    private val shades: ListShades,
    accounts: AccountRepository,
    features: ServiceFeatures,
    private val holds: ListHolds,
    private val listOrder: ListOrder,
    private val pins: PinnedLists,
    clock: Clock
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "list route needs an id" }

    private val overrides = CompletionOverrides()
    private val adds = PendingAdds()
    private val heldLists = HeldLists(holds)
    private val prefs = MutableStateFlow(ListPrefs())
    private var pinned = false

    private val content = combine(
        tasks.list(listId),
        tasks.openTasks(listId),
        tasks.completedTasks(listId),
        todayFlow(clock)
    ) { list, open, done, today ->
        val shown = list?.takeUnless { it.deletedLocally }
        ListContent(shown?.title, open, done, today, ListSharing.of(shown?.isShared == true, shown?.isOwner != false))
    }

    val state: StateFlow<ListUiState> = combine(
        content,
        combine(tasks.listSummaries(), listOrder.rank, pins.pinned) { lists, rank, pinned ->
            ListOrder.sort(lists, rank) { it.localId } to (listId in pinned)
        },
        combine(
            accounts.account,
            features.importance,
            combine(features.storesOrder, listOrder.noteShown, ::OrderFeatures)
        ) { account, importance, order -> Triple(account, importance, order) },
        prefs,
        combine(overrides.overrides, adds.pending, taskTemplatePicks(templates, features.importance), ::Triple)
    ) { content, (lists, pinned), (account, importance, order), prefs, (pending, added, picks) ->
        val all = content.open + content.done
        val ids = all.mapTo(HashSet()) { it.task.localId }
        adds.settle(ids)
        overrides.settle(all.associate { it.task.localId to it.task.completed }, waiting = adds.waiting())
        // Once the clear is written the rows are gone; one put back by the service shows again.
        if (prefs.cleared.any { it !in ids }) settleCleared(ids)
        val rows = all.map { it.toRow(content.today, pending[it.task.localId], importance) }
        val (done, open) = rows.partition { it.completed }
        val title = content.title.orEmpty()
        ListUiState(
            loading = false,
            exists = content.title != null,
            title = title,
            sharing = content.sharing,
            pinned = pinned,
            open = PendingAdds.merge(
                sorted(open, all, prefs.sort),
                // The list's name may have changed since the add; the caption follows it.
                PendingAdds.ticked(added.rows, pending).map {
                    it.copy(caption = DueText.caption(title, it.due, content.today, it.completed))
                }
            ) { new, row ->
                when (prefs.sort) {
                    ListSort.MY_ORDER -> true // a new task's order key is the list's top
                    ListSort.DUE -> row.due == null
                    ListSort.TITLE -> row.title.lowercase() > new.title.lowercase()
                }
            },
            completed = if (prefs.cleared.isEmpty()) done else done.filterNot { it.id in prefs.cleared },
            completedExpanded = prefs.completedExpanded,
            sort = prefs.sort,
            lists = lists.map { it.toUi() },
            serviceName = serviceName(account?.provider),
            today = content.today,
            importance = importance,
            reordering = prefs.reordering,
            reorderFrom = prefs.reorderFrom,
            orderNote = prefs.reordering && !order.storesOrder && !order.noteShown,
            added = added.added,
            taskTemplates = picks
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun setSort(sort: ListSort) = prefs.update { it.copy(sort = sort) }

    /** Opens reorder mode in "my order" (FR-207), holding sync for this list until it ends (FR-228). */
    fun startReorder(from: String? = null) {
        if (!pinned) holds.pin(listId)
        pinned = true
        prefs.update { it.copy(sort = ListSort.MY_ORDER, reordering = true, reorderFrom = from) }
    }

    fun endReorder() {
        if (pinned) holds.unpin(listId)
        pinned = false
        prefs.update { it.copy(reordering = false, reorderFrom = null) }
    }

    fun dismissOrderNote() {
        viewModelScope.launch { runCatching { listOrder.markNoteShown() } }
    }

    /** Saves a drop: [order] is every open task id in its new order (FR-220, FR-241). */
    fun reorder(moved: String, order: List<String>) {
        val index = order.indexOf(moved)
        if (index < 0) return
        viewModelScope.launch {
            runCatching { tasks.moveTask(moved, order.getOrNull(index - 1), order.getOrNull(index + 1)) }
        }
    }

    fun toggleCompletedGroup() = prefs.update { it.copy(completedExpanded = !it.completedExpanded) }

    /**
     * Deletes every completed task in this list. The rows leave the page at once; the write and,
     * later, the sync follow.
     */
    fun clearCompleted() {
        val ids = state.value.completed.mapTo(HashSet()) { it.id }
        if (ids.isEmpty()) return
        prefs.update { it.copy(cleared = it.cleared + ids, completedExpanded = false) }
        viewModelScope.launch {
            runCatching { tasks.clearCompleted(listId) }.onFailure {
                prefs.update { it.copy(cleared = it.cleared - ids) }
            }
        }
    }

    private fun settleCleared(present: Set<String>) = prefs.update { it.copy(cleared = it.cleared intersect present) }

    fun setCompleted(id: String, completed: Boolean) {
        overrides.set(id, completed)
        viewModelScope.launch {
            runCatching { tasks.setCompleted(id, completed) }.onFailure { overrides.clear(id) }
        }
    }

    /** Shows the task at once, before the write; the stored row replaces it under the same id. */
    fun addTask(title: String) {
        if (title.isBlank()) return
        add(title, details = null, due = null) { id -> tasks.createTask(listId, title, id = id) }
    }

    /** Adds task template [pick] to this list like a typed task, due today plus its offset (spec 004 US2). */
    fun addFromTemplate(pick: TaskTemplatePick) {
        val today = state.value.today
        val due = pick.dueOffset?.let { today.plusDays(it.toLong()) }
        add(pick.title, pick.details, due) { id -> templates.useTaskTemplate(pick.id, listId, today, id) }
    }

    /** Saves task [id] as a task template and hands the template's id to [then] (spec 004 US3). */
    fun saveTaskAsTemplate(id: String, then: (String) -> Unit) {
        viewModelScope.launch { runCatching { templates.saveTaskAsTemplate(id) }.onSuccess(then) }
    }

    /** Saves this list, with its shade, as a list template and hands its id to [then] (spec 004 US3). */
    fun saveAsTemplate(then: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val step = shades.byList.first()[listId] ?: 0
                templates.saveListAsTemplate(listId, step).id
            }.onSuccess(then)
        }
    }

    private fun add(title: String, details: String?, due: LocalDate?, write: suspend (id: String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val today = state.value.today
        val title = title.trim()
        val row = TaskRowUi(
            id = id,
            listId = listId,
            title = title,
            details = details?.trimEnd()?.takeIf { it.isNotBlank() },
            caption = due?.let {
                DueText.caption(state.value.title, it, today, completed = false)
            } ?: state.value.title,
            overdue = due != null && due.isBefore(today),
            completed = false,
            due = due
        )
        adds.add(id, row)
        viewModelScope.launch {
            runCatching { write(id) }.onFailure { adds.clear(id) }
        }
    }

    fun setImportant(id: String, important: Boolean) {
        viewModelScope.launch { runCatching { tasks.editTask(id, TaskEdit(important = important)) } }
    }

    /** Holds sync for this list while a finger is on it or it is still flinging (FR-008). */
    fun holdSync(active: Boolean) = heldLists.set(active) { listOf(listId) }

    override fun onCleared() {
        heldLists.releaseAll()
        if (pinned) holds.unpin(listId)
    }

    fun deleteTask(id: String) {
        adds.clear(id)
        viewModelScope.launch { runCatching { tasks.deleteTask(id) } }
    }

    fun moveTask(id: String, toList: String) {
        viewModelScope.launch { runCatching { tasks.editTask(id, TaskEdit(listId = toList)) } }
    }

    fun rename(title: String) {
        viewModelScope.launch { runCatching { tasks.renameList(listId, title) } }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { pins.unpin(listId) }
            runCatching { tasks.deleteList(listId) }
        }
    }

    /** Pins this list to the home panorama as its own section, or takes it off. */
    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { runCatching { if (pinned) pins.pin(listId) else pins.unpin(listId) } }
    }

    private fun sorted(rows: List<TaskRowUi>, source: List<TaskWithList>, sort: ListSort): List<TaskRowUi> {
        val byId = source.associateBy { it.task.localId }
        return when (sort) {
            // The service's own order: its position string, newest first when it has none yet.
            ListSort.MY_ORDER -> rows.sortedWith(compareBy(OrderKeys.taskComparator) { byId.getValue(it.id).task })
            ListSort.DUE -> rows.sortedWith(compareBy(nullsLast()) { byId[it.id]?.task?.dueDate })
            ListSort.TITLE -> rows.sortedBy { it.title.lowercase() }
        }
    }
}

private data class ListContent(
    val title: String?,
    val open: List<TaskWithList>,
    val done: List<TaskWithList>,
    val today: LocalDate,
    val sharing: ListSharing
)
