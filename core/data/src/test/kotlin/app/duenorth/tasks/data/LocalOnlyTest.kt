package app.duenorth.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.provider.AccountProviderRegistry
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.LOCAL_CAPABILITIES
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "This phone only" (spec 010): the phone's rows are the only copy, so nothing is queued to sync. */
@RunWith(RobolectricTestRunner::class)
class LocalOnlyTest {
    private lateinit var db: DueNorthDatabase
    private lateinit var repo: TaskRepository
    private var nextId = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        repo = TaskRepository(db, Clock.systemUTC(), serviceStoresOrder = { false }) { "id${++nextId}" }
        db.accountDao().upsert(AccountEntity(provider = ProviderKind.LOCAL, displayName = "this phone", email = null))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun changesQueueNothing() = runBlocking {
        val list = repo.defaultListIdOrCreate()
        val task = repo.createTask(list, "Buy milk")
        repo.editTask(task, TaskEdit(title = "Buy oat milk", important = true))
        repo.addStep(task, "Check the fridge")
        repo.setCompleted(task, true)
        repo.renameList(list, "Groceries")

        assertTrue(db.pendingOperationDao().all().isEmpty())
        val saved = db.taskDao().get(task)!!
        assertEquals("Buy oat milk", saved.title)
        assertTrue(saved.important && saved.completed)
        assertEquals("Groceries", db.taskListDao().get(list)!!.title)
    }

    @Test
    fun deletesAreFinalAtOnce() = runBlocking {
        val list = repo.createList("Errands")
        val kept = repo.createTask(list, "Post the parcel")
        val gone = repo.createTask(list, "Return books")
        val step = repo.addStep(kept, "Find the address")

        repo.removeStep(step)
        repo.deleteTask(gone)
        assertNull(db.stepDao().get(step))
        assertNull(db.taskDao().get(gone))

        repo.setCompleted(kept, true)
        assertEquals(1, repo.clearCompleted(list))
        assertNull(db.taskDao().get(kept))

        val other = repo.createList("Old")
        repo.createTask(other, "Something")
        repo.deleteList(other)
        assertNull(db.taskListDao().get(other))
        assertTrue(db.pendingOperationDao().all().isEmpty())
    }

    @Test
    fun registryHasNoProviderButKnowsWhatThePhoneKeeps() = runBlocking {
        val registry = AccountProviderRegistry(
            db.accountDao(),
            mapOf<ProviderKind, () -> TaskProvider>(
                ProviderKind.GOOGLE to {
                    fail("not asked")
                    error("")
                }
            )
        )
        assertNull(registry.current())
        assertEquals(LOCAL_CAPABILITIES, registry.capabilities())
    }
}
