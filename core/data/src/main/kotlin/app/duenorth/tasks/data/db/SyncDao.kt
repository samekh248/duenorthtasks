package app.duenorth.tasks.data.db

import androidx.room.Dao
import androidx.room.Query

/** Lookups only the sync engine needs: rows by remote id, and the outbox by entity. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM task_list")
    suspend fun allLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_list WHERE remoteId = :remoteId LIMIT 1")
    suspend fun listByRemoteId(remoteId: String): TaskListEntity?

    @Query("SELECT * FROM task WHERE remoteId = :remoteId LIMIT 1")
    suspend fun taskByRemoteId(remoteId: String): TaskEntity?

    @Query("SELECT * FROM task WHERE listId = :listId")
    suspend fun tasksInList(listId: String): List<TaskEntity>

    @Query("SELECT * FROM pending_operation WHERE seq = :seq")
    suspend fun operation(seq: Long): PendingOperationEntity?

    @Query("SELECT * FROM pending_operation WHERE entity = :entity")
    suspend fun operationsFor(entity: EntityType): List<PendingOperationEntity>
}
