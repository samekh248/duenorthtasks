package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import kotlinx.coroutines.launch

private const val INACTIVE_HEADER_ALPHA = 0.4f

/** Times round the ring the pager has room for, half of them either side of where it starts. */
private const val LAPS = 100_000

/**
 * Where a [MetroPivot] is. The pages form a ring, like the panorama's sections: past the last page
 * comes the first again, and before the first comes the last. Underneath it is a pager many laps
 * long that starts in the middle, so there is no end to swipe into.
 */
@Stable
class PivotState internal constructor(val pageCount: Int, internal val pager: PagerState) {
    /** The page showing, within [0, pageCount). */
    val currentPage: Int get() = pager.currentPage.mod(pageCount)

    /** Slides on to [page], forwards round the ring like tapping its header. */
    suspend fun animateScrollToPage(page: Int) {
        pager.animateScrollToPage(pager.currentPage + (page - currentPage).mod(pageCount))
    }
}

/** A [PivotState] that survives configuration changes and process death. */
@Composable
fun rememberPivotState(pageCount: Int, initialPage: Int = 0): PivotState {
    require(pageCount > 0) { "A pivot needs at least one page" }
    val laps = if (pageCount > 1) LAPS else 1
    val pager = rememberPagerState(initialPage = pageCount * (laps / 2) + initialPage.mod(pageCount)) {
        pageCount * laps
    }
    return remember(pageCount, pager) { PivotState(pageCount, pager) }
}

/**
 * The WP8.1 Pivot, for secondary pages with tabs (settings). The current header comes first and
 * the others follow it in order, at 40% opacity, running off the right edge; tapping one jumps to
 * it. Pages swipe like a pager and loop: swiping on from the last page brings the first, and
 * swiping back from the first brings the last.
 */
@Composable
fun MetroPivot(
    headers: List<String>,
    modifier: Modifier = Modifier,
    pageTitle: String? = null,
    state: PivotState = rememberPivotState(headers.size),
    page: @Composable (index: Int) -> Unit
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().background(colors.background)) {
        if (pageTitle != null) {
            MetroText(
                pageTitle.uppercase(),
                type.pageTitle,
                Modifier.padding(start = MetroDimens.Gutter, top = MetroDimens.Gutter)
            )
        }
        val pager = state.pager
        val current = state.currentPage
        val ordered = headers.indices.map { (current + it) % headers.size }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(start = MetroDimens.Gutter)
                    .graphicsLayer {
                        // Headers drift with the finger, then settle when the page changes.
                        translationX = -pager.currentPageOffsetFraction * 120.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter * 2)
            ) {
                ordered.forEachIndexed { position, index ->
                    MetroText(
                        headers[index],
                        type.header,
                        Modifier
                            .graphicsLayer { alpha = if (position == 0) 1f else INACTIVE_HEADER_ALPHA }
                            .selectable(
                                selected = position == 0,
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab
                            ) {
                                scope.launch { state.animateScrollToPage(index) }
                            },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { slot ->
            Box(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) { page(slot.mod(headers.size)) }
        }
    }
}
