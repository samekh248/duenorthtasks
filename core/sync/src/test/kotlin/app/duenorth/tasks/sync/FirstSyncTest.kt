package app.duenorth.tasks.sync

import app.duenorth.tasks.provider.api.TaskChangePage
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A first sync of an account with a long history: open tasks first, the rest behind them. */
@RunWith(RobolectricTestRunner::class)
class FirstSyncTest : SyncTestBase() {
    @Test
    fun openTasksShowAndTheDotsStopBeforeCompletedOnesArrive() = runBlocking {
        val errands = remote.seed("Errands")
        val home = remote.seed("Home")
        seedTasks(errands.id, open = 2, done = 7)
        seedTasks(home.id, open = 1, done = 5)

        // What the phone holds when the first full-history page is asked for.
        var tasksAtFirstPage = -1
        var syncingAtFirstPage = true
        val watched = object : TaskProvider by remote {
            // Lists are fetched side by side; one at a time here keeps the count from moving under us.
            val oneAtATime = Mutex()

            override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage = oneAtATime.withLock {
                if (tasksAtFirstPage < 0) {
                    tasksAtFirstPage = lists().sumOf { tasks(it.title).size }
                    syncingAtFirstPage = engine.isSyncing.value
                }
                remote.getTaskChanges(listId, cursor)
            }
        }
        engine =
            SyncEngine(db, {
                watched
            }, holds, clock, { java.util.UUID.randomUUID().toString() }, Dispatchers.Unconfined)

        assertEquals(SyncResult.Success, sync())

        assertEquals(3, tasksAtFirstPage)
        assertFalse(syncingAtFirstPage)
        assertEquals(9, tasks("Errands").size)
        assertEquals(5, tasks("Home").count { it.completed })
        assertEquals(listOf("Step 0"), steps(task("Errands 0")).map { it.title })
        // Every list now has a change cursor, so later syncs only ask for changes.
        assertTrue(lists().all { it.tasksCursor != null })
    }

    @Test
    fun aTaskDeletedBetweenTheTwoPassesDoesNotLinger() = runBlocking {
        val errands = remote.seed("Errands")
        seedTasks(errands.id, open = 2, done = 3)
        val watched = object : TaskProvider by remote {
            var deleted = false
            override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage {
                if (!deleted) {
                    deleted = true
                    remote.getOpenTasks(listId).first().let { remote.deleteTask(listId, it.id) }
                }
                return remote.getTaskChanges(listId, cursor)
            }
        }
        engine =
            SyncEngine(db, {
                watched
            }, holds, clock, { java.util.UUID.randomUUID().toString() }, Dispatchers.Unconfined)

        sync()

        assertEquals(4, tasks("Errands").size)
    }

    @Test
    fun aBackfillThatKeepsFailingDoesNotHoldBackChangesMadeHere() = runBlocking {
        val errands = remote.seed("Errands")
        seedTasks(errands.id, open = 1, done = 3)
        // The completed-task history never finishes loading, as when the service keeps throttling.
        val stuck = object : TaskProvider by remote {
            override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage =
                throw app.duenorth.tasks.provider.api.ProviderError.RateLimited(kotlin.time.Duration.ZERO)
        }
        engine =
            SyncEngine(db, { stuck }, holds, clock, { java.util.UUID.randomUUID().toString() }, Dispatchers.Unconfined)
        sync()
        assertEquals(1, tasks("Errands").size)

        val mine = io { repo.createList("Mine") }
        io { repo.createTask(mine, "Made on the phone") }
        sync()

        val remoteMine = remote.getLists().single { it.title == "Mine" }
        assertEquals(listOf("Made on the phone"), remoteTasks(remoteMine.id).map { it.title })
    }

    @Test
    fun aSyncWhileTheHistoryLoadsSendsNewTasksStraightAway() = runBlocking {
        val errands = remote.seed("Errands")
        seedTasks(errands.id, open = 1, done = 3)
        // The history load stalls on its first page until the test lets it go.
        val letGo = CompletableDeferred<Unit>()
        val stalled = CompletableDeferred<Unit>()
        val slow = object : TaskProvider by remote {
            override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage {
                stalled.complete(Unit)
                letGo.await()
                return remote.getTaskChanges(listId, cursor)
            }
        }
        engine =
            SyncEngine(db, { slow }, holds, clock, { java.util.UUID.randomUUID().toString() }, Dispatchers.Unconfined)
        clock.tick()
        assertEquals(SyncResult.Success, engine.sync())
        assertTrue(engine.backfillPending)
        val history = launch(Dispatchers.Default) { engine.backfill() }
        withTimeout(5_000) { stalled.await() }

        io { repo.createTask(list("Errands").localId, "Added during the first sync") }
        clock.tick()
        val result = withTimeout(5_000) { engine.sync() }

        assertEquals(SyncResult.Success, result)
        assertTrue(remoteTasks(errands.id).any { it.title == "Added during the first sync" })
        letGo.complete(Unit)
        history.join()
        assertFalse(engine.backfillPending)
        assertEquals(5, tasks("Errands").size)
        assertTrue(lists().all { it.tasksCursor != null })
    }

    @Test
    fun aLargeHistoryLoadsWithoutSlowingDownAsItGrows() = runBlocking {
        val big = remote.seed("Big")
        seedTasks(big.id, open = 50, done = 1500)
        val start = System.nanoTime()
        sync()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1550, tasks("Big").size)
        // Was about 14 s for 3,600 tasks when each new task re-read its whole list.
        assertTrue("first sync took $ms ms", ms < 20_000)
    }

    private suspend fun seedTasks(listId: String, open: Int, done: Int) {
        val title = remote.getLists().single { it.id == listId }.title
        repeat(open + done) { n ->
            val task = remote.createTask(
                listId,
                TaskDraft("$title $n", steps = listOf(app.duenorth.tasks.provider.api.StepDraft("Step $n")))
            )
            if (n >= open) remote.updateTask(listId, task.id, TaskPatch(completed = true))
        }
    }
}
