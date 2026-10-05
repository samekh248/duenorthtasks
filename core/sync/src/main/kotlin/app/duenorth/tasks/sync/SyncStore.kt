package app.duenorth.tasks.sync

import androidx.room.withTransaction
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.Fields
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.PendingOperationEntity
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.SyncLogEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.provider.api.RemoteTask
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Database steps shared by push and pull. Callers own the transaction unless noted. */
internal class SyncStore(
    val db: DueNorthDatabase,
    private val clock: Clock,
    private val newId: () -> String,
    val journal: CreateJournal
) {
    val accounts = db.accountDao()
    val lists = db.taskListDao()
    val tasks = db.taskDao()
    val steps = db.stepDao()
    val ops = db.pendingOperationDao()
    val sync = db.syncDao()
    private val log = db.syncLogDao()

    fun now(): Instant = clock.instant()

    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    suspend fun opsFor(entity: EntityType, localId: String) = ops.forEntity(entity, localId)

    /** Pending operations on [task] itself and on any of its steps. */
    suspend fun pendingFor(task: TaskEntity): Pair<List<PendingOperationEntity>, List<PendingOperationEntity>> {
        val stepIds = steps.forTask(task.localId).map { it.localId }.toSet()
        val stepOps = if (stepIds.isEmpty()) {
            emptyList()
        } else {
            sync.operationsFor(EntityType.STEP).filter {
                it.entityLocalId in
                    stepIds
            }
        }
        return opsFor(EntityType.TASK, task.localId) to stepOps
    }

    suspend fun hasPending(task: TaskEntity): Boolean = pendingFor(task).let { (t, s) ->
        t.isNotEmpty() ||
            s.isNotEmpty()
    }

    suspend fun dropOps(entity: EntityType, localId: String) = ops.deleteForEntity(entity, localId)

    suspend fun dropStepOps(taskId: String) {
        val ids = steps.forTask(taskId).map { it.localId }
        if (ids.isNotEmpty()) ids.chunked(MAX_SQL_ARGS).forEach { ops.deleteForEntities(EntityType.STEP, it) }
    }

    suspend fun enqueue(entity: EntityType, localId: String, kind: OperationKind, fields: Set<String> = emptySet()) {
        ops.insert(
            PendingOperationEntity(
                entity = entity,
                entityLocalId = localId,
                kind = kind,
                changedFields = fields,
                createdAt = now()
            )
        )
    }

    suspend fun log(type: SyncLogType, summary: String, losing: JsonObject? = null) {
        log.insert(SyncLogEntity(at = now(), type = type, summary = summary, losingVersionJson = losing?.toString()))
    }

    /**
     * Forgets that [task] exists remotely and queues it to be created again, with its steps riding
     * along. Used when the remote copy (or its list) vanished while the phone still had changes.
     */
    suspend fun recreate(task: TaskEntity, listId: String = task.listId) {
        journal.clear(task.localId)
        tasks.update(task.copy(listId = listId, remoteId = null, etag = null, remoteUpdatedAt = null, position = null))
        steps.forTask(task.localId).forEach { step ->
            if (step.deletedLocally) steps.delete(step.localId) else steps.update(step.copy(remoteId = null))
        }
        dropStepOps(task.localId)
        dropOps(EntityType.TASK, task.localId)
        if (task.deletedLocally) {
            tasks.delete(task.localId)
        } else {
            enqueue(EntityType.TASK, task.localId, OperationKind.CREATE)
        }
    }

    /** The local "Recovered" list for tasks whose list was deleted elsewhere, created on first use. */
    suspend fun recoveredList(): TaskListEntity {
        sync.allLists().firstOrNull { it.title == RECOVERED_LIST && !it.deletedLocally }?.let { return it }
        val list = TaskListEntity(localId = newId(), title = RECOVERED_LIST, localUpdatedAt = now())
        lists.insert(list)
        enqueue(EntityType.LIST, list.localId, OperationKind.CREATE)
        return list
    }

    fun newLocalId(): String = newId()

    /** Remote steps merged into [taskId]: remote ones update or appear, ones gone remotely leave. */
    suspend fun mergeSteps(taskId: String, remote: RemoteTask, keepPending: Set<String>) {
        val local = steps.forTask(taskId)
        val byRemote = local.filter { it.remoteId != null }.associateBy { it.remoteId }
        val remoteIds = remote.steps.map { it.id }.toSet()
        remote.steps.forEachIndexed { index, step ->
            val existing = byRemote[step.id]
            when {
                existing == null -> steps.insert(
                    StepEntity(
                        localId = newId(),
                        taskId = taskId,
                        remoteId = step.id,
                        title = step.title,
                        done = step.done,
                        sortOrder = index
                    )
                )
                existing.localId in keepPending -> steps.update(existing.copy(sortOrder = index))
                else -> steps.update(
                    existing.copy(title = step.title, done = step.done, sortOrder = index, deletedLocally = false)
                )
            }
        }
        local.filter { it.remoteId != null && it.remoteId !in remoteIds && it.localId !in keepPending }
            .forEach { steps.delete(it.localId) }
        // Steps added here and not pushed yet go after the remote ones.
        local.filter { it.remoteId == null }.forEachIndexed { i, step ->
            steps.update(
                step.copy(
                    sortOrder =
                    remote.steps.size + i
                )
            )
        }
    }

    companion object {
        const val RECOVERED_LIST = "Recovered"
        private const val MAX_SQL_ARGS = 900

        /** The fields a full "send everything" update covers. */
        val ALL_TASK_FIELDS = setOf(Fields.TITLE, Fields.NOTES, Fields.DUE_DATE, Fields.COMPLETED, Fields.IMPORTANT)
        val ALL_STEP_FIELDS = setOf(Fields.TITLE, Fields.DONE)

        fun TaskEntity.toJson(steps: List<StepEntity>): JsonObject = buildJsonObject {
            put("title", title)
            notes?.let { put("notes", it) }
            dueDate?.let { put("dueDate", it.toString()) }
            put("completed", completed)
            if (important) put("important", true)
            put(
                "steps",
                JsonArray(
                    steps.filterNot { it.deletedLocally }.map {
                        buildJsonObject {
                            put("title", it.title)
                            put("done", it.done)
                        }
                    }
                )
            )
        }

        fun RemoteTask.toJson(): JsonObject = buildJsonObject {
            put("title", title)
            notes?.let { put("notes", it) }
            dueDate?.let { put("dueDate", it.toString()) }
            put("completed", completed)
            if (important) put("important", true)
            put(
                "steps",
                JsonArray(
                    steps.map {
                        buildJsonObject {
                            put("title", it.title)
                            put("done", it.done)
                        }
                    }
                )
            )
            put("updated", updatedAt.toString())
        }
    }
}
