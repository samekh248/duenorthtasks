package app.duenorth.tasks.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroInputDialog
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroPivot
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.rememberPivotState
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.home.EmptyNote

/** What the templates page can open. */
class TemplatesActions(val useList: (String) -> Unit, val editList: (String) -> Unit, val editTask: (String) -> Unit)

@Composable
fun TemplatesScreen(actions: TemplatesActions, viewModel: TemplatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TemplatesContent(state, viewModel, actions)
}

private enum class Kind { LIST, TASK }

private sealed interface Prompt {
    data class New(val kind: Kind) : Prompt
    data class Rename(val kind: Kind, val row: TemplateRowUi) : Prompt
    data class Delete(val kind: Kind, val row: TemplateRowUi) : Prompt
}

/**
 * spec 004 FR-330: a pivot with "lists" and "tasks". Tapping a list template uses it; tapping a
 * task template edits it (task templates are used from the "add a task" box). Long-press for
 * edit, rename and delete.
 */
@Composable
fun TemplatesContent(state: TemplatesUiState, viewModel: TemplatesViewModel, actions: TemplatesActions) {
    val pager = rememberPivotState(2)
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var newName by rememberSaveable { mutableStateOf<Kind?>(null) }

    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        MetroPivot(
            headers = listOf("lists", "tasks"),
            pageTitle = "templates",
            state = pager,
            modifier = Modifier.weight(1f).statusBarsPadding().testTag("templates")
        ) { index ->
            val kind = if (index == 0) Kind.LIST else Kind.TASK
            val rows = if (kind == Kind.LIST) state.lists else state.tasks
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                if (state.loading) {
                    item(key = "loading") { MetroTaskPlaceholders() }
                    return@LazyColumn
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyNote(if (kind == Kind.LIST) "no list templates yet" else "no task templates yet")
                    }
                }
                items(rows, key = { it.id }) { row ->
                    TemplateRow(
                        row = row,
                        tile = kind == Kind.LIST,
                        onClick = { if (kind == Kind.LIST) actions.useList(row.id) else actions.editTask(row.id) },
                        menu = listOf(
                            ContextMenuItem("edit") {
                                if (kind == Kind.LIST) actions.editList(row.id) else actions.editTask(row.id)
                            },
                            ContextMenuItem("rename") { prompt = Prompt.Rename(kind, row) },
                            ContextMenuItem("delete") { prompt = Prompt.Delete(kind, row) }
                        ),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Add, "new") { newName = if (pager.currentPage == 0) Kind.LIST else Kind.TASK }
            )
        )
    }

    newName?.let { kind ->
        MetroInputDialog(
            title = if (kind == Kind.LIST) "new list template" else "new task template",
            initialValue = "",
            confirmLabel = "create",
            onConfirm = { name ->
                newName = null
                when (kind) {
                    Kind.LIST -> viewModel.newListTemplate(name, actions.editList)
                    Kind.TASK -> viewModel.newTaskTemplate(name, actions.editTask)
                }
            },
            onDismiss = { newName = null }
        )
    }
    when (val p = prompt) {
        is Prompt.Rename -> MetroInputDialog(
            title = "rename template",
            initialValue = p.row.title,
            confirmLabel = "rename",
            onConfirm = {
                prompt = null
                if (p.kind == Kind.LIST) viewModel.renameList(p.row.id, it) else viewModel.renameTask(p.row.id, it)
            },
            onDismiss = { prompt = null }
        )
        is Prompt.Delete -> MetroDialog(
            title = "delete template?",
            message = "\"${p.row.title}\" will be deleted from this phone. Lists and tasks made from it stay " +
                "as they are.",
            confirmLabel = "delete",
            onConfirm = {
                prompt = null
                if (p.kind == Kind.LIST) viewModel.deleteList(p.row.id) else viewModel.deleteTask(p.row.id)
            },
            onDismiss = { prompt = null }
        )
        else -> Unit
    }
}

@Composable
private fun TemplateRow(
    row: TemplateRowUi,
    tile: Boolean,
    onClick: () -> Unit,
    menu: List<ContextMenuItem>,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        MetroListItem(
            title = row.title,
            modifier = Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter),
            caption = row.caption,
            captionColor = MetroTheme.colors.secondary,
            leading = if (tile) {
                { Box(Modifier.size(24.dp).background(shadeAccent(row.shadeStep).fill)) }
            } else {
                null
            },
            onClick = onClick,
            onLongClick = { open = true }
        )
        MetroContextMenu(expanded = open, onDismiss = { open = false }, items = menu)
    }
}
