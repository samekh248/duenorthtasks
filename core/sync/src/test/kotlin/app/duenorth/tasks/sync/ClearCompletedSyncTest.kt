package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "clear completed" in a list's menu: gone here at once, then from the service. */
@RunWith(RobolectricTestRunner::class)
class ClearCompletedSyncTest : SyncTestBase() {
    @Test
    fun clearingRemovesOnlyCompletedTasksHereAndRemotely() = runBlocking {
        val home = remote.seed("Home", (1..5).map { TaskDraft(title = "Chore $it") })
        remoteTasks(home.id).take(3).forEach { remote.editRemotely(home.id, it.id, TaskPatch(completed = true)) }
        sync()
        assertEquals(3, tasks("Home").count { it.completed })

        val cleared = io { repo.clearCompleted(list("Home").localId) }

        assertEquals(3, cleared)
        assertEquals(listOf("Chore 4", "Chore 5"), tasks("Home").map { it.title }.sorted())
        sync()
        assertEquals(listOf("Chore 4", "Chore 5"), remoteTasks(home.id).map { it.title }.sorted())
        assertEquals(0, pending())
        assertEquals(listOf("Chore 4", "Chore 5"), tasks("Home").map { it.title }.sorted())
    }

    @Test
    fun aCompletedTaskNeverSyncedIsClearedWithoutAskingTheService() = runBlocking {
        remote.seed("Home")
        sync()
        val id = io { repo.createTask(list("Home").localId, "Made offline") }
        io { repo.setCompleted(id, true) }

        io { repo.clearCompleted(list("Home").localId) }

        assertTrue(tasks("Home").isEmpty())
        assertEquals(0, pending())
    }

    @Test
    fun completedTasksStillLoadingWhenClearedAreDeletedAsTheyArrive() = runBlocking {
        val home = remote.seed("Home", (1..4).map { TaskDraft(title = "Chore $it") })
        remoteTasks(home.id).take(2).forEach { remote.editRemotely(home.id, it.id, TaskPatch(completed = true)) }
        // The open tasks are in; the history with the completed ones is still to come.
        io { engine.sync() }
        assertEquals(2, tasks("Home").size)

        io { repo.clearCompleted(list("Home").localId) }
        // Finished on the web after the clear: not covered by it.
        val later = remoteTasks(home.id).first { !it.completed }
        io { remote.editRemotely(home.id, later.id, TaskPatch(completed = true)) }
        io { engine.backfill() }

        assertEquals(listOf(later.title), tasks("Home").filter { it.completed }.map { it.title })
        sync()
        assertEquals(2, remoteTasks(home.id).size)
        assertTrue(remoteTasks(home.id).none { it.completed && it.id != later.id })
        assertEquals(0, pending())
    }

    @Test
    fun aRefusedDeleteIsPutBackAndTheOthersStillGo() = runBlocking {
        val shared = remote.seed("Club", (1..3).map { TaskDraft(title = "Book $it") })
        remote.setSharing(shared.id, isShared = true, isOwner = false)
        remoteTasks(shared.id).forEach { remote.editRemotely(shared.id, it.id, TaskPatch(completed = true)) }
        sync()

        remote.refuseDelete(remoteTasks(shared.id).first().id)

        io { repo.clearCompleted(list("Club").localId) }
        assertTrue(tasks("Club").isEmpty())
        sync()

        assertEquals(1, remoteTasks(shared.id).size)
        sync()
        assertEquals(remoteTasks(shared.id).map { it.title }, tasks("Club").map { it.title })
        assertTrue(log().any { it.type == SyncLogType.CONFLICT && "so it was put back" in it.summary })
        assertEquals(0, pending())
    }
}
