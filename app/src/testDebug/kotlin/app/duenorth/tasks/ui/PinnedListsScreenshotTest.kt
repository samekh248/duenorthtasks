package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroTheme
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

/**
 * Pinned lists: "pin to home" on a list, each pinned list as its own home section between "today"
 * and "lists", and "unpin from home" on that section, light and dark.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PinnedListsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var pins: PinnedLists
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        runBlocking { DemoSeeder(accounts, tasks, clock).connect() }
        pins = testPinnedLists(tasks, accounts)
        viewModel = HomeViewModel(
            tasks,
            TemplateRepository(db, tasks, clock),
            accounts,
            ServiceFeatures(accounts) { FakeProvider() },
            ListHolds(),
            testListOrder(tasks, accounts),
            pins,
            clock
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pinAndUnpinLight() = pinAndUnpin(dark = false)

    @Test
    fun pinAndUnpinDark() = pinAndUnpin(dark = true)

    @Test
    fun listPageMenuLight() = listPageMenu(dark = false)

    @Test
    fun listPageMenuDark() = listPageMenu(dark = true)

    @Test
    fun pinnedSectionsComeInPinOrderAndTheRingStillWraps() {
        showHome(dark = false)
        swipeLeft("today")
        pinFromLists("Work")
        pinFromLists("Errands")
        assertEquals(listOf("Work", "Errands"), pinnedTitles())

        // lists ← Errands ← Work ← today ← stats: back from "lists" to "today", then round to "stats".
        swipeRight("lists")
        compose.waitUntilAtLeastOneExists(hasContentDescription("new task"), TIMEOUT)
        swipeRight("pinned:Errands")
        swipeRight("pinned:Work")
        compose.waitUntilAtLeastOneExists(hasContentDescription("new task"), TIMEOUT)
        swipeRight("today")
        compose.waitUntilDoesNotExist(hasContentDescription("new task"), TIMEOUT)
        compose.onNodeWithContentDescription("new list").assertDoesNotExist()

        // And on from "stats" round to "today" and the first pinned list.
        swipeLeft("stats")
        swipeLeft("today")
        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("unpin from home"), TIMEOUT)
        compose.onNodeWithText("unpin from home").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("pinned:Work"), TIMEOUT)
        assertEquals(listOf("Errands"), pinnedTitles())
    }

    @Test
    fun deletingAPinnedListUnpinsIt() {
        showHome(dark = false)
        swipeLeft("today")
        pinFromLists("Work")
        listRow("Work").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("delete"), TIMEOUT)
        compose.onNodeWithText("delete").performClick()
        compose.onNodeWithText("delete").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("pinned:Work"), TIMEOUT)
        assertEquals(emptyList<String>(), pinnedTitles())
    }

    private fun pinAndUnpin(dark: Boolean) {
        showHome(dark)
        swipeLeft("today")
        compose.waitUntilAtLeastOneExists(hasText("next: Call the vet"), TIMEOUT)
        listRow("Errands").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("pin to home"), TIMEOUT)
        snap("lists_menu_pin", dark)

        compose.onNodeWithText("pin to home").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("pinned:Errands"), TIMEOUT)
        // Pinning keeps "lists" in view; the new section is just before it.
        compose.waitUntilAtLeastOneExists(hasContentDescription("new list"), TIMEOUT)

        swipeRight("lists")
        compose.waitUntilAtLeastOneExists(hasContentDescription("new task"), TIMEOUT)
        settle()
        snap("pinned_section", dark)

        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("unpin from home"), TIMEOUT)
        snap("pinned_menu_unpin", dark)

        compose.onNodeWithText("unpin from home").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("pinned:Errands"), TIMEOUT)
        // The section that took its place, "lists", comes into view.
        compose.waitUntilAtLeastOneExists(hasContentDescription("new list"), TIMEOUT)
        settle()
        snap("after_unpin", dark)
    }

    private fun listPageMenu(dark: Boolean) {
        val id = runBlocking { tasks.listSummaries().first().first { it.title == "Errands" }.localId }
        val list = ListViewModel(
            SavedStateHandle(mapOf("id" to id)),
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
        show(dark) {
            val state by list.state.collectAsState()
            ListContent(state, list, onOpenTask = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("Pick up dry cleaning"), TIMEOUT)
        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("pin to home"), TIMEOUT)
        snap("list_menu_pin", dark)

        compose.onNodeWithText("pin to home").performClick()
        settle()
        compose.onNodeWithContentDescription("more").performClick()
        compose.waitUntilAtLeastOneExists(hasText("unpin from home"), TIMEOUT)
        assertEquals(listOf("Errands"), pinnedTitles())
    }

    /** The list's row on "lists", not a task caption naming it on a pinned section. */
    private fun listRow(title: String) = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("lists")))

    private fun pinFromLists(title: String) {
        listRow(title).performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("pin to home"), TIMEOUT)
        compose.onNodeWithText("pin to home").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("pinned:$title"), TIMEOUT)
        settle()
    }

    private fun pinnedTitles(): List<String> = runBlocking {
        val titles = tasks.listSummaries().first().associate { it.localId to it.title }
        pins.pinned.first().map { titles.getValue(it) }
    }

    private fun showHome(dark: Boolean) {
        val shades = runBlocking {
            mapOf(tasks.listSummaries().first().first { it.title == "Errands" }.localId to -2)
        }
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        show(dark, shades) {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, actions)
        }
        compose.waitUntilAtLeastOneExists(hasText("Pay water bill"), TIMEOUT)
    }

    private fun show(dark: Boolean, shades: Map<String, Int> = emptyMap(), content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = dark, listShades = shades) { content() } }
    }

    private fun swipeLeft(tag: String) {
        compose.onNode(hasTestTag(tag)).performTouchInput { swipeLeft() }
        settle()
    }

    private fun swipeRight(tag: String) {
        compose.onNode(hasTestTag(tag)).performTouchInput { swipeRight() }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
    }

    private fun snap(name: String, dark: Boolean) {
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/pinned-lists/${name}_${if (dark) "dark" else "light"}.png")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
