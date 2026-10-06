package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.AssignmentSource
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
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.detail.TaskDetailContent
import app.duenorth.tasks.ui.detail.TaskDetailViewModel
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.list.ListContent
import app.duenorth.tasks.ui.list.ListViewModel
import app.duenorth.tasks.ui.sharing.SharingContent
import app.duenorth.tasks.ui.sharing.SharingViewModel
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

/**
 * Spec 002 screens on the demo data, light and dark: "Home" shared by you, "Work" shared with you,
 * and one task assigned from a Google Doc. Matches specs/002-shared-lists/mockups.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SharedListsScreenshotTest {
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
        features = ServiceFeatures(accounts) {
            FakeProvider(
                ProviderCapabilities(importance = true, manualOrder = false, dueTime = false, sharedLists = true)
            )
        }
        runBlocking {
            DemoSeeder(accounts, tasks, clock).connect()
            // As if signed in to Microsoft To Do, where lists can be shared.
            db.accountDao().upsert(
                AccountEntity(provider = ProviderKind.MICROSOFT, displayName = "Dustin", email = "demo@outlook.com")
            )
            share("Home", isOwner = true)
            share("Work", isOwner = false)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun homeListsLight() = homeLists(dark = false)

    @Test
    fun homeListsDark() = homeLists(dark = true)

    @Test
    fun listSharedWithYouLight() = list("Work", dark = false)

    @Test
    fun listSharedWithYouDark() = list("Work", dark = true)

    @Test
    fun sharingWithYouLight() = sharing("Work", "sharing_with_you", dark = false)

    @Test
    fun sharingWithYouDark() = sharing("Work", "sharing_with_you", dark = true)

    @Test
    fun sharingByYou() = sharing("Home", "sharing_by_you", dark = false)

    @Test
    fun sharingPrivate() = sharing("Errands", "sharing_private", dark = false)

    @Test
    fun assignedTaskLight() = assigned(dark = false)

    @Test
    fun assignedTaskDark() = assigned(dark = true)

    @Test
    fun aListSharedWithYouOffersNoRenameOrDelete() {
        homeLists(dark = false, name = null)
        compose.onNodeWithText("Work").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("list info"), TIMEOUT)
        compose.onNodeWithText("rename").assertDoesNotExist()
        compose.onNodeWithText("delete").assertDoesNotExist()
    }

    @Test
    fun aListYouShareStillOffersRenameAndDelete() {
        homeLists(dark = false, name = null)
        compose.onNodeWithText("Home").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("rename"), TIMEOUT)
        compose.waitUntilAtLeastOneExists(hasText("delete"), TIMEOUT)
    }

    private suspend fun share(title: String, isOwner: Boolean) {
        val list = db.syncDao().allLists().single { it.title == title }
        db.taskListDao().update(list.copy(isShared = true, isOwner = isOwner))
    }

    private fun listId(title: String) = runBlocking {
        tasks.listSummaries().first().first { it.title == title }.localId
    }

    private fun homeLists(dark: Boolean, name: String? = "home_lists_shared") {
        val viewModel =
            HomeViewModel(
                tasks,
                TemplateRepository(db, tasks, clock),
                accounts,
                features,
                ListHolds(),
                testListOrder(tasks, accounts),
                clock
            )
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show(dark) {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntilAtLeastOneExists(hasText("Pay water bill"), TIMEOUT)
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        compose.waitUntilAtLeastOneExists(hasText("shared with you · next: Draft the quarterly update"), TIMEOUT)
        compose.mainClock.advanceTimeBy(2_000)
        if (name != null) snap(name, dark)
    }

    private fun list(title: String, dark: Boolean) {
        val viewModel = ListViewModel(
            SavedStateHandle(mapOf("id" to listId(title))),
            tasks, TemplateRepository(db, tasks, clock), testListShades(tasks, accounts),
            accounts,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            clock
        )
        show(dark) {
            val state by viewModel.state.collectAsState()
            ListContent(state, viewModel, onOpenTask = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("shared with you · details"), TIMEOUT)
        snap("list_shared_with_you", dark)
    }

    private fun sharing(title: String, name: String, dark: Boolean) {
        val viewModel = SharingViewModel(SavedStateHandle(mapOf("id" to listId(title))), tasks, accounts, features)
        show(dark) {
            val state by viewModel.state.collectAsState()
            SharingContent(state, onShade = {}, onHandOff = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("what you can do here"), TIMEOUT)
        snap(name, dark)
    }

    private fun assigned(dark: Boolean) {
        val task = runBlocking {
            db.syncDao().allLists().flatMap { db.syncDao().tasksInList(it.localId) }
                .single { it.title == "Book travel for the offsite" }
        }
        runBlocking {
            db.taskDao().update(
                task.copy(
                    assignmentSource = AssignmentSource.DOCUMENT,
                    assignmentLink = "https://docs.google.com/document/d/offsite"
                )
            )
        }
        val viewModel = TaskDetailViewModel(
            SavedStateHandle(mapOf("id" to task.localId)),
            tasks,
            features,
            ListHolds(),
            testListOrder(tasks, accounts),
            accounts,
            clock
        )
        show(dark) {
            val state by viewModel.state.collectAsState()
            TaskDetailContent(state, viewModel, animatedScope = null)
        }
        compose.waitUntilAtLeastOneExists(hasText("open in google docs"), TIMEOUT)
        snap("task_assigned", dark)
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
