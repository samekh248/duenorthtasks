package app.duenorth.tasks.sync

import app.duenorth.tasks.data.order.OrderKeys
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.StepDraft
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.fake.FakeProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** specs/003-reordering R012, R017: Google order syncs both ways; Microsoft order stays on the phone. */
@RunWith(RobolectricTestRunner::class)
class ReorderSyncTest : SyncTestBase() {
    @Test
    fun aGoogleTaskMoveReachesTheService() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("A"), TaskDraft("B"), TaskDraft("C"))) }
        sync()
        val before = localOrder("Errands")
        io { repo.moveTask(task(before.last()).localId, afterId = null, beforeId = task(before.first()).localId) }
        sync()

        val expected = listOf(before.last()) + before.dropLast(1)
        assertEquals(expected, localOrder("Errands"))
        assertEquals(expected, remoteTasks(errands.id).sortedBy { it.position }.map { it.title })
        assertEquals(0, pending())
    }

    @Test
    fun aGoogleTaskMovedBetweenTwoLandsThere() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("A"), TaskDraft("B"), TaskDraft("C"))) }
        sync()
        val (first, second, third) = localOrder("Errands")
        io { repo.moveTask(task(first).localId, afterId = task(second).localId, beforeId = task(third).localId) }
        sync()

        assertEquals(listOf(second, first, third), localOrder("Errands"))
        assertEquals(listOf(second, first, third), remoteTasks(errands.id).sortedBy { it.position }.map { it.title })
    }

    @Test
    fun anOrderChangedOnTheWebArrives() {
        val errands = io { remote.seed("Errands", listOf(TaskDraft("A"), TaskDraft("B"))) }
        sync()
        val (first, second) = localOrder("Errands")
        io { remote.moveTask(errands.id, task(first).remoteId!!, task(second).remoteId) }
        sync()

        assertEquals(listOf(second, first), localOrder("Errands"))
    }

    @Test
    fun aGoogleStepMoveReachesTheService() {
        val errands = io {
            remote.seed(
                "Errands",
                listOf(TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Hat"), StepDraft("Scarf"))))
            )
        }
        sync()
        val pack = task("Pack")
        val ids = steps(pack).map { it.localId }
        io { repo.moveStep(pack.localId, ids[2], listOf(ids[2], ids[0], ids[1])) }
        sync()

        assertEquals(listOf("Scarf", "Socks", "Hat"), steps(task("Pack")).map { it.title })
        assertEquals(listOf("Scarf", "Socks", "Hat"), remoteTasks(errands.id).single().steps.map { it.title })
        assertEquals(0, pending())
    }

    @Test
    fun microsoftOrderStaysOnThePhoneThroughSyncs() {
        useMicrosoftShape()
        val errands = io { remote.seed("Errands", listOf(TaskDraft("A"), TaskDraft("B"), TaskDraft("C"))) }
        sync()
        val (first, second, third) = localOrder("Errands")
        io { repo.moveTask(task(third).localId, afterId = null, beforeId = task(first).localId) }
        assertEquals(0, pending())
        io { remote.editRemotely(errands.id, task(second).remoteId!!, TaskPatch(title = "B2")) }
        sync()

        assertEquals(listOf(third, first, "B2"), localOrder("Errands"))
    }

    @Test
    fun microsoftStepOrderStaysOnThePhone() {
        useMicrosoftShape()
        io {
            remote.seed("Errands", listOf(TaskDraft("Pack", steps = listOf(StepDraft("Socks"), StepDraft("Hat")))))
        }
        sync()
        val pack = task("Pack")
        val ids = steps(pack).map { it.localId }
        io { repo.moveStep(pack.localId, ids[1], listOf(ids[1], ids[0])) }
        sync()

        assertEquals(listOf("Hat", "Socks"), steps(task("Pack")).map { it.title })
        assertEquals(0, pending())
    }

    private fun useMicrosoftShape() {
        remote = FakeProvider(
            capabilities = ProviderCapabilities(importance = true, manualOrder = false, dueTime = false),
            clock = clock,
            pageSize = 3
        )
    }

    private fun localOrder(list: String): List<String> = tasks(list)
        .filter { !it.completed }
        .sortedWith(OrderKeys.taskComparator)
        .map { it.title }
}
