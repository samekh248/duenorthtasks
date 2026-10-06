package app.duenorth.tasks.ui.common

import app.duenorth.tasks.data.db.AssignmentSource
import app.duenorth.tasks.ui.sharing.SharingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingTest {
    @Test
    fun sharingComesFromTheTwoFlags() {
        assertEquals(ListSharing.PRIVATE, ListSharing.of(isShared = false, isOwner = true))
        assertEquals(ListSharing.PRIVATE, ListSharing.of(isShared = false, isOwner = false))
        assertEquals(ListSharing.OWNED, ListSharing.of(isShared = true, isOwner = true))
        assertEquals(ListSharing.WITH_YOU, ListSharing.of(isShared = true, isOwner = false))
    }

    @Test
    fun onlyTheOwnerManagesTheList() {
        assertTrue(ListSharing.PRIVATE.canManage)
        assertTrue(ListSharing.OWNED.canManage)
        assertFalse(ListSharing.WITH_YOU.canManage)
    }

    @Test
    fun deletingAListYouShareSaysEveryoneLosesIt() {
        assertEquals("delete for everyone?", deleteListTitle("Family", ListSharing.OWNED))
        assertEquals("delete Family?", deleteListTitle("Family", ListSharing.PRIVATE))
        assertTrue("everyone it's shared with" in deleteListMessage("Family", 3, ListSharing.OWNED, "Microsoft To Do"))
        assertFalse("everyone" in deleteListMessage("Family", 3, ListSharing.PRIVATE, "Microsoft To Do"))
    }

    @Test
    fun assignedCaptionNamesWhereNotWho() {
        assertNull(assignedFrom(AssignmentSource.NONE))
        assertEquals("assigned from a google doc", assignedFrom(AssignmentSource.DOCUMENT))
        assertEquals("assigned from a chat space", assignedFrom(AssignmentSource.SPACE))
    }

    @Test
    fun handOffPicksToDoOnTheWebForTheAccountType() {
        assertEquals("https://to-do.live.com/tasks/", SharingViewModel.handOffFor("someone@outlook.com").webUrl)
        assertEquals("https://to-do.live.com/tasks/", SharingViewModel.handOffFor("someone@hotmail.co.uk").webUrl)
        assertEquals("https://to-do.office.com/tasks/", SharingViewModel.handOffFor("someone@example.com").webUrl)
        assertEquals("https://to-do.office.com/tasks/", SharingViewModel.handOffFor(null).webUrl)
        assertEquals("com.microsoft.todos", SharingViewModel.handOffFor(null).appPackage)
    }
}
