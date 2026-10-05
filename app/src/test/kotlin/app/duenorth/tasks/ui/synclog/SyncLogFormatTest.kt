package app.duenorth.tasks.ui.synclog

import app.duenorth.tasks.ui.synclog.SyncLogViewModel.Companion.dayLabel
import app.duenorth.tasks.ui.synclog.SyncLogViewModel.Companion.parseReplaced
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLogFormatTest {
    private val today = LocalDate.of(2026, 10, 5) // a Monday

    @Test
    fun daysReadRelativeThenByDate() {
        assertEquals("today", dayLabel(today, today))
        assertEquals("yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("friday", dayLabel(today.minusDays(3), today))
        assertEquals("sep 20", dayLabel(LocalDate.of(2026, 9, 20), today))
        assertEquals("dec 30, 2025", dayLabel(LocalDate.of(2025, 12, 30), today))
    }

    @Test
    fun accountVersionCarriesItsUpdatedTime() {
        val json = """{"title":"Buy milk","notes":"2%","dueDate":"2026-10-06","completed":false,""" +
            """"important":true,"steps":[{"title":"Oat","done":true}],"updated":"2026-10-05T08:00:00Z"}"""
        val version = parseReplaced(json, today)!!
        assertTrue(version.fromAccount)
        assertEquals("Buy milk", version.title)
        assertEquals("2%", version.details)
        assertEquals("due tomorrow", version.due)
        assertTrue(version.important)
        assertEquals(listOf(ReplacedStep("Oat", true)), version.steps)
    }

    @Test
    fun phoneVersionHasNoUpdatedTime() {
        val version = parseReplaced("""{"title":"Call mom","completed":true,"steps":[]}""", today)!!
        assertFalse(version.fromAccount)
        assertTrue(version.completed)
        assertNull(version.details)
        assertNull(version.due)
    }

    @Test
    fun unreadableJsonShowsNoVersion() {
        assertNull(parseReplaced("not json", today))
    }
}
