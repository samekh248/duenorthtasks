package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.ThemeMode
import app.duenorth.tasks.settings.ThemeSettings
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.list.ListContent
import app.duenorth.tasks.ui.list.ListViewModel
import app.duenorth.tasks.ui.settings.SettingsContent
import app.duenorth.tasks.ui.shade.ListShadeContent
import app.duenorth.tasks.ui.shade.ListShadeUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** T061: the theme settings in three accents, list shades, light and dark (CI uploads them). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        runBlocking { DemoSeeder(accounts, tasks, clock).connect() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun settingsMagentaLight() = settings(Accent.Magenta, dark = false)

    @Test
    fun settingsMagentaDark() = settings(Accent.Magenta, dark = true)

    @Test
    fun settingsCobaltLight() = settings(Accent.Cobalt, dark = false)

    @Test
    fun settingsCobaltDark() = settings(Accent.Cobalt, dark = true)

    @Test
    fun settingsCoralLight() = settings(Accent.Coral, dark = false)

    @Test
    fun settingsCoralDark() = settings(Accent.Coral, dark = true)

    @Test
    fun listShadeLight() = listShade(dark = false)

    @Test
    fun listShadeDark() = listShade(dark = true)

    @Test
    fun shadedListsLight() = shadedLists(Accent.Magenta, dark = false)

    @Test
    fun shadedListsDark() = shadedLists(Accent.Magenta, dark = true)

    @Test
    fun shadedListsCoralLight() = shadedLists(Accent.Coral, dark = false)

    @Test
    fun shadedListPageLight() = shadedListPage(dark = false)

    @Test
    fun shadedListPageDark() = shadedListPage(dark = true)

    private fun settings(accent: Accent, dark: Boolean) {
        show(dark, accent) {
            SettingsContent(ThemeSettings(ThemeMode.SYSTEM, accent), onMode = {}, onAccent = {})
        }
        snap("settings_theme_${accent.name.lowercase()}", dark)
    }

    private fun listShade(dark: Boolean) {
        val state = ListShadeUiState(loading = false, listId = "errands", title = "Errands", openCount = 3, step = -2)
        show(dark, Accent.Magenta) { ListShadeContent(state, onPick = {}) }
        snap("list_shade", dark)
    }

    /** The lists section with Errands two shades lighter and Work two darker. */
    private fun shadedLists(accent: Accent, dark: Boolean) {
        val viewModel = HomeViewModel(tasks, accounts, ServiceFeatures(accounts) { FakeProvider() }, ListHolds(), clock)
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show(dark, accent, demoShades()) {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntilAtLeastOneExists(hasText("Pay water bill"), TIMEOUT)
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        compose.waitUntilAtLeastOneExists(hasText("next: Call the vet"), TIMEOUT)
        compose.mainClock.advanceTimeBy(2_000)
        snap("lists_shaded_${accent.name.lowercase()}", dark)
    }

    private fun shadedListPage(dark: Boolean) {
        val shades = demoShades()
        val id = runBlocking { tasks.listSummaries().first().first { it.title == "Errands" }.localId }
        val viewModel = ListViewModel(
            SavedStateHandle(mapOf("id" to id)),
            tasks,
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            ListHolds(),
            clock
        )
        viewModel.toggleCompletedGroup()
        show(dark, Accent.Magenta, shades) {
            val state by viewModel.state.collectAsState()
            ListContent(state, viewModel, onOpenTask = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("Mail the birthday card"), TIMEOUT)
        snap("list_page_shaded", dark)
    }

    private fun demoShades(): Map<String, Int> = runBlocking {
        val lists = tasks.listSummaries().first()
        buildMap {
            lists.firstOrNull { it.title == "Errands" }?.let { put(it.localId, -2) }
            lists.firstOrNull { it.title == "Work" }?.let { put(it.localId, 2) }
        }
    }

    private fun show(
        dark: Boolean,
        accent: Accent,
        shades: Map<String, Int> = emptyMap(),
        content: @Composable () -> Unit
    ) {
        compose.setContent { MetroTheme(darkTheme = dark, accent = accent, listShades = shades) { content() } }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/theme/${name}_${if (dark) "dark" else "light"}.png"
        )
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
