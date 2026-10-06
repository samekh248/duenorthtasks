package app.duenorth.tasks.ui.templates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.ui.common.ServiceFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
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

@Immutable
data class ListTemplateUiState(
    val loading: Boolean = true,
    /** False once the template is gone; the page closes itself. */
    val exists: Boolean = true,
    val name: String = "",
    val shadeStep: Int = 0,
    val tasks: List<TemplateRowUi> = emptyList(),
    val reordering: Boolean = false,
    val reorderFrom: String? = null
)

private data class EditorPrefs(val reordering: Boolean = false, val reorderFrom: String? = null, val shade: Int? = null)

/** The list template editor (spec 004 FR-331, FR-332): laid out like a list page. */
@HiltViewModel
class ListTemplateViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val templates: TemplateRepository,
    features: ServiceFeatures
) : ViewModel() {
    val templateId: String = checkNotNull(savedState["id"]) { "list template route needs an id" }

    private val prefs = MutableStateFlow(EditorPrefs())

    val state: StateFlow<ListTemplateUiState> = combine(
        templates.listTemplate(templateId),
        templates.tasksIn(templateId),
        features.importance,
        prefs
    ) { list, tasks, importance, prefs ->
        ListTemplateUiState(
            loading = false,
            exists = list != null,
            name = list?.name.orEmpty(),
            // The tapped shade shows before the write lands, like the list shade page.
            shadeStep = prefs.shade ?: list?.shadeStep ?: 0,
            tasks = tasks.map { it.toRow(inList = true, importance = importance) },
            reordering = prefs.reordering,
            reorderFrom = prefs.reorderFrom
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListTemplateUiState())

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { runCatching { templates.createTemplateTask(title, templateId) } }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { runCatching { templates.deleteTemplateTask(id) } }
    }

    fun rename(name: String) {
        viewModelScope.launch { runCatching { templates.renameListTemplate(templateId, name) } }
    }

    fun setShade(step: Int) {
        prefs.update { it.copy(shade = step) }
        viewModelScope.launch { runCatching { templates.setListTemplateShade(templateId, step) } }
    }

    fun delete() {
        viewModelScope.launch { runCatching { templates.deleteListTemplate(templateId) } }
    }

    fun startReorder(from: String? = null) = prefs.update { it.copy(reordering = true, reorderFrom = from) }

    fun endReorder() = prefs.update { it.copy(reordering = false, reorderFrom = null) }

    /** Saves a drop; [order] is every task id in its new order. */
    fun reorder(moved: String, order: List<String>) {
        if (moved !in order) return
        viewModelScope.launch { runCatching { templates.moveTemplateTasks(templateId, order) } }
    }
}
