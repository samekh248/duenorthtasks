package app.duenorth.tasks.share

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reader and card sessions over the APDU framing, as two phones would run them. */
class TapSessionTest {
    private class Phone(val name: String) {
        val id = Identity.generate()
        val store = MemoryShareStore()
    }

    private class Reader(var answer: Boolean? = null) : ReaderHost {
        var tiles: List<TileFace>? = null
        var met: String? = null

        override fun peerFound(name: String, tiles: List<TileFace>?) {
            met = name
            this.tiles = tiles
        }

        override fun myAnswer() = answer
    }

    private class Card(var reply: Answer? = null) : CardHost {
        var tiles: List<TileFace>? = null
        var invited: InvitePreview? = null
        var outcome: CardOutcome? = null

        override fun invite(fromName: String, preview: InvitePreview, tiles: List<TileFace>?) {
            invited = preview
            this.tiles = tiles
        }

        override fun answer() = reply

        override fun finished(outcome: CardOutcome) {
            this.outcome = outcome
        }
    }

    private val dustin = Phone("Dustin").apply {
        store.lists["L"] = ListState("L", "groceries", listOf(TaskState("t1", "Oat milk", updatedMs = 1)))
    }
    private val alex = Phone("Alex")

    /** Joins the two phones through APDUs; [cutAfter] breaks the link after that many APDUs. */
    private fun link(card: Card, cutAfter: Int = Int.MAX_VALUE): ApduReader {
        val apduCard = ApduCard { CardSession(alex.store, alex.id, { alex.name }, card)::handle }
        var count = 0
        return ApduReader { apdu ->
            if (++count > cutAfter) throw IOException("tag lost")
            apduCard.process(apdu)
        }
    }

    private suspend fun shareOnce(reader: Reader, card: Card, cutAfter: Int = Int.MAX_VALUE): TapResult {
        val l = link(card, cutAfter)
        assertTrue(l.select())
        return ReaderSession(dustin.store, dustin.id, dustin.name, reader, pollMs = 0).share(l, "L")
    }

    @Test
    fun `first share shows the same tiles on both phones and sends the list after both say yes`() = runTest {
        val reader = Reader(answer = true)
        val card = Card(reply = Answer.SYNC)
        val result = shareOnce(reader, card)
        assertEquals(TapResult.Shared("Alex", Answer.SYNC), result)
        assertNotNull(reader.tiles)
        assertEquals(reader.tiles, card.tiles)
        assertEquals("groceries", card.invited!!.title)
        assertEquals("Oat milk", alex.store.lists["L"]!!.tasks.single().title)
        assertNotNull(dustin.store.peer(alex.id.id))
        assertNotNull(alex.store.peer(dustin.id.id))
    }

    @Test
    fun `nothing is sent when the tiles don't match`() = runTest {
        val result = shareOnce(Reader(answer = false), Card(reply = Answer.SYNC))
        assertEquals(TapResult.Declined(byMe = true), result)
        assertTrue(alex.store.lists.isEmpty())
        assertNull(alex.store.peer(dustin.id.id))
    }

    @Test
    fun `a broken tap leaves the other phone empty`() = runTest {
        // Select, three handshake APDUs, then one poll: cut before the list goes.
        val result = shareOnce(Reader(answer = true), Card(reply = Answer.SYNC), cutAfter = 5)
        assertEquals(TapResult.Lost, result)
        assertTrue(alex.store.lists.isEmpty())
    }

    @Test
    fun `just a copy makes an ordinary list`() = runTest {
        val result = shareOnce(Reader(answer = true), Card(reply = Answer.COPY))
        assertEquals(TapResult.Shared("Alex", Answer.COPY), result)
        assertTrue(alex.store.shared.isEmpty())
        assertEquals("groceries", alex.store.lists.values.single().title)
    }

    @Test
    fun `a phone on a service refuses`() = runTest {
        alex.store.localOnly = false
        val result = shareOnce(Reader(answer = true), Card(reply = Answer.SYNC))
        assertEquals(TapResult.Refused("Alex", Refusal.NOT_LOCAL_ONLY), result)
    }

    @Test
    fun `later taps skip the tiles and sync both ways`() = runTest {
        shareOnce(Reader(answer = true), Card(reply = Answer.SYNC))
        alex.store.lists["L"] = alex.store.lists["L"]!!.let {
            it.copy(tasks = it.tasks + TaskState("t2", "Basil", updatedMs = System.currentTimeMillis()))
        }
        dustin.store.lists["L"] = dustin.store.lists["L"]!!.copy(title = "food", updatedMs = System.currentTimeMillis())

        val reader = Reader()
        val card = Card()
        val l = link(card)
        assertTrue(l.select())
        val result = ReaderSession(dustin.store, dustin.id, dustin.name, reader, pollMs = 0).sync(l) as TapResult.Synced
        assertEquals(1, result.lists.size)
        assertEquals(listOf("Oat milk", "Basil"), dustin.store.lists["L"]!!.tasks.map { it.title })
        assertEquals("food", alex.store.lists["L"]!!.title)
        assertTrue(card.outcome is CardOutcome.Synced)
    }

    @Test
    fun `syncing with a phone you never met is refused`() = runTest {
        val l = link(Card())
        assertTrue(l.select())
        val result = ReaderSession(dustin.store, dustin.id, dustin.name, Reader(), pollMs = 0).sync(l)
        assertEquals(TapResult.Refused("Alex", Refusal.NOT_LINKED), result)
    }

    @Test
    fun `a big list crosses in chunks`() = runTest {
        dustin.store.lists["L"] = ListState(
            "L",
            "big",
            (1..300).map {
                TaskState("t$it", "Task number $it with a longer title", notes = "note $it".repeat(5), updatedMs = 1)
            }
        )
        val result = shareOnce(Reader(answer = true), Card(reply = Answer.SYNC))
        assertEquals(TapResult.Shared("Alex", Answer.SYNC), result)
        assertEquals(300, alex.store.lists["L"]!!.tasks.size)
    }
}
