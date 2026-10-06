package app.duenorth.tasks.data.db

import androidx.room.ColumnInfo
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
    val deletedLocally: Boolean = false,
    /** Shared with other people (Microsoft To Do only). Copied from the service on every sync. */
    @ColumnInfo(defaultValue = "0") val isShared: Boolean = false,
    /** The signed-in user owns this list; only the owner can rename or delete it. */
    @ColumnInfo(defaultValue = "1") val isOwner: Boolean = true
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
    val deletedLocally: Boolean = false,
    /** Where the task was assigned to the user from (Google Docs or Chat); read-only. */
    @ColumnInfo(defaultValue = "NONE") val assignmentSource: AssignmentSource = AssignmentSource.NONE,
    /** Opens the task where it was assigned; null when not assigned. */
    val assignmentLink: String? = null
)

/** Mirrors the provider's assignment source, plus NONE for tasks nobody assigned (spec 002). */
enum class AssignmentSource { NONE, DOCUMENT, SPACE, OTHER }

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

    /** Never sent as a field; marks a task whose queued `MOVE` keeps its place over a pull's. */
    const val POSITION = "position"
}

/*
 * Templates (specs/004-templates/data-model.md): recipes kept on this phone only. No remote ids,
 * never in the outbox or the sync log. They hang off the account row like everything else, so
 * signing out or switching services removes them (FR-303).
 */

/** A list template: a name, a shade, and its [TemplateTaskEntity] rows. */
@Entity(
    tableName = "template_list",
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
data class TemplateListEntity(
    @PrimaryKey val id: String,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    val name: String,
    /** Spec 001 list shade step, -3..3; 0 is the app accent. */
    val shadeStep: Int = 0,
    val updatedAt: Instant
)

/** A task inside a list template, or a task template on its own when [templateListId] is null. */
@Entity(
    tableName = "template_task",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TemplateListEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateListId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("templateListId")]
)
data class TemplateTaskEntity(
    @PrimaryKey val id: String,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    val templateListId: String? = null,
    val title: String,
    val notes: String? = null,
    /** Only offered while connected to a service with importance (Microsoft To Do). */
    val important: Boolean = false,
    /**
     * Days from the start date (list template, may be negative) or from the day it is used (task
     * template, 0 or more). Null means no due date.
     */
    val dueOffsetDays: Int? = null,
    val sortOrder: Int = 0,
    /** Orders the "use a template" picker, most recently used first. */
    val lastUsedAt: Instant? = null
)

@Entity(
    tableName = "template_step",
    foreignKeys = [
        ForeignKey(
            entity = TemplateTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateTaskId")]
)
data class TemplateStepEntity(
    @PrimaryKey val id: String,
    val templateTaskId: String,
    val title: String,
    val sortOrder: Int
)
