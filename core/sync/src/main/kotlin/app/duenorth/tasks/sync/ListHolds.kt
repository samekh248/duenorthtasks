package app.duenorth.tasks.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lets the UI pause sync writes to a list while the user is touching or flinging it, so nothing
 * moves under their finger (FR-008, T043). The screen calls [hold] on gesture start and [release]
 * when it settles; the engine waits before each batch it applies to that list.
 *
 * A reorder mode (specs/003-reordering FR-228) [pin]s its list instead: it stays held for as long
 * as the mode is open, up to [maxPinned], which only guards against a hold that is never released.
 */
class ListHolds(private val maxWait: Duration = 5.seconds, private val maxPinned: Duration = 10.minutes) {
    private val held = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val pinned = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun pin(listLocalId: String) = pinned.update { it + (listLocalId to (it[listLocalId] ?: 0) + 1) }

    fun unpin(listLocalId: String) = pinned.update {
        val count = (it[listLocalId] ?: 0) - 1
        if (count <= 0) it - listLocalId else it + (listLocalId to count)
    }

    fun hold(listLocalId: String) = held.update { it + (listLocalId to (it[listLocalId] ?: 0) + 1) }

    fun release(listLocalId: String) = held.update {
        val count = (it[listLocalId] ?: 0) - 1
        if (count <= 0) it - listLocalId else it + (listLocalId to count)
    }

    /** Waits until nobody holds [listLocalId], or [maxWait] passes so a stuck hold cannot stop sync. */
    suspend fun awaitReleased(listLocalId: String) {
        if (listLocalId in pinned.value) withTimeoutOrNull(maxPinned) { pinned.first { listLocalId !in it } }
        if (listLocalId !in held.value) return
        withTimeoutOrNull(maxWait) { held.first { listLocalId !in it } }
    }
}
