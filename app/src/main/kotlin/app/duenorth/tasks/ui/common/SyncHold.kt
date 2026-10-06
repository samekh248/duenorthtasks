package app.duenorth.tasks.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.duenorth.tasks.sync.ListHolds

/**
 * Keeps synced changes from moving a list while a finger is on it or it is still flinging
 * (FR-008, constitution Principle II). While [active], the sync engine waits before applying the
 * next batch to the held lists ([ListHolds]), and [frozen] keeps showing the rows the user is
 * looking at, so even a batch already in flight lands only once they let go.
 */
@Stable
class TouchHold internal constructor(private val list: LazyListState) {
    internal var pressed by mutableStateOf(false)

    val active: Boolean get() = pressed || list.isScrollInProgress
}

/**
 * Tracks touches on [list] and calls [onHeldChange] with true when a hold starts and false when it
 * ends; calls always come in pairs, including when the screen leaves mid-gesture.
 */
@Composable
fun rememberTouchHold(list: LazyListState, onHeldChange: (Boolean) -> Unit): TouchHold {
    val hold = remember(list) { TouchHold(list) }
    val callback by rememberUpdatedState(onHeldChange)
    LaunchedEffect(hold) {
        var holding = false
        try {
            snapshotFlow { hold.active }.collect { active ->
                if (active != holding) {
                    holding = active
                    callback(active)
                }
            }
        } finally {
            if (holding) callback(false)
        }
    }
    return hold
}

/** Watches presses without consuming them, so taps, long-presses and scrolling work as before. */
fun Modifier.touchHold(hold: TouchHold): Modifier = pointerInput(hold) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        hold.pressed = true
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            hold.pressed = false
        }
    }
}

/** [value] as it was when the hold started, until it ends; the live [value] otherwise. */
@Composable
fun <T> TouchHold.frozen(value: T): T {
    val snapshot = remember(this) { Snapshot(value) }
    if (!active) snapshot.value = value
    return snapshot.value
}

/** Rows frozen like [frozen], except that rows the user just [added] show up at once. */
@Composable
fun TouchHold.frozen(rows: List<TaskRowUi>, added: Set<String>): List<TaskRowUi> =
    PendingAdds.admit(frozen(rows), rows, added)

private class Snapshot<T>(var value: T)

/**
 * The lists a screen has asked [ListHolds] to hold. Several parts of a screen can hold at once
 * (panorama sections); the lists are held from the first [set] true to the last matching false,
 * and released by the exact ids that were held even if the lists changed in between.
 */
class HeldLists(private val holds: ListHolds) {
    private var users = 0
    private var held: Set<String> = emptySet()

    @Synchronized
    fun set(active: Boolean, listIds: () -> Collection<String>) {
        if (active) {
            if (users++ == 0) {
                held = listIds().toSet()
                held.forEach(holds::hold)
            }
        } else if (users > 0 && --users == 0) {
            releaseAll()
        }
    }

    /** Lets go of everything, for a screen that is going away. */
    @Synchronized
    fun releaseAll() {
        held.forEach(holds::release)
        held = emptySet()
        users = 0
    }
}
