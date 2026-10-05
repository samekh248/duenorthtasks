package app.duenorth.tasks.provider.api

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The behavior every [TaskProvider] must share (constitution Principle V). Each provider module
 * subclasses this: the fake directly, Google and Microsoft against recorded MockWebServer responses.
 */
abstract class TaskProviderContractTest {
    /** A provider signed in to an empty account. */
    abstract suspend fun newProvider(): TaskProvider

    /** Simulates an edit made on the web, bypassing the app. */
    abstract suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch)

    @Test
    fun createsReadsRenamesAndDeletesLists() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        assertEquals(listOf("Errands"), provider.getLists().map { it.title })

        val renamed = provider.updateList(list.id, ListPatch(title = "Chores"))
        assertEquals("Chores", renamed.title)

        provider.deleteList(list.id)
        assertTrue(provider.getLists().none { it.id == list.id })
    }

    @Test
    fun createsTasksWithDetailsDueDateAndSteps() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val draft = TaskDraft(
            title = "Return library books",
            notes = "Due back Tuesday",
            dueDate = LocalDate.of(2026, 10, 6),
            steps = listOf(StepDraft("Find the books"), StepDraft("Bring a bag", done = true))
        )
        val created = provider.createTask(list.id, draft)

        val fetched = provider.allTasks(list.id).single()
        assertEquals(created.id, fetched.id)
        assertEquals("Return library books", fetched.title)
        assertEquals("Due back Tuesday", fetched.notes)
        assertEquals(LocalDate.of(2026, 10, 6), fetched.dueDate)
        assertEquals(
            listOf("Find the books" to false, "Bring a bag" to true),
            fetched.steps.map {
                it.title to it.done
            }
        )
        assertFalse(fetched.completed)
    }

    @Test
    fun patchChangesOnlyTheGivenFields() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(
            list.id,
            TaskDraft("Buy milk", notes = "Oat", dueDate = LocalDate.of(2026, 10, 5))
        )

        val updated = provider.updateTask(list.id, task.id, TaskPatch(title = "Buy oat milk"))

        assertEquals("Buy oat milk", updated.title)
        assertEquals("Oat", updated.notes)
        assertEquals(LocalDate.of(2026, 10, 5), updated.dueDate)
    }

    @Test
    fun clearsOptionalFields() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(
            list.id,
            TaskDraft("Buy milk", notes = "Oat", dueDate = LocalDate.of(2026, 10, 5))
        )

        val updated = provider.updateTask(list.id, task.id, TaskPatch(notes = Patch.Clear, dueDate = Patch.Clear))

        assertNull(updated.dueDate)
        assertTrue(updated.notes.isNullOrEmpty())
    }

    @Test
    fun completesAndUncompletes() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(list.id, TaskDraft("Buy milk"))

        val done = provider.updateTask(list.id, task.id, TaskPatch(completed = true))
        assertTrue(done.completed)

        val reopened = provider.updateTask(list.id, task.id, TaskPatch(completed = false))
        assertFalse(reopened.completed)
    }

    @Test
    fun stepsRoundTrip() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(list.id, TaskDraft("Pack", steps = listOf(StepDraft("Socks"))))
        val sock = task.steps.single()

        val updated = provider.updateTask(
            list.id,
            task.id,
            TaskPatch(steps = listOf(StepPatch.Update(sock.id, done = true), StepPatch.Add("Shoes")))
        )

        assertEquals(listOf("Socks" to true, "Shoes" to false), updated.steps.map { it.title to it.done })
    }

    @Test
    fun incrementalChangesReturnOnlyEditsAfterTheCursor() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val keep = provider.createTask(list.id, TaskDraft("Unchanged"))
        val edit = provider.createTask(list.id, TaskDraft("Will change"))
        val cursor = provider.drain(list.id, null).second

        editRemotely(provider, list.id, edit.id, TaskPatch(title = "Changed on the web"))
        val (changes, _) = provider.drain(list.id, cursor)

        assertEquals(listOf("Changed on the web"), changes.map { it.title })
        assertTrue(changes.none { it.id == keep.id })
    }

    @Test
    fun deletedTasksAppearInDeletedIds() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(list.id, TaskDraft("Gone soon"))
        val cursor = provider.drain(list.id, null).second

        provider.deleteTask(list.id, task.id)
        var page = provider.getTaskChanges(list.id, cursor)
        val deleted = page.deletedIds.toMutableList()
        while (page.hasMore) {
            page = provider.getTaskChanges(list.id, page.nextCursor)
            deleted += page.deletedIds
        }

        assertEquals(listOf(task.id), deleted)
    }

    @Test
    fun openTasksLeaveOutCompletedOnesAndKeepSteps() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val open = provider.createTask(list.id, TaskDraft("Pack", steps = listOf(StepDraft("Socks"))))
        val done = provider.createTask(list.id, TaskDraft("Old"))
        provider.updateTask(list.id, done.id, TaskPatch(completed = true))

        // Optional: a provider that cannot filter by status returns null and is fetched in full.
        val tasks = provider.getOpenTasks(list.id) ?: return@runTest

        assertEquals(listOf(open.id), tasks.map { it.id })
        assertEquals(listOf("Socks"), tasks.single().steps.map { it.title })
    }

    @Test
    fun missingTaskIsNotFound() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        assertThrows<ProviderError.NotFound> {
            provider.updateTask(list.id, "does-not-exist", TaskPatch(title = "x"))
        }
    }

    @Test
    fun importanceFollowsCapabilities() = runTest {
        val provider = newProvider()
        val list = provider.createList("Errands")
        val task = provider.createTask(list.id, TaskDraft("Star me"))

        val updated = provider.updateTask(list.id, task.id, TaskPatch(important = true))

        assertEquals(provider.capabilities.importance, updated.important)
    }

    private suspend fun TaskProvider.allTasks(listId: String): List<RemoteTask> = drain(listId, null).first

    private suspend fun TaskProvider.drain(listId: String, cursor: String?): Pair<List<RemoteTask>, String> {
        val tasks = mutableListOf<RemoteTask>()
        var page = getTaskChanges(listId, cursor)
        tasks += page.changed
        while (page.hasMore) {
            page = getTaskChanges(listId, page.nextCursor)
            tasks += page.changed
        }
        return tasks to page.nextCursor
    }
}
