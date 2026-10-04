package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.MetroWeights

/** WP8.1 button: a flat rectangle with a 2dp outline that fills with the accent while pressed. */
@Composable
fun MetroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val foreground = when {
        !enabled -> colors.outline
        pressed -> accent.onFill
        else -> colors.foreground
    }
    Box(
        modifier
            .metroTilt(enabled)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .defaultMinSize(minWidth = 108.dp, minHeight = MetroDimens.TouchTarget)
            .background(if (pressed && enabled) accent.fill else Color.Transparent)
            .border(2.dp, if (pressed && enabled) accent.fill else foreground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        MetroText(text, MetroTheme.typography.body.copy(fontWeight = MetroWeights.Semibold), color = foreground)
    }
}
