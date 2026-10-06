package app.duenorth.tasks.ui.stats

import app.duenorth.tasks.data.db.CompletedStat
import app.duenorth.tasks.data.db.OpenStat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 005 SC-402: every stats number against a hand count. */
class StatsTest {
    private val denver = ZoneId.of("America/Denver")

    /** Thursday Oct 8, 2026. */
    private val today = LocalDate.of(2026, 10, 8)

    /** Done at noon Denver time on [day], due [due], in [list]. */
    private fun done(day: LocalDate, due: LocalDate? = null, list: String = "home") =
        CompletedStat(day.atTime(12, 0).atZone(denver).toInstant(), due, list)

    private fun compute(
        completed: List<CompletedStat>,
        open: List<OpenStat> = emptyList(),
        first: DayOfWeek = DayOfWeek.SUNDAY,
        partial: Boolean = false
    ) = Stats.compute(completed, open, today, denver, first, partial)

    @Test
    fun thisWeekCountsFromTheLocaleWeekStart() {
        val rows = listOf(
            done(today.minusDays(4)), // Sunday Oct 4
            done(today.minusDays(3)),
            done(today.minusDays(3)),
            done(today),
            done(today.minusDays(5)) // Saturday Oct 3: last week for a Sunday start
        )
        val sunday = compute(rows)
        assertEquals(LocalDate.of(2026, 10, 4), sunday.weekStart)
        assertEquals(listOf(1, 2, 0, 0, 1, 0, 0), sunday.thisWeek)
        assertEquals(4, sunday.thisWeekTotal)
        assertEquals(1, sunday.lastWeekTotal)

        val monday = compute(rows, first = DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 5), monday.weekStart)
        assertEquals(listOf(2, 0, 0, 1, 0, 0, 0), monday.thisWeek)
        assertEquals(2, monday.lastWeekTotal)
    }

    @Test
    fun streakSurvivesUntilTodayEnds() {
        val run = (1L..5L).map { done(today.minusDays(it)) }
        assertEquals(5, compute(run).streak)
        assertEquals(6, compute(run + done(today)).streak)
        // A gap two days ago breaks it.
        assertEquals(1, compute(listOf(done(today.minusDays(1)), done(today.minusDays(3)))).streak)
        assertEquals(0, compute(listOf(done(today.minusDays(2)))).streak)
    }

    @Test
    fun bestStreakIsTheLongestRun() {
        val rows = (10L..16L).map { done(today.minusDays(it)) } + done(today.minusDays(1))
        val stats = compute(rows)
        assertEquals(7, stats.bestStreak)
        assertEquals(1, stats.streak)
    }

    @Test
    fun onTimeNeedsFiveDatedTasks() {
        val four = (1L..4L).map { done(today.minusDays(it), due = today) }
        assertNull(compute(four).onTimePercent)
        val rows = four + listOf(
            done(today.minusDays(2), due = today.minusDays(3)), // late
            done(today.minusDays(1)) // no due date: not counted
        )
        assertEquals(80, compute(rows).onTimePercent)
    }

    @Test
    fun onTimeOnlyLooksAtTheLast30Days() {
        val old = (31L..40L).map { done(today.minusDays(it), due = today.minusDays(50)) }
        val recent = (1L..5L).map { done(today.minusDays(it), due = today) }
        assertEquals(100, compute(old + recent).onTimePercent)
    }

    @Test
    fun microsoftMidnightUtcIsReadAsTheDate() {
        // To Do sends a date-only completion as midnight UTC; in Denver that is 6 pm the day before.
        val midnight = LocalDate.of(2026, 10, 8).atStartOfDay().toInstant(ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 10, 8), Stats.dayOf(midnight, denver))
        // A real time just after midnight UTC is still the evening before in Denver.
        assertEquals(LocalDate.of(2026, 10, 7), Stats.dayOf(midnight.plusSeconds(60), denver))
        // A tick on the phone late in the evening stays on its local day.
        val evening = Instant.parse("2026-10-08T04:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 7), Stats.dayOf(evening, denver))
    }

    @Test
    fun gridCovers12WeeksAndMarksTheFuture() {
        val stats = compute(listOf(done(today), done(today), done(today.minusDays(20))))
        assertEquals(Stats.GRID_DAYS, stats.grid.size)
        assertEquals(stats.weekStart.minusWeeks(11), stats.gridStart)
        val todayIndex = (today.toEpochDay() - stats.gridStart.toEpochDay()).toInt()
        assertEquals(2, stats.grid[todayIndex])
        assertEquals(-1, stats.grid[todayIndex + 1])
        assertEquals(2, stats.gridMax)
        assertEquals(0, stats.level(0))
        assertEquals(3, stats.level(1))
        assertEquals(5, stats.level(2))
    }

    @Test
    fun rightNowCounts() {
        val open = listOf(
            OpenStat(today.minusDays(2)),
            OpenStat(today),
            OpenStat(null),
            OpenStat(null),
            OpenStat(today.plusDays(4))
        )
        val stats = compute(listOf(done(today)), open)
        assertEquals(5, stats.open)
        assertEquals(1, stats.overdue)
        assertEquals(2, stats.noDate)
    }

    @Test
    fun byListKeepsTheTopFiveOfTheLast30Days() {
        val rows = (1..7).flatMap { list -> List(list) { done(today.minusDays(1), list = "l$list") } } +
            List(10) { done(today.minusDays(45), list = "old") }
        val stats = compute(rows)
        assertEquals(listOf("l7", "l6", "l5", "l4", "l3"), stats.byList.map { it.listId })
        assertEquals(7, stats.byList.first().count)
        assertEquals(2, stats.moreLists)
    }

    @Test
    fun allTime() {
        val since = LocalDate.of(2026, 8, 11) // a Tuesday, 59 days before today
        val rows = listOf(done(since), done(since), done(since.plusDays(7)), done(today))
        val stats = compute(rows)
        assertEquals(4, stats.allTime)
        assertEquals(since, stats.since)
        assertEquals(DayOfWeek.TUESDAY, stats.busiestDay)
        // 9 Tuesdays from Aug 11 to Oct 6, 3 tasks on them.
        assertEquals(3.0 / 9, stats.busiestAverage, 0.0001)
        assertEquals(LocalDate.of(2026, 8, 9), stats.bestWeekStart)
        assertEquals(2, stats.bestWeekTotal)
        assertEquals(4 / (59 / 7.0), stats.perWeek, 0.0001)
    }

    @Test
    fun nothingDoneYet() {
        val stats = compute(emptyList(), partial = true)
        assertEquals(0, stats.allTime)
        assertEquals(0, stats.streak)
        assertNull(stats.since)
        assertNull(stats.busiestDay)
        assertTrue(stats.partial)
        assertFalse(compute(emptyList()).partial)
    }
}
