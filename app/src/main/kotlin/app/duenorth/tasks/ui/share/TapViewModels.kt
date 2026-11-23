package app.duenorth.tasks.ui.share

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.RoomShareStore
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.share.Answer
import app.duenorth.tasks.share.CardState
import app.duenorth.tasks.share.ReaderState
import app.duenorth.tasks.share.ShareIdentity
import app.duenorth.tasks.share.TapController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The reader page: "share by tap" for the `list` argument, or "tap to sync" without one. */
@HiltViewModel
class TapViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val controller: TapController,
    private val identity: ShareIdentity,
    tasks: TaskRepository
) : ViewModel() {
    val listId: String? = savedState.get<String>("list")?.takeIf { it.isNotBlank() }

    val reader: StateFlow<ReaderState> = controller.reader

    val listTitle: StateFlow<String> = (listId?.let { id -> tasks.list(id).map { it?.title.orEmpty() } } ?: flowOf(""))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val savedName: String? get() = identity.name

    fun nfcOn(): Boolean = controller.nfcOn()

    fun start(activity: Activity) = controller.startReader(activity, listId)

    fun stop(activity: Activity) = controller.stopReader(activity)

    fun saveName(name: String) {
        identity.name = name
    }

    fun answer(yes: Boolean) = controller.answerAsReader(yes)
}

/** The tapped phone's invite page. */
@HiltViewModel
class InviteViewModel @Inject constructor(private val controller: TapController, private val identity: ShareIdentity) :
    ViewModel() {
    val card: StateFlow<CardState> = controller.card

    val savedName: String? get() = identity.name

    fun saveName(name: String) {
        identity.name = name
    }

    fun answer(answer: Answer) = controller.answerAsCard(answer)

    fun dismiss() = controller.dismissCard()
}

/**
 * Whether this phone can share by tap at all (NFC and "this phone only", FR-800, FR-811) and which
 * lists it is in, for the menus, the home app bar and the lists' glyphs. Also carries the card
 * side's state so the app can bring the invite page up.
 */
@HiltViewModel
class TapStatusViewModel @Inject constructor(
    controller: TapController,
    accounts: AccountRepository,
    store: RoomShareStore
) : ViewModel() {
    val card: StateFlow<CardState> = controller.card

    val availability: StateFlow<TapAvailability> = combine(
        accounts.account,
        store.observeSharedLists()
    ) { account, shared ->
        val available = controller.canOffer(account?.provider == ProviderKind.LOCAL)
        TapAvailability(available, if (available) shared.filter { !it.left }.map { it.listId }.toSet() else emptySet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TapAvailability())
}
