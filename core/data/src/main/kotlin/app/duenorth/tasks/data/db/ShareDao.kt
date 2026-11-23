package app.duenorth.tasks.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Share by tap's reads and writes. Blocking on purpose: a tap runs on an NFC worker thread inside
 * one transaction (`RoomShareStore`). The flows are for the screens.
 */
@Dao
interface ShareDao {
    @Query("SELECT provider FROM account WHERE id = 1")
    fun provider(): String?

    @Query("SELECT * FROM shared_list")
    fun sharedLists(): List<SharedListEntity>

    @Query("SELECT * FROM shared_list")
    fun observeSharedLists(): Flow<List<SharedListEntity>>

    @Query("SELECT * FROM shared_list WHERE listId = :listId")
    fun sharedList(listId: String): SharedListEntity?

    @Upsert
    fun saveSharedList(list: SharedListEntity)

    @Query("DELETE FROM shared_list WHERE listId = :listId")
    fun deleteSharedList(listId: String)

    @Query("SELECT * FROM share_roster WHERE listId = :listId")
    fun roster(listId: String): List<ShareRosterEntity>

    @Query("SELECT * FROM share_roster WHERE listId = :listId")
    fun observeRoster(listId: String): Flow<List<ShareRosterEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addRoster(entry: ShareRosterEntity)

    @Query("DELETE FROM share_roster WHERE listId = :listId")
    fun deleteRoster(listId: String)

    @Query("SELECT * FROM share_field WHERE listId = :listId")
    fun fields(listId: String): List<ShareFieldEntity>

    @Upsert
    fun putField(field: ShareFieldEntity)

    @Query("DELETE FROM share_field WHERE listId = :listId")
    fun deleteFields(listId: String)

    @Query("SELECT * FROM share_peer")
    fun peers(): List<SharePeerEntity>

    @Query("SELECT * FROM share_peer")
    fun observePeers(): Flow<List<SharePeerEntity>>

    @Query("SELECT * FROM share_peer WHERE id = :id")
    fun peer(id: String): SharePeerEntity?

    @Upsert
    fun savePeer(peer: SharePeerEntity)

    @Query("DELETE FROM shared_list")
    fun clearSharedLists()

    @Query("DELETE FROM share_peer")
    fun clearPeers()

    @Query("DELETE FROM share_roster")
    fun clearRosters()

    @Query("DELETE FROM share_field")
    fun clearFields()

    // The list itself, read and written without the outbox ("this phone only" queues nothing).

    @Query("SELECT * FROM task_list WHERE localId = :listId")
    fun list(listId: String): TaskListEntity?

    @Upsert
    fun saveList(list: TaskListEntity)

    @Query("SELECT * FROM task WHERE listId = :listId AND deletedLocally = 0")
    fun tasks(listId: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE localId = :id")
    fun task(id: String): TaskEntity?

    @Upsert
    fun saveTask(task: TaskEntity)

    @Query("DELETE FROM task WHERE localId = :id")
    fun deleteTask(id: String)

    @Query("SELECT * FROM step WHERE taskId = :taskId AND deletedLocally = 0 ORDER BY sortOrder")
    fun steps(taskId: String): List<StepEntity>

    @Query("DELETE FROM step WHERE taskId = :taskId")
    fun deleteSteps(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSteps(steps: List<StepEntity>)

    @Insert
    fun log(entry: SyncLogEntity)
}
