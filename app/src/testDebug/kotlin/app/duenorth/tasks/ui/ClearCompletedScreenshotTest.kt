package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.settings.testListShades
import app.duenorth.tasks.settings.testPinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "clear completed" in a list's ••• menu: the menu, its dialog, and the list after, light and dark. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ClearCompletedScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var viewModel: ListViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        val features = ServiceFeatures(accounts) {
            FakeProvider(ProviderCapabilities(importance = true, manualOrder = false, dueTime = false))
        }
        val home = runBlocking {
            DemoSeeder(accounts, tasks, clock).connect()
            db.accountDao().upsert(
                AccountEntity(provider = ProviderKind.MICROSOFT, displayName = "Dustin", email = "demo@outlook.com")
            )
            val home = tasks.listSummaries().first().first { it.title == "Home" }.localId
            listOf("Fix the squeaky door", "Change the furnace filter").forEach {
                tasks.setCompleted(tasks.createTask(home, it), true)
            }
            home
        }
        viewModel = ListViewModel(
            SavedStateHandle(mapOf("id" to home)),
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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun clearCompletedLight() = clearCompleted(dark = false)

    @Test
    fun clearCompletedDark() = clearCompleted(dark = true)

    @Test
    fun aListWithNothingCompletedDoesNotOfferIt() {
        show(dark = false)
        compose.onNodeWithText("completed (3) ⌄").performClick()
        compose.onNodeWithContentDescription("more").performClick()
        compose.onNodeWithText("clear completed").performClick()
        compose.onNodeWithText("clear").performClick()
        compose.waitUntilDoesNotExist(hasText("Water the plants"), TIMEOUT)

        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("list shade"), TIMEOUT)
        compose.onNodeWithText("clear completed").assertDoesNotExist()
    }

    private fun clearCompleted(dark: Boolean) {
        show(dark)
        compose.onNodeWithText("completed (3) ⌄").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Water the plants"), TIMEOUT)
        snap("list_before", dark)

        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("clear completed"), TIMEOUT)
        snap("menu", dark)

        compose.onNodeWithText("clear completed").performClick()
        compose.waitUntilAtLeastOneExists(hasText("clear completed?"), TIMEOUT)
        snap("dialog", dark)

        compose.onNodeWithText("clear").performClick()
        compose.waitUntilDoesNotExist(hasText("Water the plants"), TIMEOUT)
        compose.waitUntilDoesNotExist(hasContentDescription("less"), TIMEOUT)
        snap("list_after", dark)
        val left = runBlocking { tasks.completedTasks(viewModel.listId).first() }
        assertEquals(0, left.size)
    }

    private fun show(dark: Boolean) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                val state by viewModel.state.collectAsState()
                ListContent(state, viewModel, onOpenTask = {})
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Replace the hallway bulb"), TIMEOUT)
    }

    private fun snap(name: String, dark: Boolean) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage(
            "build/outputs/roborazzi/clear-completed/${name}_${if (dark) "dark" else "light"}.png"
        )
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
