package app.duenorth.tasks.sync

import app.duenorth.tasks.provider.api.TaskChangePage
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
