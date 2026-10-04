package app.duenorth.tasks.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.MetroEasing
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

private val TrackWidth = 50.dp
private val TrackHeight = 22.dp
private val ThumbWidth = 12.dp
private const val SLIDE_MS = 150

/**
 * WP8.1 toggle switch: a rectangular track with a rectangular thumb, "On"/"Off" written to its
 * left. The track fills with the accent when on.
 */
@Composable
fun MetroToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    onText: String = "On",
    offText: String = "Off"
) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val thumbX by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbWidth else 0.dp,
        animationSpec = tween(SLIDE_MS, easing = MetroEasing),
        label = "thumb"
    )
    val foreground = if (enabled) colors.foreground else colors.outline
    Row(
        modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = null,
                indication = null,
                onValueChange = onCheckedChange
            )
            .heightIn(min = MetroDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        if (label != null) {
            MetroText(label, MetroTheme.typography.body, Modifier.weight(1f), color = foreground)
        }
        MetroText(
            if (checked) onText else offText,
            MetroTheme.typography.subheader,
            color = foreground
        )
        Box(Modifier.size(TrackWidth, TrackHeight).border(2.dp, foreground)) {
            if (checked) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(TrackWidth - 8.dp, TrackHeight - 8.dp)
                        .background(if (enabled) accent.fill else colors.outline)
                )
            }
            Box(
                Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 0) }
                    .size(ThumbWidth, TrackHeight)
                    .background(foreground)
            )
        }
    }
}
