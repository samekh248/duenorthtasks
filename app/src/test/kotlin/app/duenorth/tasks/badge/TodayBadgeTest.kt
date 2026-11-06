package app.duenorth.tasks.badge

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskWithList
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The app icon badge: what it counts, and that the permission is asked for only once. */
class TodayBadgeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun settings() = TodayBadgeSettings(
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "badge.preferences_pb") }
    )

    private fun row(title: String, completed: Boolean = false) = TaskWithList(
        TaskEntity(
            localId = title,
            listId = "l",
            title = title,
            dueDate = LocalDate.EPOCH,
            completed = completed,
            localUpdatedAt = Instant.EPOCH
        ),
        listTitle = "list"
    )

    @Test
    fun countsOpenTasksAndKeepsTheFirstTitles() {
        val today = TodayCount.of((1..7).map { row("t$it") } + row("done", completed = true))
        assertEquals(7, today.count)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), today.titles)
        assertEquals("7 tasks for today", today.title())
        assertEquals("1 task for today", TodayCount.of(listOf(row("one"))).title())
        assertEquals(0, TodayCount.of(emptyList()).count)
    }

    @Test
    fun onByDefaultAndAsksOnce() = runBlocking {
        val settings = settings()
        assertTrue(settings.enabled.first())
        assertFalse(settings.asked())
        assertTrue(settings.takeFirstAsk())
        assertFalse(settings.takeFirstAsk())
        assertTrue(settings.asked())
        settings.setEnabled(false)
        assertFalse(settings.enabled.first())
    }
}
