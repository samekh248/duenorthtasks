package app.duenorth.tasks.ui.account

import app.duenorth.tasks.BuildConfig
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.SignInHost
import app.duenorth.tasks.sync.AccountConnector
import app.duenorth.tasks.sync.SyncScheduler
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing in and out of the one connected service (constitution Principle III). Signing out stops
 * sync, signs out of the service and deletes the account row, which takes every list, task and
 * queued change on the phone with it. Tasks already synced stay in that account.
 *
 * "This phone only" (spec 010) has no account to sign in to: connecting just writes the account
 * row and the default list, and signing out deletes everything, since there is no other copy.
 */
@Singleton
class AccountSession @Inject constructor(
    private val accounts: AccountRepository,
    private val connector: AccountConnector,
    private val scheduler: SyncScheduler,
    private val registry: ProviderRegistry,
    private val tasks: TaskRepository,
    demo: Optional<DemoAccount>
) {
    private val demoAccount: DemoAccount? = demo.orElse(null)

    val demoAvailable: Boolean = demoAccount != null

    /** Whether this build has the client ID the service's sign-in needs (kept out of git). */
    fun isConfigured(kind: ProviderKind): Boolean = when (kind) {
        ProviderKind.GOOGLE -> BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
        ProviderKind.MICROSOFT -> BuildConfig.MSAL_CLIENT_ID.isNotBlank()
        ProviderKind.FAKE -> demoAvailable
        ProviderKind.LOCAL -> true
    }

    suspend fun connect(kind: ProviderKind, host: SignInHost) {
        when (kind) {
            ProviderKind.FAKE -> connectDemo()
            ProviderKind.LOCAL -> connectLocal()
            else -> connector.connect(kind, host)
        }
    }

    /** Starts "this phone only" with one empty list, as a new Google or To Do account has. */
    private suspend fun connectLocal() {
        if (accounts.current() == null) accounts.connect(ProviderKind.LOCAL, displayName = LOCAL_NAME, email = null)
        tasks.defaultListIdOrCreate()
    }

    suspend fun connectDemo() {
        checkNotNull(demoAccount) { "This build has no demo account" }.connect()
        scheduler.syncNow()
    }

    suspend fun signOut() {
        scheduler.cancelAll()
        // Signing out of the service is best effort; the phone forgets the account either way.
        runCatching { registry.current()?.signOut() }
        accounts.disconnect()
    }

    /** Signs out of the current service, then into [kind] (FR-021: nothing is copied across). */
    suspend fun switchTo(kind: ProviderKind, host: SignInHost) {
        signOut()
        connect(kind, host)
    }
}

private const val LOCAL_NAME = "this phone"
