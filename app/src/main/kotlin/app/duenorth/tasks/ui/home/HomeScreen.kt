package app.duenorth.tasks.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroInputDialog
import app.duenorth.tasks.design.components.MetroPanorama
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.PanoramaSection
import app.duenorth.tasks.design.components.PanoramaState
import app.duenorth.tasks.design.motion.rememberContinuumState
import app.duenorth.tasks.design.theme.LocalListShades
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.deleteListMessage
import app.duenorth.tasks.ui.common.deleteListTitle
import app.duenorth.tasks.ui.common.importanceItem
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
private const val STATS = "stats"

private fun pinKey(listId: String) = "pin:$listId"

/** What the home screen can ask of the rest of the app. */
class HomeActions(
    val openTask: (String) -> Unit,
    val openList: (String) -> Unit,
    val search: () -> Unit,
    /** "edit" on a task's menu: its page with the title and details already open for typing. */
    val editTask: (String) -> Unit = openTask,
    /** Syncs now; null hides the sync button (no sync engine, as in tests). */
    val sync: (() -> Unit)? = null,
    val openSettings: () -> Unit = {},
    val openListShade: (String) -> Unit = {},
    /** The list's sharing page (spec 002): who owns it and what can be done where. */
    val openListInfo: (String) -> Unit = {},
    /** The reorder lists page, with the long-pressed list (or none) in view. */
    val reorderLists: (String?) -> Unit = {},
    /** The templates page and a task template's editor (spec 004). */
    val openTemplates: () -> Unit = {},
    val openTemplateTask: (String) -> Unit = {},
    val openListTemplate: (String) -> Unit = {},
    val menuItems: List<AppBarMenuItem> = emptyList()
)

@Composable
fun HomeScreen(
    actions: HomeActions,
    syncing: Boolean,
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
        showLists,
        onListsShown,
        stats,
        onStatsSeen = statsViewModel::start
    )
}

/**
 * The Light Panorama home (FR-002): "tasks" (with "due north" under it) over today, any pinned
 * lists, lists and stats (spec 005). [onStatsSeen] fires the first time the panorama moves, so
 * stats are only counted once they may be looked at (FR-431).
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    actions: HomeActions,
    syncing: Boolean = false,
    /** Jump to the lists section, as after making a list from a template; [onListsShown] then clears it. */
    showLists: Boolean = false,
    onListsShown: () -> Unit = {},
    stats: StatsUi? = null,
    onStatsSeen: () -> Unit = {}
) {
    val sectionKeys = remember(state.pinned) {
        listOf(TODAY) + state.pinned.map { pinKey(it.list.id) } + listOf(LISTS, STATS)
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
    val shades = LocalListShades.current

    val openTask: (String) -> Unit = { id ->
        scope.launch {
            continuum.flyOut(id)
            actions.openTask(id)
        }
    }
    val taskMenu: (TaskRowUi) -> List<ContextMenuItem> = { row ->
        // The one task menu order (open, organize, share, delete last), the same as on a list page.
        listOfNotNull(
            ContextMenuItem("edit", MetroIcon.Edit) { actions.editTask(row.id) },
            ContextMenuItem("move to list", MetroIcon.Move) { moving = row },
            importanceItem(state.importance, row) { viewModel.setImportant(row.id, it) },
            ContextMenuItem("make template") { viewModel.saveTaskAsTemplate(row.id, actions.openTemplateTask) },
            ContextMenuItem("delete", MetroIcon.Delete) { viewModel.deleteTask(row.id) }
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
                            onUnpin = { viewModel.unpin(it.id) },
                            onMakeTemplate = {
                                viewModel.saveListAsTemplate(it.id, shades[it.id] ?: 0, actions.openListTemplate)
                            }
                        )
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
            onNewTask = { section ->
                when {
                    section.startsWith("pin:") -> focusFor(section.removePrefix("pin:")).requestFocus()
                    section == TODAY -> addFocus.requestFocus()
                    else -> scope.launch {
                        pager.scrollToSection(0)
                        addFocus.requestFocus()
                    }
                }
            },
            onNewList = { newList = true },
            onUnpin = { viewModel.unpin(it) }
        )
    }

    if (newList) {
        MetroInputDialog(
            title = "add list",
            initialValue = "",
            placeholder = "list name",
            confirmLabel = "add",
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
            title = "move to list",
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
    onNewTask: (section: String) -> Unit,
    onNewList: () -> Unit,
    onUnpin: (listId: String) -> Unit
) {
    val section = sectionKeys.getOrElse(pager.currentSection) { TODAY }
    val pinned = section.takeIf { it.startsWith("pin:") }?.removePrefix("pin:")
    val search = AppBarButton(MetroIcon.Search, "search", onClick = actions.search)
    // Due North Budget's hub menu: the section's own tools, then the shared tail under a rule (sync,
    // then settings, last as WP8.1 apps put it). Sync account and sync log live in settings.
    val menu = listOfNotNull(
        pinned?.let { AppBarMenuItem("unpin from home") { onUnpin(it) } },
        AppBarMenuItem("reorder lists") { actions.reorderLists(null) }.takeIf { section == LISTS },
        AppBarMenuItem("templates", onClick = actions.openTemplates)
    ) + hubTail(actions.sync, actions.openSettings) + actions.menuItems
    // Every hub section leads with add, its caption the noun alone (the + is the verb); on stats it
    // adds to today. So search never moves.
    val first = when (section) {
        LISTS -> AppBarButton(MetroIcon.Add, "add list", caption = "list", onClick = onNewList)
        else -> AppBarButton(MetroIcon.Add, "add task", caption = "task") { onNewTask(section) }
    }
    MetroAppBar(buttons = listOf(first, search), menuItems = menu)
}

/**
 * The tail every hub and object page menu ends with, under a rule, as in Due North Budget: sync when
 * there is a sync engine, then settings.
 */
internal fun hubTail(sync: (() -> Unit)?, openSettings: () -> Unit): List<AppBarMenuItem> = listOfNotNull(
    sync?.let { AppBarMenuItem("sync", onClick = it) },
    AppBarMenuItem("settings", onClick = openSettings)
).mapIndexed { i, item -> if (i == 0) item.copy(rule = true) else item }

@Composable
internal fun EmptyNote(text: String) {
    MetroText(
        text,
        MetroTheme.typography.subheader,
        Modifier.padding(top = 12.dp, end = MetroDimens.Gutter),
        color = MetroTheme.colors.secondary
    )
}
