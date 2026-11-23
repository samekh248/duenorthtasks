package app.duenorth.tasks.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Three phones that pass bundles by hand, to test the merge rules without NFC. */
class EngineTest {
    private var clock = 1_000_000L
    private val tick: () -> Long = { clock }

    inner class Phone(val name: String) {
        val id = Identity.generate()
        val store = MemoryShareStore()
        val engine = ShareEngine(store, id, tick)

        fun list(listId: String = "L") = store.lists[listId]!!

        fun edit(listId: String = "L", f: (ListState) -> ListState) {
            clock += 1000
            store.lists[listId] = f(list(listId))
        }

        fun task(taskId: String, listId: String = "L") = list(listId).tasks.first { it.id == taskId }
    }

    /** One sync tap between [a] and [b] for every list they share. */
    private fun tap(a: Phone, b: Phone): List<ListResult> {
        clock += 10
        val lists = a.engine.listsWith(b.id.id)
        lists.forEach { a.engine.capture(it) }
        lists.forEach { if (b.store.sharedList(it)?.left == false) b.engine.capture(it) }
        val aSum = lists.associateWith { a.engine.summary(it) }
        val bSum = lists.associateWith { b.engine.summary(it) }
        val toB = lists.associateWith { a.engine.missing(it, bSum[it]!!) }
        val toA = lists.associateWith { b.engine.missing(it, aSum[it]!!) }
        lists.forEach { b.engine.receive(it, toB[it]!!) }
        return lists.map { a.engine.receive(it, toA[it]!!) }
    }

    private fun share(a: Phone, b: Phone, listId: String = "L") {
        a.engine.start(listId, a.name)
        a.engine.capture(listId)
        val add = a.engine.add(listId, b.id.id, b.name)
        b.store.saveSharedList(SharedList(listId, a.id.id, linkedAt = clock))
        b.engine.receive(listId, a.engine.everything(listId))
        assertTrue(add.verify())
    }

    private val dustin = Phone("Dustin")
    private val alex = Phone("Alex")
    private val sam = Phone("Sam")

    init {
        dustin.store.lists["L"] = ListState(
            "L",
            "groceries",
            listOf(
                TaskState("t1", "Oat milk", updatedMs = 1),
                TaskState("t2", "Coffee beans", notes = "dark roast", updatedMs = 1)
            )
        )
    }

    @Test
    fun `a new member gets the whole list`() {
        share(dustin, alex)
        assertEquals("groceries", alex.list().title)
        assertEquals(listOf("Oat milk", "Coffee beans"), alex.list().tasks.map { it.title })
        assertEquals("dark roast", alex.task("t2").notes)
        assertEquals(setOf(dustin.id.id, alex.id.id), alex.engine.roster("L").members)
    }

    @Test
    fun `edits on both sides to different fields both survive`() {
        share(dustin, alex)
        dustin.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t2"
                    ) {
                        it.copy(title = "Coffee", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        alex.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t2"
                    ) {
                        it.copy(doneAt = "2026-11-23T10:00:00Z", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        tap(dustin, alex)
        for (p in listOf(dustin, alex)) {
            assertEquals("Coffee", p.task("t2").title)
            assertEquals("2026-11-23T10:00:00Z", p.task("t2").doneAt)
        }
        assertTrue(dustin.store.conflicts.isEmpty())
    }

    @Test
    fun `same field on both sides keeps the later edit and logs the other`() {
        share(dustin, alex)
        dustin.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t1"
                    ) {
                        it.copy(title = "Milk", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        alex.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t1"
                    ) {
                        it.copy(title = "Oat milk x2", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        val results = tap(dustin, alex)
        assertEquals("Oat milk x2", dustin.task("t1").title)
        assertEquals("Oat milk x2", alex.task("t1").title)
        assertEquals(1, results.single().conflicts.size)
        assertEquals(1, dustin.store.conflicts.size)
    }

    @Test
    fun `changes travel through a member who met both`() {
        share(dustin, alex)
        share(alex, sam)
        sam.edit { l -> l.copy(tasks = l.tasks + TaskState("t3", "Basil", updatedMs = clock)) }
        tap(sam, alex)
        tap(alex, dustin)
        assertEquals("Basil", dustin.task("t3").title)
        assertTrue(sam.id.id in dustin.engine.roster("L").members)
    }

    @Test
    fun `a delete beats an older edit and a later edit brings the task back`() {
        share(dustin, alex)
        alex.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t1"
                    ) {
                        it.copy(notes = "2 cartons", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        dustin.edit { l -> l.copy(tasks = l.tasks.filter { it.id != "t1" }) }
        tap(dustin, alex)
        assertTrue(alex.list().tasks.none { it.id == "t1" })
        assertTrue(dustin.list().tasks.none { it.id == "t1" })

        // Alex's phone recreates it later (an edit after the delete).
        clock += 5000
        alex.store.lists["L"] =
            alex.list().copy(tasks = alex.list().tasks + TaskState("t1", "Oat milk", updatedMs = clock))
        tap(alex, dustin)
        assertEquals("Oat milk", dustin.task("t1").title)
    }

    @Test
    fun `a removed member gets nothing new and keeps their copy`() {
        share(dustin, alex)
        share(dustin, sam)
        dustin.engine.remove("L", alex.id.id)
        dustin.edit { l -> l.copy(title = "food") }
        tap(alex, sam) // Sam doesn't know yet: still syncs with Alex
        tap(dustin, sam) // Sam learns of the removal
        assertFalse(alex.id.id in sam.engine.roster("L").members)
        assertTrue(sam.engine.listsWith(alex.id.id).isEmpty())
        // The next time Alex and Dustin meet, Alex only hears that he's out.
        val bundle = dustin.engine.missing("L", alex.engine.summary("L"))
        alex.engine.receive("L", bundle.copy(records = emptyList()))
        assertTrue(alex.store.sharedList("L")!!.left)
        assertEquals("groceries", alex.list().title)
    }

    @Test
    fun `leaving tells the others at the next tap`() {
        share(dustin, alex)
        alex.engine.leave("L")
        val bundle = alex.engine.missing("L", dustin.engine.summary("L"))
        dustin.engine.receive("L", bundle)
        assertFalse(alex.id.id in dustin.engine.roster("L").members)
        assertTrue(dustin.engine.listsWith(alex.id.id).isEmpty())
    }

    @Test
    fun `records from strangers or with bad signatures are ignored`() {
        share(dustin, alex)
        val stranger = Identity.generate()
        val forged = FieldRecord("L", "t1", Fields.TITLE, "hacked", Hlc(clock + 99_999), stranger.id).signedBy(stranger)
        val tampered = dustin.store.records("L").first { it.entity == "t2" && it.field == Fields.TITLE }
            .copy(value = "tampered", clock = Hlc(clock + 99_999))
        alex.engine.receive("L", Bundle(records = listOf(forged, tampered)))
        assertEquals("Oat milk", alex.task("t1").title)
        assertEquals("Coffee beans", alex.task("t2").title)
    }

    @Test
    fun `completed ticks stay home when the list says so`() {
        share(dustin, alex)
        dustin.engine.setSyncCompleted("L", false)
        dustin.edit { l ->
            l.copy(
                tasks = l.tasks.map {
                    if (it.id ==
                        "t1"
                    ) {
                        it.copy(doneAt = "2026-11-23T10:00:00Z", updatedMs = clock)
                    } else {
                        it
                    }
                }
            )
        }
        tap(dustin, alex)
        assertNull(alex.task("t1").doneAt)
        assertEquals("2026-11-23T10:00:00Z", dustin.task("t1").doneAt)
    }

    @Test
    fun `a group stops at eight`() {
        dustin.engine.start("L", "Dustin")
        repeat(7) { dustin.engine.add("L", Identity.generate().id, "p$it") }
        val full = runCatching { dustin.engine.add("L", Identity.generate().id, "one too many") }
        assertTrue(full.isFailure)
    }
}
