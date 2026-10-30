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
import app.duenorth.tasks.design.theme.Relief

/**
 * WP8.1 button: a flat rectangle with a 2dp outline that fills with the accent while pressed. Under tile relief
 * and mica it stands off the page and presses in under the finger; under Windows 10 Mobile it is a flat grey block.
 */
@Composable
fun MetroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val style = MetroTheme.style
    val foreground = when {
        !enabled -> colors.outline
        style.relief && pressed -> accent.text
        style.mobile10 -> colors.foreground
        pressed -> accent.onFill
        else -> colors.foreground
    }
    val face = when {
        style.relief -> Modifier.relief(if (pressed && enabled) Relief.Inset else Relief.Raised, small = true)
        style.mobile10 -> Modifier.background(colors.foreground.copy(alpha = if (pressed && enabled) 0.4f else 0.2f))
        else ->
            Modifier
                .background(if (pressed && enabled) accent.fill else Color.Transparent)
                .border(2.dp, if (pressed && enabled) accent.fill else foreground)
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
            .then(face)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Windows 10 Mobile labels its buttons in regular weight.
        val label = if (style.mobile10) {
            MetroTheme.typography.body
        } else {
            MetroTheme.typography.body.copy(
                fontWeight = MetroWeights.Semibold
            )
        }
        MetroText(text, label, color = foreground)
    }
}
