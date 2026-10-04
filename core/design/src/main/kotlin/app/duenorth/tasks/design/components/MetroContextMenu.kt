package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

@Immutable
data class ContextMenuItem(val label: String, val onClick: () -> Unit)

/**
 * WP8.1 context menu: a full-width band of lowercase commands that drops just below the
 * long-pressed item (or above it near the bottom of the screen). Place it inside the item.
 */
@Composable
fun MetroContextMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<ContextMenuItem>) {
    if (!expanded) return
    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        MetroContextMenuContent(items) { item ->
            onDismiss()
            item.onClick()
        }
    }
}

@Composable
fun MetroContextMenuContent(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    onItemClick: (ContextMenuItem) -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MetroTheme.colors.chrome)
            .padding(vertical = MetroDimens.Gutter)
    ) {
        items.forEach { item ->
            MetroText(
                item.label,
                MetroTheme.typography.subheader,
                Modifier
                    .fillMaxWidth()
                    .metroTilt()
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onItemClick(item) }
                    .heightIn(min = MetroDimens.TouchTarget)
                    .padding(horizontal = MetroDimens.Gutter * 2, vertical = 10.dp)
            )
        }
    }
}

/** Full window width, directly under the anchor, flipping above it when there is no room below. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(0, y)
    }
}
