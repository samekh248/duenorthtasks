package app.duenorth.tasks.ui.home

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.unit.DpSize
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.settings.testPinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.common.TickLinger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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
    private val holds = ListHolds()
    private val opened = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
        tasks = TaskRepository(db, clock)
        val accounts = AccountRepository(db)
        runBlocking { accounts.connect(ProviderKind.FAKE, "demo", null) }
        viewModel = HomeViewModel(
            tasks,
            TemplateRepository(db, tasks, clock),
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            holds,
            testListOrder(tasks, accounts),
            testPinnedLists(tasks, accounts),
            clock
        )
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

        // Complete: the first check box ticks, and the task leaves today. Done tasks have no
        // section of their own; they stay in their list.
        compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("today"))).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { runBlocking { tasks.recentlyCompleted().first().size == 1 } }
        val done = runBlocking { tasks.recentlyCompleted().first().single().task }
        compose.waitUntil(TIMEOUT) {
            ShadowLooper.idleMainLooper(TickLinger.LINGER_MS, TimeUnit.MILLISECONDS)
            compose.onAllNodes(hasText(done.title) and hasAnyAncestor(hasTestTag("today")))
                .fetchSemanticsNodes().isEmpty()
        }

        // Swipe to "lists" and back.
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        waitFor(hasTestTag("lists"))
        compose.onNode(hasTestTag("lists")).performTouchInput { swipeRight() }
        compose.waitForIdle()

        // Edit and delete from the long-press menu on the task still open.
        val open = runBlocking { tasks.tasksDueBy(LocalDate.of(2026, 10, 6)).first().single().task }
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

    @Test
    fun syncedChangesWaitForTheFingerToLift() {
        add("Return library books")
        val task = runBlocking { tasks.tasksDueBy(LocalDate.of(2026, 10, 5)).first().single().task }

        // A finger rests on today: sync is asked to wait for that list...
        compose.onNode(hasTestTag("today")).performTouchInput { down(center) }
        compose.waitForIdle()
        assertTrue(stillHeld(task.listId))

        // ...and a change that lands anyway (a batch already in flight) doesn't redraw the rows.
        runBlocking { tasks.editTask(task.localId, TaskEdit(title = "Return the library books")) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNode(hasText("Return library books") and hasAnyAncestor(hasTestTag("today"))).assertExists()

        // Lifting the finger releases the list and shows the change.
        compose.onNode(hasTestTag("today")).performTouchInput { up() }
        waitFor(hasText("Return the library books") and hasAnyAncestor(hasTestTag("today")))
        assertFalse(stillHeld(task.listId))
    }

    @Test
    fun importantTasksShowTheStar() {
        add("Call the vet")
        val task = runBlocking { tasks.tasksDueBy(LocalDate.of(2026, 10, 5)).first().single().task }
        assertTrue(compose.onAllNodes(hasContentDescription("important")).fetchSemanticsNodes().isEmpty())

        longPress("Call the vet")
        compose.onNode(hasText("mark important")).performClick()
        waitFor(hasContentDescription("important"))
        assertTrue(runBlocking { tasks.task(task.localId).first()!!.important })
    }

    /** T064: every tap target on home says what it is to TalkBack and is at least 48dp square. */
    @Test
    fun everyTapTargetIsLabelledAndAtLeast48dp() {
        add("Call the vet")
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        waitFor(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 open"))
        // The check box names its task; the list row reads its count after its name.
        compose.onNode(isToggleable() and hasContentDescription("Call the vet")).assertExists()

        val targets = compose.onAllNodes(hasClickAction() or isToggleable()).fetchSemanticsNodes()
        assertTrue(targets.size > 5)
        for (node in targets) {
            val config = node.config
            val label = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)
            assertTrue("unlabelled tap target ${node.id}", label.any { it.isNotBlank() })
            val size = with(node.layoutInfo.density) { DpSize(node.size.width.toDp(), node.size.height.toDp()) }
            assertTrue(
                "$label is $size",
                size.width >= MetroDimens.TouchTarget && size.height >= MetroDimens.TouchTarget
            )
        }
    }

    /** True while sync would still be waiting on [listId]. */
    private fun stillHeld(listId: String): Boolean =
        runBlocking { withTimeoutOrNull(HOLD_PROBE_MS) { holds.awaitReleased(listId) } == null }

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
        const val HOLD_PROBE_MS = 100L
    }
}
