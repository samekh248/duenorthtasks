package app.duenorth.tasks.ui.synclog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duenorth.tasks.data.db.SyncLogEntity
import app.duenorth.tasks.data.db.SyncLogType
import app.duenorth.tasks.data.repo.SyncLogRepository
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SyncLogKind(val label: String) {
    CONFLICT("conflict"),
    PROBLEM("sync problem"),
    PUT_BACK("put back")
}

/** A step of a replaced version. */
@Immutable
data class ReplacedStep(val title: String, val done: Boolean)

/** The version of a task that lost a conflict, as the log kept it. */
@Immutable
data class ReplacedVersion(
    /** True when the version came from the account; false when it was the one on this phone. */
    val fromAccount: Boolean,
    val title: String,
    val details: String?,
    val due: String?,
    val completed: Boolean,
    val important: Boolean,
    val steps: List<ReplacedStep>
)

@Immutable
data class SyncLogRow(
    val id: Long,
    val kind: SyncLogKind,
    val time: String,
    val summary: String,
    val replaced: ReplacedVersion?
)

@Immutable
data class SyncLogDay(val label: String, val rows: List<SyncLogRow>)

@Immutable
data class SyncLogUiState(val loading: Boolean = true, val days: List<SyncLogDay> = emptyList()) {
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

/** The "sync log" page (T058, FR-023): what sync settled or couldn't do, newest first, by day. */
@HiltViewModel
class SyncLogViewModel @Inject constructor(private val log: SyncLogRepository, clock: Clock) : ViewModel() {
    private val zone: ZoneId = clock.zone

    val state: StateFlow<SyncLogUiState> = combine(log.entries, todayFlow(clock)) { entries, today ->
        SyncLogUiState(loading = false, days = group(entries, today))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncLogUiState())

    fun clear() {
        viewModelScope.launch { log.clear() }
    }

    private fun group(entries: List<SyncLogEntity>, today: LocalDate): List<SyncLogDay> =
        entries.groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (day, dayEntries) -> SyncLogDay(dayLabel(day, today), dayEntries.map { it.toRow(today) }) }

    private fun SyncLogEntity.toRow(today: LocalDate) = SyncLogRow(
        id = id,
        kind = when (type) {
            SyncLogType.CONFLICT -> SyncLogKind.CONFLICT
            SyncLogType.ERROR -> SyncLogKind.PROBLEM
            SyncLogType.RECOVERED -> SyncLogKind.PUT_BACK
        },
        time = timeFormat.format(at.atZone(zone)).lowercase(Locale.getDefault()),
        summary = summary,
        replaced = losingVersionJson?.let { parseReplaced(it, today) }
    )

    internal companion object {
        private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
        private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        private val dayYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

        fun dayLabel(day: LocalDate, today: LocalDate): String {
            val ago = ChronoUnit.DAYS.between(day, today)
            return when {
                ago == 0L -> "today"
                ago == 1L -> "yesterday"
                ago in 2..6 -> weekdayFormat.format(day)
                day.year == today.year -> dayFormat.format(day)
                else -> dayYearFormat.format(day)
            }.lowercase(Locale.getDefault())
        }

        /**
         * Reads the JSON the sync engine keeps for the losing version. The account's copy carries
         * an "updated" time; the phone's doesn't. Anything unreadable just shows no version.
         */
        fun parseReplaced(json: String, today: LocalDate): ReplacedVersion? = runCatching {
            val obj = Json.parseToJsonElement(json).jsonObject
            ReplacedVersion(
                fromAccount = "updated" in obj,
                title = obj.string("title").orEmpty(),
                details = obj.string("notes")?.takeIf { it.isNotBlank() },
                due = obj.string("dueDate")?.let { DueText.dueLine(LocalDate.parse(it), today) },
                completed = obj.bool("completed"),
                important = obj.bool("important"),
                steps = obj["steps"]?.jsonArray.orEmpty().map {
                    val step = it.jsonObject
                    ReplacedStep(step.string("title").orEmpty(), step.bool("done"))
                }
            )
        }.getOrNull()

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
    }
}
