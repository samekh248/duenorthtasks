package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
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

/**
 * The WP8.1 Pivot, for secondary pages with tabs (settings). The current header comes first and
 * the others follow it in order, at 40% opacity, running off the right edge; tapping one jumps to
 * it. Pages swipe like a pager.
 */
@Composable
fun MetroPivot(
    headers: List<String>,
    modifier: Modifier = Modifier,
    pageTitle: String? = null,
    state: PagerState = rememberPagerState { headers.size },
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
        val current = state.currentPage
        val ordered = headers.indices.map { (current + it) % headers.size }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(start = MetroDimens.Gutter)
                    .graphicsLayer {
                        // Headers drift with the finger, then settle when the page changes.
                        translationX = -state.currentPageOffsetFraction * 120.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter * 2)
            ) {
                ordered.forEachIndexed { position, index ->
                    MetroText(
                        headers[index],
                        type.header,
                        Modifier
                            .graphicsLayer { alpha = if (position == 0) 1f else INACTIVE_HEADER_ALPHA }
                            .clickable(interactionSource = null, indication = null, role = Role.Tab) {
                                scope.launch { state.animateScrollToPage(index) }
                            },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) { page(index) }
        }
    }
}
