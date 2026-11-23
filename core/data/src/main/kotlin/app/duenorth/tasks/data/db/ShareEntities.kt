package app.duenorth.tasks.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/*
 * Share by tap (specs/011-share-by-tap). Share data hangs off its list, so deleting the list (or
 * the account row on a service switch) removes it. Peers hang off the account row.
 */

@Entity(
    tableName = "shared_list",
    primaryKeys = ["listId"],
    foreignKeys = [
        ForeignKey(entity = TaskListEntity::class, parentColumns = ["localId"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class SharedListEntity(
    val listId: String,
    /** Identity (hex Ed25519 key) of the member who started the list. */
    val owner: String,
    val syncCompleted: Boolean = true,
    val linkedAt: Long,
    val left: Boolean = false
)

/** One signed roster entry; [sig] is unique per entry. */
@Entity(
    tableName = "share_roster",
    primaryKeys = ["sig"],
    foreignKeys = [
        ForeignKey(entity = TaskListEntity::class, parentColumns = ["localId"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("listId")]
)
data class ShareRosterEntity(
    val sig: String,
    val listId: String,
    val member: String,
    val name: String,
    val kind: String,
    val actor: String,
    val ms: Long,
    val n: Int
)

/** The latest signed record for one field of a shared list or task. */
@Entity(
    tableName = "share_field",
    primaryKeys = ["listId", "entity", "field"],
    foreignKeys = [
        ForeignKey(entity = TaskListEntity::class, parentColumns = ["localId"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ShareFieldEntity(
    val listId: String,
    val entity: String,
    val field: String,
    val value: String?,
    val ms: Long,
    val n: Int,
    val author: String,
    val prevMs: Long?,
    val prevN: Int?,
    val sig: String
)

/** A phone met with a tap and verified with safety tiles. */
@Entity(
    tableName = "share_peer",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("accountId")]
)
data class SharePeerEntity(
    val id: String,
    val accountId: Int = AccountEntity.ACCOUNT_ID,
    val dhPublic: String,
    val name: String,
    val verifiedAt: Long,
    val lastTapAt: Long?
)
