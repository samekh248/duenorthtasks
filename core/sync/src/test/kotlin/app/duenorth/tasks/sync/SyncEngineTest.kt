package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.AuthState
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.repo.TaskEdit
import app.duenorth.tasks.provider.api.Patch
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.StepDraft
import app.duenorth.tasks.provider.api.StepPatch
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import java.io.IOException
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sync engine against the fake provider (T041, constitution Principle V). */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest : SyncTestBase() {
    // First sync and pulling

    @Test
    fun firstSyncBringsInListsTasksAndSteps() {
        io {
            remote.seed("My Tasks")
            remote.seed(
                "Errands",
                listOf(
                    TaskDraft("Buy milk", dueDate = LocalDate.of(2026, 10, 4)),
                    TaskDraft(
                        "Pack",
                        notes = "Weather?",
                        steps = listOf(StepDraft("Socks"), StepDraft("Hat", done = true))
                    )
                )
            )
        }

        assertEquals(SyncResult.Success, sync())

        assertEquals(listOf("My Tasks", "Errands"), lists().map { it.title })
        assertTrue(list("My Tasks").isDefault)
        assertEquals(setOf("Buy milk", "Pack"), tasks("Errands").map { it.title }.toSet())
        assertEquals("Weather?", task("Pack").notes)
        assertEquals(listOf("Socks" to false, "Hat" to true), steps(task("Pack")).map { it.title to it.done })
        assertEquals(0, pending())
    }

    @Test
    fun webEditsArriveOnTheNextSync() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val id = checkNotNull(task("Buy milk").remoteId)

        io { remote.editRemotely(errands.id, id, TaskPatch(title = "Buy oat milk", completed = true)) }
        sync()

        val task = task("Buy oat milk")
        assertTrue(task.completed)
    }

    @Test
    fun webDeletesArriveOnTheNextSync() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"), TaskDraft("Keep"))) }
        sync()

        io { remote.deleteTask(errands.id, checkNotNull(task("Buy milk").remoteId)) }
        sync()

        assertEquals(listOf("Keep"), tasks("Errands").map { it.title })
    }

    @Test
    fun anExpiredCursorFetchesTheListAgainAndDropsWhatIsGone() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"), TaskDraft("Keep"))) }
        sync()
        io { remote.deleteTask(errands.id, checkNotNull(task("Buy milk").remoteId)) }

        // The fake answers a cursor it cannot read with CursorExpired, like a too-old updatedMin.
        io { db.taskListDao().update(list("Errands").copy(tasksCursor = "garbled")) }
        assertEquals(SyncResult.Success, sync())

        assertEquals(listOf("Keep"), tasks("Errands").map { it.title })
    }

    // Pushing

    @Test
    fun localCreatesArePushedOnceWithStepsAndCompletion() {
        io { remote.seed("My Tasks") }
        sync()
        val listId = io { repo.createList("Errands") }
        val taskId = io { repo.createTask(listId, "Pack", notes = "Bag", dueDate = LocalDate.of(2026, 10, 9)) }
        io { repo.addStep(taskId, "Socks") }
        io { repo.setCompleted(taskId, true) }

        assertEquals(SyncResult.Success, sync())
        sync()

        val pushed = remoteTasks(remoteList("Errands").id).single()
        assertEquals("Pack", pushed.title)
        assertEquals("Bag", pushed.notes)
        assertEquals(LocalDate.of(2026, 10, 9), pushed.dueDate)
        assertTrue(pushed.completed)
        assertEquals(listOf("Socks"), pushed.steps.map { it.title })
        assertEquals(1, remote.calls.count { it == "createTask" })
        assertEquals(1, runBlocking { remote.getLists() }.count { it.title == "Errands" })
        assertEquals(pushed.id, task("Pack").remoteId)
        assertEquals(pushed.steps.single().id, steps(task("Pack")).single().remoteId)
        assertEquals(0, pending())
    }

    @Test
    fun editsSendOnlyTheChangedFields() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk", notes = "Oat"))) }
        sync()
        val local = task("Buy milk")
        val remoteId = checkNotNull(local.remoteId)
        // Someone adds details on the web; the phone only renames.
        io { remote.editRemotely(errands.id, remoteId, TaskPatch(notes = Patch.Set("Oat, 2 litres"))) }
        sync()
        io { repo.editTask(local.localId, TaskEdit(title = "Buy oat milk")) }

        sync()

        val pushed = remoteTasks(errands.id).single()
        assertEquals("Buy oat milk", pushed.title)
        assertEquals("Oat, 2 litres", pushed.notes)
    }

    @Test
    fun stepsAddedTickedAndRemovedOnThePhoneReachTheService() {
        val errands =
            io {
                remote.seed("Errands", listOf(TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Hat")))))
            }
        sync()
        val pack = task("Pack")
        val (socks, hat) = steps(pack)
        io { repo.editStep(socks.localId, done = true) }
        io { repo.removeStep(hat.localId) }
        io { repo.addStep(pack.localId, "Shoes") }

        sync()

        assertEquals(
            listOf("Socks" to true, "Shoes" to false),
            remoteTasks(errands.id).single().steps.map {
                it.title to
                    it.done
            }
        )
        assertEquals(listOf("Socks", "Shoes"), steps(task("Pack")).map { it.title })
        assertEquals(0, pending())
    }

    @Test
    fun deletesArePushedAndRowsRemoved() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"), TaskDraft("Keep"))) }
        sync()
        io { repo.deleteTask(task("Buy milk").localId) }

        sync()

        assertEquals(listOf("Keep"), remoteTasks(errands.id).map { it.title })
        assertEquals(listOf("Keep"), tasks("Errands").map { it.title })
    }

    @Test
    fun movingATaskToAnotherListLeavesOneCopy() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk", steps = listOf(StepDraft("Oat"))))) }
        val home = io { remote.seed("Home") }
        sync()
        io { repo.editTask(task("Buy milk").localId, TaskEdit(listId = list("Home").localId)) }

        sync()
        sync()

        assertTrue(remoteTasks(errands.id).isEmpty())
        val moved = remoteTasks(home.id).single()
        assertEquals("Buy milk", moved.title)
        assertEquals(listOf("Oat"), moved.steps.map { it.title })
        assertEquals(listOf("Buy milk"), tasks("Home").map { it.title })
        assertTrue(tasks("Errands").isEmpty())
        assertEquals(0, pending())
    }

    // Offline and failures

    @Test
    fun offlineEditsQueueAndGoOutWhenTheNetworkReturns() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        io { repo.setCompleted(task("Buy milk").localId, true) }

        remote.failNext(ProviderError.Transient("offline", IOException("no route")))
        assertTrue(sync() is SyncResult.Retry)
        assertEquals(1, pending())
        assertEquals(0, runBlocking { db.pendingOperationDao().all() }.single().attempts)

        assertEquals(SyncResult.Success, sync())
        assertTrue(remoteTasks(errands.id).single().completed)
        assertEquals(0, pending())
    }

    @Test
    fun aLostCreateResponseDoesNotMakeADuplicate() {
        val errands = io { remote.seed("Errands") }
        sync()
        io { repo.createTask(list("Errands").localId, "Buy milk") }
        // The create reaches the service but the answer never arrives.
        val sneaky = object : app.duenorth.tasks.provider.api.TaskProvider by remote {
            var dropNextCreate = true

            override suspend fun createTask(listId: String, draft: TaskDraft) = remote.createTask(listId, draft).also {
                if (dropNextCreate) {
                    dropNextCreate = false
                    throw ProviderError.Transient("lost response")
                }
            }
        }
        val flaky =
            SyncEngine(db, {
                sneaky
            }, holds, clock, {
                "f${clock.instant().toEpochMilli()}${Math.random()}"
            }, kotlinx.coroutines.Dispatchers.Unconfined)

        assertTrue(
            runBlocking {
                clock.tick()
                flaky.sync()
            } is SyncResult.Retry
        )
        assertEquals(
            SyncResult.Success,
            runBlocking {
                clock.tick()
                flaky.sync()
            }
        )

        assertEquals(listOf("Buy milk"), remoteTasks(errands.id).map { it.title })
        assertEquals(listOf("Buy milk"), tasks("Errands").map { it.title })
        assertEquals(0, pending())
    }

    @Test
    fun revokedAccessStopsSyncAndKeepsEditsQueued() {
        io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        io { repo.setCompleted(task("Buy milk").localId, true) }

        remote.failNext(ProviderError.AuthRequired())
        assertEquals(SyncResult.NeedsSignIn, sync())

        assertEquals(AuthState.NEEDS_SIGN_IN, runBlocking { db.accountDao().get() }?.authState)
        assertEquals(1, pending())
        val callsBefore = remote.calls.size
        assertEquals(SyncResult.NeedsSignIn, sync())
        assertEquals(callsBefore, remote.calls.size)
    }

    @Test
    fun rateLimitsWaitAsLongAsTheServiceAsks() {
        remote.failNext(ProviderError.RateLimited(42.seconds))
        assertEquals(SyncResult.Retry(42.seconds), sync())
    }

    @Test
    fun noAccountMeansNothingToDo() {
        runBlocking { db.accountDao().delete() }
        assertEquals(SyncResult.NoAccount, sync())
        assertTrue(remote.calls.isEmpty())
    }

    // Conflicts (FR-023)

    @Test
    fun aNewerWebEditBeatsAnOlderPhoneEditAndThePhoneVersionIsLogged() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        // The phone edits first (offline), the web edits later.
        io { repo.editTask(local.localId, TaskEdit(title = "Buy milk (phone)")) }
        io { remote.editRemotely(errands.id, checkNotNull(local.remoteId), TaskPatch(title = "Buy milk (web)")) }

        sync()

        assertEquals("Buy milk (web)", remoteTasks(errands.id).single().title)
        assertEquals("Buy milk (web)", task("Buy milk (web)").title)
        val conflict = log().single { it.type == SyncLogType.CONFLICT }
        assertTrue(checkNotNull(conflict.losingVersionJson).contains("Buy milk (phone)"))
        assertEquals(0, pending())
    }

    @Test
    fun aNewerPhoneEditBeatsAnOlderWebEditAndTheWebVersionIsLogged() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        // The web edits first, the phone edits later.
        io { remote.editRemotely(errands.id, checkNotNull(local.remoteId), TaskPatch(title = "Buy milk (web)")) }
        io { repo.editTask(local.localId, TaskEdit(title = "Buy milk (phone)")) }

        sync()

        assertEquals("Buy milk (phone)", remoteTasks(errands.id).single().title)
        assertEquals("Buy milk (phone)", task("Buy milk (phone)").title)
        val conflict = log().single { it.type == SyncLogType.CONFLICT }
        assertTrue(checkNotNull(conflict.losingVersionJson).contains("Buy milk (web)"))
    }

    @Test
    fun whenThePhoneIsNewerAWebEditToAnotherFieldStillSurvives() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        io { remote.editRemotely(errands.id, checkNotNull(local.remoteId), TaskPatch(notes = Patch.Set("Oat"))) }
        io { repo.editTask(local.localId, TaskEdit(dueDate = Patch.Set(LocalDate.of(2026, 10, 5)))) }

        sync()

        val merged = remoteTasks(errands.id).single()
        assertEquals("Oat", merged.notes)
        assertEquals(LocalDate.of(2026, 10, 5), merged.dueDate)
        assertEquals("Oat", task("Buy milk").notes)
    }

    @Test
    fun editedHereButDeletedOnTheWebIsPutBack() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        io { repo.editTask(local.localId, TaskEdit(title = "Buy oat milk")) }
        io { remote.deleteTask(errands.id, checkNotNull(local.remoteId)) }

        sync()

        assertEquals(listOf("Buy oat milk"), remoteTasks(errands.id).map { it.title })
        assertEquals(listOf("Buy oat milk"), tasks("Errands").map { it.title })
        assertTrue(log().any { it.type == SyncLogType.RECOVERED })
    }

    @Test
    fun aListDeletedOnTheWebKeepsItsUnsyncedTasksInRecovered() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Synced"), TaskDraft("Edited here"))) }
        sync()
        io { repo.editTask(task("Edited here").localId, TaskEdit(notes = Patch.Set("important"))) }
        io { repo.createTask(list("Errands").localId, "New here") }
        io { remote.deleteList(errands.id) }

        sync()
        sync()

        assertTrue(lists().none { it.title == "Errands" })
        assertEquals(setOf("Edited here", "New here"), tasks(SyncStore.RECOVERED_LIST).map { it.title }.toSet())
        val recovered = remoteList(SyncStore.RECOVERED_LIST)
        assertEquals(setOf("Edited here", "New here"), remoteTasks(recovered.id).map { it.title }.toSet())
        assertEquals(1, log().count { it.type == SyncLogType.RECOVERED })
        assertEquals(0, pending())
    }

    @Test
    fun deletedHereButEditedOnTheWebAfterwardsComesBack() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        io { repo.deleteTask(local.localId) }
        io { remote.editRemotely(errands.id, checkNotNull(local.remoteId), TaskPatch(title = "Buy milk today")) }

        sync()

        assertEquals(listOf("Buy milk today"), tasks("Errands").map { it.title })
        assertEquals(listOf("Buy milk today"), remoteTasks(errands.id).map { it.title })
    }

    // Smooth sync (T043)

    @Test
    fun aHeldListWaitsForTheGestureToEnd() {
        io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val errands = list("Errands")
        val held = ListHolds(maxWait = 1.seconds)
        val engine =
            SyncEngine(db, { remote }, held, clock, { "h${Math.random()}" }, kotlinx.coroutines.Dispatchers.Unconfined)
        io {
            remote.editRemotely(
                remoteList("Errands").id,
                checkNotNull(task("Buy milk").remoteId),
                TaskPatch(title = "Buy oat milk")
            )
        }

        held.hold(errands.localId)
        runBlocking {
            val started = System.nanoTime()
            engine.sync()
            // Nobody released it, so the engine waited out the cap and then applied the change.
            assertTrue(System.nanoTime() - started >= 900_000_000L)
        }
        assertEquals("Buy oat milk", task("Buy oat milk").title)
    }

    @Test
    fun aBigListIsAppliedInSmallTransactions() {
        io { remote.seed("Errands", (1..120).map { TaskDraft("Task $it") }) }

        sync()

        assertEquals(120, tasks("Errands").size)
        assertEquals(50, Puller.BATCH)
    }

    @Test
    fun syncingFlagIsOnlyUpWhileRunning() {
        assertFalse(engine.isSyncing.value)
        sync()
        assertFalse(engine.isSyncing.value)
    }

    @Test
    fun importanceIsNotPushedToAServiceThatCannotStoreIt() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        io { repo.editTask(task("Buy milk").localId, TaskEdit(important = true)) }

        sync()

        assertFalse(remoteTasks(errands.id).single().important)
        assertNull(log().firstOrNull { it.type == SyncLogType.ERROR })
    }

    @Test
    fun stepEditedOnTheWebUpdatesTheLocalStep() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("Pack", steps = listOf(StepDraft("Socks"))))) }
        sync()
        val pack = task("Pack")
        val socks = checkNotNull(steps(pack).single().remoteId)

        io {
            remote.editRemotely(
                errands.id,
                checkNotNull(pack.remoteId),
                TaskPatch(steps = listOf(StepPatch.Update(socks, done = true)))
            )
        }
        sync()

        assertTrue(steps(task("Pack")).single().done)
    }

    @Test
    fun reopeningSendsTheStatusTheTaskHadBefore() {
        io { remote.seed("Errands", listOf(TaskDraft("Buy milk"))) }
        sync()
        val local = task("Buy milk")
        // As if To Do had it "in progress" when the phone last saw it.
        io { db.taskDao().update(local.copy(remoteStatusRaw = "inProgress")) }
        val patches = mutableListOf<TaskPatch>()
        val recording = object : app.duenorth.tasks.provider.api.TaskProvider by remote {
            override suspend fun updateTask(listId: String, id: String, patch: TaskPatch) =
                remote.updateTask(listId, id, patch).also { patches += patch }
        }
        val engine = SyncEngine(db, { recording }, holds, clock, { "r${Math.random()}" }, Dispatchers.Unconfined)
        io { repo.setCompleted(local.localId, true) }
        io { repo.setCompleted(local.localId, false) }

        runBlocking { engine.sync() }

        assertEquals(TaskPatch(completed = false, reopenStatus = "inProgress"), patches.single())
    }
}
