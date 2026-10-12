package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.PinnedLists
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.settings.testListShades
import app.duenorth.tasks.settings.testPinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.list.ListContent
import app.duenorth.tasks.ui.list.ListViewModel
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

/**
 * Every "add a task" box empties once its task is added, even when the next title is typed and
 * added before the screen draws another frame: today, a pinned list's section and the list page.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddTaskClearsTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var pins: PinnedLists
    private lateinit var listId: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        pins = testPinnedLists(tasks, accounts)
        runBlocking {
            accounts.connect(ProviderKind.FAKE, "demo", null)
            tasks.createList("Tasks", isDefault = true)
            listId = tasks.createList("Groceries")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun todayBoxClearsBetweenQuickAdds() {
        showHome()
        addQuickly(field(hasTestTag("today")))
    }

    @Test
    fun pinnedListBoxClearsBetweenQuickAdds() {
        runBlocking { pins.pin(listId) }
        showHome()
        compose.waitUntilAtLeastOneExists(hasTestTag("pinned:Groceries"), TIMEOUT)
        compose.onNode(hasTestTag("today")).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        addQuickly(field(hasTestTag("pinned:Groceries")))
    }

    @Test
    fun listPageBoxClearsBetweenQuickAdds() {
        val list = ListViewModel(
            SavedStateHandle(mapOf("id" to listId)),
            tasks,
            TemplateRepository(db, tasks, clock),
            testListShades(tasks, accounts),
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            ListHolds(),
            testListOrder(tasks, accounts),
            pins,
            clock
        )
        show {
            val state by list.state.collectAsState()
            ListContent(state, list, onOpenTask = {})
        }
        addQuickly(field(hasTestTag("list")))
    }

    /**
     * Types and adds three titles with no frame drawn in between, the way a fast typist or a
     * keyboard's late keystroke does: each lands in an empty box and is added on its own.
     */
    private fun addQuickly(box: SemanticsNodeInteraction) {
        compose.waitUntilAtLeastOneExists(isAddBox, TIMEOUT)
        box.performTextInput("Milk")
        compose.mainClock.autoAdvance = false
        box.performImeAction()
        box.performTextInput("Eggs")
        box.performImeAction()
        box.performTextInput("Bread")
        box.performImeAction()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        box.assert(hasEditableText(""))
        compose.waitUntil(TIMEOUT) { storedTitles().size == 3 }
        assertEquals(listOf("Bread", "Eggs", "Milk"), storedTitles())
    }

    private fun field(pane: SemanticsMatcher) = compose.onNode(isAddBox and hasAnyAncestor(pane))

    private fun showHome() {
        val viewModel = HomeViewModel(
            tasks,
            TemplateRepository(db, tasks, clock),
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            ListHolds(),
            testListOrder(tasks, accounts),
            pins,
            clock
        )
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntil(TIMEOUT) { viewModel.state.value.lists.isNotEmpty() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = false) { content() } }
    }

    private fun storedTitles(): List<String> =
        db.openHelper.readableDatabase.query("SELECT title FROM task ORDER BY title").use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }

    private companion object {
        const val TIMEOUT = 5_000L

        /** The pane's only text box is its "add a task" box. */
        val isAddBox = hasSetTextAction()

        fun hasEditableText(text: String) =
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
    }
}
