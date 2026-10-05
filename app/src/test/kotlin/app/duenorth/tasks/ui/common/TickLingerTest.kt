package app.duenorth.tasks.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-006: a row ticked on "today" stays, ticked, long enough to see the tick before it leaves. */
@OptIn(ExperimentalCoroutinesApi::class)
class TickLingerTest {
    private fun row(id: String, completed: Boolean = false) = TaskRowUi(
        id = id,
        listId = "l",
        title = id,
        details = null,
        caption = "",
        overdue = false,
        completed = completed
    )

    @Test
    fun aTickedRowTheDatabaseMovedStaysWhereItWasThenLeaves() = runTest {
        val linger = TickLinger(backgroundScope)
        val shown = listOf(row("a"), row("b"), row("c"))
        linger.ticked("b", true)

        // The database already moved "b" to done; today keeps it in place, ticked.
        val kept = TickLinger.keep(shown, listOf(row("a"), row("c")), linger.ticked.value)
        assertEquals(listOf("a", "b", "c"), kept.map { it.id })
        assertEquals(true, kept[1].completed)

        // Once the beat is over it goes.
        advanceTimeBy(TickLinger.LINGER_MS + 1)
        runCurrent()
        assertEquals(emptyMap<String, Boolean>(), linger.ticked.value)
        assertEquals(
            listOf("a", "c"),
            TickLinger.keep(kept, listOf(row("a"), row("c")), linger.ticked.value).map {
                it.id
            }
        )
    }

    @Test
    fun rowsNobodyTickedComeAndGoAsTheDatabaseSays() {
        val shown = listOf(row("a"), row("b"))
        assertEquals(listOf("a"), TickLinger.keep(shown, listOf(row("a")), mapOf("x" to true)).map { it.id })
        assertEquals(listOf("a", "n"), TickLinger.keep(shown, listOf(row("a"), row("n")), emptyMap()).map { it.id })
    }

    @Test
    fun aRowThatIsStillThereIsNotDoubled() {
        val shown = listOf(row("a"))
        val rows = listOf(row("a", completed = true))
        assertEquals(rows, TickLinger.keep(shown, rows, mapOf("a" to true)))
    }

    @Test
    fun untickingAgainWithinTheBeatKeepsTheLatestState() = runTest {
        val linger = TickLinger(backgroundScope)
        linger.ticked("a", true)
        advanceTimeBy(TickLinger.LINGER_MS / 2)
        linger.ticked("a", false)
        advanceTimeBy(TickLinger.LINGER_MS / 2 + 1)
        runCurrent()
        // The first tick's timer ran out but must not end the second.
        assertEquals(mapOf("a" to false), linger.ticked.value)
    }
}
