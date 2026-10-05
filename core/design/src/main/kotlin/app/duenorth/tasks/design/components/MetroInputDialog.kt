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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** A top band dialog over a dimmed page; back or a tap outside dismisses it. */
@Composable
private fun MetroBandDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {}
                    .background(MetroTheme.colors.chrome)
                    .statusBarsPadding()
                    .padding(horizontal = MetroDimens.Gutter * 2, vertical = MetroDimens.Grid),
                verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
            ) {
                content()
            }
        }
    }
}

/** Asks for one line of text, for naming and renaming lists. The text box takes focus at once. */
@Composable
fun MetroInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String? = null,
    dismissLabel: String = "cancel"
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val focus = remember { FocusRequester() }
    val confirm = { if (value.isNotBlank()) onConfirm(value.trim()) }
    MetroBandDialog(onDismiss) {
        MetroText(title, MetroTheme.typography.listName)
        MetroTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() })
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
            MetroButton(confirmLabel, { confirm() }, Modifier.weight(1f), enabled = value.isNotBlank())
            MetroButton(dismissLabel, onDismiss, Modifier.weight(1f))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Picks one option from a short list, such as the list a task belongs to. */
@Composable
fun <T> MetroPickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    MetroBandDialog(onDismiss) {
        MetroText(title, MetroTheme.typography.listName)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { option ->
                MetroRadio(selected = option == selected, onClick = { onPick(option) }, label = label(option))
            }
        }
    }
}
