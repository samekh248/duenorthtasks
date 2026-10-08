package app.duenorth.tasks.ui.home

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A task ticked on today before the screen has read its add back from the database: the tick
 * shows, and the task leaves today like any other instead of sticking there unticked.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeAddThenTickTest {
    @get:Rule
    val compose = createComposeRule()

    /** Room's reads: closing it keeps the screen from seeing writes until it opens again. */
    private val reads = GatedExecutor()

    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var viewModel: HomeViewModel
    private lateinit var listId: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(reads)
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
        tasks = TaskRepository(db, clock)
        val accounts = AccountRepository(db)
        runBlocking {
            accounts.connect(ProviderKind.FAKE, "demo", null)
            listId = tasks.createList("Tasks", isDefault = true)
        }
        viewModel = HomeViewModel(
            tasks,
            TemplateRepository(db, tasks, clock),
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            ListHolds(),
            testListOrder(tasks, accounts),
            clock
        )
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        compose.setContent {
            val state by viewModel.state.collectAsState()
            MetroTheme(darkTheme = false) { HomeContent(state, viewModel, actions) }
        }
    }

    @After
    fun tearDown() {
        reads.open()
        db.close()
    }

    @Test
    fun aTaskTickedBeforeItsAddIsReadBackLeavesToday() {
        compose.waitUntil(TIMEOUT) { viewModel.state.value.lists.isNotEmpty() }

        // The add and the tick both land before the screen reads either of them back.
        reads.close()
        compose.runOnIdle { viewModel.addTask("Call the vet", listId = listId) }
        val box = isToggleable() and hasContentDescription("Call the vet") and hasAnyAncestor(hasTestTag("today"))
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(box).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(box).performClick()
        compose.waitUntil(TIMEOUT) { storedCompleted("Call the vet") }

        // The tap shows at once, though the stored row hasn't been read yet.
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(box and isOn).fetchSemanticsNodes().isNotEmpty() }

        // Reads catch up: the task is done, and today never shows it unticked again.
        reads.open()
        compose.waitUntil(TIMEOUT) { viewModel.state.value.done.any { it.title == "Call the vet" } }
        compose.waitForIdle()
        assertTrue(
            "today still shows the done task unticked",
            viewModel.state.value.dueToday.none { it.title == "Call the vet" && !it.completed }
        )
        assertTrue(compose.onAllNodes(box and !isOn).fetchSemanticsNodes().isEmpty())
    }

    /** Reads the stored row on this thread, around the gated executor. */
    private fun storedCompleted(title: String): Boolean =
        db.openHelper.readableDatabase.query("SELECT completed FROM task WHERE title = ?", arrayOf(title)).use {
            it.moveToFirst() && it.getInt(0) == 1
        }

    /** Runs reads on a pool, or holds them while closed and runs them all when opened. */
    private class GatedExecutor : Executor {
        private val pool = Executors.newCachedThreadPool()
        private val held = ArrayDeque<Runnable>()
        private var closed = false

        override fun execute(command: Runnable) {
            synchronized(this) {
                if (closed) {
                    held.addLast(command)
                    return
                }
            }
            pool.execute(command)
        }

        @Synchronized
        fun close() {
            closed = true
        }

        fun open() {
            val release = synchronized(this) {
                closed = false
                held.toList().also { held.clear() }
            }
            release.forEach(pool::execute)
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
        val isOn = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
    }
}
