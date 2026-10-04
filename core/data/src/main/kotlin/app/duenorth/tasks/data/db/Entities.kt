package app.duenorth.tasks.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.duenorth.tasks.provider.api.ProviderKind
import java.time.Instant
import java.time.LocalDate

/*
 * Room schema per specs/001-metro-todo-app/data-model.md.
 *
 * Every table hangs off the single Account row (id = 1), directly or through its list, so deleting
 * that row when the user switches service clears everything (constitution Principle III).
 */

/** The one connected service. [id] is always [ACCOUNT_ID], so a second provider cannot be stored. */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: Int = ACCOUNT_ID,
    val provider: ProviderKind,
    val displayName: String,
    val email: String?,
    /** To Do lists deltaLink; null for Google, which has no list cursor. */
    val listsCursor: String? = null,
    val lastSyncAt: Instant? = null,
    val authState: AuthState = AuthState.OK
) {
    companion object {
        const val ACCOUNT_ID = 1
    }
}

enum class AuthState { OK, NEEDS_SIGN_IN }

@Entity(
    tableName = "task_list",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("remoteId")]
)
data class TaskListEntity(
    @PrimaryKey val localId: String,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    /** Null until the list is created remotely. */
    val remoteId: String? = null,
    val title: String,
    val isDefault: Boolean = false,
    /** Google updatedMin or To Do deltaLink. */
    val tasksCursor: String? = null,
    val etag: String? = null,
    val remoteUpdatedAt: Instant? = null,
    val localUpdatedAt: Instant,
    val deletedLocally: Boolean = false
)

@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["localId"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index("remoteId"), Index("dueDate"), Index("completedAt")]
)
data class TaskEntity(
    @PrimaryKey val localId: String,
    val listId: String,
    val remoteId: String? = null,
    val title: String,
    /** Shown as "details" in the UI. */
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    /** Microsoft To Do only; hidden when the provider cannot store it. */
    val important: Boolean = false,
    /** Google position, or a local order key. */
    val position: String? = null,
    /** Keeps To Do's inProgress and friends so un-completing restores them. */
    val remoteStatusRaw: String? = null,
    val etag: String? = null,
    val remoteUpdatedAt: Instant? = null,
    val localUpdatedAt: Instant,
    val deletedLocally: Boolean = false
)

@Entity(
    tableName = "step",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["localId"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class StepEntity(
    @PrimaryKey val localId: String,
    val taskId: String,
    /** Google child task id or To Do checklistItem id. */
    val remoteId: String? = null,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int,
    val deletedLocally: Boolean = false
)

/** One queued change to push, in [seq] order (the outbox, research R9). */
@Entity(
    tableName = "pending_operation",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index(value = ["entity", "entityLocalId"])]
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    val entity: EntityType,
    val entityLocalId: String,
    val kind: OperationKind,
    /** Field names to PATCH, for [OperationKind.UPDATE]. */
    val changedFields: Set<String> = emptySet(),
    val attempts: Int = 0,
    val createdAt: Instant
)

enum class EntityType { LIST, TASK, STEP }

enum class OperationKind { CREATE, UPDATE, DELETE, MOVE }

@Entity(
    tableName = "sync_log",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    val at: Instant,
    val type: SyncLogType,
    val summary: String,
    val losingVersionJson: String? = null
)

enum class SyncLogType { CONFLICT, ERROR, RECOVERED }

/** Field names used in [PendingOperationEntity.changedFields]. */
object Fields {
    const val TITLE = "title"
    const val NOTES = "notes"
    const val DUE_DATE = "dueDate"
    const val COMPLETED = "completed"
    const val IMPORTANT = "important"
    const val LIST = "list"
    const val DONE = "done"
}
