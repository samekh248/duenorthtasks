package app.duenorth.tasks.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** When a sync asked for now actually runs, with WorkManager's test driver (no network until set). */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler
    private lateinit var settings: SyncSettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        settings = SyncSettingsStore(PreferenceDataStoreFactory.create { File(folder.root, "sync.preferences_pb") })
        scheduler = SyncScheduler(
            workManager = workManager,
            settings = settings,
            localEdits = emptyFlow(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @Test
    fun aTapReplacesASyncThatIsOnlyWaitingInsteadOfQueueingBehindIt() {
        scheduler.syncNow()
        settle(SyncScheduler.NOW, 1)
        scheduler.syncNow()
        settle(SyncScheduler.NOW, 2)

        // Appending would leave the tap BLOCKED behind a sync that may sit in backoff for minutes.
        val states = states(SyncScheduler.NOW)
        assertEquals(1, states.count { it == WorkInfo.State.ENQUEUED })
        assertTrue(WorkInfo.State.BLOCKED !in states)
    }

    @Test
    fun theHistoryLoadIsItsOwnJobAndSignOutStopsIt() {
        scheduler.syncNow()
        settle(SyncScheduler.NOW, 1)
        scheduler.backfillSoon()
        settle(SyncScheduler.BACKFILL, 1)
        scheduler.backfillSoon()
        Thread.sleep(200)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(SyncScheduler.BACKFILL))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(SyncScheduler.NOW))

        scheduler.cancelAll()

        assertTrue(states(SyncScheduler.BACKFILL).all { it == WorkInfo.State.CANCELLED })
    }

    @Test
    fun fiveAndTenMinutesRunAsAChainOfOneTimeSyncsAndLongerIntervalsStopIt() {
        runBlocking { settings.setInterval(5) }
        scheduler.syncNow()
        settle(SyncScheduler.TICK, 1)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(SyncScheduler.TICK))

        // A finished tick queues the next one behind itself.
        scheduler.nextTick()
        settle(SyncScheduler.TICK, 2)
        assertEquals(2, states(SyncScheduler.TICK).size)

        runBlocking { settings.setInterval(30) }
        scheduler.syncNow()
        val until = System.currentTimeMillis() + 5_000
        while (states(SyncScheduler.TICK).any { it != WorkInfo.State.CANCELLED } &&
            System.currentTimeMillis() < until
        ) {
            Thread.sleep(10)
        }
        assertTrue(states(SyncScheduler.TICK).all { it == WorkInfo.State.CANCELLED })
    }

    /** The scheduler reads settings off the calling thread; waits until [name] has [count] requests. */
    private fun settle(name: String, count: Int) {
        val until = System.currentTimeMillis() + 5_000
        while (workManager.getWorkInfosForUniqueWork(name).get().size < count && System.currentTimeMillis() < until) {
            Thread.sleep(10)
        }
    }

    private fun states(name: String) = workManager.getWorkInfosForUniqueWork(name).get().map { it.state }
}
