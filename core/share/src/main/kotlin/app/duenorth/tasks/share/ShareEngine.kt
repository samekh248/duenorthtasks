package app.duenorth.tasks.share

/** What happened to one list during a tap. */
data class ListResult(
    val listId: String,
    val title: String,
    /** Records received, grouped by author id. */
    val receivedBy: Map<String, Int> = emptyMap(),
    val sent: Int = 0,
    val conflicts: List<String> = emptyList()
)

/**
 * The merge rules of share by tap (FR-805 to FR-809), independent of NFC.
 *
 * Changes are found by comparing the phone's rows with the last record for each field
 * ([capture]); nothing hooks the repository. Phones swap per-author summaries ([summary]) and
 * send only newer records ([missing]); [receive] keeps, per field, the record that [beats] the
 * other and rewrites the rows from the records.
 */
class ShareEngine(
    private val store: ShareStore,
    private val me: Identity,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** Starts sharing [listId] with this phone as its first member and starter. */
    fun start(listId: String, myName: String): SharedList = store.transaction {
        store.sharedList(listId)?.takeIf { !it.left }?.let { return@transaction it }
        val shared = SharedList(listId, owner = me.id, linkedAt = now())
        store.saveSharedList(shared)
        store.addRoster(RosterEntry(listId, me.id, myName, RosterKind.ADD, me.id, nextClock(listId)).signedBy(me))
        capture(listId)
        shared
    }

    fun roster(listId: String): Roster = Roster(store.roster(listId))

    /** Adds [peer] to [listId]; the entry spreads to the others on later taps. */
    fun add(listId: String, memberId: String, name: String): RosterEntry = store.transaction {
        val roster = roster(listId)
        check(me.id in roster.members) { "only members can add people" }
        check(memberId in roster.members || roster.members.size < Roster.MAX_MEMBERS) { "the list is full" }
        RosterEntry(
            listId,
            memberId,
            name,
            RosterKind.ADD,
            me.id,
            nextClock(listId)
        ).signedBy(me).also(store::addRoster)
    }

    /** Only the starter can remove someone (FR-808). */
    fun remove(listId: String, memberId: String) = store.transaction {
        val roster = roster(listId)
        check(roster.owner == me.id && memberId != me.id) { "only the starter can remove people" }
        store.addRoster(
            RosterEntry(
                listId,
                memberId,
                roster.name(memberId).orEmpty(),
                RosterKind.REMOVE,
                me.id,
                nextClock(listId)
            ).signedBy(me)
        )
    }

    /**
     * Leaves [listId] (FR-808, FR-812): a signed "left" entry, then the list becomes an ordinary list
     * here. Its roster stays, so the departure reaches the others at the next tap with any of them.
     */
    fun leave(listId: String) = store.transaction {
        val shared = store.sharedList(listId) ?: return@transaction
        if (shared.left) return@transaction
        val name = roster(listId).name(me.id).orEmpty()
        store.addRoster(RosterEntry(listId, me.id, name, RosterKind.LEAVE, me.id, nextClock(listId)).signedBy(me))
        store.saveSharedList(shared.copy(left = true))
    }

    /** Lists this phone is still in. */
    fun active(): List<SharedList> = store.sharedLists().filter { !it.left }

    fun setSyncCompleted(listId: String, on: Boolean) = store.transaction {
        store.sharedList(listId)?.let { store.saveSharedList(it.copy(syncCompleted = on)) }
    }

    /** Lists shared with [peerId] that this phone is still in. */
    fun listsWith(peerId: String): List<String> = active().map { it.listId }.filter { id ->
        val members = roster(id).members
        me.id in members && peerId in members
    }

    /** Turns differences between the rows and the records into new signed records by this phone. */
    fun capture(listId: String): Int {
        val shared = store.sharedList(listId)?.takeIf { !it.left } ?: return 0
        val rows = store.read(listId) ?: return 0
        val records = store.records(listId).associateBy { it.entity to it.field }
        val view = materialize(listId, rows.title, records.values)
        var made = 0
        var myLast = summaryOf(listId)[me.id] ?: Hlc.ZERO
        fun emit(entity: String, field: String, value: String?, editedMs: Long) {
            val old = records[entity to field]
            var clock = Hlc(minOf(editedMs, now()), 0)
            if (clock <= myLast) clock = myLast.next()
            if (old != null && clock <= old.clock) clock = Hlc(old.clock.ms, old.clock.n + 1)
            myLast = clock
            store.putRecord(FieldRecord(listId, entity, field, value, clock, me.id, old?.clock).signedBy(me))
            made++
        }

        if (records[Fields.LIST to Fields.TITLE]?.value !=
            rows.title
        ) {
            emit(Fields.LIST, Fields.TITLE, rows.title, rows.updatedMs)
        }
        val shown = view.tasks.associateBy { it.id }
        for (task in rows.tasks) {
            val seen = shown[task.id]
            for (field in Fields.TASK) {
                if (field == Fields.DONE && !shared.syncCompleted) continue
                val value = task.value(field)
                val known = records[task.id to field]
                if (known == null || known.value != value) emit(task.id, field, value, task.updatedMs)
            }
            if (seen == null && records[task.id to Fields.DELETED]?.value == "1") {
                emit(task.id, Fields.DELETED, "0", task.updatedMs)
            }
        }
        val rowIds = rows.tasks.map { it.id }.toSet()
        for (gone in shown.keys - rowIds) emit(gone, Fields.DELETED, "1", now())
        return made
    }

    /** The newest clock this phone holds from each author, for one list. */
    fun summary(listId: String): Map<String, Hlc> = summaryOf(listId)

    /** Roster entries and records newer than [theirs], from any author. */
    fun missing(listId: String, theirs: Map<String, Hlc>): Bundle {
        fun newer(author: String, clock: Hlc) = clock > (theirs[author] ?: Hlc(-1, 0))
        return Bundle(
            roster = store.roster(listId).filter { newer(it.by, it.clock) },
            records = store.records(listId).filter { newer(it.author, it.clock) }
        )
    }

    /** Everything about [listId], for a new member (their summary is empty). */
    fun everything(listId: String): Bundle = missing(listId, emptyMap())

    /**
     * Merges what another phone sent for [listId] and rewrites the rows. Roster entries first, so
     * a new member's own records are accepted; then records signed by anyone who was ever a member.
     */
    fun receive(listId: String, bundle: Bundle): ListResult {
        bundle.roster.filter { it.list == listId && it.verify() }.forEach(store::addRoster)
        val roster = roster(listId)
        val shared = store.sharedList(listId)
        if (shared != null && !shared.left && me.id !in roster.members) {
            // Removed by the starter: keep the list as it is, as an ordinary list.
            store.saveSharedList(shared.copy(left = true))
        }
        if (store.sharedList(listId)?.left == true) return ListResult(listId, store.read(listId)?.title.orEmpty())
        val existing = store.records(listId).associateBy { it.entity to it.field }.toMutableMap()
        val byAuthor = mutableMapOf<String, Int>()
        val conflicts = mutableListOf<String>()
        for (r in bundle.records) {
            if (r.list != listId || r.author !in roster.everMembers || !r.verify()) continue
            byAuthor.merge(r.author, 1, Int::plus)
            val old = existing[r.entity to r.field]
            if (old == null) {
                store.putRecord(r)
                existing[r.entity to r.field] = r
                continue
            }
            if (old.sig == r.sig) continue
            val concurrent =
                old.author != r.author && r.prev != old.clock && old.prev != r.clock && old.value != r.value
            val winner = if (r.beats(old)) r else old
            val loser = if (winner === r) old else r
            if (winner === r) {
                store.putRecord(r)
                existing[r.entity to r.field] = r
            }
            if (concurrent && r.field != Fields.DELETED) {
                val who = roster.name(winner.author) ?: "someone"
                val what = describe(existing, r.entity, r.field)
                val text = "$what changed on two phones; $who's change was kept"
                conflicts += text
                store.logConflict(text, loser.value)
            }
        }
        val rows = store.read(listId)
        val view = materialize(listId, rows?.title.orEmpty(), existing.values)
        if (bundle.records.isNotEmpty()) store.write(merge(rows, view))
        return ListResult(listId, view.title, byAuthor, conflicts = conflicts)
    }

    /** The list as the records describe it. */
    fun materialize(listId: String, fallbackTitle: String, records: Collection<FieldRecord>): ListState {
        val title = records.firstOrNull { it.entity == Fields.LIST && it.field == Fields.TITLE }?.value ?: fallbackTitle
        val tasks = records.filter { it.entity != Fields.LIST }.groupBy { it.entity }.mapNotNull { (id, rs) ->
            val deleted = rs.firstOrNull { it.field == Fields.DELETED }
            val lastEdit = rs.filter { it.field != Fields.DELETED }.maxOfOrNull { it.clock }
            val gone = deleted?.value == "1" && (lastEdit == null || lastEdit <= deleted.clock)
            if (gone) return@mapNotNull null
            if (rs.none { it.field == Fields.TITLE }) return@mapNotNull null
            rs.fold(TaskState(id, "")) { t, r -> if (r.field == Fields.DELETED) t else t.with(r.field, r.value) }
                .copy(updatedMs = rs.maxOf { it.clock.ms })
        }
        return ListState(listId, title, tasks, records.maxOfOrNull { it.clock.ms } ?: 0)
    }

    /** Keeps local-only facts (such as fields the records don't carry) from the current rows. */
    private fun merge(rows: ListState?, view: ListState): ListState {
        if (rows == null) return view
        val doneLocal = rows.tasks.associateBy { it.id }
        val keepDone = store.sharedList(view.id)?.syncCompleted == false
        return view.copy(
            tasks = view.tasks.map { t -> if (keepDone) t.copy(doneAt = doneLocal[t.id]?.doneAt) else t }
        )
    }

    private fun describe(records: Map<Pair<String, String>, FieldRecord>, entity: String, field: String): String {
        if (entity == Fields.LIST) return "The list name"
        val title = records[entity to Fields.TITLE]?.value ?: "A task"
        val what = when (field) {
            Fields.TITLE -> "name"
            Fields.NOTES -> "details"
            Fields.DUE -> "due date"
            Fields.DONE -> "tick"
            Fields.STAR -> "star"
            Fields.ORDER -> "place"
            Fields.STEPS -> "steps"
            else -> field
        }
        return "“$title” $what"
    }

    private fun summaryOf(listId: String): Map<String, Hlc> {
        val out = mutableMapOf<String, Hlc>()
        store.records(listId).forEach { r -> out.merge(r.author, r.clock) { a, b -> maxOf(a, b) } }
        store.roster(listId).forEach { e -> out.merge(e.by, e.clock) { a, b -> maxOf(a, b) } }
        return out
    }

    /** A clock later than anything this phone has seen for [listId], so roster changes order correctly. */
    internal fun nextClock(listId: String): Hlc {
        val seen =
            (store.records(listId).map { it.clock } + store.roster(listId).map { it.clock }).maxOrNull() ?: Hlc.ZERO
        val wall = Hlc(now(), 0)
        return if (wall > seen) wall else seen.next()
    }
}
