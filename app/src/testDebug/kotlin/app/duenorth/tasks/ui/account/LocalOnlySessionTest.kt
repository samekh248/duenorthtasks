package app.duenorth.tasks.ui.account

import android.app.Application
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
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.sync.AccountConnector
import app.duenorth.tasks.sync.SyncEngine
import app.duenorth.tasks.sync.SyncResult
import app.duenorth.tasks.sync.SyncScheduler
import app.duenorth.tasks.sync.SyncSettingsStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "This phone only" (spec 010): connecting needs no sign-in and makes one list, nothing ever goes
 * to a service, and leaving it warns that its tasks are deleted, then clears the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalOnlySessionTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var session: AccountSession
    private lateinit var registry: AccountProviderRegistry

    /** Stands in for Google Tasks; nothing may reach it until the user switches. */
    private val google = FakeProvider()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(
            db,
            Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneOffset.UTC),
            // As the app wires it: the phone keeps order itself until a service that stores it.
            serviceStoresOrder = { db.accountDao().get()?.provider != ProviderKind.LOCAL }
        )
        val providers = mapOf<ProviderKind, () -> TaskProvider>(ProviderKind.GOOGLE to { google })
        registry = AccountProviderRegistry(db.accountDao(), providers)
        val scheduler = SyncScheduler(
            workManager = WorkManager.getInstance(context),
            settings = SyncSettingsStore(
                PreferenceDataStoreFactory.create {
                    File(folder.root, "sync.preferences_pb")
                }
            ),
            localEdits = tasks.localEdits,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        session = AccountSession(
            accounts = AccountRepository(db),
            connector = AccountConnector(db.accountDao(), providers, onConnected = {}),
            scheduler = scheduler,
            registry = registry,
            tasks = tasks,
            demo = Optional.empty()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun connectingNeedsNoSignInAndNothingSyncs() = runBlocking {
        assertTrue(session.isConfigured(ProviderKind.LOCAL))
        session.connect(ProviderKind.LOCAL, object : SignInHost {})

        assertEquals(ProviderKind.LOCAL, db.accountDao().get()?.provider)
        val list = checkNotNull(tasks.defaultListId()) { "a default list is made" }
        tasks.createTask(list, "Buy milk")
        assertEquals(0, tasks.pendingCount.first())

        val engine = SyncEngine(db, registry, dispatcher = Dispatchers.Unconfined)
        assertEquals(SyncResult.NoAccount, engine.sync())
        assertFalse(engine.isSyncing.value)
        assertTrue("nothing reached a service: ${google.calls}", google.calls.isEmpty())
    }

    @Test
    fun switchingToAServiceAsksThenCopiesEverythingUp() {
        runBlocking {
            session.connect(ProviderKind.LOCAL, object : SignInHost {})
            val inbox = tasks.defaultListIdOrCreate()
            val errands = tasks.createList("Errands")
            tasks.createTask(inbox, "Buy milk")
            val vet = tasks.createTask(inbox, "Call the vet")
            tasks.addStep(vet, "Find the card")
            val done = tasks.createTask(errands, "Post the parcel")
            tasks.setCompleted(done, true)
            // Already on the service; the copy goes beside it, untouched.
            google.seed("My Tasks", listOf(TaskDraft("Already there")))
        }
        var picked: ProviderKind? = null
        compose.setContent {
            MetroTheme(darkTheme = false) {
                SyncAccountContent(
                    state = SyncAccountUiState(
                        loading = false,
                        provider = ProviderKind.LOCAL,
                        signedInAs = "this phone"
                    ),
                    demoAvailable = false,
                    isConfigured = { true },
                    onInterval = {},
                    onWifiOnly = {},
                    onSyncNow = {},
                    actions = SyncAccountActions(switchTo = { picked = it }, signOut = {})
                )
            }
        }
        compose.onNode(hasText("sync every")).assertDoesNotExist()
        compose.onNode(hasText("Google Tasks")).performClick()
        compose.onNode(hasText("copied to Google Tasks", substring = true)).assertExists()
        compose.onNode(hasText("switch")).performClick()
        compose.waitForIdle()
        assertEquals(ProviderKind.GOOGLE, picked)

        runBlocking {
            session.switchTo(ProviderKind.GOOGLE, object : SignInHost {})
            assertEquals(ProviderKind.GOOGLE, db.accountDao().get()?.provider)
            val engine = SyncEngine(db, registry, dispatcher = Dispatchers.Unconfined)
            engine.sync()
            if (engine.backfillPending) engine.backfill()
            assertEquals(0, tasks.pendingCount.first())

            val lists = google.getLists().associateBy { it.title }
            assertEquals(setOf("My Tasks", "Tasks", "Errands"), lists.keys)
            val inbox = google.getTaskChanges(lists.getValue("Tasks").id, null).changed
            // Created bottom first: the fake numbers tasks in creation order, and Google, which adds
            // each new task at the top, then ends up with the phone's order ("Call the vet" on top).
            assertEquals(
                listOf("Buy milk", "Call the vet"),
                inbox.sortedBy { it.position }.map { it.title }
            )
            assertEquals(listOf("Find the card"), inbox.first { it.title == "Call the vet" }.steps.map { it.title })
            val errands = google.getTaskChanges(lists.getValue("Errands").id, null).changed.single()
            assertTrue(errands.title == "Post the parcel" && errands.completed)
            val mine = google.getTaskChanges(lists.getValue("My Tasks").id, null).changed
            assertEquals(listOf("Already there"), mine.map { it.title })
        }
    }

    @Test
    fun aFailedSignInLeavesThePhoneAsItWas() = runBlocking {
        session.connect(ProviderKind.LOCAL, object : SignInHost {})
        tasks.createTask(tasks.defaultListIdOrCreate(), "Buy milk")
        google.failNext(ProviderError.AuthRequired())

        assertThrows(ProviderError.AuthRequired::class.java) {
            runBlocking { session.switchTo(ProviderKind.GOOGLE, object : SignInHost {}) }
        }
        assertEquals(ProviderKind.LOCAL, db.accountDao().get()?.provider)
        assertEquals(0, tasks.pendingCount.first())
        assertEquals(1, tasks.openTasks(checkNotNull(tasks.defaultListId())).first().size)
    }
}
