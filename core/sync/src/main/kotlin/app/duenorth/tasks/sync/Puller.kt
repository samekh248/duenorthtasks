package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.sync.ConflictResolver.Decision
import app.duenorth.tasks.sync.SyncStore.Companion.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Pulls remote changes into Room (research R9, plan "Keeping it fast").
 *
 * Lists are reconciled first, then each list's tasks since its cursor. Changes are applied in
 * transactions of at most [BATCH] rows, tasks due soonest first, and each batch waits while the
 * user is touching that list ([ListHolds]), so the screen never jumps under a finger (FR-008).
 *
 * Lists never fetched before get their open tasks first, all lists at once, when the provider can
 * filter them ([TaskProvider.getOpenTasks]). Lists that have a cursor are brought up to date next,
 * and then [beforeBackfill] runs (the engine stops the dots and sends local changes). Only then
 * does the full fetch that brings in completed tasks and the change cursor carry on, so an account
 * with years of finished tasks is usable in seconds, and a slow or failing backfill never holds
 * back what was changed on the phone.
 */
internal class Puller(
    private val store: SyncStore,
    private val provider: TaskProvider,
    private val holds: ListHolds,
    private val stillConnected: suspend () -> Boolean,
    private val beforeBackfill: suspend () -> Unit = {}
) {
    private val canStoreImportance = provider.capabilities.importance

    /** Google keeps order and sends it; To Do doesn't, so the phone's order keys are the only ones. */
    private val storesOrder = provider.capabilities.manualOrder

    suspend fun pullAll() {
        reconcileLists(provider.getLists())
        val lists = store.sync.allLists()
            .filter { !it.deletedLocally && it.remoteId != null }
            .sortedWith(compareByDescending<TaskListEntity> { it.isDefault }.thenBy { it.title.lowercase() })
        val gate = Semaphore(PARALLEL_LISTS)
        val loadedOpen = coroutineScope {
            lists.filter { it.tasksCursor == null }
                .map { list -> async { gate.withPermit { stillConnected() && pullOpenTasks(list) } } }
                .awaitAll()
        }
        val (fresh, known) = lists.partition { it.tasksCursor == null }
        // With open tasks in hand, a fresh list's full history can wait; without them it cannot.
        val backfill = if (loadedOpen.any { it }) fresh else emptyList()
        pullLists(known + (fresh - backfill.toSet()), gate)
        if (backfill.isEmpty()) return
        if (stillConnected()) beforeBackfill()
        pullLists(backfill, gate)
    }

    private suspend fun pullLists(lists: List<TaskListEntity>, gate: Semaphore) = coroutineScope {
        lists.map { list -> async { gate.withPermit { if (stillConnected()) pullList(list) } } }.awaitAll()
    }

    // Lists

    private suspend fun reconcileLists(remote: List<RemoteList>) = store.transaction {
        val local = store.sync.allLists()
        val byRemoteId = local.filter { it.remoteId != null }.associateBy { it.remoteId }
        for (rl in remote) {
            val existing = byRemoteId[rl.id]
            if (existing == null) {
                val pendingCreate = local.firstOrNull { candidate ->
                    candidate.remoteId == null &&
                        (store.journal.sentTitle(candidate.localId) ?: candidate.title) == rl.title &&
                        store.opsFor(EntityType.LIST, candidate.localId).any { it.kind == OperationKind.CREATE }
                }
                if (pendingCreate != null) {
                    // A create whose answer was lost: this is ours (FR-022).
                    store.lists.update(
                        pendingCreate.copy(remoteId = rl.id, etag = rl.etag, remoteUpdatedAt = rl.updatedAt)
                    )
                    store.dropOps(EntityType.LIST, pendingCreate.localId)
                    if (pendingCreate.title !=
                        rl.title
                    ) {
                        store.enqueue(
                            EntityType.LIST,
                            pendingCreate.localId,
                            OperationKind.UPDATE,
                            setOf(Fields.TITLE)
                        )
                    }
                    store.journal.clear(pendingCreate.localId)
                } else {
                    store.lists.insert(
                        TaskListEntity(
                            localId = store.newLocalId(),
                            remoteId = rl.id,
                            title = rl.title,
                            isDefault = rl.isDefault,
                            etag = rl.etag,
                            remoteUpdatedAt = rl.updatedAt,
                            localUpdatedAt = rl.updatedAt
                        )
                    )
                }
                continue
            }
            if (existing.deletedLocally) continue // our delete is still on its way
            val pending = store.opsFor(EntityType.LIST, existing.localId).any { it.kind == OperationKind.UPDATE }
            // Microsoft has no modified time for lists and reports "now"; an unchanged etag means
            // unchanged, or every rename made here would lose to a remote that never moved.
            val remoteUpdatedAt = existing.remoteUpdatedAt?.takeIf { existing.etag != null && existing.etag == rl.etag }
                ?: rl.updatedAt
            val decision = ConflictResolver.decide(
                pending,
                existing.localUpdatedAt,
                existing.remoteUpdatedAt,
                remoteUpdatedAt
            )
            val takeRemote = decision == Decision.APPLY_REMOTE || decision == Decision.REMOTE_WINS
            if (decision == Decision.REMOTE_WINS && existing.title != rl.title) {
                store.log(
                    SyncLogType.CONFLICT,
                    "List renamed here and elsewhere; kept “${rl.title}” over “${existing.title}”"
                )
            }
            if (decision == Decision.LOCAL_WINS && existing.title != rl.title) {
                store.log(
                    SyncLogType.CONFLICT,
                    "List renamed here and elsewhere; kept “${existing.title}” over “${rl.title}”"
                )
            }
            if (decision == Decision.REMOTE_WINS) store.dropOps(EntityType.LIST, existing.localId)
            store.lists.update(
                existing.copy(
                    title = if (takeRemote) rl.title else existing.title,
                    isDefault = rl.isDefault,
                    etag = rl.etag,
                    remoteUpdatedAt = remoteUpdatedAt,
                    localUpdatedAt = if (takeRemote) remoteUpdatedAt else existing.localUpdatedAt
                )
            )
        }
        val remoteIds = remote.map { it.id }.toSet()
        local.filter { it.remoteId != null && it.remoteId !in remoteIds }.forEach { listGoneRemotely(it) }
    }

    /**
     * Spec edge case: a list deleted on the web while the phone has unsynced tasks in it. Those
     * tasks move to a local "Recovered" list (and are created again from there); the rest go.
     */
    private suspend fun listGoneRemotely(list: TaskListEntity) {
        val tasks = store.sync.tasksInList(list.localId)
        val unsynced = if (list.deletedLocally) {
            emptyList()
        } else {
            tasks.filter {
                !it.deletedLocally &&
                    store.hasPending(it)
            }
        }
        if (unsynced.isNotEmpty()) {
            val recovered = store.recoveredList()
            unsynced.forEach { store.recreate(it, listId = recovered.localId) }
            val count = if (unsynced.size == 1) "1 task" else "${unsynced.size} tasks"
            store.log(
                SyncLogType.RECOVERED,
                "“${list.title}” was deleted elsewhere. $count with unsynced changes moved to “${recovered.title}”."
            )
        }
        tasks.filter { it !in unsynced }.forEach {
            store.dropStepOps(it.localId)
            store.dropOps(EntityType.TASK, it.localId)
        }
        store.dropOps(EntityType.LIST, list.localId)
        store.lists.delete(list.localId)
    }

    // Tasks

    /**
     * The open tasks of a list fetched for the first time; no deletions and no cursor, which the
     * full fetch owns. False when the provider cannot filter, so there is nothing early to show.
     */
    private suspend fun pullOpenTasks(list: TaskListEntity): Boolean {
        val open = try {
            provider.getOpenTasks(checkNotNull(list.remoteId))
        } catch (_: ProviderError.NotFound) {
            null
        } catch (_: ProviderError.NotAllowed) {
            null
        } ?: return false
        applyChanged(list, open, mayAdopt(list), firstFetch = true)
        return true
    }

    /** Only a create that was sent can come back unrecognised; most pulls have none to look for. */
    private suspend fun mayAdopt(list: TaskListEntity): Boolean =
        store.sync.unsyncedTasksInList(list.localId).any { store.journal.sentTitle(it.localId) != null }

    /**
     * Writes [changed] into [list]: today's tasks first, one lookup and one insert per batch.
     * [firstFetch] is true while the list has never been fetched, which decides where tasks new to
     * the phone go when the service keeps no order (newest created first, else at the top).
     */
    private suspend fun applyChanged(
        list: TaskListEntity,
        changed: List<RemoteTask>,
        mayAdopt: Boolean,
        firstFetch: Boolean
    ) {
        // Today's tasks first, so the "today" section fills in before the rest (FR-009a).
        // A task listed twice counts once, in its last state, since a batch inserts new rows together.
        val ordered = changed.asReversed().distinctBy { it.id }
            .sortedWith(compareBy<RemoteTask> { it.dueDate == null }.thenBy { it.dueDate })
        for (batch in ordered.chunked(BATCH)) {
            holds.awaitReleased(list.localId)
            store.transaction {
                // Looking rows up and inserting them one at a time was most of a first sync.
                val known = store.sync.tasksByRemoteIds(batch.map { it.id }).associateBy { it.remoteId }
                val fresh = NewRows(list, firstFetch)
                batch.forEach { applyTask(list, it, known[it.id], mayAdopt, fresh) }
                fresh.write()
            }
        }
    }

    private suspend fun pullList(list: TaskListEntity) {
        val listRemoteId = checkNotNull(list.remoteId)
        var cursor = list.tasksCursor
        var full = cursor == null
        val seen = mutableSetOf<String>()
        val mayAdopt = mayAdopt(list)
        while (true) {
            val page = try {
                provider.getTaskChanges(listRemoteId, cursor)
            } catch (_: ProviderError.CursorExpired) {
                if (full) throw ProviderError.Transient("Full fetch of ${list.title} was refused")
                cursor = null
                full = true
                continue
            } catch (_: ProviderError.NotFound) {
                return // gone since the list check; the next sync's list check recovers it
            } catch (_: ProviderError.NotAllowed) {
                return // a list we may not read; the others still sync
            }
            seen += page.changed.map { it.id }
            applyChanged(list, page.changed, mayAdopt, firstFetch = list.tasksCursor == null)
            for (batch in page.deletedIds.chunked(BATCH)) {
                holds.awaitReleased(list.localId)
                store.transaction { batch.forEach { applyRemoteDelete(list, it) } }
            }
            cursor = page.nextCursor
            if (!page.hasMore) break
        }
        store.transaction {
            if (full) {
                // A full fetch has no deletion list: whatever it did not return is gone.
                val missing = store.sync.tasksInList(list.localId).mapNotNull { it.remoteId }.filter { it !in seen }
                missing.forEach { applyRemoteDelete(list, it) }
            }
            store.lists.get(list.localId)?.let { store.lists.update(it.copy(tasksCursor = cursor)) }
            if (!storesOrder) giveKeysToUnkeyed(list.localId)
        }
    }

    private suspend fun applyTask(
        list: TaskListEntity,
        remote: RemoteTask,
        local: TaskEntity?,
        mayAdopt: Boolean,
        fresh: NewRows
    ) {
        if (local == null) {
            if (!mayAdopt || !adoptLostCreate(list, remote)) fresh.add(remote)
            return
        }
        val (taskOps, stepOps) = store.pendingFor(local)
        if (local.deletedLocally) {
            // Deleted here but changed elsewhere since: the newer edit wins and the task comes back.
            val decision = ConflictResolver.decide(true, local.localUpdatedAt, local.remoteUpdatedAt, remote.updatedAt)
            if (decision == Decision.REMOTE_WINS) {
                store.dropStepOps(local.localId)
                store.dropOps(EntityType.TASK, local.localId)
                store.log(
                    SyncLogType.CONFLICT,
                    "“${remote.title}” was changed elsewhere after you deleted it, so it was kept"
                )
                applyFields(local.copy(deletedLocally = false), remote, list, keep = emptySet())
                store.mergeSteps(local.localId, remote, keepPending = emptySet(), keepOrder = !storesOrder)
            }
            return
        }
        val pendingFields = taskOps.flatMap { op ->
            when (op.kind) {
                OperationKind.CREATE -> SyncStore.ALL_TASK_FIELDS
                // A queued move keeps the phone's place for the task until it is sent (FR-227).
                OperationKind.MOVE -> setOf(Fields.POSITION)
                else -> op.changedFields
            }
        }.toSet()
        val stepMovePending = stepOps.any { it.kind == OperationKind.MOVE }
        val pendingSteps = stepOps.map { it.entityLocalId }.toSet()
        val decision = ConflictResolver.decide(
            hasPendingLocalChange = taskOps.isNotEmpty() || stepOps.isNotEmpty(),
            localUpdatedAt = local.localUpdatedAt,
            lastSeenRemoteUpdatedAt = local.remoteUpdatedAt,
            remoteUpdatedAt = remote.updatedAt
        )
        when (decision) {
            Decision.APPLY_REMOTE -> {
                applyFields(local, remote, list, keep = emptySet())
                store.mergeSteps(local.localId, remote, keepPending = emptySet(), keepOrder = !storesOrder)
            }
            Decision.KEEP_LOCAL -> Unit
            Decision.LOCAL_WINS -> {
                // Fields not changed here still take the remote value; ours win where both changed.
                val lost = differingFields(local, remote, pendingFields)
                if (lost.isNotEmpty() || stepsDiffer(local, remote, pendingSteps)) {
                    store.log(
                        SyncLogType.CONFLICT,
                        "“${local.title}” changed here and elsewhere; kept your newer version",
                        remote.toJson()
                    )
                }
                applyFields(local, remote, list, keep = pendingFields)
                store.mergeSteps(
                    local.localId,
                    remote,
                    keepPending = pendingSteps,
                    keepOrder = !storesOrder || stepMovePending
                )
            }
            Decision.REMOTE_WINS -> {
                val lost = differingFields(local, remote, pendingFields)
                if (lost.isNotEmpty() || stepsDiffer(local, remote, pendingSteps)) {
                    store.log(
                        SyncLogType.CONFLICT,
                        "“${remote.title}” changed here and elsewhere; kept the newer version from your account",
                        local.toJson(store.steps.forTask(local.localId))
                    )
                }
                taskOps.filter {
                    it.kind == OperationKind.UPDATE || it.kind == OperationKind.MOVE
                }.forEach { store.ops.delete(it.seq) }
                // Steps added here are not in conflict with anything, so they stay queued.
                stepOps.filter { it.kind != OperationKind.CREATE }.forEach { store.ops.delete(it.seq) }
                val keptNewSteps = stepOps.filter { it.kind == OperationKind.CREATE }.map { it.entityLocalId }.toSet()
                applyFields(local, remote, list, keep = emptySet())
                store.mergeSteps(local.localId, remote, keepPending = keptNewSteps, keepOrder = !storesOrder)
            }
        }
    }

    /** Matches a task this phone created but never heard back about (FR-022: no duplicates on retry). */
    private suspend fun adoptLostCreate(list: TaskListEntity, remote: RemoteTask): Boolean {
        // Only never-synced tasks can match; reading the whole list per task made a first sync quadratic.
        val candidate = store.sync.unsyncedTasksInList(list.localId).firstOrNull { task ->
            store.journal.sentTitle(task.localId) == remote.title &&
                store.opsFor(EntityType.TASK, task.localId).any { it.kind == OperationKind.CREATE }
        } ?: return false
        store.journal.clear(candidate.localId)
        store.tasks.update(
            candidate.copy(
                remoteId = remote.id,
                etag = remote.etag,
                remoteUpdatedAt = remote.updatedAt,
                position = if (storesOrder) remote.position else candidate.position
            )
        )
        // Pair steps by title in order; any left over are added as new steps.
        val remaining = remote.steps.toMutableList()
        store.steps.forTask(candidate.localId).filterNot { it.deletedLocally }.forEach { step ->
            val match = remaining.firstOrNull { it.title == step.title } ?: return@forEach
            remaining.remove(match)
            store.steps.update(step.copy(remoteId = match.id))
            store.dropOps(EntityType.STEP, step.localId)
            if (match.done !=
                step.done
            ) {
                store.enqueue(EntityType.STEP, step.localId, OperationKind.UPDATE, setOf(Fields.DONE))
            }
        }
        // The create is done; our copy is the newer one, so push all of its fields once.
        store.dropOps(EntityType.TASK, candidate.localId)
        store.enqueue(EntityType.TASK, candidate.localId, OperationKind.UPDATE, SyncStore.ALL_TASK_FIELDS)
        return true
    }

    /** Tasks new to this phone, with their steps, written together at the end of a batch. */
    private inner class NewRows(private val list: TaskListEntity, private val firstFetch: Boolean) {
        private val tasks = mutableListOf<TaskEntity>()
        private val steps = mutableListOf<StepEntity>()
        private var top: String? = null
        private var topRead = false

        /** Keys given on a first fetch: tasks created in the same millisecond still get their own. */
        private val used = mutableSetOf<String>()

        /**
         * The task's place. A service with order sends it. Without one, a list's first fetch puts
         * the newest created first; after that, a task new to the phone goes to the top.
         */
        private suspend fun positionFor(remote: RemoteTask): String? {
            if (storesOrder) return remote.position
            if (firstFetch) {
                var key = OrderKeys.timeKey(remote.createdAt ?: remote.updatedAt)
                while (!used.add(key)) key += "5"
                return key
            }
            if (!topRead) {
                top = store.tasks.firstPosition(list.localId)
                topRead = true
            }
            return OrderKeys.between(null, top).also { top = it }
        }

        suspend fun add(remote: RemoteTask) {
            val id = store.newLocalId()
            tasks += TaskEntity(
                localId = id,
                listId = list.localId,
                remoteId = remote.id,
                title = remote.title,
                notes = remote.notes,
                dueDate = remote.dueDate,
                completed = remote.completed,
                completedAt = remote.completedAt,
                important = remote.important && canStoreImportance,
                position = positionFor(remote),
                remoteStatusRaw = remote.rawStatus,
                etag = remote.etag,
                remoteUpdatedAt = remote.updatedAt,
                localUpdatedAt = remote.updatedAt
            )
            remote.steps.forEachIndexed { index, step ->
                steps += StepEntity(
                    localId = store.newLocalId(),
                    taskId = id,
                    remoteId = step.id,
                    title = step.title,
                    done = step.done,
                    sortOrder = index
                )
            }
        }

        suspend fun write() {
            if (tasks.isNotEmpty()) store.tasks.insertAll(tasks)
            if (steps.isNotEmpty()) store.steps.insertAll(steps)
        }
    }

    private suspend fun applyRemoteDelete(list: TaskListEntity, remoteId: String) {
        val local = store.sync.taskByRemoteId(remoteId) ?: return
        if (local.listId != list.localId) return // moved here to another list; that push owns it
        val (taskOps, stepOps) = store.pendingFor(local)
        when {
            local.deletedLocally || (taskOps.isEmpty() && stepOps.isEmpty()) -> {
                store.dropStepOps(local.localId)
                store.dropOps(EntityType.TASK, local.localId)
                store.tasks.delete(local.localId)
            }
            else -> {
                // Deleted elsewhere, changed here: nothing is lost if it comes back.
                store.log(
                    SyncLogType.RECOVERED,
                    "“${local.title}” was deleted elsewhere but had changes here, so it was put back"
                )
                store.recreate(local)
            }
        }
    }

    /** Writes [remote] over [local], except the fields in [keep] (pending changes that won). */
    private suspend fun applyFields(local: TaskEntity, remote: RemoteTask, list: TaskListEntity, keep: Set<String>) {
        val completedChanges = Fields.COMPLETED !in keep
        store.tasks.update(
            local.copy(
                listId = if (Fields.LIST in keep) local.listId else list.localId,
                title = if (Fields.TITLE in keep) local.title else remote.title,
                notes = if (Fields.NOTES in keep) local.notes else remote.notes,
                dueDate = if (Fields.DUE_DATE in keep) local.dueDate else remote.dueDate,
                completed = if (completedChanges) remote.completed else local.completed,
                completedAt = if (completedChanges) remote.completedAt else local.completedAt,
                important = if (Fields.IMPORTANT in keep || !canStoreImportance) local.important else remote.important,
                position = if (Fields.POSITION in keep || !storesOrder) local.position else remote.position,
                remoteStatusRaw = remote.rawStatus,
                etag = remote.etag,
                remoteUpdatedAt = remote.updatedAt,
                localUpdatedAt = if (keep.isEmpty()) remote.updatedAt else local.localUpdatedAt
            )
        )
    }

    /**
     * Tasks synced before order keys existed have none, and "my order" would follow their last
     * edit. Gives them keys in the order they show now, above the keyed ones (To Do only).
     */
    private suspend fun giveKeysToUnkeyed(listId: String) {
        val open = store.tasks.openInList(listId).sortedWith(OrderKeys.taskComparator)
        val unkeyed = open.takeWhile { it.position == null }
        if (unkeyed.isEmpty()) return
        val keys = OrderKeys.keysBefore(open.getOrNull(unkeyed.size)?.position, unkeyed.size)
        unkeyed.zip(keys).forEach { (task, key) -> store.tasks.update(task.copy(position = key)) }
    }

    private fun differingFields(local: TaskEntity, remote: RemoteTask, fields: Set<String>): Set<String> =
        fields.filter { field ->
            when (field) {
                Fields.TITLE -> local.title != remote.title
                Fields.NOTES -> local.notes.orEmpty() != remote.notes.orEmpty()
                Fields.DUE_DATE -> local.dueDate != remote.dueDate
                Fields.COMPLETED -> local.completed != remote.completed
                Fields.IMPORTANT -> canStoreImportance && local.important != remote.important
                else -> false
            }
        }.toSet()

    private suspend fun stepsDiffer(local: TaskEntity, remote: RemoteTask, pendingSteps: Set<String>): Boolean {
        val remoteById = remote.steps.associateBy { it.id }
        return store.steps.forTask(local.localId).filter {
            it.localId in pendingSteps && it.remoteId != null
        }.any { step ->
            val other = remoteById[step.remoteId] ?: return@any true
            other.title != step.title || other.done != step.done
        }
    }

    companion object {
        /** At most this many rows per transaction, so the UI's Room flows never stall (T043). */
        const val BATCH = 50

        /** Lists fetched at once; Microsoft allows four requests in flight per mailbox. */
        const val PARALLEL_LISTS = 3
    }
}
