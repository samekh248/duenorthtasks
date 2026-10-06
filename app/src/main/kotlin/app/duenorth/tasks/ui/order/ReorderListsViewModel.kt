package app.duenorth.tasks.ui.order

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.ListSummary
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.settings.ListOrder
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.serviceName
import app.duenorth.tasks.ui.home.ListRowUi
import app.duenorth.tasks.ui.home.toUi
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
data class ReorderListsUiState(
    val loading: Boolean = true,
    val lists: List<ListRowUi> = emptyList(),
    /** The list long-pressed to get here, scrolled into view. */
    val from: String? = null,
    /** The one-time "order stays on this phone" note (FR-231), for services that store no order. */
    val orderNote: Boolean = false,
    val serviceName: String = ""
)

/** The reorder lists page (specs/003-reordering US4): list order lives only on the phone (FR-225). */
@HiltViewModel
class ReorderListsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    tasks: TaskRepository,
    accounts: AccountRepository,
    features: ServiceFeatures,
    private val order: ListOrder
) : ViewModel() {
    private val from: String? = savedState["from"]

    val state: StateFlow<ReorderListsUiState> = combine(
        tasks.listSummaries(),
        order.rank,
        combine(features.storesOrder, order.noteShown) { stores, shown -> !stores && !shown },
        accounts.account
    ) { lists, rank, noteDue, account ->
        ReorderListsUiState(
            loading = false,
            lists = ListOrder.sort(lists, rank) { it.localId }.map(ListSummary::toUi),
            from = from,
            orderNote = noteDue,
            serviceName = serviceName(account?.provider)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReorderListsUiState(from = from))

    /** Saves [ids], every list in its new order. */
    fun reorder(ids: List<String>) {
        viewModelScope.launch { runCatching { order.set(ids) } }
    }

    fun dismissOrderNote() {
        viewModelScope.launch { runCatching { order.markNoteShown() } }
    }
}
