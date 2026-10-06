package app.duenorth.tasks.nav

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** spec 004 FR-325: a list made from a template opens in place of the templates pages. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TemplateNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backFromAListMadeFromATemplateGoesToHomeLists() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                listOf(Routes.HOME, Routes.TEMPLATES, Routes.LIST_TEMPLATE, Routes.USE_LIST_TEMPLATE, Routes.LIST)
                    .forEach { route -> composable(route) { BasicText(route) } }
            }
        }
        compose.runOnIdle {
            nav.navigate(Routes.TEMPLATES)
            nav.navigate(Routes.listTemplate("trip"))
            nav.navigate(Routes.useListTemplate("trip"))
        }

        compose.runOnIdle { nav.openCreatedList("denver") }

        compose.runOnIdle {
            assertEquals(Routes.LIST, nav.currentBackStackEntry?.destination?.route)
            assertEquals("denver", nav.currentBackStackEntry?.arguments?.getString("id"))
            assertEquals(Routes.HOME, nav.previousBackStackEntry?.destination?.route)
            assertEquals(true, nav.getBackStackEntry(Routes.HOME).savedStateHandle.get<Boolean>(SHOW_LISTS))
        }
    }
}
