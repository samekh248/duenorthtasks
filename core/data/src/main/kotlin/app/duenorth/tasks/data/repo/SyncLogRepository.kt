package app.duenorth.tasks.data.repo

import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.db.SyncLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * The sync log (FR-023): conflicts the engine settled, changes it put back and syncs that failed,
 * newest first. Only the sync engine writes it; the app reads and clears it.
 */
class SyncLogRepository(db: DueNorthDatabase) {
    private val log = db.syncLogDao()

    val entries: Flow<List<SyncLogEntity>> = log.observeAll()

    suspend fun clear() = log.clear()
}
