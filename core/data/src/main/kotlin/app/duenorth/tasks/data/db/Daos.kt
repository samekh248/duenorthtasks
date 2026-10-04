package app.duenorth.tasks.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE id = 1")
    suspend fun get(): AccountEntity?

    @Query("SELECT * FROM account WHERE id = 1")
    fun observe(): Flow<AccountEntity?>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    /** Cascades to every other table (data-model.md, "One account"). */
    @Query("DELETE FROM account")
    suspend fun delete()
}

/** A list row for the "lists" panorama section: tile count and the next task's title. */
data class ListSummary(
    val localId: String,
    val remoteId: String?,
    val title: String,
    val isDefault: Boolean,
    val openCount: Int,
    val nextTaskTitle: String?
)

@Dao
interface TaskListDao {
    @Query(
        """
        SELECT l.localId, l.remoteId, l.title, l.isDefault,
            (SELECT COUNT(*) FROM task t
                WHERE t.listId = l.localId AND t.completed = 0 AND t.deletedLocally = 0) AS openCount,
            (SELECT t.title FROM task t
                WHERE t.listId = l.localId AND t.completed = 0 AND t.deletedLocally = 0
                ORDER BY t.dueDate IS NULL, t.dueDate, t.position, t.localUpdatedAt DESC LIMIT 1) AS nextTaskTitle
        FROM task_list l
        WHERE l.deletedLocally = 0
        ORDER BY l.isDefault DESC, l.title COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<ListSummary>>

    @Query("SELECT * FROM task_list WHERE localId = :localId")
    suspend fun get(localId: String): TaskListEntity?

    @Query("SELECT * FROM task_list WHERE localId = :localId")
    fun observe(localId: String): Flow<TaskListEntity?>

    @Query("SELECT * FROM task_list WHERE deletedLocally = 0 ORDER BY isDefault DESC, title COLLATE NOCASE LIMIT 1")
    suspend fun defaultList(): TaskListEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(list: TaskListEntity)

    @Update
    suspend fun update(list: TaskListEntity)

    @Query("DELETE FROM task_list WHERE localId = :localId")
    suspend fun delete(localId: String)
}

/** A task with the title of its list, for rows that show "List · when". */
data class TaskWithList(@Embedded val task: TaskEntity, val listTitle: String)

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.*, l.title AS listTitle FROM task t JOIN task_list l ON l.localId = t.listId
        WHERE t.listId = :listId AND t.completed = 0 AND t.deletedLocally = 0
        ORDER BY t.dueDate IS NULL, t.dueDate, t.position, t.localUpdatedAt DESC
        """
    )
    fun observeOpenInList(listId: String): Flow<List<TaskWithList>>

    @Query(
        """
        SELECT t.*, l.title AS listTitle FROM task t JOIN task_list l ON l.localId = t.listId
        WHERE t.listId = :listId AND t.completed = 1 AND t.deletedLocally = 0
        ORDER BY t.completedAt DESC
        """
    )
    fun observeCompletedInList(listId: String): Flow<List<TaskWithList>>

    /** Open tasks due on or before [lastDay] across every list: the "today" section (overdue, today, tomorrow). */
    @Query(
        """
        SELECT t.*, l.title AS listTitle FROM task t JOIN task_list l ON l.localId = t.listId
        WHERE t.completed = 0 AND t.deletedLocally = 0 AND l.deletedLocally = 0
            AND t.dueDate IS NOT NULL AND t.dueDate <= :lastDay
        ORDER BY t.dueDate, t.position, t.localUpdatedAt DESC
        """
    )
    fun observeDueBy(lastDay: Long): Flow<List<TaskWithList>>

    /** The "done" section, newest first. */
    @Query(
        """
        SELECT t.*, l.title AS listTitle FROM task t JOIN task_list l ON l.localId = t.listId
        WHERE t.completed = 1 AND t.deletedLocally = 0 AND l.deletedLocally = 0
        ORDER BY t.completedAt DESC LIMIT :limit
        """
    )
    fun observeRecentlyCompleted(limit: Int): Flow<List<TaskWithList>>

    /** Titles and details containing [pattern] (a LIKE pattern escaped with a backslash), grouped by list. */
    @Query(
        """
        SELECT t.*, l.title AS listTitle FROM task t JOIN task_list l ON l.localId = t.listId
        WHERE t.deletedLocally = 0 AND l.deletedLocally = 0
            AND (t.title LIKE :pattern ESCAPE '\' OR t.notes LIKE :pattern ESCAPE '\')
        ORDER BY l.isDefault DESC, l.title COLLATE NOCASE, t.completed, t.title COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun observeSearch(pattern: String, limit: Int): Flow<List<TaskWithList>>

    @Query("SELECT * FROM task WHERE localId = :localId")
    suspend fun get(localId: String): TaskEntity?

    @Query("SELECT * FROM task WHERE localId = :localId")
    fun observe(localId: String): Flow<TaskEntity?>

    @Query("SELECT localId FROM task WHERE listId = :listId")
    suspend fun idsInList(listId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE task SET deletedLocally = 1 WHERE listId = :listId")
    suspend fun markDeletedInList(listId: String)

    @Query("DELETE FROM task WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step WHERE taskId = :taskId ORDER BY sortOrder")
    suspend fun forTask(taskId: String): List<StepEntity>

    @Query("SELECT * FROM step WHERE taskId = :taskId AND deletedLocally = 0 ORDER BY sortOrder")
    fun observeForTask(taskId: String): Flow<List<StepEntity>>

    @Query("SELECT localId FROM step WHERE taskId IN (:taskIds)")
    suspend fun idsForTasks(taskIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM step WHERE taskId = :taskId AND deletedLocally = 0")
    suspend fun count(taskId: String): Int

    @Query("SELECT * FROM step WHERE localId = :localId")
    suspend fun get(localId: String): StepEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM step WHERE taskId = :taskId")
    suspend fun maxOrder(taskId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(step: StepEntity)

    @Update
    suspend fun update(step: StepEntity)

    @Query("DELETE FROM step WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operation ORDER BY seq")
    suspend fun all(): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operation")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_operation WHERE entity = :entity AND entityLocalId = :localId ORDER BY seq")
    suspend fun forEntity(entity: EntityType, localId: String): List<PendingOperationEntity>

    @Insert
    suspend fun insert(op: PendingOperationEntity): Long

    @Update
    suspend fun update(op: PendingOperationEntity)

    @Query("DELETE FROM pending_operation WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("DELETE FROM pending_operation WHERE entity = :entity AND entityLocalId = :localId")
    suspend fun deleteForEntity(entity: EntityType, localId: String)

    @Query("DELETE FROM pending_operation WHERE entity = :entity AND entityLocalId IN (:localIds)")
    suspend fun deleteForEntities(entity: EntityType, localIds: List<String>)
}

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY at DESC")
    fun observeAll(): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_log")
    suspend fun clear()
}
