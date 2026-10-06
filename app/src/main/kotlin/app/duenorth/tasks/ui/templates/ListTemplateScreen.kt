package app.duenorth.tasks.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroInputDialog
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroReorderList
import app.duenorth.tasks.design.components.MetroShadeRow
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.theme.LocalAccent
import app.duenorth.tasks.design.theme.LocalAppAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.common.PageHeader
import app.duenorth.tasks.ui.common.ReorderHint
import app.duenorth.tasks.ui.common.ReorderRow
import app.duenorth.tasks.ui.home.EmptyNote

class ListTemplateActions(val use: () -> Unit, val editTask: (String) -> Unit, val closed: () -> Unit)

@Composable
fun ListTemplateScreen(actions: ListTemplateActions, viewModel: ListTemplateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exists) { if (!state.exists) actions.closed() }
    ListTemplateContent(state, viewModel, actions)
}

/**
 * spec 004 FR-331: a list page marked "LIST TEMPLATE", check boxes dimmed and not tappable, due
 * offsets in place of dates. The whole page uses the template's shade.
 */
@Composable
fun ListTemplateContent(state: ListTemplateUiState, viewModel: ListTemplateViewModel, actions: ListTemplateActions) {
    CompositionLocalProvider(LocalAccent provides shadeAccent(state.shadeStep)) {
        if (state.reordering) {
            ReorderTemplateTasks(state, viewModel)
        } else {
            TemplatePage(state, viewModel, actions)
        }
    }
}

@Composable
private fun ReorderTemplateTasks(state: ListTemplateUiState, viewModel: ListTemplateViewModel) {
    BackHandler(onBack = viewModel::endReorder)
    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            PageHeader(state.name, overline = "LIST TEMPLATE")
            MetroReorderList(
                items = state.tasks,
                key = { it.id },
                onMove = viewModel::reorder,
                modifier = Modifier.fillMaxSize().testTag("reorder-template"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                initialKey = state.reorderFrom,
                headerItems = 1,
                header = { item(key = "hint", contentType = "hint") { ReorderHint("drag a task to move it") } }
            ) { task ->
                ReorderRow(title = task.title, caption = task.caption, captionColor = MetroTheme.colors.secondary)
            }
        }
        MetroAppBar(buttons = listOf(AppBarButton(MetroIcon.Check, "done", onClick = viewModel::endReorder)))
    }
}

@Composable
private fun TemplatePage(state: ListTemplateUiState, viewModel: ListTemplateViewModel, actions: ListTemplateActions) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var shading by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            PageHeader(state.name, overline = "LIST TEMPLATE")
            if (shading) {
                Column(Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)) {
                    MetroShadeRow(
                        accent = LocalAppAccent.current,
                        selectedStep = state.shadeStep,
                        onSelect = viewModel::setShade
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetroText(
                            "lists made from it get this shade",
                            MetroTheme.typography.caption,
                            Modifier.weight(1f),
                            color = MetroTheme.colors.secondary
                        )
                        MetroLink("done", { shading = false })
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("list template"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                if (state.loading) {
                    item(key = "loading") { MetroTaskPlaceholders() }
                    return@LazyColumn
                }
                if (state.tasks.isEmpty()) item(key = "empty") { EmptyNote("no tasks yet") }
                items(state.tasks, key = { it.id }) { task ->
                    TemplateTaskRow(
                        task,
                        onOpen = { actions.editTask(task.id) },
                        menu = listOfNotNull(
                            ContextMenuItem("reorder") {
                                viewModel.startReorder(task.id)
                            }.takeIf { state.tasks.size > 1 },
                            ContextMenuItem("edit") { actions.editTask(task.id) },
                            ContextMenuItem("delete") { viewModel.deleteTask(task.id) }
                        ),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Check, "use", onClick = actions.use),
                AppBarButton(MetroIcon.Add, "add") { adding = true },
                AppBarButton(MetroIcon.Reorder, "reorder", enabled = state.tasks.size > 1) { viewModel.startReorder() }
            ),
            menuItems = listOf(
                AppBarMenuItem("rename template") { renaming = true },
                AppBarMenuItem("template shade") { shading = true },
                AppBarMenuItem("delete template") { deleting = true }
            )
        )
    }

    if (adding) {
        MetroInputDialog(
            title = "add a task",
            initialValue = "",
            confirmLabel = "add",
            onConfirm = {
                adding = false
                viewModel.addTask(it)
            },
            onDismiss = { adding = false }
        )
    }
    if (renaming) {
        MetroInputDialog(
            title = "rename template",
            initialValue = state.name,
            confirmLabel = "rename",
            onConfirm = {
                renaming = false
                viewModel.rename(it)
            },
            onDismiss = { renaming = false }
        )
    }
    if (deleting) {
        MetroDialog(
            title = "delete template?",
            message = "\"${state.name}\" will be deleted from this phone. Lists made from it stay as they are.",
            confirmLabel = "delete",
            onConfirm = {
                deleting = false
                viewModel.delete()
            },
            onDismiss = { deleting = false }
        )
    }
}

/** A template task: a dimmed, untappable check box, the title, and its offset and steps. */
@Composable
private fun TemplateTaskRow(task: TemplateRowUi, onOpen: () -> Unit, menu: List<ContextMenuItem>, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        MetroListItem(
            title = task.title,
            modifier = Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter),
            caption = task.caption,
            captionColor = MetroTheme.accent.text,
            leading = { MetroCheckBox(checked = false, onCheckedChange = null, modifier = Modifier.alpha(DIMMED)) },
            onClick = onOpen,
            onLongClick = { open = true }
        )
        MetroContextMenu(expanded = open, onDismiss = { open = false }, items = menu)
    }
}

private const val DIMMED = 0.35f
