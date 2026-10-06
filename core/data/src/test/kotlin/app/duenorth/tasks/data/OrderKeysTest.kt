package app.duenorth.tasks.data

import app.duenorth.tasks.data.order.OrderKeys
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderKeysTest {
    @Test
    fun keyFitsBetweenNeighbors() {
        assertEquals("5", OrderKeys.between(null, null))
        assertEquals("055", OrderKeys.between("05", "06"))
        assertEquals("02", OrderKeys.between(null, "05"))
        assertEquals("95", OrderKeys.between("9", null))
        assertBetween("00000000000000000005", "00000000000000000006")
        assertBetween("0500", "06")
        assertBetween(null, "00000000000000000001")
    }

    @Test
    fun noRoomBetweenEqualKeysOrBelowZero() {
        assertNull(OrderKeys.between("05", "05"))
        assertNull(OrderKeys.between("05", "050"))
        assertNull(OrderKeys.between(null, "00000000000000000000"))
        assertNull(OrderKeys.between("06", "05"))
    }

    @Test
    fun repeatedInsertsAlwaysFindRoom() {
        var low: String? = null
        var high: String? = "00000000000000000001"
        repeat(200) { i ->
            val key = OrderKeys.between(low, high)!!
            assertBetween(low, high, key)
            if (i % 2 == 0) low = key else high = key
        }
        var top = "5"
        repeat(200) {
            val key = OrderKeys.between(null, top)!!
            assertTrue(key < top)
            top = key
        }
    }

    @Test
    fun keysBeforeAreAscendingShortAndBelowTheFirst() {
        val keys = OrderKeys.keysBefore("4", 500)
        assertEquals(500, keys.size)
        keys.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
        assertTrue(keys.last() < "4")
        assertTrue(keys.all { it.length <= 8 && !it.endsWith('0') && it.all(Char::isDigit) })
        assertEquals(listOf("25", "5", "75"), OrderKeys.keysBefore(null, 3))
    }

    @Test
    fun timeKeysPutNewestFirst() {
        val older = OrderKeys.timeKey(Instant.parse("2026-01-01T00:00:00Z"))
        val newer = OrderKeys.timeKey(Instant.parse("2026-10-06T00:00:00Z"))
        assertTrue(newer < older)
        assertEquals(13, newer.length)
    }

    private fun assertBetween(low: String?, high: String?, key: String = OrderKeys.between(low, high)!!) {
        assertTrue("$key > $low", low == null || key > low)
        assertTrue("$key < $high", high == null || key < high)
        assertTrue("no trailing zero in $key", !key.endsWith('0'))
    }
}
