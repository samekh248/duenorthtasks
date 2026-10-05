package app.duenorth.tasks.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.HeldLists
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.home.ListRowUi
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
    val open: List<TaskRowUi> = emptyList(),
    val completed: List<TaskRowUi> = emptyList(),
    val completedExpanded: Boolean = false,
    val sort: ListSort = ListSort.MY_ORDER,
    val lists: List<ListRowUi> = emptyList(),
    val serviceName: String = "",
    val today: LocalDate = LocalDate.MIN,
    /** The connected service has an importance star (To Do); false hides it everywhere. */
    val importance: Boolean = false
)

private data class ListPrefs(val sort: ListSort = ListSort.MY_ORDER, val completedExpanded: Boolean = false)

/** One list's page (T028): open tasks in the chosen order and a collapsible "completed" group. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    accounts: AccountRepository,
    features: ServiceFeatures,
    holds: ListHolds,
    clock: Clock
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "list route needs an id" }

    private val overrides = CompletionOverrides()
    private val heldLists = HeldLists(holds)
    private val prefs = MutableStateFlow(ListPrefs())

    private val content = combine(
        tasks.list(listId),
        tasks.openTasks(listId),
        tasks.completedTasks(listId),
        todayFlow(clock)
    ) { list, open, done, today -> ListContent(list?.takeUnless { it.deletedLocally }?.title, open, done, today) }

    val state: StateFlow<ListUiState> = combine(
        content,
        tasks.listSummaries(),
        combine(accounts.account, features.importance, ::Pair),
        prefs,
        overrides.overrides
    ) { content, lists, (account, importance), prefs, pending ->
        overrides.settle((content.open + content.done).associate { it.task.localId to it.task.completed })
        val all = content.open + content.done
        val rows = all.map { it.toRow(content.today, pending[it.task.localId], importance) }
        val (done, open) = rows.partition { it.completed }
        ListUiState(
            loading = false,
            exists = content.title != null,
            title = content.title.orEmpty(),
            open = sorted(open, all, prefs.sort),
            completed = done,
            completedExpanded = prefs.completedExpanded,
            sort = prefs.sort,
            lists = lists.map { ListRowUi(it.localId, it.title, it.openCount, it.nextTaskTitle) },
            serviceName = serviceName(account?.provider),
            today = content.today,
            importance = importance
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun setSort(sort: ListSort) = prefs.update { it.copy(sort = sort) }

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

    override fun onCleared() = heldLists.releaseAll()

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
            ListSort.MY_ORDER -> rows.sortedWith(
                compareBy<TaskRowUi, String?>(nullsFirst()) { byId[it.id]?.task?.position }
                    .thenByDescending { byId[it.id]?.task?.localUpdatedAt }
            )
            ListSort.DUE -> rows.sortedWith(compareBy(nullsLast()) { byId[it.id]?.task?.dueDate })
            ListSort.TITLE -> rows.sortedBy { it.title.lowercase() }
        }
    }
}

private data class ListContent(
    val title: String?,
    val open: List<TaskWithList>,
    val done: List<TaskWithList>,
    val today: LocalDate
)
