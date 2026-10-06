package app.duenorth.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** specs/003-reordering R005, R006, R012, R014: moves saved on the phone, queued only for Google. */
@RunWith(RobolectricTestRunner::class)
class ReorderRepositoryTest {
    private lateinit var db: DueNorthDatabase
    private lateinit var repo: TaskRepository
    private var storesOrder = true
    private var nextId = 0
    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC)
    private val list = "list-1"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        repo = TaskRepository(db, clock, { storesOrder }) { "id${++nextId}" }
        db.accountDao().upsert(AccountEntity(provider = ProviderKind.FAKE, displayName = "Demo", email = null))
        db.taskListDao().insert(
            TaskListEntity(localId = list, remoteId = "r-list", title = "Errands", localUpdatedAt = clock.instant())
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun googleMoveBetweenTwoTasksQueuesOneMove() = runBlocking {
        val (a, b, c) = synced("00000000000000000001", "00000000000000000002", "00000000000000000003")
        repo.moveTask(c, afterId = a, beforeId = b)

        assertEquals(listOf(a, c, b), order())
        assertEquals(listOf(EntityType.TASK to c to OperationKind.MOVE), ops())
    }

    @Test
    fun googleMoveToTheTopUsesNoKey() = runBlocking {
        val (a, b) = synced("00000000000000000000", "00000000000000000001")
        repo.moveTask(b, afterId = null, beforeId = a)

        assertEquals(null, db.taskDao().get(b)!!.position)
        assertEquals(listOf(b, a), order())
    }

    @Test
    fun microsoftMovesStayOnThePhone() = runBlocking {
        storesOrder = false
        val (a, b, c) = synced(null, null, null)
        repo.moveTask(a, afterId = c, beforeId = null)

        assertTrue(ops().isEmpty())
        assertTrue(tasks().all { it.position != null })
        assertEquals(c, order()[1])
        assertEquals(a, order().last())
        assertTrue(b in order())
    }

    @Test
    fun microsoftNewTasksGoOnTop() = runBlocking {
        storesOrder = false
        val (a) = synced("5")
        val added = repo.createTask(list, "New one")

        assertEquals(listOf(added, a), order())
    }

    @Test
    fun manyMovesNeverRunOutOfRoom() = runBlocking {
        storesOrder = false
        val (a, b) = synced("1", "2")
        val c = synced("3").first()
        repeat(60) { repo.moveTask(if (it % 2 == 0) c else b, afterId = a, beforeId = if (it % 2 == 0) b else c) }

        assertEquals(3, order().toSet().size)
        assertEquals(a, order().first())
    }

    @Test
    fun stepMovesRenumberAndQueueForGoogle() = runBlocking {
        val (task) = synced("1")
        val steps = (0 until 4).map { step(task, it) }
        repo.moveStep(task, steps[2], listOf(steps[0], steps[2], steps[1], steps[3]))

        assertEquals(listOf(steps[0], steps[2], steps[1], steps[3]), db.stepDao().forTask(task).map { it.localId })
        assertTrue(EntityType.STEP to steps[2] to OperationKind.MOVE in ops())
    }

    @Test
    fun stepMovesStayOnThePhoneForMicrosoft() = runBlocking {
        storesOrder = false
        val (task) = synced("1")
        val steps = (0 until 3).map { step(task, it) }
        repo.moveStep(task, steps[0], listOf(steps[1], steps[2], steps[0]))

        assertEquals(listOf(steps[1], steps[2], steps[0]), db.stepDao().forTask(task).map { it.localId })
        assertTrue(ops().none { it.second == OperationKind.MOVE })
    }

    private suspend fun synced(vararg positions: String?): List<String> = positions.map { position ->
        val id = "task-${++nextId}"
        db.taskDao().insert(
            TaskEntity(
                localId = id,
                listId = list,
                remoteId = "r-$id",
                title = id,
                position = position,
                localUpdatedAt = clock.instant().minusSeconds(nextId.toLong())
            )
        )
        id
    }

    private suspend fun step(task: String, order: Int): String {
        val id = "step-${++nextId}"
        db.stepDao().insert(StepEntity(localId = id, taskId = task, remoteId = "r-$id", title = id, sortOrder = order))
        return id
    }

    private suspend fun tasks() = db.taskDao().openInList(list)

    private suspend fun order() = tasks().sortedWith(OrderKeys.taskComparator).map { it.localId }

    private suspend fun ops() = db.pendingOperationDao().all().map { it.entity to it.entityLocalId to it.kind }
}
