package app.duenorth.tasks.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import app.duenorth.tasks.ui.account.SyncAccountActions
import app.duenorth.tasks.ui.account.SyncAccountScreen
import app.duenorth.tasks.ui.detail.TaskDetailScreen
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeScreen
import app.duenorth.tasks.ui.list.ListScreen
import app.duenorth.tasks.ui.list.TemplateLinks
import app.duenorth.tasks.ui.order.ReorderListsScreen
import app.duenorth.tasks.ui.search.SearchScreen
import app.duenorth.tasks.ui.settings.SettingsScreen
import app.duenorth.tasks.ui.shade.ListShadeScreen
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
    const val TASK = "task/{id}"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val LIST_SHADE = "list/{id}/shade"
    const val LIST_SHARING = "list/{id}/sharing"
    const val SYNC_LOG = "sync-log"
    const val REORDER_LISTS = "lists/reorder?from={from}"
    const val TEMPLATES = "templates"
    const val LIST_TEMPLATE = "templates/list/{id}"
    const val USE_LIST_TEMPLATE = "templates/list/{id}/use"
    const val TEMPLATE_TASK = "templates/task/{id}"

    fun list(id: String) = "list/$id"

    fun listShade(id: String) = "list/$id/shade"

    fun listSharing(id: String) = "list/$id/sharing"

    fun task(id: String) = "task/$id"

    fun listTemplate(id: String) = "templates/list/$id"

    fun useListTemplate(id: String) = "templates/list/$id/use"

    fun templateTask(id: String) = "templates/task/$id"

    fun reorderLists(from: String?) = if (from == null) "lists/reorder" else "lists/reorder?from=$from"
}

/** App-wide actions the pages need; signing out and switching outlive the page that asked. */
class AppActions(val syncNow: () -> Unit, val switchTo: (ProviderKind) -> Unit, val signOut: () -> Unit)

/**
 * Every page after the account gate (T026). Pages swing with the turnstile: the leaving page turns
 * away first, then the new one swings in, so the NavHost keeps the old page on screen until then.
 */
@Composable
fun DueNorthNavHost(
    app: AppActions,
    syncing: Boolean,
    syncButtonTurning: Boolean = syncing,
    nav: NavHostController = rememberNavController()
) {
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
        composable(Routes.HOME) {
            val actions = HomeActions(
                openTask = { nav.navigate(Routes.task(it)) },
                openList = { nav.navigate(Routes.list(it)) },
                search = { nav.navigate(Routes.SEARCH) },
                openSyncAccount = { nav.navigate(Routes.ACCOUNT) },
                sync = app.syncNow,
                openSettings = { nav.navigate(Routes.SETTINGS) },
                openListShade = { nav.navigate(Routes.listShade(it)) },
                openListInfo = { nav.navigate(Routes.listSharing(it)) },
                openSyncLog = { nav.navigate(Routes.SYNC_LOG) },
                reorderLists = { nav.navigate(Routes.reorderLists(it)) },
                openTemplates = { nav.navigate(Routes.TEMPLATES) },
                openTemplateTask = { nav.navigate(Routes.templateTask(it)) },
                menuItems = if (DebugRoutes.GALLERY_ENABLED) {
                    listOf(AppBarMenuItem("component gallery") { nav.navigate(Routes.GALLERY) })
                } else {
                    emptyList()
                }
            )
            Page(this) { HomeScreen(actions, syncing, syncButtonTurning) }
        }
        composable(Routes.LIST) { entry ->
            Page(this) {
                ListScreen(
                    onOpenTask = { nav.navigate(Routes.task(it)) },
                    onClosed = { nav.closeIfOn(entry) },
                    onShade = { id -> nav.navigate(Routes.listShade(id)) },
                    onInfo = { id -> nav.navigate(Routes.listSharing(id)) },
                    onTemplates = TemplateLinks(
                        openListTemplate = { nav.navigate(Routes.listTemplate(it)) },
                        openTemplateTask = { nav.navigate(Routes.templateTask(it)) }
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
                        editTask = { nav.navigate(Routes.templateTask(it)) }
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
                        closed = { nav.closeIfOn(entry) }
                    )
                )
            }
        }
        composable(Routes.USE_LIST_TEMPLATE) { entry ->
            Page(this) {
                UseListTemplateScreen(
                    onCreated = { listId ->
                        // The new list replaces this form, so back goes to where the template was picked.
                        if (nav.currentBackStackEntry?.id == entry.id) {
                            nav.popBackStack()
                            nav.navigate(Routes.list(listId))
                        }
                    },
                    onCancel = { nav.closeIfOn(entry) }
                )
            }
        }
        composable(Routes.TEMPLATE_TASK) { entry ->
            Page(this) { TemplateTaskScreen(onClosed = { nav.closeIfOn(entry) }) }
        }
        composable(Routes.LIST_SHADE) {
            Page(this) { ListShadeScreen() }
        }
        composable(Routes.LIST_SHARING) { entry ->
            Page(this) {
                SharingScreen(
                    onClosed = { nav.closeIfOn(entry) },
                    onShade = { id -> nav.navigate(Routes.listShade(id)) }
                )
            }
        }
        composable(Routes.SETTINGS) {
            Page(this) {
                SettingsScreen(
                    onOpenSyncAccount = { nav.navigate(Routes.ACCOUNT) },
                    onOpenSyncLog = { nav.navigate(Routes.SYNC_LOG) }
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
        composable(Routes.TASK) { entry ->
            Page(this) { TaskDetailScreen(onClosed = { nav.closeIfOn(entry) }, animatedScope = this) }
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

/** Closes [entry]'s page once, even if its content asks twice. */
private fun NavHostController.closeIfOn(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
