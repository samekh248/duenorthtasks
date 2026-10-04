package app.duenorth.tasks.provider.fake

import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.TaskDraft
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeProviderTest {
    @Test
    fun queuedFailureIsThrownOnceThenCallsSucceed() = runTest {
        val provider = FakeProvider()
        provider.failNext(ProviderError.Transient("offline"))

        assertThrows<ProviderError.Transient> { provider.getLists() }
        assertEquals(emptyList<Any>(), provider.getLists())
    }

    @Test
    fun recordsCallsInOrder() = runTest {
        val provider = FakeProvider()
        val list = provider.createList("Errands")
        provider.createTask(list.id, TaskDraft("Buy milk"))

        assertEquals(listOf("createList", "createTask"), provider.calls)
    }

    @Test
    fun garbageCursorIsReportedAsExpired() = runTest {
        val provider = FakeProvider()
        val list = provider.createList("Errands")

        assertThrows<ProviderError.CursorExpired> { provider.getTaskChanges(list.id, "nonsense") }
    }
}
