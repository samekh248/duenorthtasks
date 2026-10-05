package app.duenorth.tasks.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.MetroEasing
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

@Immutable
data class AppBarButton(
    val icon: MetroIcon,
    /** Shown under the button when the bar is expanded, and read by TalkBack. */
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Immutable
data class AppBarMenuItem(val label: String, val onClick: () -> Unit)

private const val MAX_BUTTONS = 4
private const val EXPAND_MS = 200

/**
 * The WP8.1 Application Bar: up to four round outlined buttons along the bottom and an ellipsis
 * that expands the bar to show their labels and the overflow menu (research R3).
 */
@Composable
fun MetroAppBar(
    buttons: List<AppBarButton>,
    modifier: Modifier = Modifier,
    menuItems: List<AppBarMenuItem> = emptyList(),
    initiallyExpanded: Boolean = false
) {
    require(buttons.size <= MAX_BUTTONS) { "The app bar holds at most $MAX_BUTTONS buttons" }
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .navigationBarsPadding()
            .animateContentSize(tween(EXPAND_MS, easing = MetroEasing))
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                buttons.forEach { button ->
                    AppBarIconButton(button, showLabel = expanded) {
                        expanded = false
                        button.onClick()
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 56.dp, height = MetroDimens.TouchTarget)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        expanded = !expanded
                    }
                    .semantics { contentDescription = if (expanded) "less" else "more" }
                    // A full 48dp target, with the dots where WP8.1 draws them near the top edge.
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                MetroIconGlyph(MetroIcon.More, size = 20.dp)
            }
        }
        if (expanded && menuItems.isNotEmpty()) {
            Column(Modifier.padding(start = MetroDimens.Gutter * 2, bottom = MetroDimens.Gutter)) {
                menuItems.forEach { item ->
                    MetroText(
                        item.label,
                        type.subheader,
                        Modifier
                            .fillMaxWidth()
                            .metroTilt()
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                expanded = false
                                item.onClick()
                            }
                            .heightIn(min = MetroDimens.TouchTarget)
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBarIconButton(button: AppBarButton, showLabel: Boolean, onClick: () -> Unit) {
    val colors = MetroTheme.colors
    val color = if (button.enabled) colors.foreground else colors.outline
    Column(
        Modifier
            .width(64.dp)
            .metroTilt(button.enabled)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = button.enabled,
                onClickLabel = button.label,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = button.label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(MetroDimens.TouchTarget).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            MetroIconGlyph(button.icon, color = color)
        }
        if (showLabel) {
            MetroText(
                button.label,
                MetroTheme.typography.caption,
                Modifier.padding(top = 4.dp, bottom = 6.dp),
                color = color,
                maxLines = 1
            )
        } else {
            Box(Modifier.height(4.dp))
        }
    }
}
