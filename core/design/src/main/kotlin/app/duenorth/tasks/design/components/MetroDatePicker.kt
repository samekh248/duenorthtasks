package app.duenorth.tasks.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroTheme
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.filter

private val CellHeight = 80.dp
private val CellGap = 6.dp

/** Large enough to scroll for a long time, a multiple of 12 and 7 so loops line up. */
private const val LOOP_ITEMS = 8400

/**
 * WP8.1 date picker: three looping columns (month, day, year) of flat squares. The middle row is
 * the chosen date and is filled with the accent; the rows above and below hint at the neighbours.
 * Dates only, no times (spec: due dates have no time in v1).
 */
@Composable
fun MetroDatePicker(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    years: IntRange = (date.year - 1)..(date.year + 10),
    locale: Locale = Locale.getDefault()
) {
    val current by rememberUpdatedState(date)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        LoopingColumn(
            count = 12,
            selected = date.monthValue - 1,
            label = { i -> number(i + 1) to Month.of(i + 1).getDisplayName(TextStyle.SHORT, locale).lowercase(locale) },
            onSelect = { i -> onDateChange(current.moveTo(month = i + 1)) },
            modifier = Modifier.weight(1f)
        )
        val days = date.lengthOfMonth()
        LoopingColumn(
            count = days,
            selected = date.dayOfMonth - 1,
            label = { i ->
                val weekday = date.withDayOfMonth(i + 1).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                number(i + 1) to weekday.lowercase(locale)
            },
            onSelect = { i -> onDateChange(current.withDayOfMonth(i + 1)) },
            modifier = Modifier.weight(1f),
            key = days
        )
        val yearList = years.toList()
        LoopingColumn(
            count = yearList.size,
            selected = yearList.indexOf(date.year).coerceAtLeast(0),
            label = { i -> yearList[i].toString() to null },
            onSelect = { i -> onDateChange(current.moveTo(year = yearList[i])) },
            modifier = Modifier.weight(1f)
        )
    }
}

/** Changes month or year, keeping the day where it exists (31 Jan to February gives 28 or 29 Feb). */
internal fun LocalDate.moveTo(year: Int = this.year, month: Int = monthValue): LocalDate {
    val first = LocalDate.of(year, month, 1)
    return first.withDayOfMonth(dayOfMonth.coerceAtMost(first.lengthOfMonth()))
}

private fun number(n: Int): String = n.toString().padStart(2, '0')

@Composable
private fun LoopingColumn(
    count: Int,
    selected: Int,
    label: (Int) -> Pair<String, String?>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: Any = Unit
) {
    key(key) {
        val start = remember { LOOP_ITEMS / 2 - (LOOP_ITEMS / 2) % count + selected }
        val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
        val onSelectNow by rememberUpdatedState(onSelect)
        val centered by remember { derivedStateOf { state.firstVisibleItemIndex } }

        // Report the row that settled in the middle once a fling ends.
        LaunchedEffect(state) {
            snapshotFlow { state.isScrollInProgress }
                .filter { !it }
                .collect { onSelectNow(state.firstVisibleItemIndex % count) }
        }
        // Follow outside changes, such as the day clamping when the month gets shorter.
        LaunchedEffect(selected) {
            val now = state.firstVisibleItemIndex
            if (now % count != selected) state.scrollToItem(now - now % count + selected)
        }

        LazyColumn(
            modifier = modifier.height(CellHeight * 3 + CellGap * 2),
            state = state,
            contentPadding = PaddingValues(vertical = CellHeight + CellGap),
            verticalArrangement = Arrangement.spacedBy(CellGap),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start)
        ) {
            items(LOOP_ITEMS) { index ->
                val (top, bottom) = label(index % count)
                DateCell(top, bottom, chosen = index == centered)
            }
        }
    }
}

@Composable
private fun DateCell(top: String, bottom: String?, chosen: Boolean) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val type = MetroTheme.typography
    val text = if (chosen) accent.onFill else colors.secondary
    Box(
        Modifier
            .fillMaxWidth()
            .height(CellHeight)
            .then(if (chosen) Modifier.background(accent.fill) else Modifier.border(2.dp, colors.outline))
            .padding(8.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            MetroText(top, type.listName, color = text, maxLines = 1)
            if (bottom != null) MetroText(bottom, type.caption, color = text, maxLines = 1)
        }
    }
}
