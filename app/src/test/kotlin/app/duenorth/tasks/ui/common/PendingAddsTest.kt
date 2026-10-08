package app.duenorth.tasks.ui.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A task shows the moment it is added and never blinks out on its way into the database. */
class PendingAddsTest {
    private val today = LocalDate.of(2026, 10, 6)

    private fun row(id: String, due: LocalDate? = today) = TaskRowUi(
        id = id,
        listId = "l",
        title = id,
        details = null,
        caption = "",
        overdue = false,
        completed = false,
        due = due
    )

    private val byDue: (TaskRowUi, TaskRowUi) -> Boolean = { added, row -> !row.due!!.isBefore(added.due) }

    @Test
    fun anAddedRowShowsBeforeTheDatabaseHasItAndHandsOverWithoutAGap() {
        val adds = PendingAdds()
        val stored = listOf(row("overdue", today.minusDays(1)), row("a"))

        adds.add("new", row("new"))
        // Before the write: shown where the stored row will be, above today's others.
        val before = PendingAdds.merge(stored, adds.pending.value.rows, byDue)
        assertEquals(listOf("overdue", "new", "a"), before.map { it.id })

        // The write lands: the stored row takes over, in the same place, and the pending one goes.
        val withNew = listOf(row("overdue", today.minusDays(1)), row("new"), row("a"))
        val after = PendingAdds.merge(withNew, adds.pending.value.rows, byDue)
        assertEquals(listOf("overdue", "new", "a"), after.map { it.id })
        adds.settle(withNew.mapTo(HashSet()) { it.id })
        assertEquals(emptyList<TaskRowUi>(), adds.pending.value.rows)
        assertEquals(setOf("new"), adds.pending.value.added)
    }

    @Test
    fun theNewestOfSeveralAddsIsOnTop() {
        val adds = PendingAdds()
        adds.add("first", row("first"))
        adds.add("second", row("second"))

        val shown = PendingAdds.merge(listOf(row("a")), adds.pending.value.rows) { _, _ -> true }

        assertEquals(listOf("second", "first", "a"), shown.map { it.id })
    }

    @Test
    fun aFailedWriteTakesTheRowAway() {
        val adds = PendingAdds()
        adds.add("new", row("new"))

        adds.clear("new")

        assertEquals(emptyList<TaskRowUi>(), adds.pending.value.rows)
    }

    @Test
    fun aTaskNotShownHereIsStillRememberedAsAdded() {
        val adds = PendingAdds()

        adds.add("next week", null)

        assertEquals(emptyList<TaskRowUi>(), adds.pending.value.rows)
        assertEquals(setOf("next week"), adds.pending.value.added)
    }

    @Test
    fun aHeldListLetsInOnlyTheRowsAddedHere() {
        val frozen = listOf(row("a"), row("b"))
        // Meanwhile sync removed "b" and brought "synced"; the user added "new".
        val live = listOf(row("new"), row("a"), row("synced"))

        val shown = PendingAdds.admit(frozen, live, added = setOf("new"))

        assertEquals(listOf("new", "a", "b"), shown.map { it.id })
    }

    @Test
    fun anUnheldListIsLeftAsItIs() {
        val live = listOf(row("new"), row("a"))

        assertSame(live, PendingAdds.admit(live, live, added = setOf("new")))
    }

    @Test
    fun aTapOnARowNotStoredYetShowsItsTick() {
        val rows = listOf(row("new").copy(overdue = true), row("other"))

        val shown = PendingAdds.ticked(rows, mapOf("new" to true))

        assertEquals(listOf(true, false), shown.map { it.completed })
        assertEquals(listOf(false, false), shown.map { it.overdue })
        assertSame(rows, PendingAdds.ticked(rows, emptyMap()))
    }
}
