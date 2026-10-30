package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroStyle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.Relief

/**
 * WP8.1 text box: a flat rectangle with a 2dp outline and no underline. When focused it turns
 * into a white box with dark text and an accent outline, in both themes, like the phone did. Tile
 * relief and mica cut the box into the page and keep the page's ink (mica adds an accent line under
 * the box being typed in); Windows 10 Mobile keeps an outline that turns to the accent.
 */
@Composable
fun MetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textStyle: TextStyle = MetroTheme.typography.body,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val look = metroFieldLook(source, enabled)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(color = look.text),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        interactionSource = source,
        cursorBrush = SolidColor(look.text),
        decorationBox = { inner -> MetroFieldBox(look, value.isEmpty(), placeholder, textStyle, inner) }
    )
}

/**
 * The same text box over a [TextFieldState], for boxes that clear themselves after an action, like
 * "add a task". Clearing [state] empties the field's own buffer at once, so a key the keyboard
 * sends before the next frame lands in the empty box instead of bringing the old text back.
 */
@Composable
fun MetroTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textStyle: TextStyle = MetroTheme.typography.body,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val look = metroFieldLook(source, enabled)
    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(color = look.text),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        interactionSource = source,
        cursorBrush = SolidColor(look.text),
        decorator = { inner -> MetroFieldBox(look, state.text.isEmpty(), placeholder, textStyle, inner) }
    )
}

private class FieldLook(val focused: Boolean, val fill: Color, val text: Color, val outline: Color)

@Composable
private fun metroFieldLook(source: MutableInteractionSource, enabled: Boolean): FieldLook {
    val colors = MetroTheme.colors
    val focused by source.collectIsFocusedAsState()
    val outline = when {
        !enabled -> colors.outline
        focused -> MetroTheme.accent.fill
        else -> colors.outline
    }
    // Only Metro turns the box white while typing; the other styles write in the page's own ink.
    val whiteBox = focused && MetroTheme.style == MetroStyle.Metro
    return FieldLook(
        focused = focused,
        fill = if (whiteBox) Color.White else Color.Transparent,
        text = if (whiteBox) FocusedText else colors.foreground,
        outline = outline
    )
}

@Composable
private fun MetroFieldBox(
    look: FieldLook,
    empty: Boolean,
    placeholder: String?,
    textStyle: TextStyle,
    inner: @Composable () -> Unit
) {
    val style = MetroTheme.style
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(
                when {
                    style.layers -> Modifier.relief(Relief.Inset, small = true).layerFocusLine(look.focused)
                    style.relief -> Modifier.relief(Relief.Inset, small = true)
                    else -> Modifier.background(look.fill).border(2.dp, look.outline)
                }
            )
            .padding(horizontal = if (style.relief) 14.dp else 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (empty && placeholder != null) {
            MetroText(
                placeholder,
                textStyle,
                color = if (look.fill == Color.White) FocusedPlaceholder else MetroTheme.colors.secondary
            )
        }
        inner()
    }
}

private val FocusedText = Color(0xFF111111)
private val FocusedPlaceholder = Color(0xFF6E6E6E)
