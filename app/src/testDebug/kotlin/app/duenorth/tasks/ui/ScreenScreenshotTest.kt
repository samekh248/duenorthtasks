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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.account.AccountScreen
import app.duenorth.tasks.ui.account.SyncAccountActions
import app.duenorth.tasks.ui.account.SyncAccountContent
import app.duenorth.tasks.ui.account.SyncAccountUiState
import app.duenorth.tasks.ui.detail.TaskDetailContent
import app.duenorth.tasks.ui.detail.TaskDetailViewModel
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.list.ListContent
import app.duenorth.tasks.ui.list.ListViewModel
import app.duenorth.tasks.ui.search.SearchScreen
import app.duenorth.tasks.ui.search.SearchViewModel
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

/** Pictures of every M2 screen on the demo account, light and dark, for review (CI uploads them). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenScreenshotTest {
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
    fun homeTodayLight() = home(dark = false)

    @Test
    fun homeTodayDark() = home(dark = true)

    @Test
    fun homeLists() {
        home(dark = false, name = null)
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        compose.waitUntilAtLeastOneExists(hasText("next: Call the vet"), TIMEOUT)
        compose.mainClock.advanceTimeBy(2_000)
        snap("home_lists", dark = false)
    }

    @Test
    fun listLight() = list(dark = false)

    @Test
    fun listDark() = list(dark = true)

    @Test
    fun taskDetailLight() = detail(dark = false)

    @Test
    fun taskDetailDark() = detail(dark = true)

    @Test
    fun search() {
        val viewModel = SearchViewModel(tasks, clock)
        show(dark = false) { SearchScreen(onOpenTask = {}, viewModel = viewModel) }
        compose.onNode(hasTestTag("search field")).performTextInput("the")
        compose.waitUntilAtLeastOneExists(hasText("Water the plants"), TIMEOUT)
        snap("search", dark = false)
    }

    @Test
    fun account() {
        show(dark = false) {
            AccountScreen(demoAvailable = true, connecting = null, error = null, isConfigured = { true }, onPick = {})
        }
        snap("account", dark = false)
    }

    @Test
    fun syncAccountLight() = syncAccount(dark = false)

    @Test
    fun syncAccountDark() = syncAccount(dark = true)

    private fun syncAccount(dark: Boolean) {
        val state = SyncAccountUiState(
            loading = false,
            provider = ProviderKind.GOOGLE,
            signedInAs = "you@example.com",
            pendingChanges = 3
        )
        show(dark) {
            SyncAccountContent(
                state = state,
                demoAvailable = true,
                isConfigured = { it != ProviderKind.MICROSOFT },
                onInterval = {},
                onWifiOnly = {},
                onSyncNow = {},
                actions = SyncAccountActions(switchTo = {}, signOut = {})
            )
        }
        snap("sync_account", dark)
    }

    private fun home(dark: Boolean, name: String? = "home_today") {
        val viewModel = HomeViewModel(tasks, accounts, clock)
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show(dark) {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntilAtLeastOneExists(hasText("Pay water bill"), TIMEOUT)
        if (name != null) snap(name, dark)
    }

    private fun list(dark: Boolean) {
        val id = runBlocking { tasks.listSummaries().first().first { it.title == "Errands" }.localId }
        val viewModel = ListViewModel(SavedStateHandle(mapOf("id" to id)), tasks, accounts, clock)
        viewModel.toggleCompletedGroup()
        show(dark) {
            val state by viewModel.state.collectAsState()
            ListContent(state, viewModel, onOpenTask = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("Mail the birthday card"), TIMEOUT)
        snap("list", dark)
    }

    private fun detail(dark: Boolean) {
        val id = runBlocking { tasks.tasksDueBy(clock.instant().atZone(clock.zone).toLocalDate().plusDays(1)).first() }
            .first { it.task.title == "Call the vet" }.task.localId
        val viewModel = TaskDetailViewModel(SavedStateHandle(mapOf("id" to id)), tasks, clock)
        show(dark) {
            val state by viewModel.state.collectAsState()
            TaskDetailContent(state, viewModel, animatedScope = null)
        }
        compose.waitUntilAtLeastOneExists(hasText("Check Thursday afternoon"), TIMEOUT)
        snap("task_detail", dark)
    }

    private fun show(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = dark) { content() } }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/screens/${name}_${if (dark) "dark" else "light"}.png"
        )
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
