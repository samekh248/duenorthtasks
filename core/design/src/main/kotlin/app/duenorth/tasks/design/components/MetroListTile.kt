package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.Relief
import app.duenorth.tasks.design.theme.slateCard

/**
 * A list's tile: a flat square in the list's shade of the accent with its open-task count in the
 * bottom-right corner (research R3). Pass [count] = null for the outlined "new list" tile, and
 * [shared] = true to put the two-people glyph in the top-left corner (spec 002). Tile relief lifts the tile off
 * the page and frost rounds it; the "new list" tile is a raised blank, a layer card, or a faint wash under
 * slate.
 */
@Composable
fun MetroListTile(
    count: Int?,
    modifier: Modifier = Modifier,
    fill: Color = MetroTheme.accent.fill,
    onFill: Color = MetroTheme.accent.onFill,
    size: Dp = 64.dp,
    shared: Boolean = false
) {
    val style = MetroTheme.style
    if (count == null) {
        Box(
            modifier.size(size).then(
                when {
                    style.relief -> Modifier.relief(Relief.Raised)
                    style.slate -> Modifier.background(MetroTheme.colors.slateCard)
                    else -> Modifier.border(2.dp, MetroTheme.colors.foreground)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            MetroIconGlyph(MetroIcon.Add, size = size / 2)
        }
        return
    }
    val face = if (style.relief) Modifier.relief(Relief.Raised, fill = fill) else Modifier.background(fill)
    Box(modifier.size(size).then(face), contentAlignment = Alignment.BottomEnd) {
        if (shared) {
            MetroIconGlyph(
                MetroIcon.People,
                Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp),
                color = onFill,
                size = 20.dp
            )
        }
        MetroText(
            count.toString(),
            MetroTheme.typography.listName.copy(fontSize = 22.sp),
            Modifier.padding(end = 6.dp, bottom = 2.dp),
            color = onFill,
            maxLines = 1
        )
    }
}
