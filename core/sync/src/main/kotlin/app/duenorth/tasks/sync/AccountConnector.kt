package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.AccountDao
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.AuthState
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.provider.api.TaskProvider

/**
 * Connects the one service (FR-020) and signs in again after access lapsed. Switching to the
 * other service is US4's job (`AccountManager.switchProvider`), so connecting a second one here is
 * refused rather than silently replacing the first.
 */
class AccountConnector(
    private val accounts: AccountDao,
    private val providers: Map<ProviderKind, () -> TaskProvider>,
    private val onConnected: () -> Unit
) {
    suspend fun connect(kind: ProviderKind, host: SignInHost): AccountEntity {
        val existing = accounts.get()
        check(existing == null || existing.provider == kind) {
            "Already connected to ${existing?.provider}; disconnect it first (constitution Principle III)"
        }
        val provider = checkNotNull(providers[kind]) { "No provider is built into this app for $kind" }()
        val info = provider.signIn(host)
        val account = (existing ?: AccountEntity(provider = kind, displayName = info.displayName, email = info.email))
            .copy(displayName = info.displayName, email = info.email, authState = AuthState.OK)
        accounts.upsert(account)
        onConnected()
        return account
    }
}
