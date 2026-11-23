package app.duenorth.tasks.share

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A hybrid logical clock value (FR-805): wall-clock milliseconds plus a counter that breaks ties and
 * keeps one author's records strictly increasing even when its clock stalls or jumps back.
 */
@Serializable
data class Hlc(val ms: Long, val n: Int = 0) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::ms, Hlc::n)

    fun next(): Hlc = Hlc(ms, n + 1)

    companion object {
        val ZERO = Hlc(0, 0)
    }
}

/** What each shared field is called on the wire (FR-805). */
object Fields {
    const val LIST = "list"

    const val TITLE = "title"
    const val NOTES = "notes"
    const val DUE = "due"
    const val DONE = "done"
    const val STAR = "star"
    const val ORDER = "order"
    const val STEPS = "steps"
    const val DELETED = "deleted"

    val TASK = listOf(TITLE, NOTES, DUE, DONE, STAR, ORDER, STEPS)
}

/**
 * The latest value of one field of one list or task, as its author wrote it (FR-805). [entity] is
 * [Fields.LIST] for the list itself, otherwise a task id (task ids are the same on every phone).
 * [prev] is the clock of the record its author replaced, which tells a concurrent edit (a conflict)
 * from an ordinary update.
 */
@Serializable
data class FieldRecord(
    val list: String,
    val entity: String,
    val field: String,
    val value: String?,
    val clock: Hlc,
    val author: String,
    val prev: Hlc? = null,
    val sig: String = ""
) {
    /** Later clock wins; equal clocks fall back to the author key so every phone picks the same one. */
    fun beats(other: FieldRecord): Boolean = clock > other.clock || (clock == other.clock && author > other.author)

    fun signedBy(me: Identity): FieldRecord {
        require(me.id == author) { "only the author signs" }
        return copy(sig = b64(me.sign(signingBytes())))
    }

    fun verify(): Boolean = Identity.verify(author, signingBytes(), unb64(sig))

    internal fun signingBytes(): ByteArray = canonical("rec", list, entity, field, value, clock, author, prev)
}

enum class RosterKind { ADD, REMOVE, LEAVE }

/**
 * One signed change to who is in a shared list (FR-808): [by] added, removed (only the list's
 * starter) or left (only the member themselves).
 */
@Serializable
data class RosterEntry(
    val list: String,
    val member: String,
    val name: String,
    val kind: RosterKind,
    val by: String,
    val clock: Hlc,
    val sig: String = ""
) {
    fun signedBy(me: Identity): RosterEntry {
        require(me.id == by) { "only the actor signs" }
        return copy(sig = b64(me.sign(signingBytes())))
    }

    fun verify(): Boolean = Identity.verify(by, signingBytes(), unb64(sig))

    internal fun signingBytes(): ByteArray = canonical("roster", list, member, name, kind.name, clock, by, null)
}

/** Who is in a list, worked out from every roster entry either phone has seen. */
class Roster(entries: Collection<RosterEntry>) {
    private val valid: List<RosterEntry>

    /** The member who started the list: the first one to add themselves. */
    val owner: String?

    init {
        val sorted = entries.filter { it.verify() }.distinctBy { it.sig }.sortedWith(compareBy({ it.clock }, { it.by }))
        owner = sorted.firstOrNull { it.kind == RosterKind.ADD && it.member == it.by }?.member
        // Who was ever added by someone entitled to add, regardless of the order of their clocks.
        val everIn = mutableSetOf<String>()
        owner?.let { everIn += it }
        var grew = true
        while (grew) {
            grew = false
            for (e in sorted) {
                if (e.kind == RosterKind.ADD && e.by in everIn && everIn.add(e.member)) grew = true
            }
        }
        val ok = sorted.filter { e ->
            when (e.kind) {
                RosterKind.ADD -> e.by in everIn
                RosterKind.REMOVE -> e.by == owner && e.member != owner
                RosterKind.LEAVE -> e.by == e.member
            }
        }
        valid = ok
    }

    val entries: List<RosterEntry> get() = valid

    /** Current members: those whose latest entry is an ADD. */
    val members: Set<String> = valid.groupBy { it.member }
        .filterValues { es -> es.maxWith(compareBy({ it.clock }, { it.by })).kind == RosterKind.ADD }
        .keys

    /** Anyone who was ever a member; their past changes still count. */
    val everMembers: Set<String> = valid.filter { it.kind == RosterKind.ADD }.map { it.member }.toSet()

    fun name(member: String): String? = valid.lastOrNull { it.member == member && it.kind == RosterKind.ADD }?.name

    fun addedBy(member: String): String? = valid.lastOrNull { it.member == member && it.kind == RosterKind.ADD }?.by

    companion object {
        const val MAX_MEMBERS = 8
    }
}

/** A list as the phone shows it: what [ShareStore.read] returns and [ShareStore.write] takes. */
@Serializable
data class ListState(
    val id: String,
    val title: String,
    val tasks: List<TaskState>,
    /** When the title last changed on this phone (millis). */
    val updatedMs: Long = 0
)

@Serializable
data class TaskState(
    val id: String,
    val title: String,
    val notes: String? = null,
    /** ISO date. */
    val due: String? = null,
    /** ISO instant the task was completed; null while open. */
    val doneAt: String? = null,
    val star: Boolean = false,
    val order: String? = null,
    val steps: List<StepState> = emptyList(),
    /** When the task last changed on this phone (millis). */
    val updatedMs: Long = 0
) {
    fun value(field: String): String? = when (field) {
        Fields.TITLE -> title
        Fields.NOTES -> notes
        Fields.DUE -> due
        Fields.DONE -> doneAt
        Fields.STAR -> if (star) "1" else "0"
        Fields.ORDER -> order
        Fields.STEPS -> Wire.json.encodeToString(steps)
        else -> error("unknown field $field")
    }

    fun with(field: String, value: String?): TaskState = when (field) {
        Fields.TITLE -> copy(title = value.orEmpty())
        Fields.NOTES -> copy(notes = value)
        Fields.DUE -> copy(due = value)
        Fields.DONE -> copy(doneAt = value)
        Fields.STAR -> copy(star = value == "1")
        Fields.ORDER -> copy(order = value)
        Fields.STEPS -> copy(
            steps = value?.let {
                runCatching { Wire.json.decodeFromString<List<StepState>>(it) }.getOrNull()
            }.orEmpty()
        )
        else -> this
    }
}

@Serializable
data class StepState(val id: String, val title: String, val done: Boolean = false)

/** A list's share facts on this phone. */
data class SharedList(
    val listId: String,
    val owner: String,
    val syncCompleted: Boolean = true,
    val linkedAt: Long,
    /**
     * This phone left or was removed. The list stays as an ordinary list; its roster is kept so the
     * departure still reaches the others at the next tap.
     */
    val left: Boolean = false
)

/** A phone this one has met and verified with safety tiles (FR-804). */
data class Peer(
    val id: String,
    val dhPublic: String,
    val name: String,
    val verifiedAt: Long,
    val lastTapAt: Long? = null
)

/** What one phone sends another for one list: roster entries and field records it lacks. */
@Serializable
data class Bundle(
    @SerialName("r") val roster: List<RosterEntry> = emptyList(),
    @SerialName("f") val records: List<FieldRecord> = emptyList()
) {
    val isEmpty: Boolean get() = roster.isEmpty() && records.isEmpty()
}

internal fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

internal fun unb64(text: String): ByteArray = runCatching {
    Base64.getDecoder().decode(text)
}.getOrDefault(ByteArray(0))

/** Length-prefixed fields, so no two different records sign the same bytes. */
private fun canonical(kind: String, vararg parts: Any?): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        out.writeUTF(kind)
        for (p in parts) {
            when (p) {
                null -> out.writeByte(0)
                is String -> {
                    out.writeByte(1)
                    val b = p.toByteArray()
                    out.writeInt(b.size)
                    out.write(b)
                }
                is Hlc -> {
                    out.writeByte(2)
                    out.writeLong(p.ms)
                    out.writeInt(p.n)
                }
                else -> error("unsupported ${p::class}")
            }
        }
    }
    return bytes.toByteArray()
}
