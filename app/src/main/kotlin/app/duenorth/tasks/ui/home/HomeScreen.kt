package app.duenorth.tasks.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroInputDialog
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroPanorama
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.motion.rememberContinuumState
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.frozen
import app.duenorth.tasks.ui.common.importanceItem
import app.duenorth.tasks.ui.common.rememberTouchHold
import app.duenorth.tasks.ui.common.touchHold
import kotlinx.coroutines.launch

private const val TODAY = 0
private const val LISTS = 1
private const val DONE = 2

/** What the home screen can ask of the rest of the app. */
class HomeActions(
    val openTask: (String) -> Unit,
    val openList: (String) -> Unit,
    val search: () -> Unit,
    val switchAccount: () -> Unit,
    val menuItems: List<AppBarMenuItem> = emptyList()
)

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, viewModel, actions)
}

/** The Light Panorama home (FR-002): "tasks" (with "due north" under it) over today, lists and done. */
@Composable
fun HomeContent(state: HomeUiState, viewModel: HomeViewModel, actions: HomeActions) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val addFocus = remember { FocusRequester() }
    val continuum = rememberContinuumState()
    var newList by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ListRowUi?>(null) }
    var deleting by remember { mutableStateOf<ListRowUi?>(null) }
    var moving by remember { mutableStateOf<TaskRowUi?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    val openTask: (String) -> Unit = { id ->
        scope.launch {
            continuum.flyOut(id)
            actions.openTask(id)
        }
    }
    val taskMenu: (TaskRowUi) -> List<ContextMenuItem> = { row ->
        listOfNotNull(
            ContextMenuItem("edit") { actions.openTask(row.id) },
            ContextMenuItem("delete") { viewModel.deleteTask(row.id) },
            ContextMenuItem("move to") { moving = row },
            importanceItem(state.importance, row) { viewModel.setImportant(row.id, it) }
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            MetroPanorama(
                title = "tasks",
                subtitle = "due north",
                state = pager,
                sections = listOf(
                    PanoramaSection("today") {
                        TodaySection(state, viewModel, addFocus, continuum, openTask, taskMenu)
                    },
                    PanoramaSection("lists") {
                        ListsSection(
                            state = state,
                            onOpen = actions.openList,
                            onNew = { newList = true },
                            onRename = { renaming = it },
                            onDelete = { deleting = it }
                        )
                    },
                    PanoramaSection("done") {
                        DoneSection(state, viewModel, continuum, openTask)
                    }
                )
            )
        }
        val search = AppBarButton(MetroIcon.Search, "search", onClick = actions.search)
        val menu = listOf(AppBarMenuItem("switch account") { leaving = true }) + actions.menuItems
        when (pager.currentPage) {
            TODAY -> MetroAppBar(
                buttons = listOf(
                    AppBarButton(MetroIcon.Add, "new task") { addFocus.requestFocus() },
                    search
                ),
                menuItems = menu
            )

            LISTS -> MetroAppBar(
                buttons = listOf(AppBarButton(MetroIcon.Add, "new list") { newList = true }, search),
                menuItems = menu
            )

            else -> MetroAppBar(buttons = listOf(search), menuItems = menu)
        }
    }

    if (newList) {
        MetroInputDialog(
            title = "new list",
            initialValue = "",
            placeholder = "list name",
            confirmLabel = "create",
            onConfirm = {
                viewModel.createList(it)
                newList = false
            },
            onDismiss = { newList = false }
        )
    }
    renaming?.let { list ->
        MetroInputDialog(
            title = "rename list",
            initialValue = list.title,
            confirmLabel = "rename",
            onConfirm = {
                viewModel.renameList(list.id, it)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { list ->
        MetroDialog(
            title = "delete ${list.title}?",
            message = "This deletes the list and its ${list.openCount} open tasks here and in ${state.serviceName}.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteList(list.id)
                deleting = null
            },
            onDismiss = { deleting = null }
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
    if (leaving) {
        MetroDialog(
            title = "switch account?",
            message = "This signs out of ${state.serviceName} and clears its tasks from this phone. " +
                "Tasks already synced stay in that account.",
            confirmLabel = "switch",
            onConfirm = {
                leaving = false
                actions.switchAccount()
            },
            onDismiss = { leaving = false }
        )
    }
}

@Composable
private fun TodaySection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    addFocus: FocusRequester,
    continuum: ContinuumState,
    openTask: (String) -> Unit,
    taskMenu: (TaskRowUi) -> List<ContextMenuItem>
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val dueToday = hold.frozen(state.dueToday)
    val tomorrow = hold.frozen(state.tomorrow)
    LazyColumn(
        Modifier.fillMaxSize().touchHold(hold).testTag("today"),
        state = list,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "add", contentType = "add") {
            AddTaskBox(
                serviceName = state.serviceName,
                today = state.today,
                lists = state.lists,
                onAdd = { title, details, due, list -> viewModel.addTask(title, details, due, list) },
                focusRequester = addFocus,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        if (state.loading) {
            item(key = "loading", contentType = "loading") { MetroTaskPlaceholders() }
            return@LazyColumn
        }
        if (dueToday.isEmpty() && tomorrow.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyNote("nothing due today")
            }
        }
        items(dueToday, key = { it.id }, contentType = { "task" }) { row ->
            TaskRow(
                row = row,
                onToggle = { viewModel.setCompleted(row.id, it) },
                onOpen = { openTask(row.id) },
                menuItems = taskMenu(row),
                continuum = continuum,
                modifier = Modifier.animateItem()
            )
        }
        if (tomorrow.isNotEmpty()) {
            item(key = "tomorrow", contentType = "header") {
                MetroText(
                    "tomorrow",
                    MetroTheme.typography.subheader,
                    Modifier.padding(top = 16.dp, bottom = 4.dp).animateItem(),
                    color = MetroTheme.colors.secondary
                )
            }
            items(tomorrow, key = { it.id }, contentType = { "task" }) { row ->
                TaskRow(
                    row = row,
                    onToggle = { viewModel.setCompleted(row.id, it) },
                    onOpen = { openTask(row.id) },
                    menuItems = taskMenu(row),
                    continuum = continuum,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun ListsSection(
    state: HomeUiState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (ListRowUi) -> Unit,
    onDelete: (ListRowUi) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("lists"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        if (state.loading) {
            item(key = "loading") { MetroTaskPlaceholders(count = 3) }
            return@LazyColumn
        }
        items(state.lists, key = { it.id }, contentType = { "list" }) { list ->
            ListRow(list, onOpen = { onOpen(list.id) }, onRename = { onRename(list) }, onDelete = { onDelete(list) })
        }
        item(key = "new", contentType = "new") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .metroTilt()
                    .combinedClickable(interactionSource = null, indication = null, onClick = onNew),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
            ) {
                MetroListTile(count = null)
                MetroText("new list", MetroTheme.typography.listName)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(list: ListRowUi, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .metroTilt()
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onLongClick = { menu = true },
                    onClick = onOpen
                ),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroListTile(count = list.openCount)
            Column(Modifier.weight(1f)) {
                MetroText(list.title, MetroTheme.typography.listName, maxLines = 1)
                MetroText(
                    list.next?.let { "next: $it" } ?: "no open tasks",
                    MetroTheme.typography.caption,
                    color = MetroTheme.colors.secondary,
                    maxLines = 1
                )
            }
        }
        MetroContextMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOf(ContextMenuItem("rename", onRename), ContextMenuItem("delete", onDelete))
        )
    }
}

@Composable
private fun DoneSection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    continuum: ContinuumState,
    openTask: (String) -> Unit
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val done = hold.frozen(state.done)
    LazyColumn(
        Modifier.fillMaxSize().touchHold(hold).testTag("done"),
        state = list,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        if (state.loading) {
            item(key = "loading") { MetroTaskPlaceholders() }
            return@LazyColumn
        }
        if (done.isEmpty()) {
            item(key = "empty") { EmptyNote("nothing done yet") }
        }
        items(done, key = { it.id }, contentType = { "task" }) { row ->
            TaskRow(
                row = row,
                onToggle = { viewModel.setCompleted(row.id, it) },
                onOpen = { openTask(row.id) },
                continuum = continuum,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
internal fun EmptyNote(text: String) {
    MetroText(
        text,
        MetroTheme.typography.subheader,
        Modifier.padding(top = 12.dp, end = MetroDimens.Gutter),
        color = MetroTheme.colors.secondary
    )
}
