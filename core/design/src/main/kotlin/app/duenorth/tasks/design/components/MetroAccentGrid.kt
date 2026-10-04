package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** The accent picker: a grid of flat squares, four to a row, the chosen one outlined. */
@Composable
fun MetroAccentGrid(selected: Accent, onSelect: (Accent) -> Unit, modifier: Modifier = Modifier, columns: Int = 4) {
    val dark = MetroTheme.colors.isDark
    SwatchGrid(
        swatches = Accent.entries.map { Swatch(it.displayName, it.fill(dark), it == selected) { onSelect(it) } },
        columns = columns,
        modifier = modifier
    )
}

/**
 * The seven shades of the app accent for one list, lightest to darkest; the middle one is the
 * accent itself and the default (data-model.md, "List shades").
 */
@Composable
fun MetroShadeRow(accent: Accent, selectedStep: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = MetroTheme.colors.isDark
    SwatchGrid(
        swatches = AccentShades.steps.map { step ->
            val name = when {
                step == 0 -> "${accent.displayName}, default"
                step < 0 -> "${-step} lighter"
                else -> "$step darker"
            }
            Swatch(name, AccentShades.mix(accent.fill(dark), step), step == selectedStep) { onSelect(step) }
        },
        columns = AccentShades.steps.count(),
        modifier = modifier,
        spacing = 6.dp
    )
}

private class Swatch(val name: String, val color: Color, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun SwatchGrid(swatches: List<Swatch>, columns: Int, modifier: Modifier, spacing: Dp = MetroDimens.Gutter) {
    val outline = MetroTheme.colors.foreground
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        swatches.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { swatch ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .metroTilt()
                            .selectable(
                                selected = swatch.selected,
                                interactionSource = null,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = swatch.onClick
                            )
                            .semantics { contentDescription = swatch.name }
                            // The chosen swatch gets an outline with a gap, like WP8.1's accent picker.
                            .then(if (swatch.selected) Modifier.border(3.dp, outline).padding(6.dp) else Modifier)
                            .background(swatch.color)
                    )
                }
                // Keep the last row's squares the same size as the others.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
