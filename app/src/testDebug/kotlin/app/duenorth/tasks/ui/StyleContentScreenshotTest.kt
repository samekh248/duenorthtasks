package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroStyle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.settings.testListShades
import app.duenorth.tasks.settings.testPinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.detail.TaskDetailContent
import app.duenorth.tasks.ui.detail.TaskDetailViewModel
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.list.ListContent
import app.duenorth.tasks.ui.list.ListViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens' own content in each style, light and dark (spec 007): today, a list page with its completed tasks,
 * a task row's menu, the clear-completed dialog and a task's page.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StyleContentScreenshotTest(private val style: MetroStyle, private val dark: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var features: ServiceFeatures

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        features = ServiceFeatures(accounts) { FakeProvider() }
        runBlocking {
            DemoSeeder(accounts, tasks, clock).connect()
            val home = homeListId()
            listOf("Fix the squeaky door", "Change the furnace filter").forEach {
                tasks.setCompleted(tasks.createTask(home, it), true)
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun today() {
        val viewModel = HomeViewModel(
            tasks,
            TemplateRepository(db, tasks, clock),
            accounts,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            testPinnedLists(tasks, accounts),
            clock
        )
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntilAtLeastOneExists(hasText("Pick up dry cleaning"), TIMEOUT)
        snap("today")
    }

    @Test
    fun list() {
        showList()
        snap("list")
    }

    @Test
    fun menu() {
        showList()
        compose.onNodeWithText("Replace the hallway bulb").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("move to", ignoreCase = true), TIMEOUT)
        snap("menu")
    }

    @Test
    fun dialog() {
        showList()
        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("clear completed", ignoreCase = true), TIMEOUT)
        compose.onNodeWithText("clear completed", ignoreCase = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("clear completed?", ignoreCase = true), TIMEOUT)
        snap("dialog")
    }

    @Test
    fun detail() {
        val id = runBlocking { tasks.tasksDueBy(clock.instant().atZone(clock.zone).toLocalDate().plusDays(1)).first() }
            .first { it.task.title == "Call the vet" }.task.localId
        val viewModel = TaskDetailViewModel(
            SavedStateHandle(mapOf("id" to id)),
            tasks,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            accounts,
            clock
        )
        show {
            val state by viewModel.state.collectAsState()
            TaskDetailContent(state, viewModel, animatedScope = null)
        }
        compose.waitUntilAtLeastOneExists(hasText("Check Thursday afternoon"), TIMEOUT)
        snap("detail")
    }

    /** The Home list with its completed tasks open. */
    private fun showList() {
        val viewModel = ListViewModel(
            SavedStateHandle(mapOf("id" to runBlocking { homeListId() })),
            tasks,
            TemplateRepository(db, tasks, clock),
            testListShades(tasks, accounts),
            accounts,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            testPinnedLists(tasks, accounts),
            clock
        )
        viewModel.toggleCompletedGroup()
        show {
            val state by viewModel.state.collectAsState()
            ListContent(state, viewModel, onOpenTask = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("Change the furnace filter"), TIMEOUT)
    }

    private suspend fun homeListId(): String = tasks.listSummaries().first().first { it.title == "Home" }.localId

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = dark, style = style) { content() } }
    }

    private fun snap(screen: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage(
            "build/outputs/roborazzi/style-content/${style.name.lowercase()}_${screen}_${if (dark) "dark" else "light"}.png"
        )
    }

    companion object {
        const val TIMEOUT = 5_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun styles(): List<Array<Any>> = MetroStyle.entries.flatMap { style ->
            listOf(false, true).map { arrayOf(style, it) }
        }
    }
}
