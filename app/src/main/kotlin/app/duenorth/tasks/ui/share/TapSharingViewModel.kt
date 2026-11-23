package app.duenorth.tasks.ui.share

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.repo.RoomShareStore
import app.duenorth.tasks.share.Roster
import app.duenorth.tasks.share.ShareEngine
import app.duenorth.tasks.share.ShareIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One person in a tap-shared list, as the sharing page shows them. */
@Immutable
data class TapMember(
    val id: String,
    val name: String,
    val isMe: Boolean,
    val isStarter: Boolean,
    /** Who added them, by name; null for the starter. */
    val addedBy: String?,
    /** When this phone last tapped theirs (millis); null if never. */
    val lastTap: Long?
)

@Immutable
data class TapSharingState(
    /** False when the list isn't shared by tap (or this phone left it): the section is hidden. */
    val shared: Boolean = false,
    val members: List<TapMember> = emptyList(),
    val canRemove: Boolean = false,
    val full: Boolean = false,
    val syncCompleted: Boolean = true
)

/** The "shared by tap" section of a list's sharing page (spec 011 US3, FR-808, FR-810). */
@HiltViewModel
class TapSharingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: RoomShareStore,
    private val identity: ShareIdentity
) : ViewModel() {
    val listId: String = checkNotNull(savedState["id"]) { "sharing route needs an id" }

    private val engine by lazy { ShareEngine(store, identity.get()) }

    val state: StateFlow<TapSharingState> = combine(
        store.observeSharedLists(),
        store.observeRoster(listId),
        store.observePeers()
    ) { lists, entries, peers ->
        val list = lists.firstOrNull { it.listId == listId }?.takeIf { !it.left } ?: return@combine TapSharingState()
        val me = identity.get().id
        val roster = Roster(entries)
        val lastTaps = peers.associate { it.id to it.lastTapAt }
        val members = roster.members.map { id ->
            TapMember(
                id = id,
                name = roster.name(id) ?: "someone",
                isMe = id == me,
                isStarter = id == roster.owner,
                addedBy = roster.addedBy(id)?.takeIf { it != id }?.let { roster.name(it) ?: "someone" },
                lastTap = lastTaps[id]
            )
        }.sortedWith(compareBy({ !it.isStarter }, { !it.isMe }, { it.name.lowercase() }))
        TapSharingState(
            shared = true,
            members = members,
            canRemove = roster.owner == me,
            full = members.size >= Roster.MAX_MEMBERS,
            syncCompleted = list.syncCompleted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TapSharingState())

    /** The removal reaches the others at the next tap with any of them. */
    fun remove(memberId: String) = io { engine.remove(listId, memberId) }

    fun leave() = io { engine.leave(listId) }

    fun setSyncCompleted(on: Boolean) = io { engine.setSyncCompleted(listId, on) }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching(block) }
    }
}
