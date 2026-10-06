package app.duenorth.tasks.data.repo

import androidx.room.withTransaction
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.ListKey
import app.duenorth.tasks.data.db.ListSummary
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.data.order.OrderKeys
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
    /**
     * True when the connected service stores order (Google Tasks): moves are then queued for
     * sync. When it doesn't (Microsoft To Do), order lives only in the phone's keys.
     */
    private val serviceStoresOrder: suspend () -> Boolean = { true },
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

    fun listKeys(): Flow<List<ListKey>> = lists.observeKeys()

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

    /** [id] lets the screen show the task under its final id before this write commits. */
    suspend fun createTask(
        listId: String,
        title: String,
        notes: String? = null,
        dueDate: LocalDate? = null,
        id: String = newId()
    ): String {
        val cleanTitle = Validation.taskTitle(title)
        val cleanNotes = Validation.notes(notes)
        write {
            requireList(listId)
            tasks.insert(
                TaskEntity(
                    localId = id,
                    listId = listId,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueDate = dueDate,
                    position = topKey(listId),
                    localUpdatedAt = now()
                )
            )
            outbox.enqueue(EntityType.TASK, id, OperationKind.CREATE, now())
        }
        return id
    }

    /**
     * Adds [task] with its steps to the top of [listId] in one write, like a typed task (spec 004
     * FR-322, FR-323). [id] lets the screen show it before the write commits.
     */
    suspend fun createTaskWithSteps(listId: String, task: NewTask, id: String = newId()): String {
        val clean = task.cleaned()
        write {
            requireList(listId)
            insertNew(listId, clean, id, now())
        }
        return id
    }

    /**
     * Makes a list holding [tasks], first one on top, in one write (spec 004 FR-320, FR-323). The
     * tasks are queued last first: Google puts each created task at the top, so the first one
     * must arrive last to end up first there too.
     */
    suspend fun createListWithTasks(title: String, tasks: List<NewTask>, id: String = newId()): String {
        val cleanTitle = Validation.listTitle(title)
        val clean = tasks.map { it.cleaned() }
        require(clean.size <= Validation.MAX_TEMPLATE_TASKS) {
            "A list template has at most ${Validation.MAX_TEMPLATE_TASKS} tasks"
        }
        write {
            val at = now()
            lists.insert(TaskListEntity(localId = id, title = cleanTitle, localUpdatedAt = at))
            outbox.enqueue(EntityType.LIST, id, OperationKind.CREATE, at)
            // Unsynced tasks with no key sort newest first, so each later one gets a later time.
            clean.asReversed().forEachIndexed { index, task ->
                insertNew(id, task, newId(), at.minusMillis((clean.size - 1 - index).toLong()))
            }
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
            updated = updated.copy(listId = it, position = topKey(it))
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

    /**
     * Deletes every completed task in [listId] in one write and returns how many went. Each is
     * queued as its own delete, so a refusal (a shared list) puts back only that task. The list
     * remembers when, so completed tasks a sync brings in later, if they were done by then, go too.
     */
    suspend fun clearCompleted(listId: String): Int {
        var cleared = 0
        write {
            val list = requireList(listId)
            val at = now()
            val done = tasks.completedInList(listId)
            // The task deletes take their steps with them, so the steps' own queued changes are moot.
            done.chunked(CHUNK).forEach { chunk ->
                outbox.drop(EntityType.STEP, steps.idsForTasks(chunk.map { it.localId }))
            }
            done.forEach { task ->
                when (outbox.enqueue(EntityType.TASK, task.localId, OperationKind.DELETE, at)) {
                    Outbox.Result.CANCELLED -> tasks.delete(task.localId)
                    else -> tasks.update(task.copy(deletedLocally = true, localUpdatedAt = at))
                }
            }
            lists.update(list.copy(clearedCompletedAt = at))
            cleared = done.size
        }
        return cleared
    }

    /**
     * Puts task [localId] after [afterId] and before [beforeId], its new neighbors in "my order"
     * (null for the top or the bottom). Writes only this task's key; queues a `MOVE` when the
     * service stores order (spec 003, FR-220, FR-221).
     */
    suspend fun moveTask(localId: String, afterId: String?, beforeId: String?) = write {
        val task = requireTask(localId)
        val stored = serviceStoresOrder()
        if (!stored) giveKeysToUnkeyed(task.listId)
        val after = afterId?.let { tasks.get(it) }
        val before = beforeId?.let { tasks.get(it) }
        var updatedAt = if (stored) now() else task.localUpdatedAt
        val position: String? = when {
            // Google's first position can be all zeros; no key, newest edit, is the top there.
            after == null && stored -> null
            after == null -> OrderKeys.between(null, before?.position) ?: OrderKeys.between(null, null)
            after.position == null && before != null && before.position == null -> {
                // Both neighbors are unsynced tasks with no key yet: sit between their edit times.
                updatedAt = Instant.ofEpochMilli(
                    (after.localUpdatedAt.toEpochMilli() + before.localUpdatedAt.toEpochMilli()) / 2
                )
                null
            }
            after.position == null -> OrderKeys.between(null, before?.position)
            else -> OrderKeys.between(after.position, before?.position) ?: (after.position + "5")
        }
        if (position == task.position && updatedAt == task.localUpdatedAt) return@write
        tasks.update(task.copy(position = position, localUpdatedAt = updatedAt))
        if (stored) outbox.enqueue(EntityType.TASK, localId, OperationKind.MOVE, now())
    }

    // Steps

    /**
     * Puts task [taskId]'s steps in [order] (step ids; ones not named keep their place after
     * them). Renumbers at most 100 rows; queues a `MOVE` for [movedId] when the service stores
     * order (FR-222).
     */
    suspend fun moveStep(taskId: String, movedId: String, order: List<String>) = write {
        requireTask(taskId)
        val current = steps.forTask(taskId)
        val byId = current.associateBy { it.localId }
        val named = order.mapNotNull { byId[it] }
        val rest = current.filter { it.localId !in order.toSet() }
        (named + rest).forEachIndexed { index, step ->
            if (step.sortOrder != index) steps.update(step.copy(sortOrder = index))
        }
        if (serviceStoresOrder()) outbox.enqueue(EntityType.STEP, movedId, OperationKind.MOVE, now())
        touchTask(taskId)
    }

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

    /** Inserts a validated new task and its steps at the top of [listId] and queues their creates. */
    private suspend fun insertNew(listId: String, task: NewTask, id: String, at: Instant) {
        tasks.insert(
            TaskEntity(
                localId = id,
                listId = listId,
                title = task.title,
                notes = task.notes,
                dueDate = task.dueDate,
                important = task.important,
                position = topKey(listId),
                localUpdatedAt = at
            )
        )
        outbox.enqueue(EntityType.TASK, id, OperationKind.CREATE, at)
        task.steps.forEachIndexed { index, title ->
            val stepId = newId()
            steps.insert(StepEntity(localId = stepId, taskId = id, title = title, sortOrder = index))
            outbox.enqueue(EntityType.STEP, stepId, OperationKind.CREATE, at)
        }
    }

    private fun now(): Instant = clock.instant()

    /**
     * Tasks saved before order keys existed (Microsoft To Do) have none and sort first by their
     * last edit. Gives them keys in that same order, above the keyed ones, so nothing moves.
     */
    private suspend fun giveKeysToUnkeyed(listId: String) {
        val open = tasks.openInList(listId).sortedWith(OrderKeys.taskComparator)
        val unkeyed = open.takeWhile { it.position == null }
        if (unkeyed.isEmpty()) return
        val keys = OrderKeys.keysBefore(open.getOrNull(unkeyed.size)?.position, unkeyed.size)
        unkeyed.zip(keys).forEach { (task, key) -> tasks.update(task.copy(position = key)) }
    }

    /**
     * The order key for a task going to the top of [listId]: none where the service keeps order
     * (no key sorts first until the service answers with its position), else one above the first.
     */
    private suspend fun topKey(listId: String): String? =
        if (serviceStoresOrder()) null else OrderKeys.between(null, tasks.firstPosition(listId))

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

/** A task to create with its steps, as a template makes it (spec 004). */
data class NewTask(
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val important: Boolean = false,
    val steps: List<String> = emptyList()
) {
    internal fun cleaned(): NewTask {
        require(steps.size <= Validation.MAX_STEPS) { "A task has at most ${Validation.MAX_STEPS} steps" }
        return copy(
            title = Validation.taskTitle(title),
            notes = Validation.notes(notes),
            steps = steps.map(Validation::stepTitle)
        )
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
