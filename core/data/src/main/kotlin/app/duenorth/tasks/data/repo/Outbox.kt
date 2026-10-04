package app.duenorth.tasks.data.repo

import app.duenorth.tasks.data.db.EntityType
import app.duenorth.tasks.data.db.OperationKind
import app.duenorth.tasks.data.db.PendingOperationDao
import app.duenorth.tasks.data.db.PendingOperationEntity
import java.time.Instant

/**
 * Queues local changes for the sync engine, merging them so the push stays small
 * (data-model.md, "Outbox coalescing"). Callers run it inside the same transaction as the entity
 * write, so the row and its operation are saved together or not at all.
 */
internal class Outbox(private val dao: PendingOperationDao) {
    enum class Result {
        /** A new operation was added. */
        QUEUED,

        /** Folded into an operation already waiting (or made redundant by a queued CREATE). */
        MERGED,

        /** A DELETE cancelled a CREATE that was never pushed: the entity never existed remotely. */
        CANCELLED
    }

    suspend fun enqueue(
        entity: EntityType,
        localId: String,
        kind: OperationKind,
        now: Instant,
        fields: Set<String> = emptySet()
    ): Result {
        val waiting = dao.forEntity(entity, localId)
        val createWaiting = waiting.any { it.kind == OperationKind.CREATE }
        return when (kind) {
            OperationKind.CREATE -> insert(entity, localId, kind, now, fields)

            // A queued CREATE sends the entity as it is at push time, so later edits ride along.
            OperationKind.UPDATE -> {
                if (createWaiting) return Result.MERGED
                val update = waiting.firstOrNull { it.kind == OperationKind.UPDATE }
                if (update == null) {
                    insert(entity, localId, kind, now, fields)
                } else {
                    dao.update(update.copy(changedFields = update.changedFields + fields))
                    Result.MERGED
                }
            }

            OperationKind.MOVE -> {
                if (waiting.any { it.kind == OperationKind.MOVE }) {
                    Result.MERGED
                } else {
                    insert(entity, localId, kind, now, fields)
                }
            }

            OperationKind.DELETE -> {
                if (createWaiting) {
                    dao.deleteForEntity(entity, localId)
                    Result.CANCELLED
                } else {
                    waiting.filter { it.kind != OperationKind.DELETE }.forEach { dao.delete(it.seq) }
                    if (waiting.any { it.kind == OperationKind.DELETE }) {
                        Result.MERGED
                    } else {
                        insert(entity, localId, kind, now, fields)
                    }
                }
            }
        }
    }

    suspend fun hasCreateWaiting(entity: EntityType, localId: String): Boolean =
        dao.forEntity(entity, localId).any { it.kind == OperationKind.CREATE }

    suspend fun drop(entity: EntityType, localIds: List<String>) {
        localIds.chunked(MAX_SQL_ARGS).forEach { dao.deleteForEntities(entity, it) }
    }

    private suspend fun insert(
        entity: EntityType,
        localId: String,
        kind: OperationKind,
        now: Instant,
        fields: Set<String>
    ): Result {
        dao.insert(
            PendingOperationEntity(
                entity = entity,
                entityLocalId = localId,
                kind = kind,
                changedFields = fields,
                createdAt = now
            )
        )
        return Result.QUEUED
    }

    private companion object {
        /** Stay under SQLite's bound-argument limit on old devices. */
        const val MAX_SQL_ARGS = 900
    }
}
