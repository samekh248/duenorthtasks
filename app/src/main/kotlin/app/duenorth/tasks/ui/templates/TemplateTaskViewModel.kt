package app.duenorth.tasks.ui.templates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.ui.common.ServiceFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
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
data class TemplateStepUi(val id: String, val title: String)

@Immutable
data class TemplateTaskUiState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    /** The list template's name when this task belongs to one; null for a task template. */
    val listName: String? = null,
    val title: String = "",
    val details: String? = null,
    val dueOffset: Int? = null,
    val important: Boolean = false,
    /** The connected service has importance (To Do); false hides the toggle (spec 004 edge cases). */
    val importance: Boolean = false,
    val shadeStep: Int = 0,
    val steps: List<TemplateStepUi> = emptyList(),
    val reordering: Boolean = false,
    val reorderFrom: String? = null
) {
    val inList: Boolean get() = listName != null
}

/** The template task editor (spec 004 FR-331): laid out like a task page, offsets in place of dates. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TemplateTaskViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val templates: TemplateRepository,
    features: ServiceFeatures
) : ViewModel() {
    val taskId: String = checkNotNull(savedState["id"]) { "template task route needs an id" }

    private val reorder = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    private val task = templates.templateTask(taskId)

    private val list = task.map { it?.templateListId }.flatMapLatest { listId ->
        if (listId == null) flowOf(null) else templates.listTemplate(listId)
    }

    val state: StateFlow<TemplateTaskUiState> = combine(
        task,
        list,
        templates.steps(taskId),
        features.importance,
        reorder
    ) { task, list, steps, importance, (reordering, from) ->
        TemplateTaskUiState(
            loading = false,
            exists = task != null,
            listName = list?.name,
            title = task?.title.orEmpty(),
            details = task?.notes,
            dueOffset = task?.dueOffsetDays,
            important = task?.important == true,
            importance = importance,
            shadeStep = list?.shadeStep ?: 0,
            steps = steps.map { TemplateStepUi(it.id, it.title) },
            reordering = reordering,
            reorderFrom = from
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateTaskUiState())

    fun save(title: String, details: String) = launch {
        templates.editTemplateTask(
            taskId,
            title = title,
            notes = if (details.isBlank()) Patch.Clear else Patch.Set(details)
        )
    }

    fun setDueOffset(days: Int?) = launch {
        templates.editTemplateTask(taskId, dueOffset = days?.let { Patch.Set(it) } ?: Patch.Clear)
    }

    fun setImportant(important: Boolean) = launch { templates.editTemplateTask(taskId, important = important) }

    fun addStep(title: String) {
        if (title.isBlank()) return
        launch { templates.addTemplateStep(taskId, title) }
    }

    fun removeStep(id: String) = launch { templates.removeTemplateStep(id) }

    fun delete() = launch { templates.deleteTemplateTask(taskId) }

    fun startReorder(from: String? = null) = reorder.update { true to from }

    fun endReorder() = reorder.update { false to null }

    /** Saves a drop; [order] is every step id in its new order. */
    fun reorderSteps(moved: String, order: List<String>) {
        if (moved !in order) return
        launch { templates.moveTemplateSteps(taskId, order) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
