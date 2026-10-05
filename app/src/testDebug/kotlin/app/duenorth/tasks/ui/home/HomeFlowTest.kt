package app.duenorth.tasks.ui.home

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

/** T025: swipe the panorama, add, complete, edit and delete a task, against a real (in-memory) Room. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var viewModel: HomeViewModel
    private val opened = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
        tasks = TaskRepository(db, clock)
        val accounts = AccountRepository(db)
        runBlocking { accounts.connect(ProviderKind.FAKE, "demo", null) }
        viewModel = HomeViewModel(tasks, accounts, clock)
        val actions = HomeActions(openTask = { opened += it }, openList = {}, search = {}, openSyncAccount = {})
        compose.setContent {
            val state by viewModel.state.collectAsState()
            MetroTheme(darkTheme = false) { HomeContent(state, viewModel, actions) }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addCompleteEditDeleteAndSwipe() {
        waitFor(hasText("nothing due today"))

        add("Return library books")
        add("Call the vet")
        waitFor(hasText("Call the vet") and hasAnyAncestor(hasTestTag("today")))

        // Complete: the first check box ticks, and the task leaves today for done.
        compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("today"))).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { runBlocking { tasks.recentlyCompleted().first().size == 1 } }
        val done = runBlocking { tasks.recentlyCompleted().first().single().task }

        // Swipe to "done" and find it there.
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNode(hasTestTag("lists")).performTouchInput { swipeLeft() }
        waitFor(hasText(done.title) and hasAnyAncestor(hasTestTag("done")))

        // Edit and delete from the long-press menu on the task still open.
        val open = runBlocking { tasks.tasksDueBy(LocalDate.of(2026, 10, 6)).first().single().task }
        compose.onNode(hasTestTag("done")).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNode(hasTestTag("lists")).performTouchInput { swipeRight() }
        compose.waitForIdle()
        longPress(open.title)
        compose.onNode(hasText("edit")).performClick()
        assertEquals(listOf(open.localId), opened)

        longPress(open.title)
        compose.onNode(hasText("delete")).performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(open.title) and hasAnyAncestor(hasTestTag("today")))
                .fetchSemanticsNodes().isEmpty()
        }
        waitFor(hasText("nothing due today"))
    }

    private fun add(title: String) {
        val field = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("today"))).onFirst()
        field.performTextInput(title)
        field.performImeAction()
        waitFor(hasText(title) and hasAnyAncestor(hasTestTag("today")) and !hasSetTextAction())
    }

    private fun longPress(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("today")) and !hasSetTextAction())
            .performTouchInput { longClick() }
        compose.waitForIdle()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
