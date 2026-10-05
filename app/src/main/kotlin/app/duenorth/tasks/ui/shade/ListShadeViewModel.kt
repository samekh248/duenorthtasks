package app.duenorth.tasks.ui.shade

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.settings.ListShades
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ListShadeUiState(
    val loading: Boolean = true,
    val listId: String = "",
    val title: String = "",
    val openCount: Int = 0,
    val step: Int = 0
)

/** The "list shade" page (T062): seven shades of the app accent for one list. */
@HiltViewModel
class ListShadeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    tasks: TaskRepository,
    private val shades: ListShades
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "list shade route needs an id" }

    /** The tapped step, shown before the store answers so the preview never lags the finger. */
    private val picked = MutableStateFlow<Int?>(null)

    val state: StateFlow<ListShadeUiState> = combine(tasks.listSummaries(), shades.byList, picked) {
            lists,
            byList,
            picked
        ->
        val list = lists.firstOrNull { it.localId == listId }
        ListShadeUiState(
            loading = false,
            listId = listId,
            title = list?.title.orEmpty(),
            openCount = list?.openCount ?: 0,
            step = picked ?: byList[listId] ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListShadeUiState(listId = listId))

    fun pick(step: Int) {
        picked.value = step
        viewModelScope.launch { shades.set(listId, step) }
    }
}
