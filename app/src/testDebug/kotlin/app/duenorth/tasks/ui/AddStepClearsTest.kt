package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
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
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.detail.TaskDetailContent
import app.duenorth.tasks.ui.detail.TaskDetailViewModel
import app.duenorth.tasks.ui.templates.TemplateTaskContent
import app.duenorth.tasks.ui.templates.TemplateTaskViewModel
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
 * The "add a step" boxes on a task and on a task template empty once a step is added, even when
 * the next step is typed and added before the screen draws another frame.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddStepClearsTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var templates: TemplateRepository
    private lateinit var accounts: AccountRepository
    private lateinit var features: ServiceFeatures

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        templates = TemplateRepository(db, tasks, clock)
        accounts = AccountRepository(db)
        features = ServiceFeatures(accounts) { FakeProvider() }
        runBlocking { accounts.connect(ProviderKind.FAKE, "demo", null) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun taskStepBoxClearsBetweenQuickAdds() {
        val taskId = runBlocking { tasks.createTask(tasks.defaultListIdOrCreate(), "Pack") }
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
        addQuickly()
        compose.waitUntil(TIMEOUT) { storedSteps(taskId).size == 3 }
        assertEquals(listOf("Bread", "Eggs", "Milk"), storedSteps(taskId))
    }

    @Test
    fun templateStepBoxClearsBetweenQuickAdds() {
        val templateId = runBlocking { templates.createTemplateTask("Pack") }
        val viewModel = TemplateTaskViewModel(SavedStateHandle(mapOf("id" to templateId)), templates, features)
        compose.setContent {
            val state by viewModel.state.collectAsState()
            MetroTheme(darkTheme = false) { TemplateTaskContent(state, viewModel) }
        }
        addQuickly()
        compose.waitUntil(TIMEOUT) { templateSteps(templateId).size == 3 }
        assertEquals(listOf("Bread", "Eggs", "Milk"), templateSteps(templateId))
    }

    /** Types and adds three titles with no frame drawn in between. */
    private fun addQuickly() {
        compose.waitUntilAtLeastOneExists(hasSetTextAction(), TIMEOUT)
        val box: SemanticsNodeInteraction = compose.onNode(hasSetTextAction())
        box.performTextInput("Milk")
        compose.mainClock.autoAdvance = false
        box.performImeAction()
        box.performTextInput("Eggs")
        box.performImeAction()
        box.performTextInput("Bread")
        box.performImeAction()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        box.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    private fun storedSteps(taskId: String): List<String> =
        runBlocking { tasks.steps(taskId).first() }.map { it.title }.sorted()

    private fun templateSteps(templateId: String): List<String> =
        runBlocking { templates.steps(templateId).first() }.map { it.title }.sorted()

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
