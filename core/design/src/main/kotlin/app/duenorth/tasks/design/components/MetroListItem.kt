package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.MetroWeights

/**
 * A Metro list row aligned to the 12dp gutter: optional leading control, a 20sp title, up to two
 * grey lines of details and a caption (for tasks: "List · when", in the accent or red when
 * overdue), and an optional trailing mark such as the importance star. Tilts when pressed;
 * long-press opens the context menu. A [badge] (the due date) follows the grey caption in [captionColor]: on a
 * tinted pill when [badgePill] (mica), or after a dot (Windows 10 Mobile).
 */
@Composable
fun MetroListItem(
    title: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    caption: String? = null,
    captionColor: Color = Color.Unspecified,
    badge: String? = null,
    badgePill: Boolean = true,
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
            val tint = if (captionColor == Color.Unspecified) MetroTheme.accent.text else captionColor
            if (badge != null && badgePill) {
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!caption.isNullOrEmpty()) {
                        MetroText(
                            caption,
                            type.caption,
                            color = colors.secondary,
                            maxLines = 1
                        )
                    }
                    MetroPill(badge, tint)
                }
            } else if (badge != null) {
                Row {
                    if (!caption.isNullOrEmpty()) {
                        MetroText("$caption · ", type.caption, color = colors.secondary, maxLines = 1)
                    }
                    MetroText(badge, type.caption, color = tint, maxLines = 1)
                }
            } else if (!caption.isNullOrEmpty()) {
                MetroText(caption, type.caption, color = tint, maxLines = 1)
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(top = 8.dp, start = 8.dp)) { trailing() }
        }
    }
}

/** A short label on a pill tinted with [color], in that color: mica's due date. */
@Composable
fun MetroPill(text: String, color: Color, modifier: Modifier = Modifier) {
    MetroText(
        text,
        MetroTheme.typography.caption.copy(fontWeight = MetroWeights.Semibold),
        modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(PillCorner))
            .padding(horizontal = 8.dp, vertical = 1.dp),
        color = color,
        maxLines = 1
    )
}
