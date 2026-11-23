package app.duenorth.tasks.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.duenorth.tasks.DebugRoutes
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.motion.TURNSTILE_MS
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.share.CardState
import app.duenorth.tasks.ui.account.SyncAccountActions
import app.duenorth.tasks.ui.account.SyncAccountScreen
import app.duenorth.tasks.ui.detail.TaskDetailScreen
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeScreen
import app.duenorth.tasks.ui.list.ListScreen
import app.duenorth.tasks.ui.list.TapLinks
import app.duenorth.tasks.ui.list.TemplateLinks
import app.duenorth.tasks.ui.order.ReorderListsScreen
import app.duenorth.tasks.ui.search.SearchScreen
import app.duenorth.tasks.ui.settings.LicensesScreen
import app.duenorth.tasks.ui.settings.SettingsScreen
import app.duenorth.tasks.ui.shade.ListShadeScreen
import app.duenorth.tasks.ui.share.InviteScreen
import app.duenorth.tasks.ui.share.TapScreen
import app.duenorth.tasks.ui.share.TapStatusViewModel
import app.duenorth.tasks.ui.sharing.SharingScreen
import app.duenorth.tasks.ui.synclog.SyncLogScreen
import app.duenorth.tasks.ui.templates.ListTemplateActions
import app.duenorth.tasks.ui.templates.ListTemplateScreen
import app.duenorth.tasks.ui.templates.TemplateTaskScreen
import app.duenorth.tasks.ui.templates.TemplatesActions
import app.duenorth.tasks.ui.templates.TemplatesScreen
import app.duenorth.tasks.ui.templates.UseListTemplateScreen

object Routes {
    const val HOME = "home"
    const val LIST = "list/{id}"
    const val TASK = "task/{id}?edit={edit}"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val LIST_SHADE = "list/{id}/shade"
    const val LIST_SHARING = "list/{id}/sharing"
    const val SYNC_LOG = "sync-log"
    const val LICENSES = "settings/licenses"
    const val REORDER_LISTS = "lists/reorder?from={from}"
    const val TEMPLATES = "templates"
    const val LIST_TEMPLATE = "templates/list/{id}"
    const val USE_LIST_TEMPLATE = "templates/list/{id}/use"
    const val TEMPLATE_TASK = "templates/task/{id}"
    const val TAP = "tap?list={list}"
    const val INVITE = "invite"

    fun list(id: String) = "list/$id"

    fun listShade(id: String) = "list/$id/shade"

    fun listSharing(id: String) = "list/$id/sharing"

    fun task(id: String) = "task/$id"

    /** The task page with its title and details already open for typing ("edit" on a task's menu). */
    fun editTask(id: String) = "task/$id?edit=true"

    fun listTemplate(id: String) = "templates/list/$id"

    fun useListTemplate(id: String) = "templates/list/$id/use"

    fun templateTask(id: String) = "templates/task/$id"

    /** Share by tap for [listId], or "tap to sync" without one (spec 011). */
    fun tap(listId: String?) = if (listId == null) "tap" else "tap?list=$listId"

    fun reorderLists(from: String?) = if (from == null) "lists/reorder" else "lists/reorder?from=$from"
}

/** App-wide actions the pages need; signing out and switching outlive the page that asked. */
class AppActions(
    val syncNow: () -> Unit,
    /** The service to switch to, and whether to copy "this phone only"'s tasks up to it (spec 010). */
    val switchTo: (ProviderKind, Boolean) -> Unit,
    val signOut: () -> Unit
)

/**
 * Every page after the account gate (T026). Pages swing with the turnstile: the leaving page turns
 * away first, then the new one swings in, so the NavHost keeps the old page on screen until then.
 */
@Composable
fun DueNorthNavHost(
    app: AppActions,
    syncing: Boolean,
    nav: NavHostController = rememberNavController(),
    /** False for "this phone only" (spec 010): no sync item in the hub menu. */
    syncs: Boolean = true,
    tapStatus: TapStatusViewModel = hiltViewModel()
) {
    val tap by tapStatus.availability.collectAsState()
    val card by tapStatus.card.collectAsState()
    // A phone tapped while Due North is open (or brought up by the invite) shows the invite page.
    LaunchedEffect(card is CardState.Invite) {
        if (card is CardState.Invite && nav.currentDestination?.route != Routes.INVITE) nav.navigate(Routes.INVITE)
    }
    val tapSync = { nav.navigate(Routes.tap(null)) }.takeIf { tap.available && tap.shared.isNotEmpty() }
    val shareByTap: ((String) -> Unit)? = { id: String -> nav.navigate(Routes.tap(id)) }.takeIf { tap.available }
    val keepOldPage = fadeOut(tween(1, delayMillis = TURNSTILE_MS))
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { keepOldPage },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { keepOldPage }
    ) {
        composable(Routes.HOME) { entry ->
            val showLists by entry.savedStateHandle.getStateFlow(SHOW_LISTS, false).collectAsState()
            val actions = HomeActions(
                openTask = { nav.navigate(Routes.task(it)) },
                openList = { nav.navigate(Routes.list(it)) },
                search = { nav.navigate(Routes.SEARCH) },
                editTask = { nav.navigate(Routes.editTask(it)) },
                sync = app.syncNow.takeIf { syncs },
                openSettings = { nav.navigate(Routes.SETTINGS) },
                openListShade = { nav.navigate(Routes.listShade(it)) },
                openListInfo = { nav.navigate(Routes.listSharing(it)) },
                reorderLists = { nav.navigate(Routes.reorderLists(it)) },
                openTemplates = { nav.navigate(Routes.TEMPLATES) },
                openTemplateTask = { nav.navigate(Routes.templateTask(it)) },
                openListTemplate = { nav.navigate(Routes.listTemplate(it)) },
                tapSync = tapSync,
                tapShared = tap.shared,
                menuItems = if (DebugRoutes.GALLERY_ENABLED) {
                    listOf(AppBarMenuItem("component gallery") { nav.navigate(Routes.GALLERY) })
                } else {
                    emptyList()
                }
            )
            Page(this) {
                HomeScreen(
                    actions,
                    syncing,
                    showLists = showLists,
                    onListsShown = { entry.savedStateHandle[SHOW_LISTS] = false }
                )
            }
        }
        composable(Routes.LIST) { entry ->
            val listId = entry.arguments?.getString("id").orEmpty()
            Page(this) {
                ListScreen(
                    onOpenTask = { nav.navigate(Routes.task(it)) },
                    onEditTask = { nav.navigate(Routes.editTask(it)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onClosed = { nav.closeIfOn(entry) },
                    onShade = { id -> nav.navigate(Routes.listShade(id)) },
                    onInfo = { id -> nav.navigate(Routes.listSharing(id)) },
                    onTemplates = TemplateLinks(
                        openListTemplate = { nav.navigate(Routes.listTemplate(it)) },
                        openTemplateTask = { nav.navigate(Routes.templateTask(it)) }
                    ),
                    onTap = TapLinks(
                        shareByTap = shareByTap?.let { { it(listId) } },
                        tapSync = { nav.navigate(Routes.tap(null)) }.takeIf { tap.available && listId in tap.shared }
                    )
                )
            }
        }
        composable(Routes.TEMPLATES) {
            Page(this) {
                TemplatesScreen(
                    TemplatesActions(
                        useList = { nav.navigate(Routes.useListTemplate(it)) },
                        editList = { nav.navigate(Routes.listTemplate(it)) },
                        editTask = { nav.navigate(Routes.templateTask(it)) },
                        openSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                )
            }
        }
        composable(Routes.LIST_TEMPLATE) { entry ->
            val id = checkNotNull(entry.arguments?.getString("id"))
            Page(this) {
                ListTemplateScreen(
                    ListTemplateActions(
                        use = { nav.navigate(Routes.useListTemplate(id)) },
                        editTask = { nav.navigate(Routes.templateTask(it)) },
                        closed = { nav.closeIfOn(entry) },
                        openSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                )
            }
        }
        composable(Routes.USE_LIST_TEMPLATE) { entry ->
            Page(this) {
                UseListTemplateScreen(
                    onCreated = { listId ->
                        if (nav.currentBackStackEntry?.id ==
                            entry.id
                        ) {
                            nav.openCreatedList(listId)
                        }
                    },
                    onCancel = { nav.closeIfOn(entry) }
                )
            }
        }
        composable(Routes.TEMPLATE_TASK) { entry ->
            Page(this) {
                TemplateTaskScreen(onClosed = { nav.closeIfOn(entry) }, onSettings = { nav.navigate(Routes.SETTINGS) })
            }
        }
        composable(Routes.LIST_SHADE) {
            Page(this) { ListShadeScreen() }
        }
        composable(Routes.LIST_SHARING) { entry ->
            Page(this) {
                SharingScreen(
                    onClosed = { nav.closeIfOn(entry) },
                    onShade = { id -> nav.navigate(Routes.listShade(id)) },
                    onShareByTap = shareByTap
                )
            }
        }
        composable(
            Routes.TAP,
            arguments = listOf(
                navArgument("list") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            Page(this) { TapScreen(onDone = { nav.closeIfOn(entry) }) }
        }
        composable(Routes.INVITE) { entry ->
            Page(this) {
                InviteScreen(
                    onOpenList = { id ->
                        nav.navigate(Routes.list(id)) { popUpTo(Routes.INVITE) { inclusive = true } }
                    },
                    onClosed = { nav.closeIfOn(entry) }
                )
            }
        }
        composable(Routes.SETTINGS) {
            Page(this) {
                SettingsScreen(
                    onOpenSyncAccount = { nav.navigate(Routes.ACCOUNT) },
                    onOpenSyncLog = { nav.navigate(Routes.SYNC_LOG) },
                    onOpenLicenses = { nav.navigate(Routes.LICENSES) }
                )
            }
        }
        composable(
            Routes.REORDER_LISTS,
            arguments = listOf(
                navArgument("from") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            Page(this) { ReorderListsScreen(onDone = { nav.closeIfOn(entry) }) }
        }
        composable(Routes.SYNC_LOG) {
            Page(this) { SyncLogScreen() }
        }
        composable(Routes.LICENSES) {
            Page(this) { LicensesScreen() }
        }
        composable(
            Routes.TASK,
            arguments = listOf(
                navArgument("edit") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            val edit = entry.arguments?.getBoolean("edit") ?: false
            Page(this) {
                TaskDetailScreen(
                    onClosed = { nav.closeIfOn(entry) },
                    animatedScope = this,
                    startEditing = edit,
                    onSettings = { nav.navigate(Routes.SETTINGS) }
                )
            }
        }
        composable(Routes.ACCOUNT) {
            Page(this) { SyncAccountScreen(SyncAccountActions(switchTo = app.switchTo, signOut = app.signOut)) }
        }
        composable(Routes.SEARCH) {
            Page(this) { SearchScreen(onOpenTask = { nav.navigate(Routes.task(it)) }) }
        }
        if (DebugRoutes.GALLERY_ENABLED) {
            composable(Routes.GALLERY) { Page(this) { DebugRoutes.Gallery() } }
        }
    }
}

/** Tells home to show its lists section the next time it is on screen. */
internal const val SHOW_LISTS = "showLists"

/**
 * Opens list [listId], just made from a template, in place of the templates pages: back from it
 * goes to home's lists section, not to the template (spec 004 FR-325).
 */
internal fun NavHostController.openCreatedList(listId: String) {
    getBackStackEntry(Routes.HOME).savedStateHandle[SHOW_LISTS] = true
    navigate(Routes.list(listId)) { popUpTo(Routes.HOME) }
}

/** Closes [entry]'s page once, even if its content asks twice. */
private fun NavHostController.closeIfOn(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
