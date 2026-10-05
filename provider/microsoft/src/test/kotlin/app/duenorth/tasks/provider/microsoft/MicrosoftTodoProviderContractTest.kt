package app.duenorth.tasks.provider.microsoft

import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.api.TaskProviderContractTest
import java.time.ZoneId
import org.junit.jupiter.api.AfterEach

/**
 * The shared provider contract (constitution Principle V) against a fake Graph endpoint that
 * answers in recorded Graph shapes. Small delta pages exercise `@odata.nextLink` paging.
 */
open class MicrosoftTodoProviderContractTest : TaskProviderContractTest() {
    private val servers = mutableListOf<FakeGraphServer>()
    private val serverFor = mutableMapOf<TaskProvider, FakeGraphServer>()

    open fun newServer() = FakeGraphServer(pageSize = 1)

    override suspend fun newProvider(): TaskProvider {
        val server = newServer().also { servers += it }
        val provider = MicrosoftTodoProvider.create(
            auth = TestAuth(),
            baseUrl = server.baseUrl,
            zone = { ZoneId.of("America/Chicago") }
        )
        provider.signIn(TestHost)
        serverFor[provider] = server
        return provider
    }

    override suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch) {
        serverFor.getValue(provider).editRemotely(taskId, patch.toPatchBody(ZoneId.of("America/Chicago")))
    }

    @AfterEach
    fun closeServers() = servers.forEach { it.shutdown() }
}

/** The same contract when delta ignores `$expand=checklistItems` and steps are fetched per task. */
class MicrosoftTodoProviderUnexpandedDeltaContractTest : MicrosoftTodoProviderContractTest() {
    override fun newServer() = FakeGraphServer(pageSize = 5, expandOnDelta = false)
}
