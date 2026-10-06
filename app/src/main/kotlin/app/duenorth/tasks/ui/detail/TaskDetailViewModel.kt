package app.duenorth.tasks.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.home.ListRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class StepUi(val id: String, val title: String, val done: Boolean)

@Immutable
data class TaskDetailUiState(
    val loading: Boolean = true,
    /** False once the task is gone (deleted here or remotely); the page closes itself. */
    val exists: Boolean = true,
    val title: String = "",
    val details: String? = null,
    val due: LocalDate? = null,
    val completed: Boolean = false,
    val listId: String = "",
    val listTitle: String = "",
    val steps: List<StepUi> = emptyList(),
    val lists: List<ListRowUi> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    val important: Boolean = false,
    /** The connected service has an importance star (To Do only, FR-014). */
    val importance: Boolean = false,
    /** Reorder steps mode (specs/003-reordering US3). */
    val reordering: Boolean = false,
    /** The step to bring into view when reorder mode opens from its long-press menu. */
    val reorderFrom: String? = null,
    /** The one-time "order stays on this phone" note (FR-231). */
    val orderNote: Boolean = false,
    val serviceName: String = ""
)

private data class StepReorder(val active: Boolean = false, val from: String? = null)

/** One task's page (T030), straight from Room; every change is a repository write. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    features: ServiceFeatures,
    private val holds: ListHolds,
    private val listOrder: ListOrder,
    accounts: AccountRepository,
    clock: Clock
) : ViewModel() {
    val taskId: String = checkNotNull(savedState["id"]) { "task route needs an id" }

    /** The tick shows at once (FR-006); cleared when Room agrees. */
    private val completedOverride = MutableStateFlow<Boolean?>(null)

    private val reorder = MutableStateFlow(StepReorder())

    /** The list held from sync while reorder mode is open (FR-228). */
    private var pinnedList: String? = null

    private val task = tasks.task(taskId).map { it?.takeUnless { t -> t.deletedLocally } }

    private val listTitle = task.flatMapLatest { t ->
        if (t == null) flowOf("") else tasks.list(t.listId).map { it?.title.orEmpty() }
    }

    val state: StateFlow<TaskDetailUiState> = combine(
        combine(task, listTitle, ::Pair),
        tasks.steps(taskId),
        combine(tasks.listSummaries(), listOrder.rank) { lists, rank -> ListOrder.sort(lists, rank) { it.localId } },
        combine(todayFlow(clock), features.importance, ::Pair),
        combine(
            completedOverride,
            reorder,
            combine(features.storesOrder, listOrder.noteShown) { stores, shown -> !stores && !shown },
            accounts.account
        ) { override, reorder, noteDue, account ->
            DetailExtras(override, reorder, noteDue, serviceName(account?.provider))
        }
    ) { (task, listTitle), steps, lists, (today, importance), extras ->
        val override = extras.override
        if (task == null) return@combine TaskDetailUiState(loading = false, exists = false)
        if (override == task.completed) completedOverride.value = null
        TaskDetailUiState(
            loading = false,
            title = task.title,
            details = task.notes,
            due = task.dueDate,
            completed = override ?: task.completed,
            listId = task.listId,
            listTitle = listTitle,
            steps = steps.map { StepUi(it.localId, it.title, it.done) },
            lists = lists.map { ListRowUi(it.localId, it.title, it.openCount, it.nextTaskTitle) },
            today = today,
            important = importance && task.important,
            importance = importance,
            reordering = extras.reorder.active,
            reorderFrom = extras.reorder.from,
            orderNote = extras.reorder.active && extras.noteDue,
            serviceName = extras.serviceName
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    fun setCompleted(completed: Boolean) {
        completedOverride.value = completed
        viewModelScope.launch {
            runCatching { tasks.setCompleted(taskId, completed) }.onFailure { completedOverride.value = null }
        }
    }

    /** Saves the edit form; a blank title keeps the old one. */
    fun save(title: String, details: String) = edit(
        TaskEdit(
            title = title.takeIf { it.isNotBlank() },
            notes = if (details.isBlank()) Patch.Clear else Patch.Set(details.trim())
        )
    )

    fun setDue(due: LocalDate?) = edit(TaskEdit(dueDate = if (due == null) Patch.Clear else Patch.Set(due)))

    fun setImportant(important: Boolean) = edit(TaskEdit(important = important))

    fun moveTo(listId: String) = edit(TaskEdit(listId = listId))

    fun addStep(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { runCatching { tasks.addStep(taskId, title) } }
    }

    fun setStepDone(id: String, done: Boolean) {
        viewModelScope.launch { runCatching { tasks.editStep(id, done = done) } }
    }

    fun removeStep(id: String) {
        viewModelScope.launch { runCatching { tasks.removeStep(id) } }
    }

    /** Opens reorder steps mode, holding sync for the task's list until it ends (FR-228). */
    fun startReorder(from: String? = null) {
        val listId = state.value.listId
        if (pinnedList == null && listId.isNotEmpty()) {
            holds.pin(listId)
            pinnedList = listId
        }
        reorder.update { StepReorder(active = true, from = from) }
    }

    fun endReorder() {
        pinnedList?.let(holds::unpin)
        pinnedList = null
        reorder.update { StepReorder() }
    }

    fun dismissOrderNote() {
        viewModelScope.launch { runCatching { listOrder.markNoteShown() } }
    }

    /** Saves a drop: [order] is every step id in its new order (FR-222, FR-223). */
    fun reorderSteps(moved: String, order: List<String>) {
        viewModelScope.launch { runCatching { tasks.moveStep(taskId, moved, order) } }
    }

    override fun onCleared() {
        pinnedList?.let(holds::unpin)
    }

    fun delete() {
        viewModelScope.launch { runCatching { tasks.deleteTask(taskId) } }
    }

    private fun edit(edit: TaskEdit) {
        viewModelScope.launch { runCatching { tasks.editTask(taskId, edit) } }
    }
}

private data class DetailExtras(
    val override: Boolean?,
    val reorder: StepReorder,
    val noteDue: Boolean,
    val serviceName: String
)
