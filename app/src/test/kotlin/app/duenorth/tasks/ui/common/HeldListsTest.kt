package app.duenorth.tasks.ui.common

import app.duenorth.tasks.sync.ListHolds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T043: screens hold exactly the lists they asked for, until the last of their holds ends. */
class HeldListsTest {
    private val holds = ListHolds()
    private val held = HeldLists(holds)

    @Test
    fun holdsUntilReleased() {
        held.set(true) { listOf("a", "b") }
        assertTrue(isHeld("a"))
        assertTrue(isHeld("b"))
        held.set(false) { emptyList() }
        assertFalse(isHeld("a"))
        assertFalse(isHeld("b"))
    }

    @Test
    fun twoSectionsHoldingAtOnceReleaseOnlyWhenBothLetGo() {
        held.set(true) { listOf("a") }
        held.set(true) { listOf("a", "new") }
        held.set(false) { emptyList() }
        assertTrue(isHeld("a"))
        held.set(false) { emptyList() }
        assertFalse(isHeld("a"))
    }

    @Test
    fun releasesTheListsItHeldEvenIfTheyChanged() {
        var lists = listOf("a")
        held.set(true) { lists }
        lists = listOf("b")
        held.set(false) { lists }
        assertFalse(isHeld("a"))
        assertFalse(isHeld("b"))
    }

    @Test
    fun extraReleasesAreIgnored() {
        held.set(false) { listOf("a") }
        held.set(true) { listOf("a") }
        assertTrue(isHeld("a"))
        held.releaseAll()
        held.set(false) { listOf("a") }
        assertFalse(isHeld("a"))
    }

    private fun isHeld(listId: String): Boolean =
        runBlocking { withTimeoutOrNull(50) { holds.awaitReleased(listId) } == null }
}
