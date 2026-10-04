package app.duenorth.tasks.data.provider

import app.duenorth.tasks.data.db.AccountDao
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider

/**
 * Hands out the single [TaskProvider] for the connected account (constitution Principle III).
 * The sync engine asks this, never a concrete provider.
 */
fun interface ProviderRegistry {
    /** The provider for the account row, or null when no service is connected. */
    suspend fun current(): TaskProvider?
}

/**
 * Picks the provider named by the account row. [providers] is filled in by the app's DI wiring,
 * the only place allowed to see the concrete provider classes.
 */
class AccountProviderRegistry(
    private val accounts: AccountDao,
    private val providers: Map<ProviderKind, () -> TaskProvider>
) : ProviderRegistry {
    override suspend fun current(): TaskProvider? {
        val kind = accounts.get()?.provider ?: return null
        val factory = checkNotNull(providers[kind]) { "No provider is built into this app for $kind" }
        return factory()
    }
}
