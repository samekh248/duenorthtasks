package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** One panorama section: its lowercase header and its content. */
@Immutable
class PanoramaSection(val header: String, val content: @Composable () -> Unit)

/** Section geometry (research R3): each section fills the screen except a narrow strip where the next one peeks in. */
object PanoramaDefaults {
    /** How much of the next section shows at the right edge, its own gutter included. */
    val PeekWidth = 40.dp
    val SectionSpacing = 12.dp

    /** The title moves at about a third of the sections' speed. */
    const val TITLE_PARALLAX = 1f / 3f
}

/**
 * The WP8.1 Panorama hub (constitution Principle I, "Light Panorama"): one wide surface with an
 * oversized title that slides slower than the sections under it, sections that snap one at a time
 * and the next section always peeking in. Parallax is applied in the draw phase from the pager's
 * offset, so swiping never recomposes or relayouts the title.
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
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = MetroDimens.Gutter - 4.dp)
                .graphicsLayer {
                    val stridePx = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
                    val scrolled = (state.currentPage + state.currentPageOffsetFraction) * stridePx
                    translationX = -scrolled * PanoramaDefaults.TITLE_PARALLAX
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
            verticalAlignment = Alignment.Top,
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

/** Space the title takes above the sections. */
private val PanoramaTitleHeight = 132.dp

/** Extra space for the subtitle, and how far it tucks up under the title. */
private val SubtitleHeight = 16.dp
private val SubtitleLift = (-20).dp
