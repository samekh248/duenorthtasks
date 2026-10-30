package app.duenorth.tasks.design.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * A one-line text box that adds what was typed when the keyboard's done key is pressed, then
 * empties at once, like "add a task" and "add a step". [onAdd] gets the trimmed text; blank text
 * adds nothing. Pass [state] to read the text as it is typed or to keep it per item.
 */
@Composable
fun MetroQuickAddField(
    placeholder: String,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState()
) {
    MetroTextField(
        state = state,
        placeholder = placeholder,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        onKeyboardAction = {
            val text = state.text.trim().toString()
            if (text.isNotEmpty()) onAdd(text)
            state.clearText()
        }
    )
}
