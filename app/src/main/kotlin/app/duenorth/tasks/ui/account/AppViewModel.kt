package app.duenorth.tasks.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** The gate in front of the app (contracts/ui-screens.md "Screen flow"): no account, no panorama. */
@HiltViewModel
class AppViewModel @Inject constructor(private val accounts: AccountRepository, demo: Optional<DemoAccount>) :
    ViewModel() {
    private val demoAccount: DemoAccount? = demo.orElse(null)

    val demoAvailable: Boolean = demoAccount != null

    private val busy = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = busy.asStateFlow()

    val state: StateFlow<AppState> = accounts.account
        .map { if (it == null) AppState.NoAccount else AppState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    fun connectDemo() {
        val demo = demoAccount ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                demo.connect()
            } finally {
                busy.value = false
            }
        }
    }

    /** Signs out: the account row goes, and every list, task and queued change with it. */
    fun switchAccount() {
        viewModelScope.launch { accounts.disconnect() }
    }
}
