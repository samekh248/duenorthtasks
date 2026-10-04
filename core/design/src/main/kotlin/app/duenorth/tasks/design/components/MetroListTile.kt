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
import app.duenorth.tasks.design.theme.MetroWeights

/**
 * A list's tile: a flat square in the list's shade of the accent with its open-task count in the
 * bottom-right corner (research R3). Pass [count] = null for the outlined "new list" tile.
 */
@Composable
fun MetroListTile(
    count: Int?,
    modifier: Modifier = Modifier,
    fill: Color = MetroTheme.accent.fill,
    onFill: Color = MetroTheme.accent.onFill,
    size: Dp = 64.dp
) {
    if (count == null) {
        Box(
            modifier.size(size).border(2.dp, MetroTheme.colors.foreground),
            contentAlignment = Alignment.Center
        ) {
            MetroIconGlyph(MetroIcon.Add, size = size / 2)
        }
        return
    }
    Box(modifier.size(size).background(fill), contentAlignment = Alignment.BottomEnd) {
        MetroText(
            count.toString(),
            MetroTheme.typography.listName.copy(fontSize = 22.sp, fontWeight = MetroWeights.Light),
            Modifier.padding(end = 6.dp, bottom = 2.dp),
            color = onFill,
            maxLines = 1
        )
    }
}
