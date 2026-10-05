package app.duenorth.tasks.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskDraft
import app.duenorth.tasks.provider.api.TaskPatch
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.sync.SyncEngine
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives the benchmark build's fake service from adb (benchmark builds only):
 *
 * - [SEED] (`--ei tasks N`): makes sure the fake service has one list of N tasks due today and the
 *   phone has synced it; reconnects from scratch when the process was restarted. Answers "seeded"
 *   once the phone is up to date, so the benchmark can start measuring.
 * - [CHURN] (`--ei changes N --ei rounds R --ei latencyMs L`): edits N tasks "on the web" in R
 *   rounds with a sync after each, every service call taking L ms, and returns at once. This is
 *   the "500 changes while you scroll" of SC-005.
 */
@AndroidEntryPoint
class BenchmarkSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var accounts: AccountRepository

    @Inject lateinit var engine: SyncEngine

    @Inject lateinit var providers: Map<ProviderKind, @JvmSuppressWildcards Provider<TaskProvider>>

    override fun onReceive(context: Context, intent: Intent) {
        val fake = providers.getValue(ProviderKind.FAKE).get() as FakeProvider
        when (intent.action) {
            SEED -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        BenchmarkData.seed(fake, accounts, engine, intent.getIntExtra("tasks", DEFAULT_TASKS))
                        pending.resultData = "seeded"
                    } catch (e: Exception) {
                        Log.e(TAG, "Seed failed", e)
                        pending.resultData = "failed: ${e.message}"
                    } finally {
                        pending.finish()
                    }
                }
            }

            CHURN -> scope.launch {
                runCatching {
                    BenchmarkData.churn(
                        fake,
                        engine,
                        changes = intent.getIntExtra("changes", DEFAULT_CHANGES),
                        rounds = intent.getIntExtra("rounds", DEFAULT_ROUNDS).coerceAtLeast(1),
                        latency = intent.getIntExtra("latencyMs", DEFAULT_LATENCY_MS).milliseconds
                    )
                }.onFailure { Log.e(TAG, "Churn failed", it) }
            }
        }
    }

    companion object {
        const val SEED = "app.duenorth.tasks.bench.SEED"
        const val CHURN = "app.duenorth.tasks.bench.CHURN"
        private const val TAG = "BenchmarkSeed"
        private const val DEFAULT_TASKS = 1_000
        private const val DEFAULT_CHANGES = 500
        private const val DEFAULT_ROUNDS = 10
        private const val DEFAULT_LATENCY_MS = 150
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** The fake service's benchmark list, kept for the life of the process. */
private object BenchmarkData {
    private val mutex = Mutex()
    private var listId: String? = null
    private var taskIds: List<String> = emptyList()
    private var edits = 0

    suspend fun seed(fake: FakeProvider, accounts: AccountRepository, engine: SyncEngine, count: Int) {
        mutex.withLock {
            if (listId == null || taskIds.size != count) {
                // A new process has an empty fake service: start the phone over too.
                fake.latency = Duration.ZERO
                accounts.disconnect()
                accounts.connect(ProviderKind.FAKE, displayName = "benchmark", email = null)
                val list = fake.createList("Benchmark")
                val today = LocalDate.now()
                taskIds = (1..count).map { i ->
                    fake.createTask(
                        list.id,
                        TaskDraft(
                            title = "Task $i",
                            notes = if (i % 3 ==
                                0
                            ) {
                                "Details for task $i, long enough to wrap onto a second line."
                            } else {
                                null
                            },
                            dueDate = today
                        )
                    ).id
                }
                listId = list.id
            }
        }
        // Settle any sync still running from the last iteration, then pull whatever it left.
        fake.latency = Duration.ZERO
        engine.sync()
    }

    suspend fun churn(fake: FakeProvider, engine: SyncEngine, changes: Int, rounds: Int, latency: Duration) {
        val list = mutex.withLock { listId } ?: return
        val ids = taskIds
        val perRound = (changes + rounds - 1) / rounds
        var changed = 0
        repeat(rounds) {
            fake.latency = Duration.ZERO
            repeat(minOf(perRound, changes - changed)) {
                val id = ids[(edits++) % ids.size]
                fake.editRemotely(list, id, TaskPatch(title = "Edited on the web, change $edits"))
                changed++
            }
            fake.latency = latency
            engine.sync()
        }
        fake.latency = Duration.ZERO
    }
}
