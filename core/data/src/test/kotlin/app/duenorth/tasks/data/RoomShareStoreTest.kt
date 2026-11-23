package app.duenorth.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.RoomShareStore
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.share.Identity
import app.duenorth.tasks.share.ShareEngine
import app.duenorth.tasks.share.SharedList
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Share by tap (spec 011) on two real Room databases, edited through the repository. */
@RunWith(RobolectricTestRunner::class)
class RoomShareStoreTest {
    private inner class Phone(val name: String) {
        val db: DueNorthDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DueNorthDatabase::class.java
        ).allowMainThreadQueries().build()
        val repo = TaskRepository(db, Clock.systemUTC(), serviceStoresOrder = { false })
        val store = RoomShareStore(db)
        val id = Identity.generate()
        val engine = ShareEngine(store, id)

        init {
            runBlocking {
                db.accountDao().upsert(
                    AccountEntity(provider = ProviderKind.LOCAL, displayName = "this phone", email = null)
                )
            }
        }
    }

    private val dustin = Phone("Dustin")
    private val alex = Phone("Alex")

    @After
    fun tearDown() {
        dustin.db.close()
        alex.db.close()
    }

    private fun tap(a: Phone, b: Phone) {
        val lists = a.engine.listsWith(b.id.id)
        a.store.transaction { lists.forEach { a.engine.capture(it) } }
        b.store.transaction { lists.forEach { b.engine.capture(it) } }
        val toB = lists.associateWith { a.engine.missing(it, b.engine.summary(it)) }
        val toA = lists.associateWith { b.engine.missing(it, a.engine.summary(it)) }
        b.store.transaction { lists.forEach { b.engine.receive(it, toB[it]!!) } }
        a.store.transaction { lists.forEach { a.engine.receive(it, toA[it]!!) } }
    }

    @Test
    fun shareThenEditOnBothPhones() = runBlocking {
        assertTrue(dustin.store.isLocalOnly())
        val list = dustin.repo.createList("groceries")
        val milk = dustin.repo.createTask(list, "Oat milk")
        dustin.repo.addStep(milk, "Check the fridge")
        dustin.engine.start(list, "Dustin")
        dustin.engine.add(list, alex.id.id, "Alex")
        alex.store.saveSharedList(SharedList(list, dustin.id.id, linkedAt = 0))
        alex.store.transaction { alex.engine.receive(list, dustin.engine.everything(list)) }

        val received = alex.db.taskDao().get(milk)!!
        assertEquals("Oat milk", received.title)
        assertEquals("Check the fridge", alex.db.stepDao().forTask(milk).single().title)
        assertTrue(alex.db.pendingOperationDao().all().isEmpty())

        Thread.sleep(5)
        alex.repo.editTask(milk, TaskEdit(notes = Patch.Set("two cartons")))
        dustin.repo.setCompleted(milk, true)
        val basil = alex.repo.createTask(list, "Basil")
        tap(dustin, alex)

        for (p in listOf(dustin, alex)) {
            val t = p.db.taskDao().get(milk)!!
            assertEquals("two cartons", t.notes)
            assertTrue(t.completed)
            assertEquals("Basil", p.db.taskDao().get(basil)!!.title)
        }

        alex.repo.deleteTask(basil)
        tap(alex, dustin)
        assertEquals(null, dustin.db.taskDao().get(basil))
    }

    @Test
    fun clearAllKeepsTheLists() = runBlocking {
        val list = dustin.repo.createList("groceries")
        dustin.engine.start(list, "Dustin")
        dustin.store.clearAll()
        assertFalse(dustin.store.sharedList(list) != null)
        assertEquals("groceries", dustin.db.taskListDao().get(list)!!.title)
    }
}
