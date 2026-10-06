package app.duenorth.tasks.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.sync.SyncEngine
import app.duenorth.tasks.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Signs in to a sample account; only debug builds bind one (T035). */
interface DemoAccount {
    /** Connects the demo account and fills it with sample lists and tasks. */
    suspend fun connect()
}

sealed interface AppState {
    data object Loading : AppState

    data object NoAccount : AppState

    data object Connected : AppState
}

/**
 * The gate in front of the app (contracts/ui-screens.md "Screen flow"): no account, no panorama.
 * Also carries the app-wide sync status for the progress dots and the sync button.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    accounts: AccountRepository,
    private val session: AccountSession,
    private val scheduler: SyncScheduler,
    engine: SyncEngine
) : ViewModel() {
    val demoAvailable: Boolean = session.demoAvailable

    private val busy = MutableStateFlow<ProviderKind?>(null)

    /** The service being signed in to, or null. */
    val connecting: StateFlow<ProviderKind?> = busy.asStateFlow()

    private val failure = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = failure.asStateFlow()

    val syncing: StateFlow<Boolean> = engine.isSyncing

    /** The sync button turns for a sync and for a first sync's history load (the dots only for a sync). */
    val syncButtonTurning: StateFlow<Boolean> = combine(engine.isSyncing, engine.isLoadingHistory) { sync, history ->
        sync || history
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val state: StateFlow<AppState> = accounts.account
        .map { if (it == null) AppState.NoAccount else AppState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    fun isConfigured(kind: ProviderKind): Boolean = session.isConfigured(kind)

    fun connect(kind: ProviderKind, host: SignInHost) = signIn(kind) { session.connect(kind, host) }

    /**
     * Signs out, then into [kind]. Runs here rather than on the sync account page because signing
     * out closes that page (the gate takes over) and would cancel the sign-in with it.
     */
    fun switchTo(kind: ProviderKind, host: SignInHost) = signIn(kind) { session.switchTo(kind, host) }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun signIn(kind: ProviderKind, block: suspend () -> Unit) {
        if (busy.value != null) return
        busy.value = kind
        failure.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                failure.value = signInError(kind, e)
            } finally {
                busy.value = null
            }
        }
    }

    fun connectDemo() = connect(ProviderKind.FAKE, object : SignInHost {})

    fun syncNow() = scheduler.syncNow()
}
