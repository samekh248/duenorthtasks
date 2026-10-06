package app.duenorth.tasks.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.motion.MetroEasing
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How much the held row grows: "pops forward" like a tile on the WP8.1 Start screen. */
private const val LIFT_SCALE = 1.05f

/** Opacity of every other row while one is held: they "recede". */
private const val RECEDE_ALPHA = 0.45f

/** A press this long anywhere on a row picks it up; the gripper picks it up at once. */
private const val PRESS_MS = 150L

/** Neighbors slide aside and the dropped row settles in this long. */
private const val SLIDE_MS = 150

/** After a drop, the order shown waits this long at most for the saved order to come back. */
private const val SETTLE_WAIT_MS = 1_500L

/**
 * The WP8.1 reorder mode as a list (specs/003-reordering, FR-202 to FR-206 and FR-211).
 *
 * Every row gets a three-bar gripper on its right. Touching the gripper picks the row up at once;
 * pressing anywhere else on it for [PRESS_MS] does too, so a quick swipe still scrolls. The held
 * row grows a little and gets a flat accent edge, the others fade back, and neighbors slide to
 * open a dashed slot where it will land. Dragging near the top or bottom scrolls the list. On
 * release, [onMove] gets the moved key and the full new order of keys; the list keeps showing that
 * order until [items] catch up. TalkBack gets "move up" and "move down" on every row instead.
 *
 * The gesture lives on the list rather than on each row, so rows moving under the finger never
 * feed back into the drag.
 */
@Composable
fun <T> MetroReorderList(
    items: List<T>,
    key: (T) -> String,
    onMove: (moved: String, order: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    /** Scrolled into view when the mode opens (the row the user long-pressed). */
    initialKey: String? = null,
    /** How many list items [header] adds, so [initialKey] scrolls to the right place. */
    headerItems: Int = 0,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
    rowContent: @Composable RowScope.(T) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val animate = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val reorder = remember(state) { ReorderState(state, scope, with(density) { EDGE.toPx() }) }
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnMove by rememberUpdatedState(onMove)
    reorder.keysOf = { currentItems.map(currentKey) }
    reorder.commit = { moved, order ->
        if (order != currentItems.map(currentKey)) currentOnMove(moved, order)
    }
    reorder.onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val shown = reorder.arrange(items, key)
    val keys = items.map(key)
    LaunchedEffect(keys, reorder.order, reorder.draggedKey) {
        val pending = reorder.order ?: return@LaunchedEffect
        if (reorder.draggedKey != null) return@LaunchedEffect
        if (pending == keys) {
            reorder.order = null
        } else {
            delay(SETTLE_WAIT_MS)
            reorder.order = null
        }
    }
    LaunchedEffect(initialKey) {
        val index = items.indexOfFirst { key(it) == initialKey }
        if (index >= 0) state.scrollToItem(index + headerItems)
    }
    LaunchedEffect(reorder) { reorder.autoScroll() }

    val handleWidth = with(density) { HANDLE.toPx() }
    val slot = MetroTheme.accent.fill
    val background = MetroTheme.colors.background
    LazyColumn(
        modifier
            .testTag("reorder-list")
            .pointerInput(reorder) { with(reorder) { detectDrags(handleWidth) } },
        state = state,
        contentPadding = contentPadding
    ) {
        header()
        items(shown, key = { key(it) }, contentType = { "reorder-row" }) { item ->
            val k = key(item)
            val held = reorder.draggedKey == k || reorder.settlingKey == k
            val position = shown.indexOf(item)
            Row(
                Modifier
                    .then(
                        if (held || !animate) {
                            Modifier.zIndex(1f)
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tween(SLIDE_MS, easing = MetroEasing)
                            )
                        }
                    )
                    .fillMaxWidth()
                    .drawBehind {
                        if (reorder.draggedKey != k) return@drawBehind
                        val inset = 1.dp.toPx()
                        drawRect(
                            slot,
                            topLeft = Offset(MetroDimens.Gutter.toPx() + inset, inset),
                            size = Size(size.width - 2 * MetroDimens.Gutter.toPx() - 2 * inset, size.height - 2 * inset),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                            )
                        )
                    }
                    .graphicsLayer {
                        translationY = reorder.offsetOf(k)
                        val lifted = reorder.draggedKey == k
                        val scale = if (lifted && animate) LIFT_SCALE else 1f
                        scaleX = scale
                        scaleY = scale
                        alpha = if (reorder.draggedKey != null && !lifted) RECEDE_ALPHA else 1f
                    }
                    .then(if (held) Modifier.background(background) else Modifier)
                    .drawBehind {
                        if (reorder.draggedKey == k) drawRect(slot, size = Size(4.dp.toPx(), size.height))
                    }
                    .semantics {
                        stateDescription = "position ${position + 1} of ${shown.size}"
                        customActions = listOfNotNull(
                            CustomAccessibilityAction("move up") { reorder.step(k, -1) }.takeIf { position > 0 },
                            CustomAccessibilityAction("move down") {
                                reorder.step(k, 1)
                            }.takeIf { position < shown.lastIndex }
                        )
                    }
                    .heightIn(min = MetroDimens.TouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { rowContent(item) }
                Box(
                    Modifier.width(HANDLE).heightIn(min = MetroDimens.TouchTarget).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) {
                    MetroIconGlyph(MetroIcon.Gripper, size = 22.dp)
                }
            }
        }
        footer()
    }
}

/** The gripper's touch zone at the right of every row. */
private val HANDLE = 56.dp

/** Dragging within this distance of the top or bottom edge scrolls the list. */
private val EDGE = 64.dp

/** Fastest auto-scroll, in pixels per frame, right at the edge. */
private const val MAX_SCROLL_PER_FRAME = 24f

@Stable
internal class ReorderState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val edgePx: Float
) {
    var keysOf: () -> List<String> = { emptyList() }
    var commit: (String, List<String>) -> Unit = { _, _ -> }
    var onPickUp: () -> Unit = {}

    /** The row under the finger, or null. */
    var draggedKey by mutableStateOf<String?>(null)

    /** The order shown while dragging, and after a drop until the saved order comes back. */
    var order by mutableStateOf<List<String>?>(null)

    /** The row animating into its slot after a drop. */
    var settlingKey by mutableStateOf<String?>(null)
    private val settle = Animatable(0f)

    /** Finger position in the list's own coordinates. */
    private var fingerY by mutableFloatStateOf(0f)

    /** Where the finger held the row, from the row's top. */
    private var grab = 0f

    fun <T> arrange(items: List<T>, key: (T) -> String): List<T> {
        val wanted = order ?: return items
        val rank = wanted.withIndex().associate { (i, k) -> k to i }
        return items.sortedBy { rank[key(it)] ?: Int.MAX_VALUE }
    }

    private fun visible(key: String): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun top(item: LazyListItemInfo): Float = (item.offset - list.layoutInfo.viewportStartOffset).toFloat()

    /** How far the row [key] is drawn from where it is laid out. */
    fun offsetOf(key: String): Float = when (key) {
        draggedKey -> visible(key)?.let { fingerY - grab - top(it) } ?: 0f
        settlingKey -> settle.value
        else -> 0f
    }

    private fun keyAt(y: Float): String? {
        val movable = keysOf().toSet()
        return list.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val key = item.key as? String
            key in movable && y >= top(item) && y < top(item) + item.size
        }?.key as? String
    }

    private fun start(key: String, y: Float) {
        val item = visible(key) ?: return
        order = order ?: keysOf()
        fingerY = y
        grab = y - top(item)
        settlingKey = null
        draggedKey = key
        onPickUp()
    }

    private fun drag(y: Float) {
        fingerY = y
        swapIfPast()
    }

    private fun end() {
        val key = draggedKey ?: return
        val from = offsetOf(key)
        draggedKey = null
        settlingKey = key
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, tween(SLIDE_MS, easing = MetroEasing))
            if (settlingKey == key) settlingKey = null
        }
        order?.let { commit(key, it) }
    }

    /**
     * Swaps the held row with a neighbor once it passes that neighbor's middle. Measuring from the
     * held row's own edge (bottom going down, top going up) means a swap can never undo itself,
     * even between rows of different heights.
     */
    private fun swapIfPast() {
        val key = draggedKey ?: return
        val current = order ?: return
        val held = visible(key) ?: return
        val index = current.indexOf(key)
        val top = fingerY - grab
        val below = current.getOrNull(index + 1)?.let(::visible)
        val above = current.getOrNull(index - 1)?.let(::visible)
        val target = when {
            below != null && top + held.size > top(below) + below.size / 2f -> index + 1
            above != null && top < top(above) + above.size / 2f -> index - 1
            else -> return
        }
        // Keep the scroll position when the first visible row takes part in the swap.
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull()?.key
        if (first == key || first == current[target]) {
            val (index0, offset0) = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            scope.launch { list.scrollToItem(index0, offset0) }
        }
        order = current.toMutableList().apply { add(target, removeAt(index)) }
    }

    /** "move up" and "move down" for TalkBack and switch access: one place, saved at once. */
    fun step(key: String, by: Int): Boolean {
        val current = order ?: keysOf()
        val index = current.indexOf(key)
        val target = index + by
        if (index < 0 || target !in current.indices) return false
        val moved = current.toMutableList().apply { add(target, removeAt(index)) }
        order = moved
        commit(key, moved)
        return true
    }

    /** Scrolls while the held row is near an edge, faster the closer it gets; re-checks swaps. */
    suspend fun autoScroll() {
        while (scope.isActive) {
            withFrameNanos { }
            if (draggedKey == null) continue
            val height = list.layoutInfo.viewportSize.height.toFloat()
            val speed = when {
                fingerY < edgePx -> -MAX_SCROLL_PER_FRAME * (1f - fingerY / edgePx).coerceIn(0f, 1f)
                fingerY > height - edgePx -> MAX_SCROLL_PER_FRAME * (1f - (height - fingerY) / edgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (abs(speed) < 0.5f) continue
            list.dispatchRawDelta(speed)
            swapIfPast()
        }
    }

    /** The gripper picks up on touch-down; the rest of the row after a short press. */
    suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDrags(handleWidth: Float) =
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val key = keyAt(down.position.y) ?: return@awaitEachGesture
            if (down.position.x < size.width - handleWidth) {
                // Not on the gripper: wait for a still press; moving or lifting first means scroll or tap.
                val ended = withTimeoutOrNull(PRESS_MS) {
                    var gone = false
                    while (!gone) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        gone = change == null || !change.pressed || change.isConsumed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                    gone
                }
                if (ended != null) return@awaitEachGesture
            }
            start(key, down.position.y)
            if (draggedKey == null) return@awaitEachGesture
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    drag(change.position.y)
                }
            } finally {
                end()
            }
        }
}
