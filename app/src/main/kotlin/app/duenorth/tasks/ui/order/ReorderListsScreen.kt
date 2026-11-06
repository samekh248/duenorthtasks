package app.duenorth.tasks.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroReorderList
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.ui.common.OrderNoteDialog
import app.duenorth.tasks.ui.common.PageHeader
import app.duenorth.tasks.ui.common.ReorderFooter
import app.duenorth.tasks.ui.common.ReorderHint

@Composable
fun ReorderListsScreen(onDone: () -> Unit, viewModel: ReorderListsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReorderListsContent(state, onMove = viewModel::reorder, onDone = onDone)
    if (state.orderNote) OrderNoteDialog(state.serviceName, viewModel::dismissOrderNote)
}

/** The reorder lists page (specs/003-reordering US4): the list tiles with grippers. */
@Composable
fun ReorderListsContent(state: ReorderListsUiState, onMove: (List<String>) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            PageHeader("lists")
            if (state.loading) {
                MetroTaskPlaceholders(count = 3)
            } else {
                MetroReorderList(
                    items = state.lists,
                    key = { it.id },
                    onMove = { _, ids -> onMove(ids) },
                    modifier = Modifier.fillMaxSize().testTag("reorder-lists"),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    initialKey = state.from,
                    headerItems = 1,
                    header = { item(key = "hint", contentType = "hint") { ReorderHint("drag a list to move it") } },
                    footer = {
                        item(key = "footer", contentType = "footer") {
                            ReorderFooter("this order is kept on this phone")
                        }
                    }
                ) { list ->
                    Row(
                        Modifier.padding(start = MetroDimens.Gutter, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val shade = listAccent(list.id)
                        MetroListTile(
                            count = list.openCount,
                            modifier = Modifier.clearAndSetSemantics {},
                            fill = shade.fill,
                            onFill = shade.onFill
                        )
                        Column(Modifier.weight(1f)) {
                            MetroText(list.title, MetroTheme.typography.listName, maxLines = 1)
                            MetroText(
                                "${list.openCount} open",
                                MetroTheme.typography.caption,
                                color = MetroTheme.colors.secondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        MetroAppBar(buttons = listOf(AppBarButton(MetroIcon.Check, "done", onClick = onDone)))
    }
}
