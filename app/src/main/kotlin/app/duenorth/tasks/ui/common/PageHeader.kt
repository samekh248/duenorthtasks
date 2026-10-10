package app.duenorth.tasks.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/** "DUE NORTH TASKS" in small caps over a 52sp lowercase page title, aligned to the gutter. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String = "DUE NORTH TASKS",
    titleModifier: Modifier = Modifier
) {
    Column(modifier.padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = 16.dp)) {
        MetroText(overline.uppercase(), MetroTheme.typography.pageTitle, maxLines = 1)
        MetroText(
            title.lowercase(),
            MetroTheme.typography.header,
            titleModifier.semantics { heading() },
            maxLines = 1,
            softWrap = false
        )
    }
}
