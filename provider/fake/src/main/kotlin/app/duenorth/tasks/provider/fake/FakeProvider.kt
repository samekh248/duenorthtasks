package app.duenorth.tasks.provider.fake

import app.duenorth.tasks.provider.api.AccountInfo
import app.duenorth.tasks.provider.api.Assignment
import app.duenorth.tasks.provider.api.ListPatch
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteStep
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskChangePage
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [TaskProvider] for the debug demo account and for tests.
 *
 * Behaves like a remote service: ids are server-assigned, every write bumps a version used as the
 * change cursor, deletes are reported once through [TaskChangePage.deletedIds], and unknown fields
 * survive a PATCH. Tests can add [latency], queue failures with [failNext] and inspect [calls].
 */
class FakeProvider(
    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(importance = true, manualOrder = true, dueTime = false),
    private val clock: Clock = Clock.systemUTC(),
    private val pageSize: Int = 100
) : TaskProvider {
    override val kind = ProviderKind.FAKE

    /** Simulated network time for every call. */
    var latency: Duration = Duration.ZERO

    /** Every call made, by method name, in order. */
    val calls: List<String> get() = callLog.toList()

    private val mutex = Mutex()
    private val callLog = mutableListOf<String>()
    private val failures = ArrayDeque<ProviderError>()
    private val lists = linkedMapOf<String, RemoteList>()
    private val tasks = linkedMapOf<String, StoredTask>()
    private val tombstones = mutableListOf<Tombstone>()
    private var version = 0L
    private var nextId = 1L
    private var signedIn = false

    private data class StoredTask(val task: RemoteTask, val version: Long)

    private data class Tombstone(val listId: String, val id: String, val version: Long)

    private val lagging = mutableSetOf<String>()

    /**
     * Leaves task [id] out of full fetches (no cursor) until [catchUp], like a service whose
     * listings lag a moment behind its writes.
     */
    fun lagFullListing(id: String) {
        lagging += id
    }

    fun catchUp() = lagging.clear()

    private val undeletable = mutableSetOf<String>()

    /** Deleting task [id] is refused from now on, as on a list someone else owns. */
    fun refuseDelete(id: String) {
        undeletable += id
    }

    /** The next call fails with [error] instead of running. */
    fun failNext(error: ProviderError) {
        failures.addLast(error)
    }

    /** Seeds data as if it had been created on the web; returns the created list. */
    suspend fun seed(title: String, tasks: List<TaskDraft> = emptyList()): RemoteList {
        val list = createList(title)
        tasks.forEach { createTask(list.id, it) }
        return list
    }

    /** Shares or unshares a list as if done in the service's own app (spec 002). */
    suspend fun setSharing(listId: String, isShared: Boolean, isOwner: Boolean = true): RemoteList = mutex.withLock {
        val list = lists[listId] ?: throw ProviderError.NotFound(listId)
        list.copy(isShared = isShared, isOwner = isOwner, etag = etag(), updatedAt = now()).also { lists[listId] = it }
    }

    /** Marks a task as assigned to the user from elsewhere, as Google Docs or Chat would. */
    suspend fun assign(listId: String, id: String, assignment: Assignment?): RemoteTask = mutex.withLock {
        val stored = tasks[id]?.takeIf { it.task.listId == listId } ?: throw ProviderError.NotFound(id)
        val task = stored.task.copy(assignment = assignment, etag = etag(), updatedAt = now())
        tasks[id] = StoredTask(task, ++version)
        task
    }

    /** Changes a task as if edited on the web, without going through the app. */
    suspend fun editRemotely(listId: String, id: String, patch: TaskPatch): RemoteTask = updateTask(listId, id, patch)

    override suspend fun signIn(host: SignInHost): AccountInfo = call("signIn") {
        signedIn = true
        AccountInfo(id = "demo", displayName = "Demo account", email = null)
    }

    override suspend fun signOut() = call("signOut") { signedIn = false }

    override suspend fun getLists(): List<RemoteList> = call("getLists") { lists.values.toList() }

    override suspend fun createList(title: String): RemoteList = call("createList") {
        val list = RemoteList(newId("list"), title, isDefault = lists.isEmpty(), etag = etag(), updatedAt = now())
        lists[list.id] = list
        list
    }

    override suspend fun updateList(id: String, patch: ListPatch): RemoteList = call("updateList") {
        val list = lists[id] ?: throw ProviderError.NotFound(id)
        val updated = list.copy(title = patch.title ?: list.title, etag = etag(), updatedAt = now())
        lists[id] = updated
        updated
    }

    override suspend fun deleteList(id: String) = call("deleteList") {
        lists.remove(id) ?: throw ProviderError.NotFound(id)
        tasks.values.removeAll { it.task.listId == id }
        Unit
    }

    override suspend fun getTaskChanges(listId: String, cursor: String?): TaskChangePage = call("getTaskChanges") {
        if (listId !in lists) throw ProviderError.NotFound(listId)
        val since = cursor?.let { parseCursor(listId, it) } ?: -1L
        val changed = tasks.values
            .filter { it.task.listId == listId && it.version > since }
            .filter { cursor != null || it.task.id !in lagging }
            .sortedBy { it.version }
        val page = changed.take(pageSize)
        val hasMore = changed.size > page.size
        val upTo = if (hasMore) page.last().version else version
        val deleted = if (cursor == null) {
            emptyList()
        } else {
            tombstones.filter { it.listId == listId && it.version in (since + 1)..upTo }.map { it.id }
        }
        TaskChangePage(page.map { it.task }, deleted, "v:$upTo", hasMore)
    }

    override suspend fun getOpenTasks(listId: String): List<RemoteTask> = call("getOpenTasks") {
        if (listId !in lists) throw ProviderError.NotFound(listId)
        tasks.values.map { it.task }.filter { it.listId == listId && !it.completed }
    }

    override suspend fun createTask(listId: String, draft: TaskDraft): RemoteTask = call("createTask") {
        if (listId !in lists) throw ProviderError.NotFound(listId)
        val task = RemoteTask(
            id = newId("task"),
            listId = listId,
            title = draft.title,
            notes = draft.notes,
            dueDate = draft.dueDate,
            completed = false,
            completedAt = null,
            important = draft.important && capabilities.importance,
            position = "%010d".format(nextId),
            rawStatus = "open",
            steps = draft.steps.map { RemoteStep(newId("step"), it.title, it.done) },
            etag = etag(),
            updatedAt = now()
        )
        store(task)
    }

    override suspend fun updateTask(listId: String, id: String, patch: TaskPatch): RemoteTask = call("updateTask") {
        val current = tasks[id]?.task?.takeIf { it.listId == listId } ?: throw ProviderError.NotFound(id)
        val completed = patch.completed ?: current.completed
        val updated = current.copy(
            title = patch.title ?: current.title,
            notes = patch.notes.applyTo(current.notes),
            dueDate = patch.dueDate.applyTo(current.dueDate),
            completed = completed,
            completedAt = when {
                patch.completed == null -> current.completedAt
                completed -> now()
                else -> null
            },
            important = if (capabilities.importance) patch.important ?: current.important else false,
            rawStatus = if (patch.completed == null) {
                current.rawStatus
            } else if (completed) {
                "completed"
            } else {
                "open"
            },
            steps = patch.steps?.let { applySteps(current.steps, it) } ?: current.steps,
            etag = etag(),
            updatedAt = now()
        )
        store(updated)
    }

    override suspend fun moveTask(listId: String, id: String, afterId: String?) = call("moveTask") {
        val current = tasks[id]?.task?.takeIf { it.listId == listId } ?: throw ProviderError.NotFound(id)
        if (!capabilities.manualOrder) return@call
        // Like Google: siblings in position order, this one placed after [afterId] (first when null).
        val siblings = tasks.values.map { it.task }
            .filter { it.listId == listId && it.id != id }
            .sortedBy { it.position.orEmpty() }
            .toMutableList()
        val at = afterId?.let { after -> siblings.indexOfFirst { it.id == after } + 1 } ?: 0
        siblings.add(at, current)
        siblings.forEachIndexed { index, task ->
            val position = "%010d".format((index + 1) * 10L)
            if (task.id == id) {
                store(task.copy(position = position, etag = etag(), updatedAt = now()))
            } else if (task.position != position) {
                store(task.copy(position = position))
            }
        }
        Unit
    }

    override suspend fun deleteTask(listId: String, id: String) = call("deleteTask") {
        tasks[id]?.task?.takeIf { it.listId == listId } ?: throw ProviderError.NotFound(id)
        if (id in undeletable) throw ProviderError.NotAllowed(id)
        tasks.remove(id)
        tombstones += Tombstone(listId, id, ++version)
        Unit
    }

    private suspend fun <T> call(name: String, block: () -> T): T {
        if (latency > Duration.ZERO) delay(latency)
        return mutex.withLock {
            callLog += name
            failures.removeFirstOrNull()?.let { throw it }
            block()
        }
    }

    private fun store(task: RemoteTask): RemoteTask {
        tasks[task.id] = StoredTask(task, ++version)
        return task
    }

    private fun applySteps(steps: List<RemoteStep>, patches: List<StepPatch>): List<RemoteStep> {
        val result = steps.toMutableList()
        for (patch in patches) {
            when (patch) {
                is StepPatch.Add -> result += RemoteStep(newId("step"), patch.title, patch.done)
                is StepPatch.Remove -> result.removeAll { it.id == patch.id }
                is StepPatch.Move -> {
                    if (!capabilities.manualOrder) continue
                    val index = result.indexOfFirst { it.id == patch.id }
                    if (index < 0) throw ProviderError.NotFound(patch.id)
                    val step = result.removeAt(index)
                    val at = patch.afterId?.let { after -> result.indexOfFirst { it.id == after } + 1 } ?: 0
                    result.add(at, step)
                }
                is StepPatch.Update -> {
                    val index = result.indexOfFirst { it.id == patch.id }
                    if (index < 0) throw ProviderError.NotFound(patch.id)
                    val step = result[index]
                    result[index] = step.copy(title = patch.title ?: step.title, done = patch.done ?: step.done)
                }
            }
        }
        return result
    }

    private fun parseCursor(listId: String, cursor: String): Long =
        cursor.removePrefix("v:").toLongOrNull() ?: throw ProviderError.CursorExpired(listId)

    private fun <T> Patch<T>?.applyTo(current: T?): T? = when (this) {
        null -> current
        is Patch.Set -> value
        Patch.Clear -> null
    }

    private fun newId(prefix: String) = "$prefix-${nextId++}"

    private fun etag() = "\"${version + 1}\""

    private fun now(): Instant = clock.instant()
}
