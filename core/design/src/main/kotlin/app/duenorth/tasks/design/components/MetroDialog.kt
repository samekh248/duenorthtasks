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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.MetroWeights
import app.duenorth.tasks.design.theme.Relief

/**
 * WP8.1 message dialog: a full-width band across the top of the screen with a title, a message
 * and two flat buttons, over a dimmed page. Back or a tap outside the band dismisses it. Tile relief raises the
 * band off the top edge; mica centers a Windows 11 dialog with its buttons on a footer band; under every style
 * but Metro the confirm button is filled with the accent.
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
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = if (MetroTheme.style.layers) Alignment.Center else Alignment.TopStart
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
    val style = MetroTheme.style
    if (style.layers) {
        LayerDialog(title, message, confirmLabel, onConfirm, dismissLabel, onDismiss, modifier)
        return
    }
    val chrome = if (style.relief) {
        // Tile relief: a raised panel under the status bar, a gutter in from the edges.
        Modifier.statusBarsPadding().padding(MetroDimens.Gutter).relief(Relief.Raised)
    } else {
        Modifier.background(colors.chrome).statusBarsPadding()
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(chrome)
            .padding(horizontal = MetroDimens.Gutter * 2, vertical = MetroDimens.Grid),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        MetroText(title, type.subheader.copy(fontSize = type.listName.fontSize))
        MetroText(message, type.body)
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroButton(confirmLabel, onConfirm, Modifier.weight(1f), primary = true)
            if (dismissLabel != null) MetroButton(dismissLabel, onDismiss, Modifier.weight(1f))
        }
        Box(Modifier.padding(bottom = 4.dp))
    }
}

/** Mica's dialog, as Windows 11 draws one: a rounded acrylic sheet in the middle, its buttons on a footer band. */
@Composable
private fun LayerDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    val surface = MetroTheme.surface
    val type = MetroTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MetroDimens.Gutter * 2)
            .acrylicSheet(surface)
            .clip(RoundedCornerShape(LayerCorner.Floating))
    ) {
        Column(
            Modifier.padding(horizontal = MetroDimens.Grid, vertical = MetroDimens.Grid),
            verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroText(title, type.subheader.copy(fontWeight = MetroWeights.Semibold))
            MetroText(message, type.body)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(MetroTheme.colors.background)
                .drawBehind { drawRect(surface.stroke, size = Size(size.width, 1.dp.toPx())) }
                .padding(horizontal = MetroDimens.Grid, vertical = MetroDimens.Gutter * 1.5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroButton(confirmLabel, onConfirm, Modifier.weight(1f), primary = true)
            if (dismissLabel != null) MetroButton(dismissLabel, onDismiss, Modifier.weight(1f))
        }
    }
}
