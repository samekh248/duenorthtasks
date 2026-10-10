package app.duenorth.tasks.ui.shade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroShadeRow
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.LocalAppAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.common.PageHeader

@Composable
fun ListShadeScreen(viewModel: ListShadeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListShadeContent(state, onPick = viewModel::pick)
}

/**
 * contracts/ui-screens.md "List shade": seven flat swatches, lightest to darkest, the middle one
 * the app accent; above them a live preview of the list's tile and a task caption.
 */
@Composable
fun ListShadeContent(state: ListShadeUiState, onPick: (Int) -> Unit) {
    val shade = shadeAccent(state.step)
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .statusBarsPadding()
            .testTag("list shade")
    ) {
        PageHeader("list shade", overline = state.title.ifEmpty { "DUE NORTH TASKS" })
        Column(Modifier.padding(horizontal = MetroDimens.Gutter)) {
            Row(
                Modifier.fillMaxWidth().padding(top = MetroDimens.Grid, bottom = MetroDimens.Grid),
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
            ) {
                MetroListTile(count = state.openCount, fill = shade.fill, onFill = shade.onFill)
                Column(Modifier.weight(1f)) {
                    MetroText(state.title, MetroTheme.typography.listName, maxLines = 1)
                    MetroText(
                        // A task's due caption, as it reads on this list's rows.
                        "${state.title.ifEmpty { "list" }} · tomorrow",
                        MetroTheme.typography.caption,
                        color = shade.text,
                        maxLines = 1
                    )
                }
            }
            MetroShadeRow(accent = LocalAppAccent.current, selectedStep = state.step, onSelect = onPick)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                MetroText(
                    "lighter",
                    MetroTheme.typography.caption,
                    Modifier.weight(1f),
                    color = MetroTheme.colors.secondary
                )
                MetroText("darker", MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
            }
            MetroText(
                stepName(state.step),
                MetroTheme.typography.subheader,
                Modifier.padding(top = MetroDimens.Grid)
            )
            MetroText(
                "The shade follows the app accent, and stays on this phone only.",
                MetroTheme.typography.caption,
                Modifier.padding(top = 4.dp),
                color = MetroTheme.colors.secondary
            )
        }
    }
}

private fun stepName(step: Int): String = when {
    step == 0 -> "app accent"
    step == AccentShades.LIGHTEST -> "lightest"
    step == AccentShades.DARKEST -> "darkest"
    step == -1 -> "1 shade lighter"
    step < 0 -> "${-step} shades lighter"
    step == 1 -> "1 shade darker"
    else -> "$step shades darker"
}
