package app.duenorth.tasks.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One panorama section: its lowercase header and its content. */
@Immutable
class PanoramaSection(val header: String, val content: @Composable () -> Unit)

/** Section geometry (research R3): each section fills the screen except a narrow strip where the next one peeks in. */
object PanoramaDefaults {
    /** How much of the next section shows at the right edge, its own gutter included. */
    val PeekWidth = 40.dp
    val SectionSpacing = 12.dp

    /**
     * How much of the title's own width it slides by between the first and last section. The
     * title drifts slower than the sections (the panorama feel) but stays mostly on screen, so
     * "tasks" is still readable on the last section ("done") instead of scrolled off.
     */
    const val TITLE_TRAVEL = 0.15f

    /** Least space between the title and the copy of it that slides in when the panorama wraps. */
    val TitleWrapGap = 48.dp

    /** A finger lifting faster than this (per second) moves on a section even short of halfway. */
    val SnapVelocity = 400.dp
}

/**
 * Where a [MetroPanorama] is. The sections form a ring: past the last one comes the first again,
 * and before the first comes the last, so there is no end to scroll into.
 */
@Stable
class PanoramaState(val sectionCount: Int, initialSection: Int = 0) : ScrollableState {
    init {
        require(sectionCount > 0) { "A panorama needs at least one section" }
    }

    /** Where the panorama is, in sections, always within [0, sectionCount); whole numbers rest. */
    internal var position by mutableFloatStateOf(initialSection.mod(sectionCount).toFloat())

    /** One section's width plus the gap after it, in px, as the last layout measured it. */
    internal var stride = 0

    /** The section nearest to resting, the one a swipe settles on if it lifted now. */
    val currentSection: Int by derivedStateOf { position.roundToInt().mod(sectionCount) }

    private val scroller = ScrollableState { delta ->
        if (stride > 0) position = (position + delta / stride).mod(sectionCount.toFloat())
        if (stride > 0) delta else 0f
    }

    override val isScrollInProgress: Boolean get() = scroller.isScrollInProgress

    override fun dispatchRawDelta(delta: Float): Float = scroller.dispatchRawDelta(delta)

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) =
        scroller.scroll(scrollPriority, block)

    /** Jumps straight to [section]. */
    suspend fun scrollToSection(section: Int) = scroll { position = section.mod(sectionCount).toFloat() }

    /** Slides to [section] the short way round the ring. */
    suspend fun animateScrollToSection(section: Int) {
        val target = section.mod(sectionCount)
        animateScrollBy(ringDistance(position, target.toFloat(), sectionCount) * stride, SnapSpring)
        scroll { position = target.toFloat() }
    }

    companion object {
        val Saver: Saver<PanoramaState, *> = Saver(
            save = { listOf(it.sectionCount, it.currentSection) },
            restore = { PanoramaState(it[0], it[1]) }
        )
    }
}

/** A [PanoramaState] that survives configuration changes and process death. */
@Composable
fun rememberPanoramaState(sectionCount: Int, initialSection: Int = 0): PanoramaState =
    rememberSaveable(sectionCount, saver = PanoramaState.Saver) { PanoramaState(sectionCount, initialSection) }

/**
 * The WP8.1 Panorama hub (constitution Principle I, "Light Panorama"): one wide surface with an
 * oversized title that drifts a little as the sections under it slide by, sections that snap one at a time
 * and the next section always peeking in. The sections loop: swiping on from the last one brings
 * the first one in from the right, title and all, and swiping back from the first brings the last.
 *
 * Each section is built once and only ever moved, never rebuilt, as it comes round the ring.
 * Positions (sections and title parallax alike) are applied in the draw phase, so swiping never
 * recomposes or relayouts anything. Every section stays composed once the first frames are on
 * screen, so the first swipe after a cold start never has to build a section mid-gesture
 * (constitution Principle II).
 *
 * The ring needs three or more sections for the next one to peek in on every side; with two, the
 * other section only ever comes in from the left.
 */
@Composable
fun MetroPanorama(
    title: String,
    sections: List<PanoramaSection>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: PanoramaState = rememberPanoramaState(sections.size),
    peekWidth: Dp = PanoramaDefaults.PeekWidth
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val count = sections.size
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Let the first frames through, then build the sections out of view while nothing moves.
        repeat(WARM_UP_FRAMES) { withFrameNanos { } }
        snapshotFlow { state.isScrollInProgress }.first { !it }
        warm = true
    }
    // Until then, only the sections on screen (the current one and the one peeking in).
    val onScreen by remember(state, count) {
        derivedStateOf { (0 until count).filter { ringOffset(it, state.position, count) in -1f..1.5f } }
    }
    Box(modifier.fillMaxSize().background(colors.background)) {
        PanoramaTitle(state, count) {
            // Tucked up under the title's descender space, like a WP8.1 app name.
            Column {
                MetroText(title, type.panoramaTitle, maxLines = 1, softWrap = false)
                if (subtitle != null) {
                    MetroText(
                        subtitle,
                        type.subheader,
                        Modifier.padding(start = 6.dp).offset(y = SubtitleLift),
                        color = colors.secondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        val density = LocalDensity.current
        val fling = remember(state, density) {
            SectionSnap(state, with(density) { PanoramaDefaults.SnapVelocity.toPx() })
        }
        val scope = rememberCoroutineScope()
        val step: (Int) -> Boolean = { by ->
            scope.launch { state.animateScrollToSection(state.currentSection + by) }
            true
        }
        Layout(
            content = {
                sections.forEachIndexed { i, section ->
                    Box {
                        if (warm || i in onScreen) {
                            Column(Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter)) {
                                MetroText(
                                    section.header,
                                    type.sectionHeader,
                                    Modifier.semantics { heading() },
                                    maxLines = 1
                                )
                                Box(Modifier.fillMaxSize()) { section.content() }
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (subtitle != null) PanoramaTitleHeight + SubtitleHeight else PanoramaTitleHeight)
                .clipScrollableContainer(Orientation.Horizontal)
                .semantics {
                    // A ring always has a section either side, so it can always scroll both ways.
                    horizontalScrollAxisRange = ScrollAxisRange(value = { 1f }, maxValue = { 2f })
                    scrollBy { x, _ -> if (x == 0f) false else step(if (x > 0) 1 else -1) }
                    pageLeft { step(-1) }
                    pageRight { step(1) }
                }
                .scrollable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    // Dragging left moves on to the next section.
                    reverseDirection = true,
                    flingBehavior = fling
                )
        ) { measurables, constraints ->
            val spacing = PanoramaDefaults.SectionSpacing.roundToPx()
            val width = (constraints.maxWidth - spacing - peekWidth.roundToPx()).coerceAtLeast(0)
            val stride = width + spacing
            state.stride = stride
            val section = Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight)
            val placeables = measurables.map { it.measure(section) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, placeable ->
                    placeable.placeWithLayer(0, 0) {
                        translationX = ringOffset(i, state.position, count) * stride
                    }
                }
            }
        }
    }
}

/**
 * The title with a copy of itself following it, so that crossing the seam between the last and
 * first sections keeps it sliding the same way: the copy comes in from the right and settles where
 * the title started. Between the other sections it drifts by [PanoramaDefaults.TITLE_TRAVEL].
 */
@Composable
private fun PanoramaTitle(state: PanoramaState, count: Int, title: @Composable () -> Unit) {
    val gap = PanoramaDefaults.TitleWrapGap
    val start = MetroDimens.Gutter - 4.dp
    Layout(
        content = {
            Box { title() }
            // Only ever a picture of the title: screen readers and tests see one title.
            Box(Modifier.clearAndSetSemantics { }) { title() }
        }
    ) { measurables, constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val (main, copy) = measurables.map { it.measure(loose) }
        val startPx = start.roundToPx()
        val travel = main.width * PanoramaDefaults.TITLE_TRAVEL
        // Far enough apart that the copy stays off screen wherever a section rests.
        val period = maxOf(main.width + gap.toPx(), constraints.maxWidth - startPx + travel)
        layout(constraints.maxWidth, main.height) {
            main.placeWithLayer(startPx, 0) { translationX = -titleShift(state.position, count, travel, period) }
            copy.placeWithLayer(startPx, 0) {
                translationX = period - titleShift(state.position, count, travel, period)
            }
        }
    }
}

/**
 * How far left the title has slid at [position]: evenly up to [travel] by the last section, then on
 * across the seam by the rest of [period], where the copy has taken the title's place.
 */
internal fun titleShift(position: Float, count: Int, travel: Float, period: Float): Float {
    if (count < 2) return 0f
    val last = count - 1
    val at = position.mod(count.toFloat())
    return if (at <= last) at / last * travel else travel + (at - last) * (period - travel)
}

/**
 * Where section [index] sits at [position], in section widths from the resting spot: 0 is on
 * screen, 1 is peeking in at the right, -1 is just off the left. The ring puts every section in
 * [-1, count - 1), so the one after the last is the first, peeking in at the right.
 */
internal fun ringOffset(index: Int, position: Float, count: Int): Float {
    val ahead = (index - position).mod(count.toFloat())
    return if (count > 1 && ahead >= count - 1) ahead - count else ahead
}

/** The signed number of sections from [from] to [to] the short way round a ring of [count]. */
internal fun ringDistance(from: Float, to: Float, count: Int): Float {
    val ahead = (to - from).mod(count.toFloat())
    return if (ahead > count / 2f) ahead - count else ahead
}

/**
 * Lifting the finger settles on one section: the next one along the swipe when it was quick, else
 * whichever is nearer. A spring eases it in without overshooting, so it never flashes the section
 * after.
 */
private class SectionSnap(private val state: PanoramaState, private val velocityThreshold: Float) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val stride = state.stride
        if (stride == 0) return initialVelocity
        val from = state.position
        val target = when {
            initialVelocity > velocityThreshold -> floor(from + SETTLED) + 1
            initialVelocity < -velocityThreshold -> ceil(from - SETTLED) - 1
            else -> from.roundToInt().toFloat()
        }
        val distance = (target - from) * stride
        // A critically damped spring overshoots when it starts faster than this.
        val most = SNAP_STIFFNESS_ROOT * abs(distance)
        var done = 0f
        animate(0f, distance, initialVelocity.coerceIn(-most, most), SnapSpring) { value, _ ->
            scrollBy(value - done)
            done = value
        }
        state.position = target.mod(state.sectionCount.toFloat())
        return 0f
    }
}

private val SnapSpring = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)

/** √[Spring.StiffnessMediumLow], the spring's natural frequency. */
private const val SNAP_STIFFNESS_ROOT = 20f

/** How close to a whole section counts as resting on it. */
private const val SETTLED = 1e-3f

/** Frames the panorama shows before it builds the sections out of view. */
private const val WARM_UP_FRAMES = 2

/** Space the title takes above the sections. */
private val PanoramaTitleHeight = 132.dp

/** Extra space for the subtitle, and how far it tucks up under the title. */
private val SubtitleHeight = 16.dp
private val SubtitleLift = (-20).dp
