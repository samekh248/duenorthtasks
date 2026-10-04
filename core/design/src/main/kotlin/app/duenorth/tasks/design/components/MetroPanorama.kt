package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** One panorama section: its lowercase header and its content. */
@Immutable
class PanoramaSection(val header: String, val content: @Composable () -> Unit)

/** Section geometry from research R3: 310dp sections, so the next one peeks in from the right. */
object PanoramaDefaults {
    val SectionWidth = 310.dp
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
    state: PagerState = rememberPagerState { sections.size },
    sectionWidth: Dp = PanoramaDefaults.SectionWidth
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val stridePx = with(LocalDensity.current) { (sectionWidth + PanoramaDefaults.SectionSpacing).toPx() }
    Box(modifier.fillMaxSize().background(colors.background)) {
        MetroText(
            title,
            type.panoramaTitle,
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = MetroDimens.Gutter - 4.dp)
                .graphicsLayer {
                    val scrolled = (state.currentPage + state.currentPageOffsetFraction) * stridePx
                    translationX = -scrolled * PanoramaDefaults.TITLE_PARALLAX
                },
            maxLines = 1,
            softWrap = false
        )
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize().padding(top = PanoramaTitleHeight),
            pageSize = PageSize.Fixed(sectionWidth),
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

/** Space the title takes above the sections. */
private val PanoramaTitleHeight = 132.dp
