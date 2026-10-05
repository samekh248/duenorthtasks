package app.duenorth.tasks.design.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * A Metro list row aligned to the 12dp gutter: optional leading control, a 20sp title, up to two
 * grey lines of details and a caption (for tasks: "List · when", in the accent or red when
 * overdue), and an optional trailing mark such as the importance star. Tilts when pressed;
 * long-press opens the context menu.
 */
@Composable
fun MetroListItem(
    title: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    caption: String? = null,
    captionColor: Color = Color.Unspecified,
    strikethrough: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    titleModifier: Modifier = Modifier
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val interactive = onClick != null || onLongClick != null
    val click = if (interactive) {
        Modifier.combinedClickable(
            interactionSource = null,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick ?: {}
        )
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .metroTilt(interactive)
            .then(click)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(start = if (leading == null) MetroDimens.Gutter else 0.dp, end = MetroDimens.Gutter)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (leading == null) 0.dp else 4.dp)
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f).padding(top = if (leading == null) 0.dp else 8.dp)) {
            MetroText(
                title,
                type.subheader,
                titleModifier,
                color = if (strikethrough) colors.secondary else colors.foreground,
                maxLines = 2,
                strikethrough = strikethrough
            )
            if (!details.isNullOrBlank()) {
                MetroText(details, type.preview, color = colors.secondary, maxLines = 2)
            }
            if (caption != null) {
                MetroText(
                    caption,
                    type.caption,
                    color = if (captionColor == Color.Unspecified) MetroTheme.accent.text else captionColor,
                    maxLines = 1
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(top = 8.dp, start = 8.dp)) { trailing() }
        }
    }
}
