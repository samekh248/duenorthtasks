package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.AuthState
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.provider.api.ProviderError
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Two-way sync with the one connected provider (constitution Principles III and IV): pull changes
 * since each list's cursor, applying the conflict rule to anything still pending here, then push
 * the outbox. Pushing blind first would let an old offline edit overwrite a newer one made on the
 * web, because both services accept field PATCHes without a version check. It only ever talks to Room and the
 * [ProviderRegistry]'s provider, never to Google or Microsoft types, and never on the main thread.
 *
 * How each [ProviderError] is handled (contracts/task-provider.md, "Errors"):
 * - AuthRequired: stop, mark the account NEEDS_SIGN_IN; local edits keep queuing.
 * - NotFound: drop the operation, or put the task back when it still had changes here.
 * - Conflict: leave it to the pull's conflict rule.
 * - RateLimited: stop and retry after the given time.
 * - CursorExpired: fetch that list in full.
 * - Transient: stop and let WorkManager retry with backoff.
 */
class SyncEngine(
    private val db: DueNorthDatabase,
    private val registry: ProviderRegistry,
    private val holds: ListHolds = ListHolds(),
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    journal: CreateJournal = InMemoryCreateJournal()
) {
    private val mutex = Mutex()
    private val store = SyncStore(db, clock, newId, journal)
    private val running = MutableStateFlow(false)

    /** True while a sync runs: the only thing the UI shows for it (Metro progress dots). */
    val isSyncing: StateFlow<Boolean> = running.asStateFlow()

    /** Runs one sync. Concurrent calls wait for the one in progress and then run their own. */
    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(dispatcher) {
            running.value = true
            try {
                runOnce()
            } finally {
                running.value = false
            }
        }
    }

    private suspend fun runOnce(): SyncResult {
        val account = store.accounts.get() ?: return SyncResult.NoAccount
        if (account.authState == AuthState.NEEDS_SIGN_IN) return SyncResult.NeedsSignIn
        val provider = registry.current() ?: return SyncResult.NoAccount
        // A switch of service deletes the account row; stop rather than write into the next one.
        val stillConnected: suspend () -> Boolean = { store.accounts.get()?.provider == account.provider }

        return try {
            // Pull first: a pending edit only goes out if it is newer than what changed elsewhere
            // (FR-023), and creates whose answer was lost are recognised before being retried.
            Puller(store, provider, holds, stillConnected).pullAll()
            if (!stillConnected()) return SyncResult.NoAccount
            val waited = Pusher(store, provider).pushAll()
            store.accounts.get()?.takeIf { it.provider == account.provider }?.let {
                store.accounts.upsert(it.copy(lastSyncAt = store.now()))
            }
            if (waited || db.pendingOperationDao().all().isNotEmpty()) SyncResult.Retry() else SyncResult.Success
        } catch (_: ProviderError.AuthRequired) {
            store.accounts.get()?.takeIf { it.provider == account.provider }?.let {
                store.accounts.upsert(it.copy(authState = AuthState.NEEDS_SIGN_IN))
            }
            SyncResult.NeedsSignIn
        } catch (e: ProviderError.RateLimited) {
            SyncResult.Retry(e.retryAfter)
        } catch (e: ProviderError) {
            if (stillConnected() &&
                e !is ProviderError.Transient
            ) {
                store.log(SyncLogType.ERROR, e.message ?: "Sync failed")
            }
            SyncResult.Retry()
        }
    }
}

sealed interface SyncResult {
    data object Success : SyncResult

    /** No service is connected (or it was disconnected mid-sync). */
    data object NoAccount : SyncResult

    /** The user has to sign in again; nothing more happens until they do. */
    data object NeedsSignIn : SyncResult

    /** Try again later; [after] is set when the service asked for a specific wait. */
    data class Retry(val after: Duration? = null) : SyncResult
}
