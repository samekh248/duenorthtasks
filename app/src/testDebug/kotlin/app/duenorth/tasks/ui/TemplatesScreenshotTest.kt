package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.settings.testListShades
import app.duenorth.tasks.settings.testPinnedLists
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.templates.ListTemplateActions
import app.duenorth.tasks.ui.templates.ListTemplateContent
import app.duenorth.tasks.ui.templates.ListTemplateViewModel
import app.duenorth.tasks.ui.templates.TemplateTaskContent
import app.duenorth.tasks.ui.templates.TemplateTaskViewModel
import app.duenorth.tasks.ui.templates.TemplatesActions
import app.duenorth.tasks.ui.templates.TemplatesContent
import app.duenorth.tasks.ui.templates.TemplatesViewModel
import app.duenorth.tasks.ui.templates.UseListTemplateContent
import app.duenorth.tasks.ui.templates.UseListTemplateViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** specs/004-templates screens on the demo account, light and dark, matching the spec's mockups. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TemplatesScreenshotTest {
    private val compose = createComposeRule()

    /** Closes the database only after the compose rule has torn down, so no query outlives it. */
    private val database = object : ExternalResource() {
        override fun after() {
            viewModels.clear()
            if (::db.isInitialized) db.close()
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(database).around(compose)

    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var templates: TemplateRepository
    private lateinit var accounts: AccountRepository
    private lateinit var features: ServiceFeatures
    private val viewModels = ViewModelStore()
    private lateinit var trip: String
    private lateinit var rent: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        templates = TemplateRepository(db, tasks, clock)
        accounts = AccountRepository(db)
        // The demo provider has the importance star, like Microsoft To Do.
        features = ServiceFeatures(accounts) { FakeProvider() }
        runBlocking {
            DemoSeeder(accounts, tasks, clock).connect()
            trip = templates.createListTemplate("trip packing")
            templates.setListTemplateShade(trip, 0)
            task(trip, "book parking", -3)
            task(trip, "passport", -1)
            val chargers = task(trip, "chargers", null, details = "phone, watch, laptop")
            listOf("phone", "watch", "laptop").forEach { templates.addTemplateStep(chargers, it) }
            task(trip, "water the plants", 0)
            val review = templates.createListTemplate("weekly review")
            templates.setListTemplateShade(review, 2)
            listOf("empty the inbox", "look at next week", "pick three goals").forEach { task(review, it, null) }
            rent = task(null, "pay rent", 3, important = true)
            val filter = task(null, "change furnace filter", null)
            listOf("buy filter", "turn off furnace", "swap", "write the date").forEach {
                templates.addTemplateStep(filter, it)
            }
        }
    }

    @Test
    fun templatesLight() = templatesPage(dark = false)

    @Test
    fun templatesDark() = templatesPage(dark = true)

    @Test
    fun listTemplateLight() = listTemplate(dark = false)

    @Test
    fun listTemplateDark() = listTemplate(dark = true)

    @Test
    fun useListTemplateLight() = useListTemplate(dark = false)

    @Test
    fun useListTemplateDark() = useListTemplate(dark = true)

    @Test
    fun taskTemplateLight() = taskTemplate(dark = false)

    @Test
    fun taskTemplateDark() = taskTemplate(dark = true)

    @Test
    fun pickerLight() = picker(dark = false)

    @Test
    fun pickerDark() = picker(dark = true)

    @Test
    fun tappingATaskTemplateAddsItAtOnce() {
        runBlocking { task(null, "take out the bins", 0) }
        picker(dark = false, name = null)
        compose.onNodeWithText("take out the bins").performClick()
        // The picker closes and the new task is on today straight away, before it is saved.
        compose.waitUntilDoesNotExist(hasText("change furnace filter"), TIMEOUT)
        compose.waitUntilAtLeastOneExists(hasText("take out the bins"), TIMEOUT)
        // Then it is saved; waiting for that also keeps the write from outliving the database.
        val today = LocalDate.of(2026, 10, 6)
        compose.waitUntil(TIMEOUT) {
            runBlocking { tasks.tasksDueBy(today).first() }.any { it.task.title == "take out the bins" }
        }
    }

    @Test
    fun homeShowsListsAfterAListIsMadeFromATemplate() {
        val viewModel =
            ViewModelProvider(
                viewModels,
                viewModelFactory {
                    initializer {
                        HomeViewModel(
                            tasks,
                            templates,
                            accounts,
                            features,
                            ListHolds(),
                            testListOrder(tasks, accounts),
                            testPinnedLists(tasks, accounts),
                            clock
                        )
                    }
                }
            )[HomeViewModel::class.java]
        var shown = false
        show(dark = false) {
            val state by viewModel.state.collectAsState()
            HomeContent(
                state,
                viewModel,
                HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {}),
                showLists = !shown,
                onListsShown = { shown = true }
            )
        }
        compose.waitUntil(TIMEOUT) { shown }
        compose.waitUntilAtLeastOneExists(hasContentDescription("new list"), TIMEOUT)
    }

    private suspend fun task(
        list: String?,
        title: String,
        offset: Int?,
        details: String? = null,
        important: Boolean = false
    ): String {
        val id = templates.createTemplateTask(title, list)
        templates.editTemplateTask(
            id,
            notes = details?.let { Patch.Set(it) },
            important = important.takeIf { it },
            dueOffset = offset?.let { Patch.Set(it) }
        )
        return id
    }

    private fun templatesPage(dark: Boolean) {
        val viewModel = TemplatesViewModel(templates)
        show(dark) {
            val state by viewModel.state.collectAsState()
            TemplatesContent(state, viewModel, TemplatesActions(useList = {}, editList = {}, editTask = {}))
        }
        compose.waitUntilAtLeastOneExists(hasText("trip packing"), TIMEOUT)
        snap("templates", dark)
    }

    private fun listTemplate(dark: Boolean) {
        val viewModel = ListTemplateViewModel(SavedStateHandle(mapOf("id" to trip)), templates, features)
        show(dark) {
            val state by viewModel.state.collectAsState()
            ListTemplateContent(state, viewModel, ListTemplateActions(use = {}, editTask = {}, closed = {}))
        }
        compose.waitUntilAtLeastOneExists(hasText("3 days before start"), TIMEOUT)
        snap("list_template", dark)
    }

    private fun useListTemplate(dark: Boolean) {
        val viewModel =
            UseListTemplateViewModel(
                SavedStateHandle(mapOf("id" to trip)),
                templates,
                testListShades(tasks, accounts),
                clock
            )
        show(dark) {
            val state by viewModel.state.collectAsState()
            UseListTemplateContent(state, onCreate = { _, _ -> }, onCancel = {})
        }
        compose.waitUntilAtLeastOneExists(hasText("4 tasks, 3 with due dates"), TIMEOUT)
        snap("use_list_template", dark)
    }

    private fun taskTemplate(dark: Boolean) {
        val viewModel = TemplateTaskViewModel(SavedStateHandle(mapOf("id" to rent)), templates, features)
        show(dark) {
            val state by viewModel.state.collectAsState()
            TemplateTaskContent(state, viewModel)
        }
        compose.waitUntilAtLeastOneExists(hasText("due in 3 days"), TIMEOUT)
        snap("task_template", dark)
    }

    private fun picker(dark: Boolean, name: String? = "use_a_template") {
        val viewModel =
            ViewModelProvider(
                viewModels,
                viewModelFactory {
                    initializer {
                        HomeViewModel(
                            tasks,
                            templates,
                            accounts,
                            features,
                            ListHolds(),
                            testListOrder(tasks, accounts),
                            testPinnedLists(tasks, accounts),
                            clock
                        )
                    }
                }
            )[HomeViewModel::class.java]
        show(dark) {
            val state by viewModel.state.collectAsState()
            HomeContent(state, viewModel, HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {}))
        }
        compose.waitUntilAtLeastOneExists(hasText("use a template"), TIMEOUT)
        compose.onNodeWithText("use a template").performClick()
        compose.waitUntilAtLeastOneExists(hasText("change furnace filter"), TIMEOUT)
        if (name != null) snap(name, dark)
    }

    private fun show(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = dark) { content() } }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/screens/templates/${name}_${if (dark) "dark" else "light"}.png"
        )
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
