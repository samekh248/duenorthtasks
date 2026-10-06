package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * WP8.1 message dialog: a full-width band across the top of the screen with a title, a message
 * and two flat buttons, over a dimmed page. Back or a tap outside the band dismisses it.
 */
@Composable
fun MetroDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Null for a note with one button, which then also dismisses. */
    dismissLabel: String? = "cancel"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MetroTheme.colors.scrim)
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        ) {
            MetroDialogContent(
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                dismissLabel = dismissLabel,
                onDismiss = onDismiss,
                modifier = Modifier.clickable(interactionSource = null, indication = null) {}
            )
        }
    }
}

/** The band itself, separate so it can be screenshot-tested and previewed without a window. */
@Composable
fun MetroDialogContent(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .statusBarsPadding()
            .padding(horizontal = MetroDimens.Gutter * 2, vertical = MetroDimens.Grid),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        MetroText(title, type.subheader.copy(fontSize = type.listName.fontSize))
        MetroText(message, type.body)
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroButton(confirmLabel, onConfirm, Modifier.weight(1f))
            if (dismissLabel != null) MetroButton(dismissLabel, onDismiss, Modifier.weight(1f))
        }
        Box(Modifier.padding(bottom = 4.dp))
    }
}
