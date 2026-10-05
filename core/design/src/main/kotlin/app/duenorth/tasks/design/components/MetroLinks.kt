package app.duenorth.tasks.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** A lowercase accent text link, like "add details" under the add box. */
@Composable
fun MetroLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MetroTheme.typography.body
) {
    MetroText(
        text,
        style,
        modifier
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = 12.dp),
        color = MetroTheme.accent.text,
        maxLines = 1
    )
}

private val UrlPattern = Regex("""(https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]']""", RegexOption.IGNORE_CASE)

/** Plain text with web addresses turned into accent links that open in the browser. */
@Composable
fun MetroLinkifiedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MetroTheme.colors.foreground
) {
    val linkColor = MetroTheme.accent.text
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    BasicText(annotated, modifier, style = style.copy(color = color))
}

internal fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    UrlPattern.findAll(text).forEach { match ->
        val url = match.value.let { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
        addLink(LinkAnnotation.Url(url, styles), match.range.first, match.range.last + 1)
    }
}
