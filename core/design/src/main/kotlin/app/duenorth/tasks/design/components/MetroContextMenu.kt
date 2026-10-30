package app.duenorth.tasks.design.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
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
import app.duenorth.tasks.design.theme.Relief

@Immutable
data class ContextMenuItem(val label: String, val icon: MetroIcon? = null, val onClick: () -> Unit) {
    constructor(label: String, onClick: () -> Unit) : this(label, null, onClick)
}

/**
 * WP8.1 context menu: a full-width band of lowercase commands that drops just below the
 * long-pressed item (or above it near the bottom of the screen). Place it inside the item. Tile relief raises the
 * band off the page; mica floats a narrower acrylic flyout at the right, with each item's glyph and a line before
 * delete; Windows 10 Mobile shows a menu flyout of body-type commands edged in grey.
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
    val style = MetroTheme.style
    val colors = MetroTheme.colors
    if (style.layers || style.mobile10) {
        Box(modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter), contentAlignment = Alignment.TopEnd) {
            Column(
                Modifier
                    .width(if (style.layers) LayerMenuWidth else FlyoutWidth)
                    .then(
                        if (style.layers) {
                            Modifier.acrylicSheet(MetroTheme.surface).padding(4.dp)
                        } else {
                            Modifier.background(colors.chrome).border(1.dp, colors.outline).padding(vertical = 6.dp)
                        }
                    )
            ) {
                val glyphs = style.layers && items.any { it.icon != null }
                items.forEachIndexed { index, item ->
                    if (style.layers && index > 0 && item.icon == MetroIcon.Delete) {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                vertical = 4.dp
                            ).height(1.dp).background(MetroTheme.surface.stroke)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .metroTilt()
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                onItemClick(item)
                            }
                            .heightIn(min = MetroDimens.TouchTarget)
                            .padding(horizontal = if (style.layers) 10.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (glyphs) {
                            Box(Modifier.size(18.dp)) {
                                if (item.icon != null) MetroIconGlyph(item.icon, size = 18.dp)
                            }
                        }
                        MetroText(item.label, MetroTheme.typography.body)
                    }
                }
            }
        }
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (style.relief) {
                    // Tile relief: the band is a raised panel a gutter in from the edges.
                    Modifier.padding(horizontal = MetroDimens.Gutter).relief(Relief.Raised)
                } else {
                    Modifier.background(colors.chrome)
                }
            )
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

/** Mica's flyout and Windows 10 Mobile's menu flyout are narrower than the screen, at its right. */
private val LayerMenuWidth = 280.dp
private val FlyoutWidth = 240.dp

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
