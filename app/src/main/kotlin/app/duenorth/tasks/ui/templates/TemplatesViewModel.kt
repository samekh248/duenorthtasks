package app.duenorth.tasks.ui.templates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class TemplateRowUi(val id: String, val title: String, val caption: String?, val shadeStep: Int = 0)

@Immutable
data class TemplatesUiState(
    val loading: Boolean = true,
    val lists: List<TemplateRowUi> = emptyList(),
    val tasks: List<TemplateRowUi> = emptyList()
)

/** The templates page (spec 004 US4): a pivot of list templates and task templates, by name. */
@HiltViewModel
class TemplatesViewModel @Inject constructor(private val templates: TemplateRepository) : ViewModel() {
    val state: StateFlow<TemplatesUiState> = combine(
        templates.listTemplates(),
        templates.taskTemplates()
    ) { lists, tasks ->
        TemplatesUiState(
            loading = false,
            lists = lists.map { summary ->
                TemplateRowUi(
                    id = summary.list.id,
                    title = summary.list.name,
                    caption = listOfNotNull(
                        TemplateText.tasks(summary.taskCount),
                        "dates from a start day".takeIf { summary.datedCount > 0 }
                    ).joinToString(" · "),
                    shadeStep = summary.list.shadeStep
                )
            },
            tasks = tasks.map { it.toRow(inList = false, importance = true) }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    /** Makes an empty template of the pivot's kind and hands its id to [then] (FR-312). */
    fun newListTemplate(name: String, then: (String) -> Unit) {
        viewModelScope.launch { runCatching { templates.createListTemplate(name) }.onSuccess(then) }
    }

    fun newTaskTemplate(title: String, then: (String) -> Unit) {
        viewModelScope.launch { runCatching { templates.createTemplateTask(title) }.onSuccess(then) }
    }

    fun renameList(id: String, name: String) {
        viewModelScope.launch { runCatching { templates.renameListTemplate(id, name) } }
    }

    fun renameTask(id: String, title: String) {
        viewModelScope.launch { runCatching { templates.editTemplateTask(id, title = title) } }
    }

    fun deleteList(id: String) {
        viewModelScope.launch { runCatching { templates.deleteListTemplate(id) } }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { runCatching { templates.deleteTemplateTask(id) } }
    }
}

internal fun app.duenorth.tasks.data.db.TemplateTaskSummary.toRow(inList: Boolean, importance: Boolean) = TemplateRowUi(
    id = task.id,
    title = task.title,
    caption = TemplateText.caption(
        TemplateText.offset(task.dueOffsetDays, inList),
        hasDetails = !task.notes.isNullOrBlank(),
        stepCount = stepCount,
        important = task.important && importance
    )
)
