package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroTheme

/**
 * A one-line text box that adds what was typed when the keyboard's done key is pressed, then
 * empties at once, like "add a task" and "add a step". [onAdd] gets the trimmed text; blank text
 * adds nothing. Pass [state] to read the text as it is typed or to keep it per item.
 *
 * Under tonal a [standalone] box (add a task, at the head of a page or section) sits in a tonal card of its own,
 * apart from the cards under it; one inside a card already (add a step) is the bare field.
 */
@Composable
fun MetroQuickAddField(
    placeholder: String,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    standalone: Boolean = true
) {
    // The card is drawn on the field's own modifier, after the caller's, so a focus requester still reaches it.
    val card = if (standalone && MetroTheme.style.tonal) {
        Modifier
            .padding(bottom = 12.dp)
            .background(MetroTheme.surface.layer, RoundedCornerShape(TonalCorner.Card))
            .padding(12.dp)
    } else {
        Modifier
    }
    MetroTextField(
        state = state,
        placeholder = placeholder,
        modifier = modifier.then(card),
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
