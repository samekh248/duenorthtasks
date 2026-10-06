package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.provider.api.RemoteList
import app.duenorth.tasks.provider.api.TaskProvider
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lists on a service that, like Microsoft To Do, has no modified time for them. */
@RunWith(RobolectricTestRunner::class)
class ListSyncTest : SyncTestBase() {
    @Before
    fun noListTimes() {
        // Microsoft reports every list as changed "now"; only its etag says whether it really did.
        val timeless = object : TaskProvider by remote {
            override suspend fun getLists(): List<RemoteList> = remote.getLists().map {
                it.copy(updatedAt = clock.instant())
            }
        }
        engine = SyncEngine(db, { timeless }, holds, clock, { UUID.randomUUID().toString() }, Dispatchers.Unconfined)
    }

    @Test
    fun aListRenamedHereReachesTheServiceInsteadOfBeingUndone() = runBlocking {
        remote.seed("Groceries")
        sync()

        io { repo.renameList(list("Groceries").localId, "Food") }
        sync()

        assertEquals(listOf("Food"), remote.getLists().map { it.title })
        assertEquals(listOf("Food"), lists().map { it.title })
        assertTrue(log().none { it.type == SyncLogType.CONFLICT })
    }

    @Test
    fun aRefusedChangeIsPutBackAndTheRestStillSyncs() = runBlocking {
        remote.seed("Book club", listOf(app.duenorth.tasks.provider.api.TaskDraft("Read chapter 3")))
        sync()

        io { repo.renameList(list("Book club").localId, "Mine now") }
        val other = io { repo.createList("Errands") }
        io { repo.createTask(other, "Buy milk") }
        // The owner's list can't be renamed by a member: Graph answers 403.
        val memberOnly = object : TaskProvider by remote {
            override suspend fun updateList(id: String, patch: app.duenorth.tasks.provider.api.ListPatch): RemoteList =
                throw app.duenorth.tasks.provider.api.ProviderError.NotAllowed(id)
        }
        engine = SyncEngine(db, { memberOnly }, holds, clock, { UUID.randomUUID().toString() }, Dispatchers.Unconfined)
        assertEquals(SyncResult.Success, sync())

        assertEquals(app.duenorth.tasks.data.db.AuthState.OK, db.accountDao().get()?.authState)
        assertEquals(setOf("Book club", "Errands"), remote.getLists().map { it.title }.toSet())
        sync()
        assertEquals(setOf("Book club", "Errands"), lists().map { it.title }.toSet())
        assertEquals(
            listOf("Buy milk"),
            remoteTasks(
                remote.getLists().single {
                    it.title == "Errands"
                }.id
            ).map { it.title }
        )
        assertTrue(log().any { it.type == SyncLogType.CONFLICT && "Mine now" in it.summary })
    }

    @Test
    fun aListRenamedOnTheWebStillComesThrough() = runBlocking {
        val groceries = remote.seed("Groceries")
        sync()

        remote.updateList(groceries.id, app.duenorth.tasks.provider.api.ListPatch(title = "Food"))
        sync()

        assertEquals(listOf("Food"), lists().map { it.title })
    }
}
