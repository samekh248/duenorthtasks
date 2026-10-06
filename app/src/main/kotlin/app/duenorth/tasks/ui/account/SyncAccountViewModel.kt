package app.duenorth.tasks.ui.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.sync.SyncEngine
import app.duenorth.tasks.sync.SyncScheduler
import app.duenorth.tasks.sync.SyncSettings
import app.duenorth.tasks.sync.SyncSettingsStore
import app.duenorth.tasks.ui.common.ServiceFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SyncAccountUiState(
    val loading: Boolean = true,
    val provider: ProviderKind? = null,
    val signedInAs: String = "",
    val settings: SyncSettings = SyncSettings(),
    /** Changes made on the phone that the service hasn't seen yet. */
    val pendingChanges: Int = 0,
    val syncing: Boolean = false,
    /** A first sync's completed tasks are still arriving; the sync button keeps turning. */
    val loadingHistory: Boolean = false,
    /** The service stores task and step order (specs/003-reordering FR-232). */
    val storesOrder: Boolean = false
)

/** The "sync account" page once connected (T049); signing out and switching go through [AppViewModel]. */
@HiltViewModel
class SyncAccountViewModel @Inject constructor(
    accounts: AccountRepository,
    tasks: TaskRepository,
    private val session: AccountSession,
    private val settingsStore: SyncSettingsStore,
    private val scheduler: SyncScheduler,
    engine: SyncEngine,
    features: ServiceFeatures
) : ViewModel() {
    val demoAvailable: Boolean = session.demoAvailable

    val state: StateFlow<SyncAccountUiState> = combine(
        accounts.account,
        settingsStore.settings,
        tasks.pendingCount,
        combine(engine.isSyncing, engine.isLoadingHistory, ::Pair),
        features.storesOrder
    ) { account, settings, pending, (syncing, loadingHistory), storesOrder ->
        SyncAccountUiState(
            loading = false,
            provider = account?.provider,
            signedInAs = account?.email ?: account?.displayName.orEmpty(),
            settings = settings,
            pendingChanges = pending,
            syncing = syncing,
            loadingHistory = loadingHistory,
            storesOrder = storesOrder
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncAccountUiState())

    fun isConfigured(kind: ProviderKind): Boolean = session.isConfigured(kind)

    fun setInterval(minutes: Int) {
        viewModelScope.launch { settingsStore.setInterval(minutes) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settingsStore.setWifiOnly(wifiOnly) }
    }

    fun syncNow() = scheduler.syncNow()
}
