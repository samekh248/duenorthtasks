package app.duenorth.tasks.ui.stats

import androidx.compose.runtime.Immutable
import app.duenorth.tasks.data.db.CompletedStat
import app.duenorth.tasks.data.db.OpenStat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Done in the last 30 days in one list (spec 005 FR-415). */
@Immutable
data class ListCount(val listId: String, val count: Int)

/** Everything the stats section shows (spec 005), counted from the tasks on this phone. */
@Immutable
data class StatsUi(
    val today: LocalDate,
    /** The first day of this week, in the phone's locale. */
    val weekStart: LocalDate,
    /** Done on each day of this week, from [weekStart]; days after today are 0. */
    val thisWeek: List<Int>,
    val lastWeekTotal: Int,
    val streak: Int,
    val bestStreak: Int,
    /** Null until there are [Stats.MIN_DATED] dated tasks in the last 30 days (FR-413). */
    val onTimePercent: Int?,
    /** The first day of the 12-week grid, a week start. */
    val gridStart: LocalDate,
    /** Done per day from [gridStart], [Stats.GRID_DAYS] long; days after today are -1. */
    val grid: List<Int>,
    /** The most done on one day in the grid, which sets the shades (FR-414). */
    val gridMax: Int,
    val open: Int,
    val overdue: Int,
    val noDate: Int,
    val byList: List<ListCount>,
    val moreLists: Int,
    val allTime: Int,
    /** The day of the oldest completed task on the phone. */
    val since: LocalDate?,
    val perWeek: Double,
    val busiestDay: DayOfWeek?,
    val busiestAverage: Double,
    val bestWeekStart: LocalDate?,
    val bestWeekTotal: Int,
    /** Some lists' history is still loading, so totals may grow (FR-420). */
    val partial: Boolean
) {
    val thisWeekTotal: Int get() = thisWeek.sum()

    /** 0 for nothing done, else 1 to 5 by share of the busiest day. */
    fun level(count: Int): Int = if (count <= 0 ||
        gridMax <= 0
    ) {
        0
    } else {
        ceil(5.0 * count / gridMax).toInt().coerceIn(1, 5)
    }
}

/** Pure counting for spec 005, so every number is checked against fixed inputs (SC-402). */
object Stats {
    const val GRID_WEEKS = 12
    const val GRID_DAYS = GRID_WEEKS * 7
    const val RECENT_DAYS = 30L
    const val MIN_DATED = 5
    const val TOP_LISTS = 5

    /**
     * The calendar day a task was done. Microsoft To Do can report only a date, as midnight UTC
     * (research R2), which would land on the evening before west of Greenwich; read it as that date.
     */
    fun dayOf(at: Instant, zone: ZoneId): LocalDate = if (at.nano == 0 && at.epochSecond % SECONDS_PER_DAY == 0L) {
        LocalDate.ofEpochDay(at.epochSecond / SECONDS_PER_DAY)
    } else {
        at.atZone(zone).toLocalDate()
    }

    fun compute(
        completed: List<CompletedStat>,
        open: List<OpenStat>,
        today: LocalDate,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        partial: Boolean
    ): StatsUi {
        val days = completed.map { dayOf(it.completedAt, zone) }
        val perDay = HashMap<LocalDate, Int>()
        days.forEach { perDay.merge(it, 1, Int::plus) }
        val count = { day: LocalDate -> perDay[day] ?: 0 }

        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val thisWeek = (0L until 7L).map { weekStart.plusDays(it).let { d -> if (d.isAfter(today)) 0 else count(d) } }
        val lastWeekTotal = (1L..7L).sumOf { count(weekStart.minusDays(it)) }

        val gridStart = weekStart.minusWeeks(GRID_WEEKS - 1L)
        val grid = (0L until GRID_DAYS).map {
            gridStart.plusDays(it).let { d -> if (d.isAfter(today)) -1 else count(d) }
        }

        val recentFrom = today.minusDays(RECENT_DAYS - 1)
        val recent = completed.indices.filter { !days[it].isBefore(recentFrom) && !days[it].isAfter(today) }
        val dated = recent.filter { completed[it].dueDate != null }
        val onTime = dated.count { !days[it].isAfter(completed[it].dueDate) }
        val byList = recent.groupingBy { completed[it].listId }.eachCount()
            .map { (id, n) -> ListCount(id, n) }
            .sortedWith(compareByDescending<ListCount> { it.count }.thenBy { it.listId })

        val since = perDay.keys.minOrNull()
        val spanDays = since?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 0L
        val byWeekday = DayOfWeek.entries.associateWith { 0 }.toMutableMap()
        perDay.forEach { (day, n) -> byWeekday.merge(day.dayOfWeek, n, Int::plus) }
        val busiest = byWeekday.entries.filter { it.value > 0 }.maxByOrNull { it.value }
        val byWeek = HashMap<LocalDate, Int>()
        perDay.forEach { (day, n) ->
            byWeek.merge(day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)), n, Int::plus)
        }
        val best = byWeek.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenBy { it.key })

        return StatsUi(
            today = today,
            weekStart = weekStart,
            thisWeek = thisWeek,
            lastWeekTotal = lastWeekTotal,
            streak = streak(perDay.keys, today),
            bestStreak = bestStreak(perDay.keys),
            onTimePercent = if (dated.size >= MIN_DATED) (100.0 * onTime / dated.size).roundToInt() else null,
            gridStart = gridStart,
            grid = grid,
            gridMax = grid.maxOrNull()?.coerceAtLeast(0) ?: 0,
            open = open.size,
            overdue = open.count { it.dueDate?.isBefore(today) == true },
            noDate = open.count { it.dueDate == null },
            byList = byList.take(TOP_LISTS),
            moreLists = (byList.size - TOP_LISTS).coerceAtLeast(0),
            allTime = completed.size,
            since = since,
            perWeek = if (spanDays > 0) completed.size / (spanDays.coerceAtLeast(7) / 7.0) else 0.0,
            busiestDay = busiest?.key,
            busiestAverage = busiest?.let { it.value.toDouble() / occurrences(it.key, since!!, today) } ?: 0.0,
            bestWeekStart = best?.key,
            bestWeekTotal = best?.value ?: 0,
            partial = partial
        )
    }

    /** Days in a row with something done, ending today, or yesterday while today is still young (FR-412). */
    internal fun streak(done: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in done) today else today.minusDays(1)
        var n = 0
        while (day in done) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    internal fun bestStreak(done: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in done.sorted()) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** How many [weekday]s fall between [from] and [to], both included (at least 1). */
    private fun occurrences(weekday: DayOfWeek, from: LocalDate, to: LocalDate): Int {
        val first = from.with(TemporalAdjusters.nextOrSame(weekday))
        if (first.isAfter(to)) return 1
        return (ChronoUnit.DAYS.between(first, to) / 7 + 1).toInt()
    }

    private const val SECONDS_PER_DAY = 86_400L
}
