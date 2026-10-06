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
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.HeldLists
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.home.ListRowUi
import app.duenorth.tasks.ui.home.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val orderNote: Boolean = false
)

private data class ListPrefs(
    val sort: ListSort = ListSort.MY_ORDER,
    val completedExpanded: Boolean = false,
    val reordering: Boolean = false,
    val reorderFrom: String? = null
)

private data class OrderFeatures(val storesOrder: Boolean, val noteShown: Boolean)

/** One list's page (T028): open tasks in the chosen order and a collapsible "completed" group. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    accounts: AccountRepository,
    features: ServiceFeatures,
    private val holds: ListHolds,
    private val listOrder: ListOrder,
    clock: Clock
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "list route needs an id" }

    private val overrides = CompletionOverrides()
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
        combine(tasks.listSummaries(), listOrder.rank) { lists, rank -> ListOrder.sort(lists, rank) { it.localId } },
        combine(
            accounts.account,
            features.importance,
            combine(features.storesOrder, listOrder.noteShown, ::OrderFeatures)
        ) { account, importance, order -> Triple(account, importance, order) },
        prefs,
        overrides.overrides
    ) { content, lists, (account, importance, order), prefs, pending ->
        overrides.settle((content.open + content.done).associate { it.task.localId to it.task.completed })
        val all = content.open + content.done
        val rows = all.map { it.toRow(content.today, pending[it.task.localId], importance) }
        val (done, open) = rows.partition { it.completed }
        ListUiState(
            loading = false,
            exists = content.title != null,
            title = content.title.orEmpty(),
            sharing = content.sharing,
            open = sorted(open, all, prefs.sort),
            completed = done,
            completedExpanded = prefs.completedExpanded,
            sort = prefs.sort,
            lists = lists.map { it.toUi() },
            serviceName = serviceName(account?.provider),
            today = content.today,
            importance = importance,
            reordering = prefs.reordering,
            reorderFrom = prefs.reorderFrom,
            orderNote = prefs.reordering && !order.storesOrder && !order.noteShown
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

    fun setCompleted(id: String, completed: Boolean) {
        overrides.set(id, completed)
        viewModelScope.launch {
            runCatching { tasks.setCompleted(id, completed) }.onFailure { overrides.clear(id) }
        }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { runCatching { tasks.createTask(listId, title) } }
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
        viewModelScope.launch { runCatching { tasks.deleteTask(id) } }
    }

    fun moveTask(id: String, toList: String) {
        viewModelScope.launch { runCatching { tasks.editTask(id, TaskEdit(listId = toList)) } }
    }

    fun rename(title: String) {
        viewModelScope.launch { runCatching { tasks.renameList(listId, title) } }
    }

    fun delete() {
        viewModelScope.launch { runCatching { tasks.deleteList(listId) } }
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
