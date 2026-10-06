package app.duenorth.tasks.ui.templates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.settings.ListShades
import app.duenorth.tasks.ui.common.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class UseListTemplateUiState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    val name: String = "",
    val taskCount: Int = 0,
    val datedCount: Int = 0,
    val shadeStep: Int = 0,
    val today: LocalDate = LocalDate.MIN
) {
    /** The start date is asked only when some task has a due offset (FR-321). */
    val asksStart: Boolean get() = datedCount > 0
}

/** "new list from template" (spec 004 US1): a name, a start date, create. */
@HiltViewModel
class UseListTemplateViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val templates: TemplateRepository,
    private val shades: ListShades,
    clock: Clock
) : ViewModel() {
    val templateId: String = checkNotNull(savedState["id"]) { "use template route needs an id" }

    private var creating = false

    val state: StateFlow<UseListTemplateUiState> = combine(
        templates.listTemplates().map { all -> all.firstOrNull { it.list.id == templateId } },
        todayFlow(clock)
    ) { summary, today ->
        UseListTemplateUiState(
            loading = false,
            exists = summary != null,
            name = summary?.list?.name.orEmpty(),
            taskCount = summary?.taskCount ?: 0,
            datedCount = summary?.datedCount ?: 0,
            shadeStep = summary?.list?.shadeStep ?: 0,
            today = today
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UseListTemplateUiState())

    /** Makes the list, gives it the template's shade, and hands its id to [then]. Ignores double taps. */
    fun create(name: String, start: LocalDate, then: (String) -> Unit) {
        if (creating || name.isBlank()) return
        creating = true
        val step = state.value.shadeStep
        viewModelScope.launch {
            runCatching { templates.useListTemplate(templateId, name, start) }
                .onSuccess { listId ->
                    if (step != 0) runCatching { shades.set(listId, step) }
                    then(listId)
                }
            creating = false
        }
    }
}
