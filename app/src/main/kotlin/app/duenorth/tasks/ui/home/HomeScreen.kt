package app.duenorth.tasks.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.components.PanoramaState
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.motion.rememberContinuumState
import app.duenorth.tasks.design.theme.ListAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.deleteListMessage
import app.duenorth.tasks.ui.common.deleteListTitle
import app.duenorth.tasks.ui.common.frozen
import app.duenorth.tasks.ui.common.importanceItem
import app.duenorth.tasks.ui.common.rememberTouchHold
import app.duenorth.tasks.ui.common.touchHold
import app.duenorth.tasks.ui.stats.StatsUi
import app.duenorth.tasks.ui.stats.StatsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Section keys. Pinned lists sit between "today" and "lists", each under [pinKey]. */
private const val TODAY = "today"
private const val LISTS = "lists"
private const val DONE = "done"
private const val STATS = "stats"

private fun pinKey(listId: String) = "pin:$listId"

/** What the home screen can ask of the rest of the app. */
class HomeActions(
    val openTask: (String) -> Unit,
    val openList: (String) -> Unit,
    val search: () -> Unit,
    val openSyncAccount: () -> Unit,
    /** Syncs now; null hides the sync button (no sync engine, as in tests). */
    val sync: (() -> Unit)? = null,
    val openSettings: () -> Unit = {},
    val openListShade: (String) -> Unit = {},
    /** The list's sharing page (spec 002): who owns it and what can be done where. */
    val openListInfo: (String) -> Unit = {},
    val openSyncLog: () -> Unit = {},
    /** The reorder lists page, with the long-pressed list (or none) in view. */
    val reorderLists: (String?) -> Unit = {},
    /** The templates page and a task template's editor (spec 004). */
    val openTemplates: () -> Unit = {},
    val openTemplateTask: (String) -> Unit = {},
    val menuItems: List<AppBarMenuItem> = emptyList()
)

@Composable
fun HomeScreen(
    actions: HomeActions,
    syncing: Boolean,
    syncButtonTurning: Boolean = syncing,
    showLists: Boolean = false,
    onListsShown: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statsViewModel: StatsViewModel = hiltViewModel()
    val stats by statsViewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state,
        viewModel,
        actions,
        syncing,
        syncButtonTurning,
        showLists,
        onListsShown,
        stats,
        onStatsSeen = statsViewModel::start
    )
}

/**
 * The Light Panorama home (FR-002): "tasks" (with "due north" under it) over today, lists, done
 * and stats (spec 005). [onStatsSeen] fires the first time the panorama moves, so stats are only
 * counted once they may be looked at (FR-431).
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    actions: HomeActions,
    syncing: Boolean = false,
    syncButtonTurning: Boolean = syncing,
    /** Jump to the lists section, as after making a list from a template; [onListsShown] then clears it. */
    showLists: Boolean = false,
    onListsShown: () -> Unit = {},
    stats: StatsUi? = null,
    onStatsSeen: () -> Unit = {}
) {
    val sectionKeys = remember(state.pinned) {
        listOf(TODAY) + state.pinned.map { pinKey(it.list.id) } + listOf(LISTS, DONE, STATS)
    }
    // The section in view, by key and by place, so that pinning or unpinning a list keeps it in
    // view; unpinning the one in view shows the section that took its place.
    var resting by rememberSaveable { mutableStateOf(TODAY) }
    var restingIndex by rememberSaveable { mutableIntStateOf(0) }
    val pager = remember(sectionKeys.size) {
        val at = sectionKeys.indexOf(resting).takeIf { it >= 0 } ?: restingIndex.coerceAtMost(sectionKeys.lastIndex)
        PanoramaState(sectionKeys.size, at)
    }
    LaunchedEffect(pager, sectionKeys, state.loading) {
        // Pins are not known until the first load; until then, keep the section from before.
        if (state.loading) return@LaunchedEffect
        snapshotFlow { pager.currentSection }.collect {
            resting = sectionKeys[it]
            restingIndex = it
        }
    }
    val pinnedFocus = remember { mutableMapOf<String, FocusRequester>() }
    val focusFor: (String) -> FocusRequester = { id -> pinnedFocus.getOrPut(id) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    val addFocus = remember { FocusRequester() }
    val continuum = rememberContinuumState()
    var newList by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ListRowUi?>(null) }
    var deleting by remember { mutableStateOf<ListRowUi?>(null) }
    var moving by remember { mutableStateOf<TaskRowUi?>(null) }

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
            importanceItem(state.importance, row) { viewModel.setImportant(row.id, it) },
            ContextMenuItem("save as template") { viewModel.saveAsTemplate(row.id, actions.openTemplateTask) }
        )
    }

    LaunchedEffect(showLists) {
        if (showLists) {
            pager.scrollToSection(sectionKeys.indexOf(LISTS))
            onListsShown()
        }
    }

    // Each return to "today" replays the empty-today logo (spec 005 US4); the first showing plays by itself.
    var arrivals by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentSection == 0 && !pager.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .collect { arrivals++ }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress || pager.currentSection != 0 }.first { it }
        onStatsSeen()
    }

    LaunchedEffect(pager, viewModel) {
        // Hold sync batches while the sections slide, like a finger on a list does, so a sync
        // started by the cold start never lands mid-swipe.
        var holding = false
        try {
            snapshotFlow { pager.isScrollInProgress }.collect { moving ->
                if (moving != holding) {
                    holding = moving
                    viewModel.holdSync(moving)
                }
            }
        } finally {
            if (holding) viewModel.holdSync(false)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            MetroPanorama(
                title = "tasks",
                subtitle = "due north",
                state = pager,
                sections = listOf(
                    PanoramaSection(TODAY) {
                        TodaySection(state, viewModel, addFocus, continuum, openTask, taskMenu) { arrivals }
                    }
                ) + state.pinned.map { pinned ->
                    PanoramaSection(pinned.list.title.lowercase(), key = pinKey(pinned.list.id)) {
                        PinnedSection(pinned, state, viewModel, focusFor(pinned.list.id), continuum, openTask, taskMenu)
                    }
                } + listOf(
                    PanoramaSection(LISTS) {
                        ListsSection(
                            state = state,
                            onOpen = actions.openList,
                            onNew = { newList = true },
                            onRename = { renaming = it },
                            onShade = { actions.openListShade(it.id) },
                            onInfo = { actions.openListInfo(it.id) },
                            onDelete = { deleting = it },
                            onReorder = { actions.reorderLists(it.id) },
                            onPin = { viewModel.pin(it.id) },
                            onUnpin = { viewModel.unpin(it.id) }
                        )
                    },
                    PanoramaSection(DONE) {
                        DoneSection(state, viewModel, continuum, openTask)
                    },
                    PanoramaSection(STATS) {
                        StatsSection(stats, state.lists, state.serviceName, actions.openList)
                    }
                )
            )
            if (syncing) MetroProgressDots()
        }
        HomeAppBar(
            pager,
            sectionKeys,
            actions,
            syncButtonTurning,
            onNewTask = { section ->
                if (section == TODAY) addFocus.requestFocus() else focusFor(section.removePrefix("pin:")).requestFocus()
            },
            onNewList = { newList = true },
            onUnpin = { viewModel.unpin(it) }
        )
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
            title = deleteListTitle(list.title, list.sharing),
            message = deleteListMessage(list.title, list.openCount, list.sharing, state.serviceName),
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
}

/**
 * Its own composable so that the swipe passing the middle of a section, which swaps the first
 * button, recomposes only the app bar and never the panorama under the finger.
 */
@Composable
private fun HomeAppBar(
    pager: PanoramaState,
    sectionKeys: List<String>,
    actions: HomeActions,
    syncing: Boolean,
    onNewTask: (section: String) -> Unit,
    onNewList: () -> Unit,
    onUnpin: (listId: String) -> Unit
) {
    val section = sectionKeys.getOrElse(pager.currentSection) { TODAY }
    val pinned = section.takeIf { it.startsWith("pin:") }?.removePrefix("pin:")
    val search = AppBarButton(MetroIcon.Search, "search", onClick = actions.search)
    val sync = actions.sync?.let { AppBarButton(MetroIcon.Sync, "sync", spinning = syncing, onClick = it) }
    val menu = listOfNotNull(
        pinned?.let { AppBarMenuItem("unpin from home") { onUnpin(it) } },
        AppBarMenuItem("reorder lists") { actions.reorderLists(null) }.takeIf { section == LISTS },
        AppBarMenuItem("templates", actions.openTemplates),
        AppBarMenuItem("settings", actions.openSettings),
        AppBarMenuItem("sync account", actions.openSyncAccount),
        AppBarMenuItem("sync log", actions.openSyncLog)
    ) + actions.menuItems
    val first = when {
        section == TODAY || pinned != null -> AppBarButton(MetroIcon.Add, "new task") { onNewTask(section) }
        section == LISTS -> AppBarButton(MetroIcon.Add, "new list", onClick = onNewList)
        else -> null
    }
    MetroAppBar(buttons = listOfNotNull(first, sync, search), menuItems = menu)
}

@Composable
private fun TodaySection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    addFocus: FocusRequester,
    continuum: ContinuumState,
    openTask: (String) -> Unit,
    taskMenu: (TaskRowUi) -> List<ContextMenuItem>,
    arrivals: () -> Int
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val dueToday = hold.frozen(state.dueToday, state.added)
    val tomorrow = hold.frozen(state.tomorrow, state.added)
    var addHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.onSizeChanged { addHeight = it.height }.padding(top = 8.dp, bottom = 4.dp),
                    templates = state.taskTemplates,
                    onUseTemplate = { viewModel.addFromTemplate(it) }
                )
            }
            if (state.loading) {
                item(key = "loading", contentType = "loading") { MetroTaskPlaceholders() }
                return@LazyColumn
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
        val empty = !state.loading && dueToday.isEmpty() && tomorrow.isEmpty()
        AnimatedVisibility(empty, enter = fadeIn(), exit = fadeOut()) {
            val top = with(LocalDensity.current) { addHeight.toDp() }
            EmptyToday(arrivals, Modifier.fillMaxSize().padding(top = top, bottom = 24.dp))
        }
    }
}

/**
 * A pinned list's own section: an "add a task" box for that list and its open tasks in the list's
 * own order, all in the list's shade.
 */
@Composable
private fun PinnedSection(
    pinned: PinnedListUi,
    state: HomeUiState,
    viewModel: HomeViewModel,
    addFocus: FocusRequester,
    continuum: ContinuumState,
    openTask: (String) -> Unit,
    taskMenu: (TaskRowUi) -> List<ContextMenuItem>
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val open = hold.frozen(pinned.open)
    var draft by rememberSaveable(pinned.list.id) { mutableStateOf("") }
    ListAccent(pinned.list.id) {
        LazyColumn(
            Modifier.fillMaxSize().touchHold(hold).testTag("pinned:${pinned.list.title}"),
            state = list,
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
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
                        viewModel.addTask(draft.trim(), due = null, listId = pinned.list.id)
                        draft = ""
                    })
                )
            }
            if (state.loading) {
                item(key = "loading", contentType = "loading") { MetroTaskPlaceholders() }
                return@LazyColumn
            }
            if (open.isEmpty()) {
                item(key = "empty", contentType = "empty") { EmptyNote("nothing to do here") }
            }
            items(open, key = { it.id }, contentType = { "task" }) { row ->
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
    onShade: (ListRowUi) -> Unit,
    onInfo: (ListRowUi) -> Unit,
    onDelete: (ListRowUi) -> Unit,
    onReorder: (ListRowUi) -> Unit,
    onPin: (ListRowUi) -> Unit,
    onUnpin: (ListRowUi) -> Unit
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
            ListRow(
                list,
                onOpen = { onOpen(list.id) },
                onRename = { onRename(list) },
                onShade = { onShade(list) },
                onInfo = { onInfo(list) },
                onDelete = { onDelete(list) },
                onReorder = { onReorder(list) }.takeIf { state.lists.size > 1 },
                onPin = { if (list.pinned) onUnpin(list) else onPin(list) }
            )
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
private fun ListRow(
    list: ListRowUi,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShade: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (() -> Unit)?,
    onPin: () -> Unit
) {
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
                )
                // TalkBack reads the name first, then "shared with you, 3 open" instead of a bare
                // number from the tile.
                .semantics {
                    stateDescription = listOf(list.sharing.spoken, "${list.openCount} open")
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                },
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            val shade = listAccent(list.id)
            MetroListTile(
                count = list.openCount,
                modifier = Modifier.clearAndSetSemantics {},
                fill = shade.fill,
                onFill = shade.onFill,
                shared = list.sharing.isShared
            )
            Column(Modifier.weight(1f)) {
                MetroText(list.title, MetroTheme.typography.listName, maxLines = 1)
                MetroText(
                    listOfNotNull(
                        list.sharing.caption.ifEmpty { null },
                        list.next?.let { "next: $it" } ?: "no open tasks"
                    ).joinToString(" · "),
                    MetroTheme.typography.caption,
                    color = MetroTheme.colors.secondary,
                    maxLines = 1
                )
            }
        }
        MetroContextMenu(
            expanded = menu,
            onDismiss = { menu = false },
            // Only the owner can rename or delete a shared list; "list info" says so (spec 002 FR-120).
            items = listOfNotNull(
                onReorder?.let { ContextMenuItem("reorder", it) },
                ContextMenuItem(if (list.pinned) "unpin from home" else "pin to home", onPin),
                ContextMenuItem("rename", onRename).takeIf { list.sharing.canManage },
                ContextMenuItem("list shade", onShade),
                ContextMenuItem("list info", onInfo),
                ContextMenuItem("delete", onDelete).takeIf { list.sharing.canManage }
            )
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
