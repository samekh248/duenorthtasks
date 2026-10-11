package app.duenorth.tasks.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.stats.Stats
import app.duenorth.tasks.ui.stats.StatsUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The stats section after "lists" (spec 005): this week, streak, on time, 12 weeks, right now, by
 * list and all time, counted on the phone. Null [stats] shows placeholders (FR-430).
 */
@Composable
internal fun StatsSection(stats: StatsUi?, lists: List<ListRowUi>, serviceName: String, onOpenList: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("stats"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp, end = MetroDimens.Gutter)
    ) {
        if (stats == null) {
            item(key = "loading") { MetroTaskPlaceholders(count = 3) }
            return@LazyColumn
        }
        item(key = "week", contentType = "week") { ThisWeek(stats) }
        if (stats.allTime == 0) {
            item(key = "empty") {
                MetroText(
                    "Tick off a task and this page starts filling in.",
                    MetroTheme.typography.listName,
                    Modifier.padding(top = 24.dp),
                    color = MetroTheme.colors.secondary
                )
            }
            return@LazyColumn
        }
        item(key = "streak", contentType = "pair") {
            val plus = if (stats.partial) "+" else ""
            FigurePair(
                Figure(
                    "${stats.streak}$plus",
                    "day streak",
                    if (stats.partial) "still counting" else "best ${stats.bestStreak}"
                ),
                Figure(
                    stats.onTimePercent?.let { "$it%" } ?: "–",
                    "on time",
                    if (stats.onTimePercent != null) "last 30 days" else "not enough dated tasks yet"
                )
            )
        }
        item(key = "grid", contentType = "grid") { Weeks(stats) }
        if (stats.partial) {
            item(key = "partial") {
                MetroText(
                    "Older tasks are still coming in from ${serviceName.ifEmpty { "your service" }}. " +
                        "These numbers may grow.",
                    MetroTheme.typography.caption,
                    Modifier.padding(top = 8.dp),
                    color = MetroTheme.colors.secondary
                )
            }
        }
        item(key = "now", contentType = "now") { RightNow(stats) }
        val shown = stats.byList.mapNotNull { count ->
            lists.firstOrNull { it.id == count.listId }?.let {
                it to
                    count.count
            }
        }
        if (shown.isNotEmpty()) {
            item(key = "lists", contentType = "lists") { ByList(shown, stats.moreLists, onOpenList) }
        }
        item(key = "all", contentType = "all") { AllTime(stats) }
    }
}

private data class Figure(val value: String, val label: String, val note: String)

@Composable
private fun SubHeader(text: String) {
    MetroText(
        text,
        MetroTheme.typography.subheader,
        Modifier.padding(top = 20.dp, bottom = 6.dp),
        color = MetroTheme.colors.secondary
    )
}

@Composable
private fun BigNumber(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    MetroText(
        text,
        MetroTheme.typography.header,
        modifier,
        color = if (muted) MetroTheme.colors.secondary else MetroTheme.accent.text,
        maxLines = 1
    )
}

@Composable
private fun ThisWeek(stats: StatsUi) {
    val total = stats.thisWeekTotal
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$total done this week, ${stats.lastWeekTotal} last week"
        }
    ) {
        BigNumber("$total", muted = total == 0)
        MetroText("done this week", MetroTheme.typography.subheader)
        if (stats.allTime > 0) {
            MetroText(
                "${stats.lastWeekTotal} last week",
                MetroTheme.typography.caption,
                color = MetroTheme.colors.secondary
            )
        }
        WeekBars(stats)
    }
}

@Composable
private fun WeekBars(stats: StatsUi) {
    val max = maxOf(stats.thisWeek.maxOrNull() ?: 0, MIN_BAR_SCALE)
    val todayIndex = (stats.today.toEpochDay() - stats.weekStart.toEpochDay()).toInt()
    val bar = shadeAccent(-1).fill
    val todayBar = MetroTheme.accent.fill
    val empty = MetroTheme.colors.chrome
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth().height(BAR_HEIGHT + 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.thisWeek.forEachIndexed { i, n ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    if (i <= todayIndex) {
                        MetroText(
                            "$n",
                            MetroTheme.typography.caption,
                            Modifier.align(Alignment.CenterHorizontally),
                            color = MetroTheme.colors.secondary
                        )
                        Box(
                            Modifier.fillMaxWidth()
                                .height(maxOf(BAR_HEIGHT * (n.toFloat() / max), 2.dp))
                                .background(if (i == todayIndex) todayBar else bar)
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(2.dp).background(empty))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) { i ->
                val day = stats.weekStart.plusDays(
                    i.toLong()
                ).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MetroText(
                        day,
                        MetroTheme.typography.caption,
                        color = if (i == todayIndex) MetroTheme.accent.text else MetroTheme.colors.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FigurePair(first: Figure, second: Figure) {
    Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        listOf(first, second).forEach { f ->
            Column(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${f.value} ${f.label}, ${f.note}" }
            ) {
                MetroText(f.value, MetroTheme.typography.detailTitle, color = MetroTheme.accent.text, maxLines = 1)
                MetroText(f.label, MetroTheme.typography.subheader, maxLines = 1)
                MetroText(f.note, MetroTheme.typography.caption, color = MetroTheme.colors.secondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Weeks(stats: StatsUi) {
    var selected by remember(stats.gridStart) { mutableStateOf<Int?>(null) }
    val dark = MetroTheme.colors.isDark
    // Light: few is pale, many is deep. Dark: few is dim, many is bright (FR-414).
    val steps = if (dark) listOf(3, 2, 1, 0, -2) else listOf(-3, -2, -1, 0, 2)
    val shades = steps.map { shadeAccent(it).fill }
    val none = MetroTheme.colors.chrome
    val waiting = stats.since?.takeIf { stats.partial }
    val format = remember { DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault()) }
    SubHeader("last 12 weeks")
    Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        repeat(7) { weekday ->
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                repeat(Stats.GRID_WEEKS) { week ->
                    val index = week * 7 + weekday
                    val n = stats.grid[index]
                    val day = stats.gridStart.plusDays(index.toLong())
                    val color = when {
                        n < 0 -> Color.Transparent
                        waiting != null && day.isBefore(waiting) -> none.copy(alpha = 0.5f)
                        else -> stats.level(n).let { if (it == 0) none else shades[it - 1] }
                    }
                    Box(
                        Modifier.size(GRID_CELL)
                            .background(color)
                            .then(
                                if (index ==
                                    selected
                                ) {
                                    Modifier.border(2.dp, MetroTheme.colors.foreground)
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (n >= 0) {
                                    Modifier
                                        .clickable(interactionSource = null, indication = null) {
                                            selected = if (selected == index) null else index
                                        }
                                        .semantics { contentDescription = "${day.format(format)}, $n done" }
                                } else {
                                    Modifier.clearAndSetSemantics {}
                                }
                            )
                    )
                }
            }
        }
    }
    val pick = selected
    MetroText(
        if (pick !=
            null
        ) {
            "${stats.gridStart.plusDays(pick.toLong()).format(format)} · ${stats.grid[pick]} done"
        } else {
            "tap a day"
        },
        MetroTheme.typography.subheader,
        Modifier.padding(top = 6.dp),
        color = if (pick != null) MetroTheme.accent.text else MetroTheme.colors.secondary
    )
}

@Composable
private fun RightNow(stats: StatsUi) {
    SubHeader("right now")
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${stats.open} open, ${stats.overdue} overdue, ${stats.noDate} with no due date"
        }
    ) {
        Row {
            MetroText("${stats.open} open", MetroTheme.typography.subheader)
            if (stats.overdue > 0) {
                MetroText(" · ", MetroTheme.typography.subheader)
                MetroText(
                    "${stats.overdue} overdue",
                    MetroTheme.typography.subheader,
                    color = MetroTheme.colors.overdue
                )
            }
        }
        MetroText(
            "${stats.noDate} with no due date",
            MetroTheme.typography.caption,
            color = MetroTheme.colors.secondary
        )
    }
}

@Composable
private fun ByList(rows: List<Pair<ListRowUi, Int>>, more: Int, onOpenList: (String) -> Unit) {
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    SubHeader("by list · last 30 days")
    rows.forEach { (list, n) ->
        val shade = listAccent(list.id)
        Row(
            Modifier.fillMaxWidth()
                .metroTilt()
                .clickable(interactionSource = null, indication = null) { onOpenList(list.id) }
                .padding(vertical = 5.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${list.title}, $n done" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(22.dp).background(shade.fill))
            MetroText(list.title, MetroTheme.typography.listName, Modifier.width(LIST_NAME_WIDTH), maxLines = 1)
            Box(Modifier.width(LIST_BAR_WIDTH * (n.toFloat() / max)).height(14.dp).background(shade.fill))
            MetroText("$n", MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
        }
    }
    if (more > 0) {
        MetroText(
            "+ $more more ${if (more == 1) "list" else "lists"}",
            MetroTheme.typography.caption,
            color = MetroTheme.colors.secondary
        )
    }
}

@Composable
private fun AllTime(stats: StatsUi) {
    val plus = if (stats.partial) "+" else ""
    val month = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()) }
    val dayMonth = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    SubHeader("all time")
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${stats.allTime}$plus tasks done since ${stats.since?.format(month)}"
        }
    ) {
        BigNumber("%,d".format(stats.allTime) + plus)
        MetroText("tasks done since ${stats.since?.format(month)}", MetroTheme.typography.subheader)
        MetroText(
            "about ${stats.perWeek.roundToInt()} a week",
            MetroTheme.typography.caption,
            color = MetroTheme.colors.secondary
        )
    }
    val busiest = stats.busiestDay
    val bestWeek = stats.bestWeekStart
    if (busiest != null && bestWeek != null) {
        FigurePair(
            Figure(
                busiest.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                "busiest day",
                "%.1f done on average".format(stats.busiestAverage)
            ),
            Figure("${stats.bestWeekTotal}", "best week", weekRange(bestWeek, dayMonth))
        )
    }
    // This week against a typical one: the whole weeks before it in the grid that have history.
    val earlier = stats.grid.take((Stats.GRID_WEEKS - 1) * 7).filter { it > 0 }.sum()
    val weeksKnown = stats.since?.let { ChronoUnit.WEEKS.between(it, stats.weekStart).toInt() + 1 } ?: 1
    val typical = (earlier / weeksKnown.coerceIn(1, Stats.GRID_WEEKS - 1).toFloat()).roundToInt()
    val top = maxOf(stats.thisWeekTotal, typical, 1)
    SubHeader("this week vs a typical week")
    Comparison("${stats.thisWeekTotal}", "this week", stats.thisWeekTotal.toFloat() / top, MetroTheme.accent.fill)
    Comparison("$typical", "a typical week (12-week average)", typical.toFloat() / top, shadeAccent(-2).fill)
}

@Composable
private fun Comparison(value: String, label: String, fraction: Float, color: Color) {
    Column(
        Modifier.padding(bottom = 8.dp).semantics(mergeDescendants = true) {
            contentDescription = "$value $label"
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            MetroText(value, MetroTheme.typography.subheader)
            MetroText(
                " $label",
                MetroTheme.typography.caption,
                Modifier.alpha(0.8f),
                color = MetroTheme.colors.secondary
            )
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(14.dp).background(color))
    }
}

private fun weekRange(start: LocalDate, format: DateTimeFormatter): String {
    val end = start.plusDays(6)
    return if (start.month ==
        end.month
    ) {
        "${start.format(format)} to ${end.dayOfMonth}"
    } else {
        "${start.format(format)} to ${end.format(format)}"
    }
}

private val BAR_HEIGHT = 84.dp
private const val MIN_BAR_SCALE = 6
private val GRID_CELL = 18.dp
private val GRID_GAP = 3.dp
private val LIST_NAME_WIDTH = 96.dp
private val LIST_BAR_WIDTH = 110.dp
