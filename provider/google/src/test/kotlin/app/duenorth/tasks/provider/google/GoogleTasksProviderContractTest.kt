package app.duenorth.tasks.provider.google

import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.api.TaskProviderContractTest
import org.junit.jupiter.api.AfterEach

/** The shared provider contract (constitution Principle V), run against the fake Google server. */
class GoogleTasksProviderContractTest : TaskProviderContractTest() {
    private val servers = mutableListOf<FakeGoogleTasksServer>()

    override suspend fun newProvider(): TaskProvider {
        val server = FakeGoogleTasksServer(withDefaultList = false).also { servers += it }
        return googleProvider(server)
    }

    override suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch) {
        servers.last().editOnWeb(taskId, title = patch.title)
    }

    @AfterEach
    fun closeServers() = servers.forEach { it.close() }
}

/** The same contract with Google paging two items at a time, so every multi-page path runs. */
class GoogleTasksProviderPagedContractTest : TaskProviderContractTest() {
    private val servers = mutableListOf<FakeGoogleTasksServer>()

    override suspend fun newProvider(): TaskProvider {
        val server = FakeGoogleTasksServer(pageSize = 2, withDefaultList = false).also { servers += it }
        return googleProvider(server)
    }

    override suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch) {
        servers.last().editOnWeb(taskId, title = patch.title)
    }

    @AfterEach
    fun closeServers() = servers.forEach { it.close() }
}
