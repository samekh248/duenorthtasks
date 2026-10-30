package app.duenorth.tasks.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.relief
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.Relief

/**
 * A flat outlined chip for the due date and list pickers. The outline is 32dp tall, but the
 * touch target around it is the full 48dp (constitution Principle I). Under tile relief and mica it is a raised
 * button (a layer card under mica) led by its [icon].
 */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, icon: MetroIcon? = null, onClick: () -> Unit) {
    val raised = MetroTheme.style.relief
    Box(
        modifier
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .then(
                    if (raised) {
                        Modifier.relief(Relief.Raised, small = true)
                    } else {
                        Modifier.border(2.dp, MetroTheme.colors.outline)
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (raised && icon != null) MetroIconGlyph(icon, size = 16.dp)
            MetroText(text, MetroTheme.typography.caption, maxLines = 1)
        }
    }
}
