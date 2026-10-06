package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.PendingOperationEntity
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.provider.api.ListPatch
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.StepDraft
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.sync.SyncStore.Companion.toJson
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Pushes the outbox in `seq` order (research R9). Each confirmed operation is removed in the same
 * transaction that records the remote id or etag, so a crash never pushes it twice.
 *
 * Edits the user makes while a push is in flight are kept: before an operation is dropped, the
 * values it sent are compared with what is in the database now, and anything that changed again
 * stays queued.
 */
internal class Pusher(private val store: SyncStore, private val provider: TaskProvider) {
    private val canStoreImportance = provider.capabilities.importance
    private val storesOrder = provider.capabilities.manualOrder

    /**
     * Pushes everything it can and returns true when some operation had to wait for another.
     * Throws the [ProviderError] that should end this sync.
     */
    suspend fun pushAll(): Boolean {
        var waited = false
        for (snapshot in store.ops.all()) {
            val op = store.sync.operation(snapshot.seq) ?: continue // settled by an earlier push
            try {
                push(op)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Deferred) {
                waited = true
            } catch (e: ProviderError.NotFound) {
                notFound(op, e)
            } catch (_: ProviderError.NotAllowed) {
                notAllowed(op)
            } catch (e: ProviderError.Conflict) {
                // The pull that follows applies the conflict rule; give up on it if it keeps coming back.
                countFailure(op, e)
            } catch (e: ProviderError.Transient) {
                if (e.cause !is IOException) countFailure(op, e)
                throw e
            } catch (e: ProviderError) {
                throw e
            } catch (e: Exception) {
                countFailure(op, e)
                throw ProviderError.Transient("Push failed: ${e.message}", e)
            }
        }
        return waited
    }

    private suspend fun push(op: PendingOperationEntity) = when (op.entity) {
        EntityType.LIST -> pushList(op)
        EntityType.TASK -> pushTask(op)
        EntityType.STEP -> pushStep(op)
    }

    // Lists

    private suspend fun pushList(op: PendingOperationEntity) {
        val list = store.lists.get(op.entityLocalId) ?: return store.ops.delete(op.seq)
        when (op.kind) {
            OperationKind.CREATE -> {
                if (list.remoteId != null) return store.ops.delete(op.seq)
                val known = store.sync.allLists().mapNotNull { it.remoteId }.toSet()
                // A create whose answer was lost may have worked: look before making a second one (FR-022).
                val remote = store.journal.sentTitle(list.localId)?.let { sent ->
                    provider.getLists().firstOrNull { it.title == sent && it.id !in known }
                } ?: run {
                    store.journal.record(list.localId, list.title)
                    provider.createList(list.title)
                }
                store.transaction {
                    val now = store.lists.get(list.localId) ?: return@transaction
                    store.lists.update(
                        now.copy(remoteId = remote.id, etag = remote.etag, remoteUpdatedAt = remote.updatedAt)
                    )
                    if (now.title == remote.title) {
                        store.ops.delete(op.seq)
                    } else {
                        store.ops.update(op.copy(kind = OperationKind.UPDATE, changedFields = setOf(Fields.TITLE)))
                    }
                }
                store.journal.clear(list.localId)
            }
            OperationKind.UPDATE -> {
                val remoteId = list.remoteId ?: return store.ops.delete(op.seq)
                val remote = provider.updateList(remoteId, ListPatch(title = list.title))
                store.transaction {
                    val now = store.lists.get(list.localId) ?: return@transaction
                    store.lists.update(now.copy(etag = remote.etag, remoteUpdatedAt = remote.updatedAt))
                    if (now.title == list.title) store.ops.delete(op.seq)
                }
            }
            OperationKind.DELETE -> {
                list.remoteId?.let { provider.deleteList(it) }
                finishListDelete(list.localId)
            }
            OperationKind.MOVE -> store.ops.delete(op.seq)
        }
    }

    private suspend fun finishListDelete(localId: String) = store.transaction {
        store.sync.tasksInList(localId).forEach {
            store.dropStepOps(it.localId)
            store.dropOps(EntityType.TASK, it.localId)
        }
        store.dropOps(EntityType.LIST, localId)
        store.lists.delete(localId)
    }

    // Tasks

    private suspend fun pushTask(op: PendingOperationEntity) {
        val task = store.tasks.get(op.entityLocalId) ?: return store.ops.delete(op.seq)
        when (op.kind) {
            OperationKind.CREATE -> createTask(op, task)
            OperationKind.UPDATE -> updateTask(op, task)
            OperationKind.MOVE -> moveTask(op, task)
            OperationKind.DELETE -> deleteTasks(op, task)
        }
    }

    /**
     * Deletes [task] together with the other deletes queued in its list, up to [DELETES_AT_ONCE],
     * so clearing a list's completed tasks costs a few requests instead of one per task. Each one
     * done is settled as soon as the service answers; a refused one is put back on its own.
     */
    private suspend fun deleteTasks(op: PendingOperationEntity, task: TaskEntity) {
        val remoteId = task.remoteId
        val listRemoteId = store.lists.get(task.listId)?.remoteId
        if (remoteId == null || listRemoteId == null) return finishTaskDelete(task.localId)
        val batch = mutableListOf(op to task)
        for (other in store.ops.all()) {
            if (batch.size == DELETES_AT_ONCE) break
            if (other.entity != EntityType.TASK || other.kind != OperationKind.DELETE || other.seq == op.seq) continue
            val otherTask = store.tasks.get(other.entityLocalId) ?: continue
            if (otherTask.listId == task.listId && otherTask.remoteId != null) batch += other to otherTask
        }
        val result = provider.deleteTasks(listRemoteId, batch.map { (_, it) -> checkNotNull(it.remoteId) })
        for ((each, eachTask) in batch) {
            when (eachTask.remoteId) {
                in result.deleted -> finishTaskDelete(eachTask.localId)
                in result.refused -> notAllowed(each)
            }
        }
        result.stoppedBy?.let { throw it }
    }

    private suspend fun finishTaskDelete(localId: String) = store.transaction {
        store.dropStepOps(localId)
        store.dropOps(EntityType.TASK, localId)
        store.tasks.delete(localId)
    }

    private suspend fun createTask(op: PendingOperationEntity, task: TaskEntity) {
        if (task.remoteId != null) return store.ops.delete(op.seq)
        val listRemoteId = store.lists.get(task.listId)?.remoteId ?: throw Deferred
        val sentSteps = store.steps.forTask(task.localId).filterNot { it.deletedLocally }

        val known = store.sync.tasksInList(task.listId).mapNotNull { it.remoteId }.toSet()
        val lost = store.journal.sentTitle(task.localId)?.let { findLostCreate(listRemoteId, it, known) }
        if (lost == null) store.journal.record(task.localId, task.title)
        var remote = lost
            ?: provider.createTask(
                listRemoteId,
                TaskDraft(
                    title = task.title,
                    notes = task.notes,
                    dueDate = task.dueDate,
                    important = task.important && canStoreImportance,
                    steps = sentSteps.map { StepDraft(it.title, it.done) }
                )
            )
        // A draft has no "completed": finish it the way the user left it.
        if (task.completed &&
            !remote.completed
        ) {
            remote = provider.updateTask(listRemoteId, remote.id, TaskPatch(completed = true))
        }

        store.transaction {
            val now = store.tasks.get(task.localId) ?: return@transaction
            // A move queued after the create still needs the phone's place to send (spec 003).
            val keepPlace = !storesOrder || store.opsFor(EntityType.TASK, task.localId).any {
                it.kind == OperationKind.MOVE
            }
            store.tasks.update(
                now.copy(
                    remoteId = remote.id,
                    etag = remote.etag,
                    remoteUpdatedAt = remote.updatedAt,
                    position = if (keepPlace) now.position else remote.position
                )
            )
            // Steps sent with the task now exist remotely; pair them with the ids that came back.
            sentSteps.zip(remote.steps).forEach { (sent, created) ->
                val step = store.steps.get(sent.localId) ?: return@forEach
                store.steps.update(step.copy(remoteId = created.id))
                val stepOps = store.opsFor(EntityType.STEP, sent.localId)
                stepOps.filter { it.kind != OperationKind.DELETE }.forEach { store.ops.delete(it.seq) }
                val changed = changedStepFields(sent, step)
                if (changed.isNotEmpty() && stepOps.none { it.kind == OperationKind.DELETE }) {
                    store.enqueue(EntityType.STEP, step.localId, OperationKind.UPDATE, changed)
                }
            }
            // Steps added and removed before the task was ever pushed never existed remotely.
            store.steps.forTask(task.localId).filter { it.deletedLocally && it.remoteId == null }.forEach {
                store.dropOps(EntityType.STEP, it.localId)
                store.steps.delete(it.localId)
            }
            settle(op, sent = task, now = now, sentFields = SyncStore.ALL_TASK_FIELDS + Fields.LIST)
        }
        store.confirmedCreate(remote.id)
        store.journal.clear(task.localId)
    }

    private suspend fun findLostCreate(listRemoteId: String, sentTitle: String, known: Set<String>): RemoteTask? {
        var cursor: String? = null
        do {
            val page = provider.getTaskChanges(listRemoteId, cursor)
            page.changed.firstOrNull { it.title == sentTitle && it.id !in known }?.let { return it }
            cursor = page.nextCursor
        } while (page.hasMore)
        return null
    }

    private suspend fun updateTask(op: PendingOperationEntity, task: TaskEntity) {
        val remoteId = task.remoteId ?: return store.ops.delete(op.seq)
        if (Fields.LIST in op.changedFields) return moveToList(op, task)
        val listRemoteId = store.lists.get(task.listId)?.remoteId ?: throw Deferred
        val fields = op.changedFields
        val patch = TaskPatch(
            title = task.title.takeIf { Fields.TITLE in fields },
            notes = if (Fields.NOTES in fields) task.notes?.let { Patch.Set(it) } ?: Patch.Clear else null,
            dueDate = if (Fields.DUE_DATE in fields) task.dueDate?.let { Patch.Set(it) } ?: Patch.Clear else null,
            completed = task.completed.takeIf { Fields.COMPLETED in fields },
            important = task.important.takeIf { Fields.IMPORTANT in fields && canStoreImportance },
            // Un-completing restores the service's own status (To Do's "in progress" and friends).
            reopenStatus = task.remoteStatusRaw.takeIf { Fields.COMPLETED in fields && !task.completed }
        )
        if (patch.isEmpty) return store.ops.delete(op.seq)
        val remote = provider.updateTask(listRemoteId, remoteId, patch)
        store.transaction {
            val now = store.tasks.get(task.localId) ?: return@transaction
            store.tasks.update(now.copy(etag = remote.etag, remoteUpdatedAt = remote.updatedAt))
            settle(op, sent = task, now = now, sentFields = fields)
        }
    }

    /**
     * Neither service can move a task between lists in one call, so the old copy is deleted and
     * the task is queued to be created in its new list. Deleting first means a failure part-way
     * can never leave two copies.
     */
    private suspend fun moveToList(op: PendingOperationEntity, task: TaskEntity) {
        val remoteId = checkNotNull(task.remoteId)
        val target = store.lists.get(task.listId)?.remoteId
        val others = store.sync.allLists().mapNotNull { it.remoteId }.filter { it != target }
        // The old list is not stored, so look for the copy in each other list.
        for (listRemoteId in others) {
            try {
                provider.deleteTask(listRemoteId, remoteId)
                break
            } catch (_: ProviderError.NotFound) {
            }
        }
        store.transaction {
            val now = store.tasks.get(task.localId) ?: return@transaction
            store.recreate(now)
        }
    }

    private suspend fun moveTask(op: PendingOperationEntity, task: TaskEntity) {
        val remoteId = task.remoteId ?: return waitForCreate(op, EntityType.TASK, task.localId)
        val listRemoteId = store.lists.get(task.listId)?.remoteId ?: throw Deferred
        if (provider.capabilities.manualOrder) {
            val siblings = store.sync.tasksInList(task.listId)
                .filter { !it.deletedLocally && it.remoteId != null && it.localId != task.localId }
                .sortedBy { it.position.orEmpty() }
            val after = siblings.lastOrNull { it.position.orEmpty() < task.position.orEmpty() }?.remoteId
            provider.moveTask(listRemoteId, remoteId, after)
        }
        store.ops.delete(op.seq)
    }

    // Steps

    private suspend fun pushStep(op: PendingOperationEntity) {
        val step = store.steps.get(op.entityLocalId) ?: return store.ops.delete(op.seq)
        val task = store.tasks.get(step.taskId) ?: return store.ops.delete(op.seq)
        // Until its task exists remotely, the step rides along with the task's own create.
        val taskRemoteId = task.remoteId ?: throw Deferred
        val listRemoteId = store.lists.get(task.listId)?.remoteId ?: throw Deferred

        when (op.kind) {
            OperationKind.CREATE -> {
                if (step.remoteId != null) return store.ops.delete(op.seq)
                val known = store.steps.forTask(task.localId).mapNotNull { it.remoteId }.toSet()
                val remote = provider.updateTask(
                    listRemoteId,
                    taskRemoteId,
                    TaskPatch(steps = listOf(StepPatch.Add(step.title, step.done)))
                )
                val created = remote.steps.lastOrNull { it.id !in known }
                store.transaction {
                    recordTask(task, remote)
                    val now = store.steps.get(step.localId) ?: return@transaction
                    store.steps.update(now.copy(remoteId = created?.id))
                    val changed = changedStepFields(step, now)
                    if (changed.isEmpty()) {
                        store.ops.delete(op.seq)
                    } else {
                        store.ops.update(op.copy(kind = OperationKind.UPDATE, changedFields = changed))
                    }
                }
            }
            OperationKind.UPDATE -> {
                val stepRemoteId = step.remoteId ?: return store.ops.delete(op.seq)
                val patch = StepPatch.Update(
                    stepRemoteId,
                    title = step.title.takeIf { Fields.TITLE in op.changedFields },
                    done = step.done.takeIf { Fields.DONE in op.changedFields }
                )
                val remote = provider.updateTask(listRemoteId, taskRemoteId, TaskPatch(steps = listOf(patch)))
                store.transaction {
                    recordTask(task, remote)
                    val now = store.steps.get(step.localId) ?: return@transaction
                    val current = store.sync.operation(op.seq) ?: return@transaction
                    val remaining = current.changedFields.filter {
                        it !in op.changedFields ||
                            it in changedStepFields(step, now)
                    }.toSet()
                    if (remaining.isEmpty()) {
                        store.ops.delete(
                            op.seq
                        )
                    } else {
                        store.ops.update(current.copy(changedFields = remaining))
                    }
                }
            }
            OperationKind.DELETE -> {
                step.remoteId?.let { id ->
                    val remote = provider.updateTask(
                        listRemoteId,
                        taskRemoteId,
                        TaskPatch(steps = listOf(StepPatch.Remove(id)))
                    )
                    store.transaction { recordTask(task, remote) }
                }
                store.transaction {
                    store.dropOps(EntityType.STEP, step.localId)
                    store.steps.delete(step.localId)
                }
            }
            OperationKind.MOVE -> moveStep(op, step, task, listRemoteId, taskRemoteId)
        }
    }

    /**
     * Sends a step's new place: right after the nearest step above it that exists remotely, or
     * first. Services that keep no order never get one (FR-222, FR-223).
     */
    private suspend fun moveStep(
        op: PendingOperationEntity,
        step: StepEntity,
        task: TaskEntity,
        listRemoteId: String,
        taskRemoteId: String
    ) {
        if (!storesOrder) return store.ops.delete(op.seq)
        val stepRemoteId = step.remoteId ?: return waitForCreate(op, EntityType.STEP, step.localId)
        val above = store.steps.forTask(task.localId)
            .filter { !it.deletedLocally && it.sortOrder < step.sortOrder }
            .lastOrNull { it.remoteId != null }
            ?.remoteId
        val remote = provider.updateTask(
            listRemoteId,
            taskRemoteId,
            TaskPatch(steps = listOf(StepPatch.Move(stepRemoteId, above)))
        )
        store.transaction {
            recordTask(task, remote)
            store.ops.delete(op.seq)
        }
    }

    /** A move of something not created remotely yet waits for its create; with none queued, it is moot. */
    private suspend fun waitForCreate(op: PendingOperationEntity, entity: EntityType, localId: String) {
        if (store.opsFor(entity, localId).any { it.kind == OperationKind.CREATE }) throw Deferred
        store.ops.delete(op.seq)
    }

    private suspend fun recordTask(task: TaskEntity, remote: RemoteTask) {
        val now = store.tasks.get(task.localId) ?: return
        store.tasks.update(now.copy(etag = remote.etag, remoteUpdatedAt = remote.updatedAt))
    }

    // Failures

    /**
     * The service refused [op] (FR-122): drop it, put the remote copy back here, say so in the
     * sync log, and carry on with the rest. Only a create, which has no remote copy, is removed.
     */
    private suspend fun notAllowed(op: PendingOperationEntity) = store.transaction {
        store.ops.delete(op.seq)
        val task = when (op.entity) {
            EntityType.LIST -> null
            EntityType.TASK -> store.tasks.get(op.entityLocalId)
            EntityType.STEP -> store.steps.get(op.entityLocalId)?.let { store.tasks.get(it.taskId) }
        }
        val list = store.lists.get(task?.listId ?: op.entityLocalId) ?: return@transaction
        when {
            task == null && list.remoteId == null -> {
                store.log(SyncLogType.ERROR, "“${list.title}” couldn't be created in your account")
                return@transaction
            }
            task == null -> {
                // A rename comes back with the next pull; a delete is undone here.
                if (list.deletedLocally) {
                    store.lists.update(list.copy(deletedLocally = false, tasksCursor = null))
                    store.tasks.restoreInList(list.localId)
                }
                store.log(SyncLogType.CONFLICT, "You can't change the list “${list.title}”, so it was put back")
            }
            task.remoteId == null -> {
                store.log(
                    SyncLogType.CONFLICT,
                    "“${task.title}” couldn't be added to “${list.title}”, so it was removed here",
                    task.toJson(store.steps.forTask(task.localId))
                )
                store.dropStepOps(task.localId)
                store.dropOps(EntityType.TASK, task.localId)
                store.tasks.delete(task.localId)
            }
            else -> {
                store.dropStepOps(task.localId)
                store.dropOps(EntityType.TASK, task.localId)
                store.tasks.update(task.copy(deletedLocally = false))
                // Reading the list again brings back the remote copy of what was refused.
                store.lists.update(list.copy(tasksCursor = null))
                store.log(
                    SyncLogType.CONFLICT,
                    "You can't change “${task.title}” in “${list.title}”, so it was put back"
                )
            }
        }
    }

    private suspend fun notFound(op: PendingOperationEntity, error: ProviderError.NotFound) {
        when (op.entity) {
            EntityType.LIST -> when (op.kind) {
                OperationKind.DELETE -> finishListDelete(op.entityLocalId)
                // Its list is gone remotely; the pull that follows recovers its tasks.
                else -> store.ops.delete(op.seq)
            }
            EntityType.TASK -> {
                val task = store.tasks.get(op.entityLocalId) ?: return store.ops.delete(op.seq)
                when (op.kind) {
                    OperationKind.DELETE -> store.transaction {
                        store.dropStepOps(task.localId)
                        store.dropOps(EntityType.TASK, task.localId)
                        store.tasks.delete(task.localId)
                    }
                    // Its list is gone remotely; the pull moves it to "Recovered".
                    OperationKind.CREATE -> countFailure(op, error)
                    else -> store.transaction {
                        store.log(
                            SyncLogType.RECOVERED,
                            "“${task.title}” was deleted elsewhere while you were changing it, so it was put back"
                        )
                        store.recreate(task)
                    }
                }
            }
            EntityType.STEP -> {
                val step = store.steps.get(op.entityLocalId) ?: return store.ops.delete(op.seq)
                val task = store.tasks.get(step.taskId) ?: return store.ops.delete(op.seq)
                store.transaction {
                    when {
                        // The whole task is gone remotely: put it back, steps included.
                        error.id == task.remoteId -> {
                            store.log(
                                SyncLogType.RECOVERED,
                                "“${task.title}” was deleted elsewhere while you were changing it, so it was put back"
                            )
                            store.recreate(task)
                        }
                        op.kind == OperationKind.DELETE -> {
                            store.dropOps(EntityType.STEP, step.localId)
                            store.steps.delete(step.localId)
                        }
                        // Just the step is gone remotely: add it again.
                        else -> {
                            store.steps.update(step.copy(remoteId = null))
                            store.dropOps(EntityType.STEP, step.localId)
                            store.enqueue(EntityType.STEP, step.localId, OperationKind.CREATE)
                        }
                    }
                }
            }
        }
    }

    private suspend fun countFailure(op: PendingOperationEntity, error: Throwable) {
        val current = store.sync.operation(op.seq) ?: return
        val attempts = current.attempts + 1
        if (attempts < MAX_ATTEMPTS) {
            store.ops.update(current.copy(attempts = attempts))
        } else {
            store.transaction {
                store.ops.delete(op.seq)
                store.log(SyncLogType.ERROR, "Gave up on a change after $attempts tries: ${error.message}")
            }
        }
    }

    // Edits made while a push was in flight

    /**
     * Drops [op] if everything it sent is still what the database holds; otherwise keeps it
     * queued with only the fields that changed again.
     */
    private suspend fun settle(op: PendingOperationEntity, sent: TaskEntity, now: TaskEntity, sentFields: Set<String>) {
        val current = store.sync.operation(op.seq) ?: return
        val changedAgain = changedTaskFields(sent, now)
        val remaining = when (current.kind) {
            OperationKind.CREATE -> changedAgain
            else -> current.changedFields.filter { it !in sentFields || it in changedAgain }.toSet()
        }
        if (remaining.isEmpty()) {
            store.ops.delete(op.seq)
        } else {
            store.ops.update(current.copy(kind = OperationKind.UPDATE, changedFields = remaining, attempts = 0))
        }
    }

    private fun changedTaskFields(a: TaskEntity, b: TaskEntity): Set<String> = buildSet {
        if (a.title != b.title) add(Fields.TITLE)
        if (a.notes != b.notes) add(Fields.NOTES)
        if (a.dueDate != b.dueDate) add(Fields.DUE_DATE)
        if (a.completed != b.completed) add(Fields.COMPLETED)
        if (a.important != b.important) add(Fields.IMPORTANT)
        if (a.listId != b.listId) add(Fields.LIST)
    }

    private fun changedStepFields(a: StepEntity, b: StepEntity): Set<String> = buildSet {
        if (a.title != b.title) add(Fields.TITLE)
        if (a.done != b.done) add(Fields.DONE)
    }

    /** This operation has to wait for one before it (its list or task is not created remotely yet). */
    private object Deferred : Exception("Waiting for an earlier change to sync") {
        private fun readResolve(): Any = Deferred
    }

    companion object {
        const val MAX_ATTEMPTS = 10

        /** Task deletes sent in one go; each is settled as it is confirmed, so a stop loses none. */
        const val DELETES_AT_ONCE = 100
    }
}
