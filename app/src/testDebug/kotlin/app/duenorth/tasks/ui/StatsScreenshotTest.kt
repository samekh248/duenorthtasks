package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.data.repo.TemplateRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.settings.testListOrder
import app.duenorth.tasks.sync.ListHolds
import app.duenorth.tasks.ui.common.ServiceFeatures
import app.duenorth.tasks.ui.home.HomeActions
import app.duenorth.tasks.ui.home.HomeContent
import app.duenorth.tasks.ui.home.HomeViewModel
import app.duenorth.tasks.ui.stats.StatsSource
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

/** Spec 005: the stats section and the empty today, light and dark, for review (CI uploads them). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StatsScreenshotTest {
    private val compose = createComposeRule()

    /** Closes the database only after the compose rule has torn down, so no stats query outlives it. */
    private val database = object : ExternalResource() {
        override fun after() {
            viewModels.clear()
            if (::db.isInitialized) db.close()
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(database).around(compose)

    private val viewModels = ViewModelStore()

    private val clock = Clock.fixed(Instant.parse("2026-10-08T18:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 8)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        runBlocking { DemoSeeder(accounts, tasks, clock).connect() }
    }

    @Test
    fun statsLight() = stats(dark = false)

    @Test
    fun statsDark() = stats(dark = true)

    @Test
    fun statsLowerLight() = stats(dark = false, lower = true)

    @Test
    fun statsLowerDark() = stats(dark = true, lower = true)

    @Test
    fun statsLoadingHistory() = stats(dark = false, partial = true)

    @Test
    fun emptyTodayLight() = emptyToday(dark = false)

    @Test
    fun emptyTodayDark() = emptyToday(dark = true)

    /** Sixteen weeks of finished tasks across the demo lists, the same every run. */
    private fun seedHistory() = runBlocking {
        val lists = tasks.listSummaries().first().map { it.localId }
        var seed = 7L
        fun next(): Int {
            seed = (seed * 9301 + 49297) % 233280
            return (seed * 7 / 233280).toInt()
        }
        val rows = (0L until 112L).flatMap { back ->
            val day = today.minusDays(back)
            List(next()) { i ->
                TaskEntity(
                    localId = UUID.randomUUID().toString(),
                    listId = lists[(back.toInt() + i) % lists.size],
                    title = "done $back $i",
                    dueDate = if (i % 3 == 0) null else day.plusDays(if (i % 5 == 0) -1 else 1),
                    completed = true,
                    completedAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
                    localUpdatedAt = clock.instant()
                )
            }
        }
        db.taskDao().insertAll(rows)
    }

    private fun home(dark: Boolean, partial: Boolean = false) {
        val features = ServiceFeatures(accounts) { FakeProvider() }
        val viewModel =
            ViewModelProvider(
                viewModels,
                viewModelFactory {
                    initializer {
                        HomeViewModel(
                            tasks,
                            TemplateRepository(db, tasks, clock),
                            accounts,
                            features,
                            ListHolds(),
                            testListOrder(tasks, accounts),
                            clock
                        )
                    }
                }
            )[HomeViewModel::class.java]
        val source = StatsSource(tasks, flowOf(partial), clock) { DayOfWeek.SUNDAY }
        val actions = HomeActions(openTask = {}, openList = {}, search = {}, openSyncAccount = {})
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                val state by viewModel.state.collectAsState()
                val stats by source.stats.collectAsState(null)
                HomeContent(state, viewModel, actions, stats = stats)
            }
        }
    }

    private fun stats(dark: Boolean, lower: Boolean = false, partial: Boolean = false) {
        seedHistory()
        home(dark, partial)
        compose.waitUntilAtLeastOneExists(hasTestTag("today"), TIMEOUT)
        // "stats" sits just before "today" round the ring.
        compose.onNode(hasTestTag("today")).performTouchInput { swipeRight() }
        compose.waitUntilAtLeastOneExists(hasText("done this week"), TIMEOUT)
        if (lower) {
            compose.onNode(hasTestTag("stats")).performTouchInput { swipeUp() }
            compose.waitUntilAtLeastOneExists(hasText("all time"), TIMEOUT)
        }
        compose.mainClock.advanceTimeBy(2_000)
        val name = when {
            partial -> "home_stats_partial"
            lower -> "home_stats_lower"
            else -> "home_stats"
        }
        snap(name, dark)
    }

    private fun emptyToday(dark: Boolean) {
        runBlocking {
            tasks.tasksDueBy(today.plusDays(1)).first().forEach { tasks.setCompleted(it.task.localId, true) }
        }
        home(dark)
        compose.waitUntilAtLeastOneExists(hasText("all clear."), TIMEOUT)
        compose.mainClock.advanceTimeBy(2_000)
        snap("home_empty_today", dark)
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
