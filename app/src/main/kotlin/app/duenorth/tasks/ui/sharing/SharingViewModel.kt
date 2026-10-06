package app.duenorth.tasks.ui.sharing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.serviceName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class SharingUiState(
    val loading: Boolean = true,
    /** False once the list is gone; the page closes itself. */
    val exists: Boolean = true,
    val listId: String = "",
    val title: String = "",
    val sharing: ListSharing = ListSharing.PRIVATE,
    /** The connected service can share lists at all (Microsoft To Do). */
    val serviceShares: Boolean = false,
    val serviceName: String = "",
    /** Where "see people in Microsoft To Do" goes; null hides the link. */
    val handOff: HandOff? = null
)

/** Microsoft To Do's own app, or To Do on the web when the app isn't installed. */
data class HandOff(val appPackage: String, val webUrl: String)

/**
 * The "sharing" page for one list (spec 002 US2): who owns it, why member names aren't shown, and
 * a way to Microsoft To Do, the only place sharing can be managed (research R2, R3).
 */
@HiltViewModel
class SharingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    tasks: TaskRepository,
    accounts: AccountRepository,
    features: ServiceFeatures
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "sharing route needs an id" }

    val state: StateFlow<SharingUiState> = combine(
        tasks.list(listId),
        accounts.account,
        features.sharedLists
    ) { list, account, serviceShares ->
        val shown = list?.takeUnless { it.deletedLocally }
        SharingUiState(
            loading = false,
            exists = shown != null,
            listId = listId,
            title = shown?.title.orEmpty(),
            sharing = ListSharing.of(shown?.isShared == true, shown?.isOwner != false),
            serviceShares = serviceShares,
            serviceName = serviceName(account?.provider),
            handOff = if (account?.provider == ProviderKind.MICROSOFT) handOffFor(account.email) else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharingUiState(listId = listId))

    companion object {
        const val TODO_PACKAGE = "com.microsoft.todos"

        private val PERSONAL_DOMAINS = setOf("outlook.com", "hotmail.com", "live.com", "msn.com", "passport.com")

        /**
         * To Do on the web lives at to-do.live.com for personal Microsoft accounts and
         * to-do.office.com for work or school ones. The address is the best hint the app has.
         */
        fun handOffFor(email: String?): HandOff {
            val domain = email?.substringAfterLast('@', "")?.lowercase().orEmpty()
            val personal = PERSONAL_DOMAINS.any { domain == it || domain.startsWith(it.substringBefore('.') + ".") }
            val web = if (personal) "https://to-do.live.com/tasks/" else "https://to-do.office.com/tasks/"
            return HandOff(TODO_PACKAGE, web)
        }
    }
}
