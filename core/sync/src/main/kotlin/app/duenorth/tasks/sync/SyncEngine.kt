package app.duenorth.tasks.sync

import app.duenorth.tasks.data.db.AuthState
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.TaskProvider
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val backfillMutex = Mutex()
    private val store = SyncStore(db, clock, newId, journal)
    private val running = MutableStateFlow(false)
    private val loadingHistory = MutableStateFlow(false)

    /** Lists whose open tasks are in but whose history (completed tasks, change cursor) is not yet. */
    private val deferred = MutableStateFlow<Set<String>>(emptySet())

    /**
     * True while a sync runs: the only thing the UI shows for it (Metro progress dots). A
     * [backfill] does not count, so the dots stop once open tasks are in.
     */
    val isSyncing: StateFlow<Boolean> = running.asStateFlow()

    /**
     * True while a [backfill] runs. The dots ignore it so the app reads as ready, but the sync
     * button keeps turning, so a first sync of a long history still shows it is working.
     */
    val isLoadingHistory: StateFlow<Boolean> = loadingHistory.asStateFlow()

    /** True when a sync left lists for [backfill]; the scheduler then runs it as its own job. */
    val backfillPending: Boolean get() = deferred.value.isNotEmpty()

    /** Runs one sync. Concurrent calls wait for the one in progress and then run their own. */
    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(dispatcher) {
            running.value = true
            try {
                guarded { provider, stillConnected ->
                    // Pull first: a pending edit only goes out if it is newer than what changed
                    // elsewhere (FR-023), and creates whose answer was lost are recognised before
                    // being retried. Lists seen for the first time bring only their open tasks here;
                    // their history is left to [backfill], which never holds a sync up.
                    val later = Puller(store, provider, holds, stillConnected).pullAll(loading = deferred.value)
                    deferred.update { it + later.map { list -> list.localId } }
                    if (!stillConnected()) return@guarded SyncResult.NoAccount
                    val waited = Pusher(store, provider).pushAll()
                    if (stillConnected()) {
                        store.accounts.get()?.let { store.accounts.upsert(it.copy(lastSyncAt = store.now())) }
                    }
                    if (waited ||
                        db.pendingOperationDao().all().isNotEmpty()
                    ) {
                        SyncResult.Retry()
                    } else {
                        SyncResult.Success
                    }
                }
            } finally {
                running.value = false
            }
        }
    }

    /**
     * Fetches the full history of the lists a first sync left behind, which can take minutes for
     * an account with years of completed tasks. It runs beside [sync] rather than under its lock,
     * so tapping sync, or adding a task, while it runs still sends changes straight away.
     */
    suspend fun backfill(): SyncResult = backfillMutex.withLock {
        withContext(dispatcher) {
            loadingHistory.value = deferred.value.isNotEmpty()
            try {
                loadHistory()
            } finally {
                loadingHistory.value = false
            }
        }
    }

    private suspend fun loadHistory(): SyncResult = guarded { provider, stillConnected ->
        val lists = store.sync.allLists().filter {
            it.localId in deferred.value && it.remoteId != null && !it.deletedLocally && it.tasksCursor == null
        }
        try {
            Puller(store, provider, holds, stillConnected).backfill(lists)
        } finally {
            // A list is done once it has a cursor; the rest wait for the next attempt.
            val done = store.sync.allLists().filter { it.tasksCursor != null || it.deletedLocally }
                .map { it.localId }.toSet()
            val gone = deferred.value - store.sync.allLists().map { it.localId }.toSet()
            deferred.update { it - done - gone }
        }
        if (!stillConnected()) SyncResult.NoAccount else SyncResult.Success
    }

    /** Checks the account, then runs [block], handling each [ProviderError] as the class comment says. */
    private suspend fun guarded(
        block: suspend (provider: TaskProvider, stillConnected: suspend () -> Boolean) -> SyncResult
    ): SyncResult {
        val account = store.accounts.get() ?: return SyncResult.NoAccount
        if (account.authState == AuthState.NEEDS_SIGN_IN) return SyncResult.NeedsSignIn
        val provider = registry.current() ?: return SyncResult.NoAccount
        // A switch of service deletes the account row; stop rather than write into the next one.
        val stillConnected: suspend () -> Boolean = { store.accounts.get()?.provider == account.provider }

        return try {
            block(provider, stillConnected)
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
