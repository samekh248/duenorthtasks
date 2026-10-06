package app.duenorth.tasks.settings

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** specs/003-reordering R021: list order kept on the phone, per service, through sign-out and back. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListOrderTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: DueNorthDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var accounts: AccountRepository
    private lateinit var store: ListOrderStore
    private lateinit var order: ListOrder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(db, clock)
        accounts = AccountRepository(db)
        store = testListOrderStore()
        order = ListOrder(store, tasks, accounts)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun noOrderMeansTheUsualOrder() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        tasks.createList("Errands")
        assertEquals(emptyMap<String, Int>(), order.rank.first())
    }

    @Test
    fun aSavedOrderSortsTheListsAndNewOnesGoLast() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        insertRemote("a", "g-a", "Alpha")
        insertRemote("b", "g-b", "Bravo")
        order.set(listOf("b", "a"))
        insertRemote("c", "g-c", "Charlie")

        val rank = order.rank.first { it.size == 2 }
        assertEquals(listOf("b", "a", "c"), ListOrder.sort(listOf("a", "b", "c"), rank) { it })
    }

    @Test
    fun aListNotYetPushedKeepsItsPlaceAfterThePush() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        insertRemote("a", "g-a", "Alpha")
        val errands = tasks.createList("Errands")
        order.set(listOf(errands, "a"))
        assertEquals(mapOf(ProviderKind.GOOGLE to listOf("local:$errands", "g-a")), store.entries.first())

        db.taskListDao().update(db.taskListDao().get(errands)!!.copy(remoteId = "g-errands"))
        assertEquals(mapOf(errands to 0, "a" to 1), order.rank.first { it.size == 2 })
        withTimeout(5_000) { store.entries.first { it[ProviderKind.GOOGLE] == listOf("g-errands", "g-a") } }
    }

    @Test
    fun theOrderSurvivesSwitchingServiceAndBack() = runBlocking<Unit> {
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        insertRemote("a", "g-a", "Alpha")
        insertRemote("b", "g-b", "Bravo")
        order.set(listOf("b", "a"))

        accounts.disconnect()
        accounts.connect(ProviderKind.MICROSOFT, "Dustin", null)
        insertRemote("m", "m-a", "Alpha")
        assertEquals(emptyMap<String, Int>(), order.rank.first())

        accounts.disconnect()
        accounts.connect(ProviderKind.GOOGLE, "Dustin", null)
        insertRemote("a2", "g-a", "Alpha")
        insertRemote("b2", "g-b", "Bravo")
        assertEquals(mapOf("b2" to 0, "a2" to 1), order.rank.first { it.size == 2 })
    }

    @Test
    fun theNoteIsShownOnce() = runBlocking<Unit> {
        assertFalse(order.noteShown.first())
        order.markNoteShown()
        assertTrue(order.noteShown.first())
    }

    private suspend fun insertRemote(localId: String, remoteId: String, title: String) {
        db.taskListDao().insert(
            TaskListEntity(localId = localId, remoteId = remoteId, title = title, localUpdatedAt = clock.instant())
        )
    }
}
