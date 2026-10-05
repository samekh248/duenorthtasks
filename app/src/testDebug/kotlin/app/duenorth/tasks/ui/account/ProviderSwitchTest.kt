package app.duenorth.tasks.ui.account

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.provider.AccountProviderRegistry
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.sync.AccountConnector
import app.duenorth.tasks.sync.SyncScheduler
import app.duenorth.tasks.sync.SyncSettingsStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T055 (US4, constitution Principle III): switching service with changes still waiting warns
 * first; confirming wipes every table on the phone and pushes nothing to the old service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProviderSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var session: AccountSession

    /** The connected service; stands in for whichever one the user is leaving. */
    private val old = FakeProvider()

    /** The service being switched to (Google Tasks in the UI, a fake behind the same interface). */
    private val next = FakeProvider()

    private val host = object : SignInHost {}

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC))
        val providers = mapOf<ProviderKind, () -> TaskProvider>(
            ProviderKind.FAKE to { old },
            ProviderKind.GOOGLE to { next }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = SyncScheduler(
            workManager = WorkManager.getInstance(context),
            settings = SyncSettingsStore(
                PreferenceDataStoreFactory.create {
                    File(folder.root, "sync.preferences_pb")
                }
            ),
            localEdits = tasks.localEdits,
            scope = scope
        )
        val connector = AccountConnector(db.accountDao(), providers, onConnected = {})
        session = AccountSession(
            accounts = AccountRepository(db),
            connector = connector,
            scheduler = scheduler,
            registry = AccountProviderRegistry(db.accountDao(), providers),
            demo = Optional.empty()
        )
        runBlocking {
            // Signed in to the old service the way a real one is (the demo account isn't in this build).
            connector.connect(ProviderKind.FAKE, host)
            // Offline edits: a list, two tasks, one ticked and one with a step, none pushed yet.
            val list = tasks.createList("Errands")
            val first = tasks.createTask(list, "Return library books", dueDate = LocalDate.of(2026, 10, 5))
            tasks.createTask(list, "Call the vet", notes = "Ask about the booster")
            tasks.setCompleted(first, true)
            tasks.addStep(first, "Find the receipt")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun switchingWithUnsyncedChangesWarnsThenClearsEverythingAndPushesNothing() {
        val pending = runBlocking { tasks.pendingCount.first() }
        assertTrue("offline edits are queued: $pending", pending > 1)
        assertTrue("nothing but these tables were expected: ${rowCounts().keys}", rowCounts().keys == TABLES)

        var picked: ProviderKind? = null
        compose.setContent {
            val waiting by tasks.pendingCount.collectAsState(pending)
            MetroTheme(darkTheme = false) {
                SyncAccountContent(
                    state = SyncAccountUiState(
                        loading = false,
                        provider = ProviderKind.FAKE,
                        signedInAs = "demo",
                        pendingChanges = waiting
                    ),
                    demoAvailable = true,
                    isConfigured = { true },
                    onInterval = {},
                    onWifiOnly = {},
                    onSyncNow = {},
                    actions = SyncAccountActions(switchTo = { picked = it }, signOut = {})
                )
            }
        }

        // Picking the other service asks first and names the changes that will be lost.
        compose.onNode(hasText("Google Tasks")).performClick()
        compose.onNode(hasText("switch to Google Tasks?")).assertExists()
        compose.onNode(hasText("$pending changes on this phone haven't synced yet and will be lost.", substring = true))
            .assertExists()

        // Cancel leaves everything alone.
        compose.onNode(hasText("cancel")).performClick()
        compose.waitForIdle()
        assertEquals(null, picked)
        assertEquals(pending, runBlocking { tasks.pendingCount.first() })

        // Confirm switches.
        compose.onNode(hasText("Google Tasks")).performClick()
        compose.onNode(hasText("switch")).performClick()
        compose.waitForIdle()
        assertEquals(ProviderKind.GOOGLE, picked)
        runBlocking { session.switchTo(ProviderKind.GOOGLE, host) }

        // Only the new account row is left on the phone.
        val counts = rowCounts()
        assertEquals(1, counts.getValue("account"))
        assertEquals(ProviderKind.GOOGLE, runBlocking { db.accountDao().get() }?.provider)
        for (table in TABLES - "account") assertEquals("$table rows after switching", 0, counts.getValue(table))

        // The old service was signed out of and never written to; the queued edits were dropped.
        assertEquals(listOf("signIn", "signOut"), old.calls)
        assertEquals(listOf("signIn"), next.calls)
    }

    /** Rows per app table, read from sqlite_master so a new table can't slip past the wipe. */
    private fun rowCounts(): Map<String, Int> {
        val names = db.query("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot { it in ROOM_TABLES || it.startsWith("sqlite_") }
        return names.associateWith { table ->
            db.query("SELECT COUNT(*) FROM `$table`", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }

    private companion object {
        val ROOM_TABLES = setOf("android_metadata", "room_master_table")
        val TABLES = setOf("account", "task_list", "task", "step", "pending_operation", "sync_log")
    }
}
