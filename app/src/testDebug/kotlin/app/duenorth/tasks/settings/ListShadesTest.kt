package app.duenorth.tasks.settings

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T063: list shades against real Room rows, through sign-out and switching service and back. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListShadesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var store: ListShadeStore
    private lateinit var shades: ListShades

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        store =
            ListShadeStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "s.preferences_pb") })
        shades = ListShades(store, tasks, accounts)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun everyListStartsOnTheAppAccent() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        tasks.createList("Errands")
        assertEquals(emptyMap<String, Int>(), shades.byList.first())
    }

    @Test
    fun onlyThePickedListGetsAShade() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        val errands = tasks.createList("Errands")
        tasks.createList("Work")
        shades.set(errands, -2)
        assertEquals(mapOf(errands to -2), shades.byList.first())
        shades.set(errands, 0)
        assertEquals(emptyMap<String, Int>(), shades.byList.first())
    }

    @Test
    fun theShadeKeyMovesToTheRemoteIdAfterTheFirstPush() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        val errands = tasks.createList("Errands")
        shades.set(errands, 1)
        assertEquals(mapOf("local:$errands" to 1), store.steps.first())

        val list = db.taskListDao().get(errands)!!
        db.taskListDao().update(list.copy(remoteId = "g-errands"))
        assertEquals(mapOf(errands to 1), shades.byList.first())
        withTimeout(5_000) { store.steps.first { it == mapOf("google:g-errands" to 1) } }
    }

    @Test
    fun theShadeSurvivesSwitchingServiceAndBack() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        insertRemote("g1", "g-errands")
        shades.set("g1", 3)

        accounts.disconnect()
        accounts.connect(ProviderKind.MICROSOFT, "Dustin", null)
        insertRemote("m1", "g-errands")
        assertEquals(emptyMap<String, Int>(), shades.byList.first())

        accounts.disconnect()
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        // A fresh sign-in makes new local ids; the remote id is what brings the shade back.
        insertRemote("g2", "g-errands")
        assertEquals(mapOf("g2" to 3), shades.byList.first())
    }

    private suspend fun insertRemote(localId: String, remoteId: String) {
        db.taskListDao().insert(
            TaskListEntity(localId = localId, remoteId = remoteId, title = "Errands", localUpdatedAt = clock.instant())
        )
    }
}
