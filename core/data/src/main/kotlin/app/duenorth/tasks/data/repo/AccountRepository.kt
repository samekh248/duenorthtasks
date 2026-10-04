package app.duenorth.tasks.data.repo

import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.provider.api.ProviderKind
import kotlinx.coroutines.flow.Flow

/**
 * The single connected service (constitution Principle III). Connecting replaces nothing: callers
 * must [disconnect] first, which deletes the account row and with it every list, task and queued
 * change on the phone. Nothing is ever sent to either service from here.
 */
class AccountRepository(private val db: DueNorthDatabase) {
    private val accounts = db.accountDao()

    val account: Flow<AccountEntity?> = accounts.observe()

    suspend fun current(): AccountEntity? = accounts.get()

    suspend fun connect(provider: ProviderKind, displayName: String, email: String?) {
        val existing = accounts.get()
        check(existing == null || existing.provider == provider) {
            "Already connected to ${existing?.provider}; disconnect first"
        }
        accounts.upsert(AccountEntity(provider = provider, displayName = displayName, email = email))
    }

    suspend fun disconnect() {
        accounts.delete()
    }
}
