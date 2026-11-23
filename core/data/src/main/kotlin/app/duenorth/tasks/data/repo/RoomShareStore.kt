package app.duenorth.tasks.data.repo

import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.ShareFieldEntity
import app.duenorth.tasks.data.db.SharePeerEntity
import app.duenorth.tasks.data.db.ShareRosterEntity
import app.duenorth.tasks.data.db.SharedListEntity
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.SyncLogEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.share.FieldRecord
import app.duenorth.tasks.share.Hlc
import app.duenorth.tasks.share.ListState
import app.duenorth.tasks.share.Peer
import app.duenorth.tasks.share.RosterEntry
import app.duenorth.tasks.share.RosterKind
import app.duenorth.tasks.share.ShareStore
import app.duenorth.tasks.share.SharedList
import app.duenorth.tasks.share.StepState
import app.duenorth.tasks.share.TaskState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [ShareStore] over Room (spec 011). Writes go straight to the task tables: on "this phone only"
 * nothing is queued for a service (spec 010 FR-701). Call off the main thread.
 */
class RoomShareStore(
    private val db: DueNorthDatabase,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ShareStore {
    private val dao = db.shareDao()

    override fun <T> transaction(block: () -> T): T = db.runInTransaction<T> { block() }

    override fun isLocalOnly(): Boolean = dao.provider() == ProviderKind.LOCAL.name

    override fun sharedLists(): List<SharedList> = dao.sharedLists().map { it.toModel() }

    override fun sharedList(listId: String): SharedList? = dao.sharedList(listId)?.toModel()

    override fun saveSharedList(list: SharedList) {
        dao.saveSharedList(SharedListEntity(list.listId, list.owner, list.syncCompleted, list.linkedAt, list.left))
    }

    override fun unlink(listId: String) {
        dao.deleteFields(listId)
        dao.deleteRoster(listId)
        dao.deleteSharedList(listId)
    }

    override fun roster(listId: String): List<RosterEntry> = dao.roster(listId).map {
        RosterEntry(it.listId, it.member, it.name, RosterKind.valueOf(it.kind), it.actor, Hlc(it.ms, it.n), it.sig)
    }

    override fun addRoster(entry: RosterEntry) {
        dao.addRoster(
            ShareRosterEntity(entry.sig, entry.list, entry.member, entry.name, entry.kind.name, entry.by, entry.clock.ms, entry.clock.n)
        )
    }

    override fun records(listId: String): List<FieldRecord> = dao.fields(listId).map {
        FieldRecord(
            it.listId,
            it.entity,
            it.field,
            it.value,
            Hlc(it.ms, it.n),
            it.author,
            it.prevMs?.let { ms -> Hlc(ms, it.prevN ?: 0) },
            it.sig
        )
    }

    override fun putRecord(record: FieldRecord) {
        dao.putField(
            ShareFieldEntity(
                record.list,
                record.entity,
                record.field,
                record.value,
                record.clock.ms,
                record.clock.n,
                record.author,
                record.prev?.ms,
                record.prev?.n,
                record.sig
            )
        )
    }

    override fun peers(): List<Peer> = dao.peers().map { it.toModel() }

    override fun peer(id: String): Peer? = dao.peer(id)?.toModel()

    override fun savePeer(peer: Peer) {
        dao.savePeer(SharePeerEntity(peer.id, dhPublic = peer.dhPublic, name = peer.name, verifiedAt = peer.verifiedAt, lastTapAt = peer.lastTapAt))
    }

    override fun read(listId: String): ListState? {
        val list = dao.list(listId)?.takeUnless { it.deletedLocally } ?: return null
        val tasks = dao.tasks(listId).map { t ->
            TaskState(
                id = t.localId,
                title = t.title,
                notes = t.notes,
                due = t.dueDate?.toString(),
                doneAt = if (t.completed) (t.completedAt ?: t.localUpdatedAt).toString() else null,
                star = t.important,
                order = t.position,
                steps = dao.steps(t.localId).map { StepState(it.localId, it.title, it.done) },
                updatedMs = t.localUpdatedAt.toEpochMilli()
            )
        }
        return ListState(list.localId, list.title, tasks, list.localUpdatedAt.toEpochMilli())
    }

    override fun write(state: ListState) {
        val existing = dao.list(state.id)
        val updated = Instant.ofEpochMilli(state.updatedMs.coerceAtLeast(0))
        dao.saveList(
            existing?.copy(title = state.title)
                ?: TaskListEntity(localId = state.id, title = state.title, localUpdatedAt = updated)
        )
        val keep = state.tasks.map { it.id }.toSet()
        dao.tasks(state.id).filter { it.localId !in keep }.forEach { dao.deleteTask(it.localId) }
        state.tasks.forEach { writeTask(state.id, it) }
    }

    private fun writeTask(listId: String, t: TaskState) {
        val old = dao.task(t.id)
        val doneAt = t.doneAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val base = old ?: TaskEntity(localId = t.id, listId = listId, title = t.title, localUpdatedAt = now())
        val row = base.copy(
            listId = listId,
            title = t.title,
            notes = t.notes,
            dueDate = t.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            completed = t.doneAt != null,
            completedAt = doneAt,
            important = t.star,
            position = t.order,
            localUpdatedAt = if (t.updatedMs > 0) Instant.ofEpochMilli(t.updatedMs) else base.localUpdatedAt,
            deletedLocally = false
        )
        if (row != old) dao.saveTask(row)
        val oldSteps = dao.steps(t.id).map { StepState(it.localId, it.title, it.done) }
        if (oldSteps != t.steps) {
            dao.deleteSteps(t.id)
            dao.insertSteps(t.steps.mapIndexed { i, s -> StepEntity(s.id, t.id, title = s.title, done = s.done, sortOrder = i) })
        }
    }

    override fun createCopy(state: ListState): String {
        val listId = newId()
        dao.saveList(TaskListEntity(localId = listId, title = state.title, localUpdatedAt = now()))
        state.tasks.forEach { t ->
            writeTask(listId, t.copy(id = newId(), steps = t.steps.map { it.copy(id = newId()) }, updatedMs = now().toEpochMilli()))
        }
        return listId
    }

    override fun logConflict(summary: String, losing: String?) {
        dao.log(SyncLogEntity(at = now(), type = SyncLogType.CONFLICT, summary = summary, losingVersionJson = losing))
    }

    /** Spec 011 FR-812: leaving "this phone only" drops all share data; the lists stay. */
    fun clearAll() = transaction {
        dao.clearFields()
        dao.clearRosters()
        dao.clearSharedLists()
        dao.clearPeers()
    }

    private fun SharedListEntity.toModel() = SharedList(listId, owner, syncCompleted, linkedAt, left)

    private fun SharePeerEntity.toModel() = Peer(id, dhPublic, name, verifiedAt, lastTapAt)
}
