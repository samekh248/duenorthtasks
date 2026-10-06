package app.duenorth.tasks.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * A row in reorder mode (specs/003-reordering FR-204): check box, title and caption only, none of
 * them tappable, so the whole row is for dragging.
 */
@Composable
fun ReorderRow(title: String, caption: String?, captionColor: Color = Color.Unspecified, checked: Boolean? = false) {
    MetroListItem(
        title = title,
        modifier = Modifier.padding(start = MetroDimens.Gutter),
        caption = caption,
        captionColor = captionColor,
        leading = checked?.let { { MetroCheckBox(checked = it, onCheckedChange = null) } }
    )
}

/** The grey line under a reorder page's title: "drag a task to move it". */
@Composable
fun ReorderHint(text: String) {
    MetroText(
        text,
        MetroTheme.typography.body,
        Modifier.padding(start = MetroDimens.Gutter, bottom = 12.dp),
        color = MetroTheme.colors.secondary
    )
}

/** The grey note under the last row of a reorder page. */
@Composable
fun ReorderFooter(text: String) {
    Column(Modifier.padding(start = MetroDimens.Gutter, top = 16.dp, end = MetroDimens.Gutter)) {
        MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
    }
}

/** FR-231: shown once, the first time a reorder mode opens on a service that doesn't store order. */
@Composable
fun OrderNoteDialog(serviceName: String, onDismiss: () -> Unit) {
    MetroDialog(
        title = "order stays on this phone",
        message = "$serviceName doesn't let other apps read or change the order of tasks, lists or steps. " +
            "The order you set here is kept on this phone and doesn't show in $serviceName.",
        confirmLabel = "got it",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = null
    )
}
