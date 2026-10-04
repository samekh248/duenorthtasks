package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * WP8.1 radio button: a 28dp ring with a 12dp dot when selected, label to the right and an
 * optional caption under it (used on the sync account page: "signed in as ...").
 */
@Composable
fun MetroRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true
) {
    val colors = MetroTheme.colors
    val ring = if (enabled) colors.foreground else colors.outline
    Row(
        modifier
            .metroTilt(enabled)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .heightIn(min = MetroDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        Box(Modifier.size(28.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).background(ring, CircleShape))
        }
        Column {
            MetroText(label, MetroTheme.typography.subheader, color = ring)
            if (caption != null) {
                MetroText(caption, MetroTheme.typography.caption, color = colors.secondary)
            }
        }
    }
}
