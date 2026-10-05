package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import kotlinx.coroutines.flow.first

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
}

/**
 * The WP8.1 Panorama hub (constitution Principle I, "Light Panorama"): one wide surface with an
 * oversized title that drifts a little as the sections under it slide by, sections that snap one at a time
 * and the next section always peeking in. Parallax is applied in the draw phase from the pager's
 * offset, so swiping never recomposes or relayouts the title.
 *
 * Every section stays composed once the first frames are on screen, so the first swipe after a
 * cold start never has to build a section mid-gesture (constitution Principle II).
 */
@Composable
fun MetroPanorama(
    title: String,
    sections: List<PanoramaSection>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: PagerState = rememberPagerState { sections.size },
    peekWidth: Dp = PanoramaDefaults.PeekWidth
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val pageSize = remember(peekWidth) { PeekPageSize(peekWidth) }
    val defaultNested = PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
    val nested = remember(defaultNested) { SidewaysOnly(defaultNested) }
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Let the first frames through, then build the sections out of view while nothing moves.
        repeat(WARM_UP_FRAMES) { withFrameNanos { } }
        snapshotFlow { state.isScrollInProgress }.first { !it }
        warm = true
    }
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = MetroDimens.Gutter - 4.dp)
                .graphicsLayer {
                    // Spread the title's travel evenly over the swipes from first to last section.
                    val lastPage = (state.pageCount - 1).coerceAtLeast(1)
                    val progress = (state.currentPage + state.currentPageOffsetFraction) / lastPage
                    translationX = -progress.coerceIn(0f, 1f) * size.width * PanoramaDefaults.TITLE_TRAVEL
                }
        ) {
            MetroText(title, type.panoramaTitle, maxLines = 1, softWrap = false)
            if (subtitle != null) {
                // Tucked up under the title's descender space, like a WP8.1 app name.
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
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize().padding(
                top = if (subtitle !=
                    null
                ) {
                    PanoramaTitleHeight + SubtitleHeight
                } else {
                    PanoramaTitleHeight
                }
            ),
            pageSize = pageSize,
            pageSpacing = PanoramaDefaults.SectionSpacing,
            beyondViewportPageCount = if (warm) (sections.size - 1).coerceAtLeast(0) else 0,
            verticalAlignment = Alignment.Top,
            pageNestedScrollConnection = nested,
            key = { sections[it].header }
        ) { page ->
            val section = sections[page]
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

/** Sections as wide as the screen minus the gap and [peek], so the next section shows just a strip. */
private class PeekPageSize(private val peek: Dp) : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
        (availableSpace - pageSpacing - peek.roundToPx()).coerceAtLeast(0)
}

/**
 * The pager's own nested scroll handling, kept to the sideways axis. While the pager rests between
 * snap points, the default connection hands back the whole vertical delta of a section's list as
 * consumed. The last section always rests there (it is clamped to the screen's end, short of its
 * snap point by the peek), so slow drags on "done" never scrolled; only flings did.
 */
private class SidewaysOnly(private val pager: NestedScrollConnection) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        pager.onPreScroll(available.copy(y = 0f), source).copy(y = 0f)

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        pager.onPostScroll(consumed, available, source)

    override suspend fun onPreFling(available: Velocity): Velocity = pager.onPreFling(available)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        pager.onPostFling(consumed, available)
}

/** Frames the panorama shows before it builds the sections out of view. */
private const val WARM_UP_FRAMES = 2

/** Space the title takes above the sections. */
private val PanoramaTitleHeight = 132.dp

/** Extra space for the subtitle, and how far it tucks up under the title. */
private val SubtitleHeight = 16.dp
private val SubtitleLift = (-20).dp
