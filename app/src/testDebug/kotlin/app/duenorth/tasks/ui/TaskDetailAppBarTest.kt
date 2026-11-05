package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.detail.TaskDetailContent
import app.duenorth.tasks.ui.detail.TaskDetailViewModel
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

/** The task page's app bar: complete and save as buttons, delete and the rest behind the ellipsis. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskDetailAppBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var taskId: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        val features = ServiceFeatures(accounts) { FakeProvider() }
        runBlocking { accounts.connect(ProviderKind.FAKE, "demo", null) }
        taskId = runBlocking { tasks.createTask(tasks.defaultListIdOrCreate(), "Pack") }
        val viewModel = TaskDetailViewModel(
            SavedStateHandle(mapOf("id" to taskId)),
            tasks,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            accounts,
            clock
        )
        compose.setContent {
            val state by viewModel.state.collectAsState()
            MetroTheme(darkTheme = false) { TaskDetailContent(state, viewModel, animatedScope = null) }
        }
        compose.waitUntilAtLeastOneExists(hasText("Pack"), TIMEOUT)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun editTurnsIntoSaveWhileTypingAndSaveWritesTheEdit() {
        compose.onNode(hasContentDescription("save")).assertDoesNotExist()
        compose.onNode(hasContentDescription("edit")).performClick()
        compose.onNode(hasContentDescription("edit")).assertDoesNotExist()
        compose.onNode(hasContentDescription("save")).assertIsEnabled()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Pack the car")
        compose.onNode(hasContentDescription("save")).performClick()
        compose.waitUntil(TIMEOUT) { stored()?.title == "Pack the car" }
        compose.waitUntilAtLeastOneExists(hasText("Pack the car"), TIMEOUT)
        compose.onNode(hasContentDescription("save")).assertDoesNotExist()
        compose.onNode(hasContentDescription("edit")).assertIsEnabled()
    }

    @Test
    fun saveIsOffWhileTheTitleIsBlank() {
        compose.onNode(hasText("Pack")).performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("")
        compose.onNode(hasContentDescription("save")).assertIsNotEnabled()
    }

    @Test
    fun completeKeepsAnEditInProgress() {
        compose.onNode(hasText("Pack")).performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Pack light")
        compose.onNode(hasContentDescription("complete")).performClick()
        compose.waitUntil(TIMEOUT) { stored()?.let { it.completed && it.title == "Pack light" } == true }
        compose.waitUntilAtLeastOneExists(hasContentDescription("reopen"), TIMEOUT)
    }

    @Test
    fun deleteLivesInTheOverflowMenu() {
        compose.onNode(hasContentDescription("more")).performClick()
        compose.onNode(hasText("delete")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("delete task?"), TIMEOUT)
    }

    private fun stored(): TaskEntity? = runBlocking { tasks.task(taskId).first() }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
