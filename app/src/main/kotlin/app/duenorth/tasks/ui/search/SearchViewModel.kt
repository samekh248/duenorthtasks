package app.duenorth.tasks.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.ui.common.CompletionOverrides
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.toRow
import app.duenorth.tasks.ui.common.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchGroup(val listId: String, val listTitle: String, val rows: List<TaskRowUi>)

@Immutable
data class SearchUiState(
    val query: String = "",
    val groups: List<SearchGroup> = emptyList(),
    val searched: Boolean = false
)

/** Search titles and details across every list (FR-015), grouped by list. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val tasks: TaskRepository, features: ServiceFeatures, clock: Clock) :
    ViewModel() {
    private val query = MutableStateFlow("")
    val currentQuery: StateFlow<String> = query.asStateFlow()

    private val overrides = CompletionOverrides()

    private val results = query
        .debounce { if (it.isBlank()) 0 else DEBOUNCE_MS }
        .flatMapLatest { q -> tasks.search(q).map { q to it } }

    val state: StateFlow<SearchUiState> = combine(
        results,
        todayFlow(clock),
        overrides.overrides,
        features.importance
    ) { (q, found), today, pending, importance ->
        overrides.settle(found.associate { it.task.localId to it.task.completed })
        SearchUiState(
            query = q,
            groups = found
                .groupBy { it.task.listId }
                .map { (listId, rows) ->
                    SearchGroup(
                        listId,
                        rows.first().listTitle,
                        rows.map { it.toRow(today, pending[it.task.localId], importance) }
                    )
                },
            searched = q.isNotBlank()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun setCompleted(id: String, completed: Boolean) {
        overrides.set(id, completed)
        viewModelScope.launch {
            runCatching { tasks.setCompleted(id, completed) }.onFailure { overrides.clear(id) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}
