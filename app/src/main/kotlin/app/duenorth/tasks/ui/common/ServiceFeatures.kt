package app.duenorth.tasks.ui.common

import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.data.repo.AccountRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the connected service can store, for screens that hide what it can't. Asks the provider
 * through [ProviderRegistry], so the UI never names Google or Microsoft (constitution Principle III).
 */
@Singleton
class ServiceFeatures @Inject constructor(accounts: AccountRepository, private val registry: ProviderRegistry) {
    /** The importance star (FR-014): To Do has it, Google Tasks doesn't, so Google hides it. */
    val importance: Flow<Boolean> = accounts.account
        .map { account -> account != null && registry.current()?.capabilities?.importance == true }
        .distinctUntilChanged()

    /** Lists can be shared (spec 002): To Do can, Google Tasks can't. */
    val sharedLists: Flow<Boolean> = accounts.account
        .map { account -> account != null && registry.current()?.capabilities?.sharedLists == true }
        .distinctUntilChanged()

    /** Task and step order (specs/003-reordering): Google Tasks stores it, To Do doesn't. */
    val storesOrder: Flow<Boolean> = accounts.account
        .map { account -> account != null && registry.current()?.capabilities?.manualOrder == true }
        .distinctUntilChanged()
}
