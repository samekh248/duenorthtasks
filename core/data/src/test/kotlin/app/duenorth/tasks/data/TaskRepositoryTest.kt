package app.duenorth.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.PendingOperationEntity
import app.duenorth.tasks.data.db.SyncLogEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {
    private lateinit var db: DueNorthDatabase
    private lateinit var repo: TaskRepository
    private val clock = TestClock()
    private var nextId = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        repo = TaskRepository(db, clock) { "id${++nextId}" }
        db.accountDao().upsert(AccountEntity(provider = ProviderKind.FAKE, displayName = "Demo", email = null))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun newTaskIsSavedWithACreateOperation() = runBlocking {
        val list = syncedList()
        val task = repo.createTask(list, "  Buy milk  ", notes = "2%", dueDate = TODAY)

        val saved = db.taskDao().get(task)!!
        assertEquals("Buy milk", saved.title)
        assertEquals("2%", saved.notes)
        assertEquals(listOf(op(EntityType.TASK, task, OperationKind.CREATE)), ops())
    }

    @Test
    fun editsToAnUnpushedTaskRideAlongWithItsCreate() = runBlocking {
        val list = syncedList()
        val task = repo.createTask(list, "Buy milk")
        repo.editTask(task, TaskEdit(title = "Buy oat milk"))
        repo.setCompleted(task, true)

        assertEquals(listOf(op(EntityType.TASK, task, OperationKind.CREATE)), ops())
        assertTrue(db.taskDao().get(task)!!.completed)
    }

    @Test
    fun updatesToASyncedTaskCoalesceIntoOneOperation() = runBlocking {
        val task = syncedTask()
        repo.editTask(task, TaskEdit(title = "Return library books"))
        clock.advance()
        repo.setCompleted(task, true)
        repo.editTask(task, TaskEdit(dueDate = Patch.Set(TODAY)))

        assertEquals(
            listOf(op(EntityType.TASK, task, OperationKind.UPDATE, Fields.TITLE, Fields.COMPLETED, Fields.DUE_DATE)),
            ops()
        )
    }

    @Test
    fun anEditThatChangesNothingQueuesNothing() = runBlocking {
        val task = syncedTask()
        repo.editTask(task, TaskEdit(title = "Library books", notes = Patch.Clear))
        repo.setCompleted(task, false)

        assertEquals(emptyList<PendingOperationEntity>(), db.pendingOperationDao().all())
    }

    @Test
    fun deletingAnUnpushedTaskCancelsItsCreate() = runBlocking {
        val list = syncedList()
        val task = repo.createTask(list, "Typo")
        repo.addStep(task, "step")
        repo.deleteTask(task)

        assertEquals(emptyList<PendingOperationEntity>(), db.pendingOperationDao().all())
        assertNull(db.taskDao().get(task))
    }

    @Test
    fun deletingASyncedTaskReplacesItsUpdateWithADelete() = runBlocking {
        val task = syncedTask()
        repo.editTask(task, TaskEdit(title = "Changed"))
        repo.deleteTask(task)

        assertEquals(listOf(op(EntityType.TASK, task, OperationKind.DELETE)), ops())
        // Kept, hidden, until the provider confirms the delete.
        assertTrue(db.taskDao().get(task)!!.deletedLocally)
        assertEquals(emptyList<Any>(), repo.tasksDueBy(TODAY.plusDays(1)).first())
    }

    @Test
    fun deletingAnUnpushedListCancelsEverythingInIt() = runBlocking {
        val list = repo.createList("Errands")
        val task = repo.createTask(list, "Post office")
        repo.addStep(task, "stamps")
        repo.deleteList(list)

        assertEquals(emptyList<PendingOperationEntity>(), db.pendingOperationDao().all())
        assertNull(db.taskListDao().get(list))
        assertNull(db.taskDao().get(task))
    }

    @Test
    fun deletingASyncedListQueuesOnlyTheListDelete() = runBlocking {
        val list = syncedList()
        val task = syncedTask(list)
        repo.editTask(task, TaskEdit(title = "Changed"))
        repo.deleteList(list)

        assertEquals(listOf(op(EntityType.LIST, list, OperationKind.DELETE)), ops())
        assertTrue(db.taskDao().get(task)!!.deletedLocally)
        assertEquals(emptyList<Any>(), repo.listSummaries().first())
    }

    @Test
    fun deletingTheAccountClearsEveryTable() = runBlocking {
        val list = repo.createList("Errands")
        val task = repo.createTask(list, "Post office")
        repo.addStep(task, "stamps")
        db.syncLogDao().insert(SyncLogEntity(at = clock.instant(), type = SyncLogType.CONFLICT, summary = "x"))

        db.accountDao().delete()

        assertNull(db.taskListDao().get(list))
        assertNull(db.taskDao().get(task))
        assertEquals(emptyList<Any>(), db.stepDao().forTask(task))
        assertEquals(emptyList<Any>(), db.pendingOperationDao().all())
        assertEquals(emptyList<Any>(), db.syncLogDao().observeAll().first())
    }

    @Test
    fun todayShowsOpenTasksDueByTomorrowOldestFirst() = runBlocking {
        val list = syncedList()
        val tomorrow = repo.createTask(list, "Tomorrow", dueDate = TODAY.plusDays(1))
        val overdue = repo.createTask(list, "Overdue", dueDate = TODAY.minusDays(3))
        val today = repo.createTask(list, "Today", dueDate = TODAY)
        repo.createTask(list, "Next week", dueDate = TODAY.plusDays(7))
        repo.createTask(list, "Someday")
        val done = repo.createTask(list, "Done", dueDate = TODAY)
        repo.setCompleted(done, true)

        val due = repo.tasksDueBy(TODAY.plusDays(1)).first()

        assertEquals(listOf(overdue, today, tomorrow), due.map { it.task.localId })
        assertEquals("Inbox", due.first().listTitle)
        assertEquals(listOf(done), repo.recentlyCompleted().first().map { it.task.localId })
    }

    @Test
    fun listSummaryCountsOpenTasksAndNamesTheNextOne() = runBlocking {
        val list = syncedList()
        repo.createTask(list, "Later", dueDate = TODAY.plusDays(5))
        repo.createTask(list, "Soon", dueDate = TODAY)
        repo.setCompleted(repo.createTask(list, "Done"), true)

        val summary = repo.listSummaries().first().single()

        assertEquals(2, summary.openCount)
        assertEquals("Soon", summary.nextTaskTitle)
    }

    @Test
    fun stepEditsCoalesceAndRemovingAnUnpushedStepCancelsIt() = runBlocking {
        val task = syncedTask()
        val step = repo.addStep(task, "first")
        repo.editStep(step, done = true)
        repo.removeStep(step)

        assertEquals(emptyList<PendingOperationEntity>(), db.pendingOperationDao().all())
        assertEquals(emptyList<Any>(), repo.steps(task).first())
    }

    @Test
    fun movingATaskToAnotherListIsAnUpdate() = runBlocking {
        val task = syncedTask()
        val other = syncedList(remoteId = "r-other", title = "Errands")
        repo.editTask(task, TaskEdit(listId = other))

        assertEquals(other, db.taskDao().get(task)!!.listId)
        assertEquals(listOf(op(EntityType.TASK, task, OperationKind.UPDATE, Fields.LIST)), ops())
    }

    @Test
    fun everyWriteIsAnnouncedForTheSyncScheduler() = runBlocking {
        val list = syncedList()
        repo.localEdits.test {
            repo.createTask(list, "Buy milk")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTitlesAreRejected() {
        runBlocking { repo.createTask(syncedList(), "   ") }
    }

    private suspend fun syncedList(remoteId: String = "r-inbox", title: String = "Inbox"): String {
        val id = "list-$remoteId"
        db.taskListDao().insert(
            TaskListEntity(
                localId = id,
                remoteId = remoteId,
                title = title,
                isDefault = true,
                localUpdatedAt = clock.instant()
            )
        )
        return id
    }

    private suspend fun syncedTask(list: String? = null): String {
        val listId = list ?: syncedList()
        val id = "task-${++nextId}"
        db.taskDao().insert(
            TaskEntity(
                localId = id,
                listId = listId,
                remoteId = "r-$id",
                title = "Library books",
                localUpdatedAt = clock.instant()
            )
        )
        return id
    }

    private suspend fun ops() = db.pendingOperationDao().all().map {
        op(it.entity, it.entityLocalId, it.kind, *it.changedFields.toTypedArray())
    }

    private fun op(entity: EntityType, id: String, kind: OperationKind, vararg fields: String) =
        Triple(entity to id, kind, fields.toSet())

    private class TestClock(private var now: Instant = Instant.parse("2026-10-04T09:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now

        fun advance() {
            now = now.plusSeconds(60)
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 4)
    }
}
