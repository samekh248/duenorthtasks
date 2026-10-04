package app.duenorth.tasks.provider.fake

import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.api.TaskProviderContractTest

class FakeProviderContractTest : TaskProviderContractTest() {
    override suspend fun newProvider(): TaskProvider = FakeProvider().also { it.signIn(object : SignInHost {}) }

    override suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch) {
        (provider as FakeProvider).editRemotely(listId, taskId, patch)
    }
}

/** The same contract with small pages and no importance, the way Google Tasks behaves. */
class FakeProviderGoogleShapeContractTest : TaskProviderContractTest() {
    override suspend fun newProvider(): TaskProvider = FakeProvider(
        capabilities = ProviderCapabilities(importance = false, manualOrder = true, dueTime = false),
        pageSize = 1
    ).also { it.signIn(object : SignInHost {}) }

    override suspend fun editRemotely(provider: TaskProvider, listId: String, taskId: String, patch: TaskPatch) {
        (provider as FakeProvider).editRemotely(listId, taskId, patch)
    }
}
