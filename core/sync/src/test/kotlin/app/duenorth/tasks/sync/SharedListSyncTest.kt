package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.AssignmentSource
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.provider.api.Assignment
import app.duenorth.tasks.provider.api.TaskDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 002: sharing and assignment facts come from the service and follow it on every sync. */
@RunWith(RobolectricTestRunner::class)
class SharedListSyncTest : SyncTestBase() {
    @Test
    fun sharingFlagsArriveWithTheList() = runBlocking {
        val family = remote.seed("Family groceries")
        val club = remote.seed("Book club")
        remote.seed("Work")
        remote.setSharing(family.id, isShared = true, isOwner = true)
        remote.setSharing(club.id, isShared = true, isOwner = false)

        sync()

        assertTrue(list("Family groceries").isShared && list("Family groceries").isOwner)
        assertTrue(list("Book club").isShared && !list("Book club").isOwner)
        assertFalse(list("Work").isShared)
        assertTrue(list("Work").isOwner)
    }

    @Test
    fun sharingAndUnsharingElsewhereShowsAfterTheNextSync() = runBlocking {
        val work = remote.seed("Work")
        sync()
        assertFalse(list("Work").isShared)

        remote.setSharing(work.id, isShared = true)
        sync()
        assertTrue(list("Work").isShared)

        remote.setSharing(work.id, isShared = false)
        sync()
        assertFalse(list("Work").isShared)
    }

    @Test
    fun aRenameHereKeepsTheSharingFlags() = runBlocking {
        val family = remote.seed("Family")
        remote.setSharing(family.id, isShared = true)
        sync()

        io { repo.renameList(list("Family").localId, "Family groceries") }
        sync()

        assertEquals("Family groceries", remoteList("Family groceries").title)
        assertTrue(list("Family groceries").isShared)
    }

    @Test
    fun aListNoLongerSharedWithYouSaysSoOnce() = runBlocking {
        val club = remote.seed("Book club", listOf(TaskDraft("Pick October book")))
        remote.setSharing(club.id, isShared = true, isOwner = false)
        sync()

        // The owner stops sharing: the list disappears from this person's account.
        remote.deleteList(club.id)
        sync()
        sync()

        assertTrue(lists().none { it.title == "Book club" })
        val notes = log().filter { "Book club" in it.summary }
        assertEquals(listOf("“Book club” is no longer shared with you."), notes.map { it.summary })
        assertEquals(SyncLogType.RECOVERED, notes.single().type)
    }

    @Test
    fun unsyncedTasksInARemovedSharedListAreKept() = runBlocking {
        val club = remote.seed("Book club")
        remote.setSharing(club.id, isShared = true, isOwner = false)
        sync()
        io { repo.createTask(list("Book club").localId, "Bring snacks") }

        remote.deleteList(club.id)
        sync()

        assertEquals("Bring snacks", task("Bring snacks").title)
        assertTrue(log().any { it.summary.startsWith("“Book club” is no longer shared with you. 1 task") })
    }

    @Test
    fun aDeletedListYouOwnIsWordedAsBefore() = runBlocking {
        val errands = remote.seed("Errands")
        sync()
        remote.deleteList(errands.id)
        sync()

        // Only lists shared with you get a note when they go; your own vanish quietly, as before.
        assertTrue(log().none { "Errands" in it.summary })
    }

    @Test
    fun assignmentFollowsTheService() = runBlocking {
        val mine = remote.seed("My Tasks", listOf(TaskDraft("Review section 3"), TaskDraft("Plain task")))
        val review = remoteTasks(mine.id).single { it.title == "Review section 3" }
        remote.assign(mine.id, review.id, Assignment(AssignmentSourceRemote.DOCUMENT, "https://docs.google.com/d/1"))

        sync()

        assertEquals(AssignmentSource.DOCUMENT, task("Review section 3").assignmentSource)
        assertEquals("https://docs.google.com/d/1", task("Review section 3").assignmentLink)
        assertEquals(AssignmentSource.NONE, task("Plain task").assignmentSource)

        remote.assign(mine.id, review.id, null)
        sync()
        assertEquals(AssignmentSource.NONE, task("Review section 3").assignmentSource)
    }

    @Test
    fun anEditHereKeepsTheAssignment() = runBlocking {
        val mine = remote.seed("My Tasks", listOf(TaskDraft("Review section 3")))
        val review = remoteTasks(mine.id).single()
        remote.assign(mine.id, review.id, Assignment(AssignmentSourceRemote.SPACE, null))
        sync()

        io { repo.setCompleted(task("Review section 3").localId, true) }
        sync()

        assertTrue(remoteTasks(mine.id).single().completed)
        assertEquals(AssignmentSourceRemote.SPACE, remoteTasks(mine.id).single().assignment?.source)
        assertEquals(AssignmentSource.SPACE, task("Review section 3").assignmentSource)
    }
}

private typealias AssignmentSourceRemote = app.duenorth.tasks.provider.api.AssignmentSource
