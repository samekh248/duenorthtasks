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

/**
 * Text in the Metro type ramp. Defaults to the theme foreground; there is no Material Text here. Under the
 * Windows 10 Mobile style it is written in [sentenceCase].
 */
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
        text = if (MetroTheme.style.mobile10) sentenceCase(text) else text,
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

/**
 * Windows 10 Mobile writes labels in sentence case where Metro writes them in lowercase: the first letter is raised
 * when the text starts in lowercase, so names, figures, links and all-caps titles are left as they are.
 */
fun sentenceCase(text: String): String = when {
    text.firstOrNull()?.isLowerCase() != true -> text
    // A link is left as it is typed.
    text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.") -> text
    else -> text.replaceFirstChar { it.titlecaseChar() }
}
