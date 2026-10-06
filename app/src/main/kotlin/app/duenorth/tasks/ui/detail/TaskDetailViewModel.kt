package app.duenorth.tasks.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.AssignmentSource
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.todayFlow
import app.duenorth.tasks.ui.home.ListRowUi
import app.duenorth.tasks.ui.home.toUi
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
    /** "from a google doc" when someone assigned this task in Docs or Chat (spec 002 US4). */
    val assignedFrom: AssignmentSource = AssignmentSource.NONE,
    /** Opens the task where it was assigned. */
    val assignmentLink: String? = null
)

/** One task's page (T030), straight from Room; every change is a repository write. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    features: ServiceFeatures,
    clock: Clock
) : ViewModel() {
    val taskId: String = checkNotNull(savedState["id"]) { "task route needs an id" }

    /** The tick shows at once (FR-006); cleared when Room agrees. */
    private val completedOverride = MutableStateFlow<Boolean?>(null)

    private val task = tasks.task(taskId).map { it?.takeUnless { t -> t.deletedLocally } }

    private val listTitle = task.flatMapLatest { t ->
        if (t == null) flowOf("") else tasks.list(t.listId).map { it?.title.orEmpty() }
    }

    val state: StateFlow<TaskDetailUiState> = combine(
        combine(task, listTitle, ::Pair),
        tasks.steps(taskId),
        tasks.listSummaries(),
        combine(todayFlow(clock), features.importance, ::Pair),
        completedOverride
    ) { (task, listTitle), steps, lists, (today, importance), override ->
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
            lists = lists.map { it.toUi() },
            today = today,
            important = importance && task.important,
            importance = importance,
            assignedFrom = task.assignmentSource,
            assignmentLink = task.assignmentLink
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

    fun delete() {
        viewModelScope.launch { runCatching { tasks.deleteTask(taskId) } }
    }

    private fun edit(edit: TaskEdit) {
        viewModelScope.launch { runCatching { tasks.editTask(taskId, edit) } }
    }
}
