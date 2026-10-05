package app.duenorth.tasks.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import app.duenorth.tasks.ui.search.SearchScreen
import app.duenorth.tasks.ui.settings.SettingsScreen
import app.duenorth.tasks.ui.shade.ListShadeScreen

object Routes {
    const val HOME = "home"
    const val LIST = "list/{id}"
    const val TASK = "task/{id}"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val LIST_SHADE = "list/{id}/shade"

    fun list(id: String) = "list/$id"

    fun listShade(id: String) = "list/$id/shade"

    fun task(id: String) = "task/$id"
}

/** App-wide actions the pages need; signing out and switching outlive the page that asked. */
class AppActions(val syncNow: () -> Unit, val switchTo: (ProviderKind) -> Unit, val signOut: () -> Unit)

/**
 * Every page after the account gate (T026). Pages swing with the turnstile: the leaving page turns
 * away first, then the new one swings in, so the NavHost keeps the old page on screen until then.
 */
@Composable
fun DueNorthNavHost(app: AppActions, syncing: Boolean, nav: NavHostController = rememberNavController()) {
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
                menuItems = if (DebugRoutes.GALLERY_ENABLED) {
                    listOf(AppBarMenuItem("component gallery") { nav.navigate(Routes.GALLERY) })
                } else {
                    emptyList()
                }
            )
            Page(this) { HomeScreen(actions, syncing) }
        }
        composable(Routes.LIST) { entry ->
            Page(this) {
                ListScreen(
                    onOpenTask = { nav.navigate(Routes.task(it)) },
                    onClosed = { nav.closeIfOn(entry) },
                    onShade = { id -> nav.navigate(Routes.listShade(id)) }
                )
            }
        }
        composable(Routes.LIST_SHADE) {
            Page(this) { ListShadeScreen() }
        }
        composable(Routes.SETTINGS) {
            Page(this) { SettingsScreen(onOpenSyncAccount = { nav.navigate(Routes.ACCOUNT) }) }
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
