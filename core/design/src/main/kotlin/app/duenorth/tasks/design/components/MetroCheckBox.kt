package app.duenorth.tasks.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

private const val FILL_MS = 100

/**
 * WP8.1 check box: a flat square with a 2dp border that fills with the accent when checked.
 * The touch target is 48dp around a 24dp box.
 */
@Composable
fun MetroCheckBox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val fill by animateColorAsState(if (checked) accent.fill else Color.Transparent, tween(FILL_MS), label = "fill")
    val border = if (checked) accent.fill else colors.foreground
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = null,
            indication = null,
            onValueChange = onCheckedChange
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .metroTilt(enabled = enabled && onCheckedChange != null)
            .then(toggle)
            .size(MetroDimens.TouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(fill)
                .border(2.dp, if (enabled) border else colors.outline),
            contentAlignment = Alignment.Center
        ) {
            if (checked) MetroIconGlyph(MetroIcon.Check, color = accent.onFill, size = 20.dp)
        }
    }
}
