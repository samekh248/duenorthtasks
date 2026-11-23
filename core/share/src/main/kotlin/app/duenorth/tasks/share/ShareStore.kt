package app.duenorth.tasks.share

/**
 * Everything share by tap keeps on the phone. Room implements it in `:core:data`
 * (`RoomShareStore`); tests use [MemoryShareStore]. Calls block, so callers run them off the main
 * thread, and [transaction] makes a tap all or nothing (FR-806).
 */
interface ShareStore {
    fun <T> transaction(block: () -> T): T

    /** True while the account is "this phone only" (FR-800). */
    fun isLocalOnly(): Boolean

    fun sharedLists(): List<SharedList>

    fun sharedList(listId: String): SharedList?

    fun saveSharedList(list: SharedList)

    /** Drops the list's share data. The list and its tasks stay as an ordinary list. */
    fun unlink(listId: String)

    fun roster(listId: String): List<RosterEntry>

    fun addRoster(entry: RosterEntry)

    fun records(listId: String): List<FieldRecord>

    /** Replaces the record for the same list, entity and field. */
    fun putRecord(record: FieldRecord)

    fun peers(): List<Peer>

    fun peer(id: String): Peer?

    fun savePeer(peer: Peer)

    /** The list as the phone holds it now, or null when there is no such list. */
    fun read(listId: String): ListState?

    /**
     * Makes the phone's list match [state]: creates it if missing, upserts its tasks and steps,
     * and deletes tasks that aren't in it. Doesn't queue anything for a service.
     */
    fun write(state: ListState)

    /** Creates an ordinary list from [state] with fresh ids ("just a copy"); returns its id. */
    fun createCopy(state: ListState): String

    fun logConflict(summary: String, losing: String?)
}

/** An in-memory [ShareStore] for tests and previews. */
open class MemoryShareStore(var localOnly: Boolean = true) : ShareStore {
    val lists = linkedMapOf<String, ListState>()
    val shared = linkedMapOf<String, SharedList>()
    val rosters = linkedMapOf<String, MutableList<RosterEntry>>()
    val fields = linkedMapOf<String, LinkedHashMap<Pair<String, String>, FieldRecord>>()
    val peerMap = linkedMapOf<String, Peer>()
    val conflicts = mutableListOf<String>()
    private var copies = 0

    override fun <T> transaction(block: () -> T): T {
        val snapshot =
            listOf(LinkedHashMap(lists), LinkedHashMap(shared), rosters.mapValues { it.value.toMutableList() })
        val fieldSnap = fields.mapValues { LinkedHashMap(it.value) }
        val peerSnap = LinkedHashMap(peerMap)
        return try {
            block()
        } catch (e: Throwable) {
            @Suppress("UNCHECKED_CAST")
            run {
                lists.clear()
                lists.putAll(snapshot[0] as Map<String, ListState>)
                shared.clear()
                shared.putAll(snapshot[1] as Map<String, SharedList>)
                rosters.clear()
                rosters.putAll(snapshot[2] as Map<String, MutableList<RosterEntry>>)
                fields.clear()
                fields.putAll(fieldSnap)
                peerMap.clear()
                peerMap.putAll(peerSnap)
            }
            throw e
        }
    }

    override fun isLocalOnly() = localOnly

    override fun sharedLists() = shared.values.toList()

    override fun sharedList(listId: String) = shared[listId]

    override fun saveSharedList(list: SharedList) {
        shared[list.listId] = list
    }

    override fun unlink(listId: String) {
        shared.remove(listId)
        rosters.remove(listId)
        fields.remove(listId)
    }

    override fun roster(listId: String): List<RosterEntry> = rosters[listId].orEmpty().toList()

    override fun addRoster(entry: RosterEntry) {
        val l = rosters.getOrPut(entry.list) { mutableListOf() }
        if (l.none { it.sig == entry.sig }) l += entry
    }

    override fun records(listId: String): List<FieldRecord> = fields[listId]?.values?.toList().orEmpty()

    override fun putRecord(record: FieldRecord) {
        fields.getOrPut(record.list) { linkedMapOf() }[record.entity to record.field] = record
    }

    override fun peers() = peerMap.values.toList()

    override fun peer(id: String) = peerMap[id]

    override fun savePeer(peer: Peer) {
        peerMap[peer.id] = peer
    }

    override fun read(listId: String) = lists[listId]

    override fun write(state: ListState) {
        lists[state.id] = state
    }

    override fun createCopy(state: ListState): String {
        val id = "copy-${++copies}-${state.id}"
        lists[id] = state.copy(id = id, tasks = state.tasks.map { it.copy(id = "$id-${it.id}") })
        return id
    }

    override fun logConflict(summary: String, losing: String?) {
        conflicts += summary
    }
}
