package app.duenorth.tasks.data.repo

import androidx.room.withTransaction
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.ListSummary
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.provider.api.Patch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The app's only way to read or change tasks (constitution Principle IV).
 *
 * Reads are Room [Flow]s, so the screen updates the moment a write commits. Every write saves the
 * entity and its outbox operation in one transaction, then emits on [localEdits] so the sync
 * scheduler can push it. Nothing here touches the network.
 */
class TaskRepository(
    private val db: DueNorthDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val accounts = db.accountDao()
    private val lists = db.taskListDao()
    private val tasks = db.taskDao()
    private val steps = db.stepDao()
    private val outbox = Outbox(db.pendingOperationDao())

    private val edits = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits after every local write; the sync scheduler debounces it. */
    val localEdits: SharedFlow<Unit> = edits.asSharedFlow()

    // Reads

    val account: Flow<AccountEntity?> = accounts.observe()

    val pendingCount: Flow<Int> = db.pendingOperationDao().observeCount()

    fun listSummaries(): Flow<List<ListSummary>> = lists.observeSummaries()

    fun list(localId: String): Flow<TaskListEntity?> = lists.observe(localId)

    fun openTasks(listId: String): Flow<List<TaskWithList>> = tasks.observeOpenInList(listId)

    fun completedTasks(listId: String): Flow<List<TaskWithList>> = tasks.observeCompletedInList(listId)

    /** Open tasks due on or before [lastDay] in every list (overdue, today, tomorrow). */
    fun tasksDueBy(lastDay: LocalDate): Flow<List<TaskWithList>> = tasks.observeDueBy(lastDay.toEpochDay())

    fun recentlyCompleted(limit: Int = 50): Flow<List<TaskWithList>> = tasks.observeRecentlyCompleted(limit)

    fun task(localId: String): Flow<TaskEntity?> = tasks.observe(localId)

    fun steps(taskId: String): Flow<List<StepEntity>> = steps.observeForTask(taskId)

    /** Tasks whose title or details contain [query], ignoring case; empty for a blank query. */
    fun search(query: String, limit: Int = 200): Flow<List<TaskWithList>> {
        val q = query.trim()
        if (q.isEmpty()) return flowOf(emptyList())
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return tasks.observeSearch("%$escaped%", limit)
    }

    /** The default list, created as "Tasks" when the account has no lists yet. */
    suspend fun defaultListIdOrCreate(): String = defaultListId() ?: createList(DEFAULT_LIST_TITLE, isDefault = true)

    suspend fun defaultListId(): String? = lists.defaultList()?.localId

    // Lists

    suspend fun createList(title: String, isDefault: Boolean = false): String {
        val clean = Validation.listTitle(title)
        val id = newId()
        write {
            lists.insert(TaskListEntity(localId = id, title = clean, isDefault = isDefault, localUpdatedAt = now()))
            outbox.enqueue(EntityType.LIST, id, OperationKind.CREATE, now())
        }
        return id
    }

    suspend fun renameList(localId: String, title: String) {
        val clean = Validation.listTitle(title)
        write {
            val list = requireList(localId)
            if (list.title == clean) return@write
            lists.update(list.copy(title = clean, localUpdatedAt = now()))
            outbox.enqueue(EntityType.LIST, localId, OperationKind.UPDATE, now(), setOf(Fields.TITLE))
        }
    }

    /** Deleting a list deletes its tasks too, here and (once pushed) remotely. */
    suspend fun deleteList(localId: String) = write {
        val list = requireList(localId)
        val taskIds = tasks.idsInList(localId)
        // The remote list delete takes its tasks with it, so their own queued changes are moot.
        outbox.drop(EntityType.STEP, taskIds.chunked(CHUNK).flatMap { steps.idsForTasks(it) })
        outbox.drop(EntityType.TASK, taskIds)
        when (outbox.enqueue(EntityType.LIST, localId, OperationKind.DELETE, now())) {
            Outbox.Result.CANCELLED -> lists.delete(localId)
            else -> {
                lists.update(list.copy(deletedLocally = true, localUpdatedAt = now()))
                tasks.markDeletedInList(localId)
            }
        }
    }

    // Tasks

    suspend fun createTask(listId: String, title: String, notes: String? = null, dueDate: LocalDate? = null): String {
        val cleanTitle = Validation.taskTitle(title)
        val cleanNotes = Validation.notes(notes)
        val id = newId()
        write {
            requireList(listId)
            tasks.insert(
                TaskEntity(
                    localId = id,
                    listId = listId,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueDate = dueDate,
                    localUpdatedAt = now()
                )
            )
            outbox.enqueue(EntityType.TASK, id, OperationKind.CREATE, now())
        }
        return id
    }

    /** Applies only the non-null parts of [edit]; unchanged values queue nothing. */
    suspend fun editTask(localId: String, edit: TaskEdit) = write {
        val task = requireTask(localId)
        var updated = task
        val fields = mutableSetOf<String>()
        edit.title?.let { Validation.taskTitle(it) }?.takeIf { it != task.title }?.let {
            updated = updated.copy(title = it)
            fields += Fields.TITLE
        }
        edit.notes?.let { patch ->
            val notes = when (patch) {
                is Patch.Set -> Validation.notes(patch.value)
                Patch.Clear -> null
            }
            if (notes != task.notes) {
                updated = updated.copy(notes = notes)
                fields += Fields.NOTES
            }
        }
        edit.dueDate?.let { patch ->
            val due = when (patch) {
                is Patch.Set -> patch.value
                Patch.Clear -> null
            }
            if (due != task.dueDate) {
                updated = updated.copy(dueDate = due)
                fields += Fields.DUE_DATE
            }
        }
        edit.important?.takeIf { it != task.important }?.let {
            updated = updated.copy(important = it)
            fields += Fields.IMPORTANT
        }
        edit.listId?.takeIf { it != task.listId }?.let {
            requireList(it)
            updated = updated.copy(listId = it, position = null)
            fields += Fields.LIST
        }
        if (fields.isEmpty()) return@write
        tasks.update(updated.copy(localUpdatedAt = now()))
        outbox.enqueue(EntityType.TASK, localId, OperationKind.UPDATE, now(), fields)
    }

    suspend fun setCompleted(localId: String, completed: Boolean) = write {
        val task = requireTask(localId)
        if (task.completed == completed) return@write
        tasks.update(
            task.copy(
                completed = completed,
                completedAt = if (completed) now() else null,
                localUpdatedAt = now()
            )
        )
        outbox.enqueue(EntityType.TASK, localId, OperationKind.UPDATE, now(), setOf(Fields.COMPLETED))
    }

    suspend fun deleteTask(localId: String) = write {
        val task = requireTask(localId)
        outbox.drop(EntityType.STEP, steps.idsForTasks(listOf(localId)))
        when (outbox.enqueue(EntityType.TASK, localId, OperationKind.DELETE, now())) {
            Outbox.Result.CANCELLED -> tasks.delete(localId)
            else -> tasks.update(task.copy(deletedLocally = true, localUpdatedAt = now()))
        }
    }

    // Steps

    suspend fun addStep(taskId: String, title: String): String {
        val clean = Validation.stepTitle(title)
        val id = newId()
        write {
            requireTask(taskId)
            check(steps.count(taskId) < Validation.MAX_STEPS) { "A task has at most ${Validation.MAX_STEPS} steps" }
            val order = steps.maxOrder(taskId) + 1
            steps.insert(StepEntity(localId = id, taskId = taskId, title = clean, sortOrder = order))
            outbox.enqueue(EntityType.STEP, id, OperationKind.CREATE, now())
            touchTask(taskId)
        }
        return id
    }

    suspend fun editStep(localId: String, title: String? = null, done: Boolean? = null) = write {
        val step = requireStep(localId)
        var updated = step
        val fields = mutableSetOf<String>()
        title?.let { Validation.stepTitle(it) }?.takeIf { it != step.title }?.let {
            updated = updated.copy(title = it)
            fields += Fields.TITLE
        }
        done?.takeIf { it != step.done }?.let {
            updated = updated.copy(done = it)
            fields += Fields.DONE
        }
        if (fields.isEmpty()) return@write
        steps.update(updated)
        outbox.enqueue(EntityType.STEP, localId, OperationKind.UPDATE, now(), fields)
        touchTask(step.taskId)
    }

    suspend fun removeStep(localId: String) = write {
        val step = requireStep(localId)
        when (outbox.enqueue(EntityType.STEP, localId, OperationKind.DELETE, now())) {
            Outbox.Result.CANCELLED -> steps.delete(localId)
            else -> steps.update(step.copy(deletedLocally = true))
        }
        touchTask(step.taskId)
    }

    // Helpers

    private fun now(): Instant = clock.instant()

    private suspend fun write(block: suspend () -> Unit) {
        db.withTransaction { block() }
        edits.tryEmit(Unit)
    }

    /** Steps have no timestamp of their own; the conflict rule compares the task's. */
    private suspend fun touchTask(taskId: String) {
        tasks.get(taskId)?.let { tasks.update(it.copy(localUpdatedAt = now())) }
    }

    private suspend fun requireList(localId: String): TaskListEntity =
        lists.get(localId)?.takeUnless { it.deletedLocally } ?: throw NoSuchElementException("No list $localId")

    private suspend fun requireTask(localId: String): TaskEntity =
        tasks.get(localId)?.takeUnless { it.deletedLocally } ?: throw NoSuchElementException("No task $localId")

    private suspend fun requireStep(localId: String): StepEntity =
        steps.get(localId)?.takeUnless { it.deletedLocally } ?: throw NoSuchElementException("No step $localId")

    private companion object {
        const val CHUNK = 900
        const val DEFAULT_LIST_TITLE = "Tasks"
    }
}

/** A change to a task's fields; null means "leave as is". */
data class TaskEdit(
    val title: String? = null,
    val notes: Patch<String>? = null,
    val dueDate: Patch<LocalDate>? = null,
    val important: Boolean? = null,
    val listId: String? = null
)
