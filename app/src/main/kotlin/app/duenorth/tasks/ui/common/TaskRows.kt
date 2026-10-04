package app.duenorth.tasks.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.motion.continuumSource
import app.duenorth.tasks.design.theme.MetroTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a task row shows; built off the main thread by the ViewModels. */
@Immutable
data class TaskRowUi(
    val id: String,
    val listId: String,
    val title: String,
    /** Full details; the row clamps them to two lines (FR-011b). */
    val details: String?,
    val caption: String,
    val overdue: Boolean,
    val completed: Boolean
)

fun TaskWithList.toRow(today: LocalDate, completedOverride: Boolean? = null): TaskRowUi {
    val done = completedOverride ?: task.completed
    val due = task.dueDate
    return TaskRowUi(
        id = task.localId,
        listId = task.listId,
        title = task.title,
        details = task.notes,
        caption = DueText.caption(listTitle, due, today, done),
        overdue = !done && due != null && due.isBefore(today),
        completed = done
    )
}

/** Due-date wording used across the app: lowercase, relative when close, like WP8.1. */
object DueText {
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    fun caption(listTitle: String, due: LocalDate?, today: LocalDate, completed: Boolean): String {
        if (completed) return "$listTitle · done"
        if (due == null) return listTitle
        return "$listTitle · ${relative(due, today)}"
    }

    fun relative(due: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days == -1L -> "yesterday"
            days < -1L -> "${-days} days overdue"
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days in 2..6 -> due.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            due.year == today.year -> monthDay.format(due)
            else -> monthDayYear.format(due)
        }.lowercase(Locale.getDefault())
    }

    /** "due tuesday", "due oct 12", for the task page. */
    fun dueLine(due: LocalDate, today: LocalDate): String = "due ${relative(due, today)}"
}

/**
 * A task row: square check box, title, two grey lines of details and the "List · when" caption,
 * red when overdue. Long-press opens the context menu.
 */
@Composable
fun TaskRow(
    row: TaskRowUi,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    menuItems: List<ContextMenuItem> = emptyList(),
    continuum: ContinuumState? = null
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        MetroListItem(
            title = row.title,
            details = row.details,
            caption = row.caption,
            captionColor = if (row.overdue) MetroTheme.colors.overdue else MetroTheme.accent.text,
            strikethrough = row.completed,
            leading = { MetroCheckBox(checked = row.completed, onCheckedChange = onToggle) },
            onClick = onOpen,
            onLongClick = if (menuItems.isEmpty()) null else ({ menu = true }),
            titleModifier = if (continuum != null) Modifier.continuumSource(continuum, row.id) else Modifier
        )
        MetroContextMenu(expanded = menu, onDismiss = { menu = false }, items = menuItems)
    }
}
