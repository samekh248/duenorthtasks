package app.duenorth.tasks.design.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.duenorth.tasks.design.theme.MetroTheme

/** Text in the Metro type ramp. Defaults to the theme foreground; there is no Material Text here. */
@Composable
fun MetroText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    strikethrough: Boolean = false
) {
    val resolved = if (color.isSpecified) color else MetroTheme.colors.foreground
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = resolved,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else style.textDecoration
        ),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap
    )
}
