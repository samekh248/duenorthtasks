package app.duenorth.tasks.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.AccountEntity
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.StepEntity
import app.duenorth.tasks.data.db.SyncLogEntity
import app.duenorth.tasks.data.db.TaskEntity
import app.duenorth.tasks.data.db.TaskListEntity
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.RemoteTask
import app.duenorth.tasks.provider.fake.FakeProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/** A clock the test moves by hand; both the phone and the fake service read it. */
class TestClock(var now: Instant = Instant.parse("2026-10-04T12:00:00Z")) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    fun tick(seconds: Long = 1) {
        now = now.plusSeconds(seconds)
    }
}

/** Room in memory, the real repository, and [FakeProvider] shaped like Google Tasks. */
abstract class SyncTestBase {
    protected val clock = TestClock()
    protected lateinit var db: DueNorthDatabase
    protected lateinit var repo: TaskRepository
    protected lateinit var remote: FakeProvider
    protected lateinit var engine: SyncEngine
    protected val holds = ListHolds()
    private var nextId = 0

    @Before
    fun setUpSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        repo = TaskRepository(db, clock, { remote.capabilities.manualOrder }) { "local${++nextId}" }
        remote = FakeProvider(
            capabilities = ProviderCapabilities(importance = false, manualOrder = true, dueTime = false),
            clock = clock,
            pageSize = 3
        )
        engine = SyncEngine(db, { remote }, holds, clock, { "pulled${++nextId}" }, Dispatchers.Unconfined)
        db.accountDao().upsert(AccountEntity(provider = ProviderKind.FAKE, displayName = "Demo", email = null))
    }

    @After
    fun tearDownSync() {
        db.close()
    }

    /**
     * Runs a sync with the clock moved on, so remote and local stamps never tie by accident, then
     * the history load it left behind, as the scheduler would.
     */
    protected fun sync(): SyncResult = runBlocking {
        clock.tick()
        engine.sync().also { if (engine.backfillPending) engine.backfill() }
    }

    protected fun <T> io(block: suspend () -> T): T = runBlocking {
        clock.tick()
        block()
    }

    protected fun lists(): List<TaskListEntity> = runBlocking {
        db.syncDao().allLists().filterNot { it.deletedLocally }
    }

    protected fun list(title: String): TaskListEntity = lists().single { it.title == title }

    protected fun tasks(listTitle: String): List<TaskEntity> = runBlocking {
        db.syncDao().tasksInList(list(listTitle).localId).filterNot { it.deletedLocally }
    }

    protected fun task(title: String): TaskEntity = runBlocking {
        db.syncDao().allLists().flatMap { db.syncDao().tasksInList(it.localId) }.single {
            it.title == title &&
                !it.deletedLocally
        }
    }

    protected fun steps(task: TaskEntity): List<StepEntity> = runBlocking {
        db.stepDao().forTask(task.localId).filterNot { it.deletedLocally }.sortedBy { it.sortOrder }
    }

    protected fun pending(): Int = runBlocking { db.pendingOperationDao().all().size }

    protected fun log(): List<SyncLogEntity> = runBlocking { db.syncLogDao().observeAll().first() }

    protected fun remoteTasks(listId: String): List<RemoteTask> = runBlocking {
        val all = mutableListOf<RemoteTask>()
        var page = remote.getTaskChanges(listId, null)
        all += page.changed
        while (page.hasMore) {
            page = remote.getTaskChanges(listId, page.nextCursor)
            all += page.changed
        }
        all
    }

    protected fun remoteList(title: String) = runBlocking { remote.getLists().single { it.title == title } }
}
