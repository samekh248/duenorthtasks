package app.duenorth.tasks.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroTheme

/** A flat outlined chip for the due date and list pickers. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MetroText(
        text,
        MetroTheme.typography.caption,
        modifier
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 32.dp)
            .border(2.dp, MetroTheme.colors.outline)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        maxLines = 1
    )
}
