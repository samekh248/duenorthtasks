package app.duenorth.tasks.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroInputDialog
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.motion.rememberContinuumState
import app.duenorth.tasks.design.theme.ListAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.PageHeader
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.deleteListMessage
import app.duenorth.tasks.ui.common.deleteListTitle
import app.duenorth.tasks.ui.common.frozen
import app.duenorth.tasks.ui.common.importanceItem
import app.duenorth.tasks.ui.common.rememberTouchHold
import app.duenorth.tasks.ui.common.touchHold
import app.duenorth.tasks.ui.home.EmptyNote
import kotlinx.coroutines.launch

/** One list (contracts/ui-screens.md "List page"): header, open tasks, collapsible "completed". */
@Composable
fun ListScreen(
    onOpenTask: (String) -> Unit,
    onClosed: () -> Unit,
    onShade: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    viewModel: ListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exists) { if (!state.exists) onClosed() }
    ListContent(state, viewModel, onOpenTask, onShade, onInfo)
}

/** The whole page, check boxes and captions included, uses the list's shade of the accent. */
@Composable
fun ListContent(
    state: ListUiState,
    viewModel: ListViewModel,
    onOpenTask: (String) -> Unit,
    onShade: (String) -> Unit = {},
    onInfo: (String) -> Unit = {}
) {
    ListAccent(viewModel.listId) { ListPage(state, viewModel, onOpenTask, onShade, onInfo) }
}

@Composable
private fun ListPage(
    state: ListUiState,
    viewModel: ListViewModel,
    onOpenTask: (String) -> Unit,
    onShade: (String) -> Unit,
    onInfo: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val continuum = rememberContinuumState()
    val addFocus = remember { FocusRequester() }
    var draft by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf<TaskRowUi?>(null) }
    val listState = rememberLazyListState()
    val hold = rememberTouchHold(listState, viewModel::holdSync)
    val open = hold.frozen(state.open)
    val completed = hold.frozen(state.completed)

    val openTask: (String) -> Unit = { id ->
        scope.launch {
            continuum.flyOut(id)
            onOpenTask(id)
        }
    }
    val row: @Composable (TaskRowUi, Modifier) -> Unit = { task, modifier ->
        TaskRow(
            row = task,
            onToggle = { viewModel.setCompleted(task.id, it) },
            onOpen = { openTask(task.id) },
            menuItems = listOfNotNull(
                ContextMenuItem("edit") { onOpenTask(task.id) },
                ContextMenuItem("delete") { viewModel.deleteTask(task.id) },
                ContextMenuItem("move to") { moving = task },
                importanceItem(state.importance, task) { viewModel.setImportant(task.id, it) }
            ),
            continuum = continuum,
            modifier = modifier
        )
    }

    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background).imePadding()) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            PageHeader(state.title)
            if (state.sharing.isShared) SharedLine(state.sharing) { onInfo(viewModel.listId) }
            LazyColumn(
                Modifier.fillMaxSize().touchHold(hold).testTag("list"),
                state = listState,
                contentPadding = PaddingValues(start = MetroDimens.Gutter, top = 8.dp, bottom = 24.dp)
            ) {
                item(key = "add", contentType = "add") {
                    MetroTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "add a task",
                        modifier = Modifier.padding(end = MetroDimens.Gutter, bottom = 4.dp).focusRequester(addFocus),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.addTask(draft.trim())
                            draft = ""
                        })
                    )
                }
                if (state.loading) {
                    item(key = "loading") { MetroTaskPlaceholders() }
                    return@LazyColumn
                }
                if (open.isEmpty()) {
                    item(key = "empty") { EmptyNote("nothing to do here") }
                }
                items(open, key = { it.id }, contentType = { "task" }) { row(it, Modifier.animateItem()) }
                if (completed.isNotEmpty()) {
                    item(key = "completed", contentType = "header") {
                        MetroText(
                            "completed (${completed.size}) ${if (state.completedExpanded) "⌃" else "⌄"}",
                            MetroTheme.typography.subheader,
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .metroTilt()
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = viewModel::toggleCompletedGroup
                                )
                                .heightIn(min = MetroDimens.TouchTarget)
                                .padding(top = 16.dp, bottom = 4.dp),
                            color = MetroTheme.colors.secondary
                        )
                    }
                    if (state.completedExpanded) {
                        items(completed, key = { it.id }, contentType = { "task" }) {
                            row(it, Modifier.animateItem())
                        }
                    }
                }
            }
        }
        MetroAppBar(
            buttons = listOfNotNull(
                AppBarButton(MetroIcon.Add, "new task") { addFocus.requestFocus() },
                AppBarButton(MetroIcon.People, "sharing") { onInfo(viewModel.listId) }
                    .takeIf { state.sharing.isShared },
                AppBarButton(MetroIcon.Sort, "sort") { sorting = true }
            ),
            // Only the owner can rename or delete a shared list (spec 002 FR-120).
            menuItems = listOfNotNull(
                AppBarMenuItem("rename list") { renaming = true }.takeIf { state.sharing.canManage },
                AppBarMenuItem("list shade") { onShade(viewModel.listId) },
                AppBarMenuItem("list info") { onInfo(viewModel.listId) },
                AppBarMenuItem("delete list") { deleting = true }.takeIf { state.sharing.canManage }
            )
        )
    }

    if (sorting) {
        MetroPickerDialog(
            title = "sort by",
            options = ListSort.entries,
            selected = state.sort,
            label = { it.label },
            onPick = {
                viewModel.setSort(it)
                sorting = false
            },
            onDismiss = { sorting = false }
        )
    }
    if (renaming) {
        MetroInputDialog(
            title = "rename list",
            initialValue = state.title,
            confirmLabel = "rename",
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }
    if (deleting) {
        MetroDialog(
            title = deleteListTitle(state.title, state.sharing),
            message = deleteListMessage(state.title, state.open.size, state.sharing, state.serviceName),
            confirmLabel = "delete",
            onConfirm = {
                deleting = false
                viewModel.delete()
            },
            onDismiss = { deleting = false }
        )
    }
    moving?.let { task ->
        MetroPickerDialog(
            title = "move to",
            options = state.lists,
            selected = state.lists.firstOrNull { it.id == task.listId },
            label = { it.title },
            onPick = {
                viewModel.moveTask(task.id, it.id)
                moving = null
            },
            onDismiss = { moving = null }
        )
    }
}

/** "shared with you · details" under the title, in the list's shade; opens the sharing page. */
@Composable
private fun SharedLine(sharing: ListSharing, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter)
            .heightIn(min = MetroDimens.TouchTarget)
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .testTag("shared line"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetroIconGlyph(MetroIcon.People, color = MetroTheme.accent.text, size = 20.dp)
        MetroText("${sharing.caption} · details", MetroTheme.typography.body, color = MetroTheme.accent.text)
    }
}
