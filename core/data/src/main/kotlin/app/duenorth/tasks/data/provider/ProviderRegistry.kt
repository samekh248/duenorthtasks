package app.duenorth.tasks.data.provider

import app.duenorth.tasks.data.db.AccountDao
import app.duenorth.tasks.provider.api.LOCAL_CAPABILITIES
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider

/**
 * Hands out the single [TaskProvider] for the connected account (constitution Principle III).
 * The sync engine asks this, never a concrete provider.
 */
fun interface ProviderRegistry {
    /** The provider for the account row, or null when no service is connected ("this phone only" included). */
    suspend fun current(): TaskProvider?

    /** What the phone can keep for the account: the provider's, or "this phone only"'s, or null with no account. */
    suspend fun capabilities(): ProviderCapabilities? = current()?.capabilities
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
        // "This phone only" has nothing to sync with (spec 010).
        if (kind == ProviderKind.LOCAL) return null
        val factory = checkNotNull(providers[kind]) { "No provider is built into this app for $kind" }
        return factory()
    }

    override suspend fun capabilities(): ProviderCapabilities? = when (accounts.get()?.provider) {
        null -> null
        ProviderKind.LOCAL -> LOCAL_CAPABILITIES
        else -> current()?.capabilities
    }
}
