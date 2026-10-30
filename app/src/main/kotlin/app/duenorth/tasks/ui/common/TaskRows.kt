package app.duenorth.tasks.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.data.db.TaskWithList
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.GroupPosition
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.groupSurface
import app.duenorth.tasks.design.components.relief
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.motion.continuumSource
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroStyle
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.MetroWeights
import app.duenorth.tasks.design.theme.Relief
import app.duenorth.tasks.design.theme.listAccent
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
    val completed: Boolean,
    /** Starred, and the connected service has a star (FR-014); always false for Google Tasks. */
    val important: Boolean = false,
    /** Not shown as such (the caption words it); lets the row notice a synced date change. */
    val due: LocalDate? = null,
    /**
     * The caption's parts, so a style can set the due date apart ("tomorrow", "done") or a list page can leave its
     * own name out. Null [listTitle] means the parts are unknown and the row shows [caption] as it is.
     */
    val listTitle: String? = null,
    val whenText: String? = null,
    val assigned: String? = null
) {
    /** The caption without the list name, for a row on its own list's page. */
    val captionWithoutList: String
        get() = if (listTitle == null) caption else listOfNotNull(whenText, assigned).joinToString(" · ")
}

fun TaskWithList.toRow(
    today: LocalDate,
    completedOverride: Boolean? = null,
    showImportance: Boolean = false
): TaskRowUi {
    val done = completedOverride ?: task.completed
    val due = task.dueDate
    return TaskRowUi(
        id = task.localId,
        listId = task.listId,
        title = task.title,
        details = task.notes,
        caption = listOfNotNull(DueText.caption(listTitle, due, today, done), assignedFrom(task.assignmentSource))
            .joinToString(" · "),
        overdue = !done && due != null && due.isBefore(today),
        completed = done,
        important = showImportance && task.important,
        due = due,
        listTitle = listTitle,
        whenText = DueText.whenText(due, today, done),
        assigned = assignedFrom(task.assignmentSource)
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

    /** The caption's "when": "done", the relative due date, or null when there is none. */
    fun whenText(due: LocalDate?, today: LocalDate, completed: Boolean): String? = when {
        completed -> "done"
        due == null -> null
        else -> relative(due, today)
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
 * red when overdue, and an accent star when important. Long-press opens the context menu. When a
 * sync changes what the row says, it fades the new text in rather than swapping it (FR-008).
 *
 * [position] places the row in its group (a date, a list's open or completed tasks), which tile relief draws as
 * one panel (pressed in when [putAway], as completed tasks are) and Windows 10 Mobile rules off row by row. Under
 * mica each open task is its own card with its due date on a pill, and a put-away group shares one card under
 * its header. [showList] false leaves the list's name out of the caption on that list's own page, under every
 * style but Metro.
 */
@Composable
fun TaskRow(
    row: TaskRowUi,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    menuItems: List<ContextMenuItem> = emptyList(),
    continuum: ContinuumState? = null,
    position: GroupPosition = GroupPosition.Only,
    putAway: Boolean = false,
    showList: Boolean = true
) {
    var menu by remember { mutableStateOf(false) }
    val style = MetroTheme.style
    val caption = if (showList || style == MetroStyle.Metro) row.caption else row.captionWithoutList
    // Mica sets a due date apart on a pill ("done" stays in the caption); Windows 10 Mobile greys the list's name.
    val badge = row.whenText.takeIf {
        row.listTitle != null && (style.layers && !row.completed || style.mobile10)
    }
    val surface = when {
        style == MetroStyle.Metro -> Modifier
        style.layers && !putAway ->
            Modifier.padding(end = MetroDimens.Gutter, bottom = 4.dp).groupSurface(GroupPosition.Only)
        else -> Modifier.padding(
            end = MetroDimens.Gutter
        ).groupSurface(position, if (putAway) Relief.Inset else Relief.Raised)
    }
    Box(modifier.then(surface).fadeInOnChange(row.title, row.details, row.due, row.important)) {
        MetroListItem(
            title = row.title,
            details = row.details,
            caption = when {
                badge == null -> caption
                showList -> listOfNotNull(row.listTitle, row.assigned).joinToString(" · ")
                else -> row.assigned.orEmpty()
            },
            badge = badge,
            badgePill = style.layers,
            captionColor = if (row.overdue) MetroTheme.colors.overdue else listAccent(row.listId).text,
            strikethrough = row.completed,
            leading = { MetroCheckBox(checked = row.completed, onCheckedChange = onToggle, label = row.title) },
            trailing = if (row.important) ({ ImportantStar() }) else null,
            onClick = onOpen,
            onLongClick = if (menuItems.isEmpty()) null else ({ menu = true }),
            titleModifier = if (continuum != null) Modifier.continuumSource(continuum, row.id) else Modifier
        )
        MetroContextMenu(expanded = menu, onDismiss = { menu = false }, items = menuItems)
    }
}

/**
 * The header over a group of task rows ("tomorrow", "completed"), with a [count] and, when [expanded] is given,
 * a chevron that opens and closes the group. Metro writes it in light grey type; tile relief adds a raised count
 * badge (in the accent for a put-away group); mica uses a small semibold header, or for a put-away group the top
 * row of the card its rows share; Windows 10 Mobile writes it in the accent.
 */
@Composable
fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    expanded: Boolean? = null,
    putAway: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val style = MetroTheme.style
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val click = if (onClick != null) {
        Modifier
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget)
    } else {
        Modifier
    }
    val chevron: @Composable (Color) -> Unit = { color ->
        if (expanded != null) {
            MetroIconGlyph(
                MetroIcon.ChevronDown,
                Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                color = color,
                size = 16.dp
            )
        }
    }
    when {
        style.layers && putAway -> Row(
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = MetroDimens.Gutter)
                .groupSurface(if (expanded == true) GroupPosition.First else GroupPosition.Only)
                .then(click)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroText(text, type.body, Modifier.weight(1f))
            if (count != null) MetroText("$count", type.body, color = colors.secondary)
            chevron(colors.secondary)
        }
        style.layers -> MetroText(
            text,
            type.body.copy(fontWeight = MetroWeights.Semibold),
            modifier.then(click).padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
        )
        style.mobile10 -> Row(
            modifier.then(click).padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val label = type.body.copy(fontWeight = MetroWeights.Semibold)
            val counted = if (count != null && expanded != null) "$text ($count)" else text
            MetroText(counted, label, color = MetroTheme.accent.text)
            chevron(MetroTheme.accent.text)
        }
        style.relief -> Row(
            modifier.then(click).padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetroText(text, type.subheader, color = colors.secondary)
            if (count != null) CountBadge(count, accent = putAway)
            chevron(colors.secondary)
        }
        else -> MetroText(
            if (count == null || expanded == null) text else "$text ($count) ${if (expanded) "⌃" else "⌄"}",
            type.subheader,
            modifier.fillMaxWidth().then(click).padding(top = 16.dp, bottom = 4.dp),
            color = colors.secondary
        )
    }
}

/** Tile relief's count: a small raised square with the number, filled with the accent when [accent]. */
@Composable
private fun CountBadge(count: Int, accent: Boolean) {
    val fill = if (accent) MetroTheme.accent.fill else MetroTheme.colors.background
    Box(
        Modifier
            .heightIn(min = 22.dp)
            .relief(Relief.Raised, small = true, fill = fill)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        MetroText(
            "$count",
            MetroTheme.typography.caption.copy(fontWeight = MetroWeights.Semibold),
            color = if (accent) MetroTheme.accent.onFill else MetroTheme.colors.foreground
        )
    }
}

/** The row menu's star item, or null when the service has no star (Google Tasks). */
fun importanceItem(supported: Boolean, row: TaskRowUi, onChange: (Boolean) -> Unit): ContextMenuItem? =
    if (!supported) {
        null
    } else if (row.important) {
        ContextMenuItem("not important", MetroIcon.Star) { onChange(false) }
    } else {
        ContextMenuItem("mark important", MetroIcon.Star) { onChange(true) }
    }

@Composable
private fun ImportantStar() {
    MetroIconGlyph(
        MetroIcon.Star,
        Modifier.semantics { contentDescription = "important" },
        color = MetroTheme.accent.text,
        size = 18.dp,
        filled = true
    )
}

/** How long changed rows take to fade back in; short enough to read as "updated", not as motion. */
private const val CHANGE_FADE_MS = 280

/**
 * Fades the content back in when any of [keys] change after the first composition. Draw-only
 * (graphicsLayer alpha), so nothing is re-measured and nothing moves. Skipped when the user has
 * turned animations off.
 */
@Composable
private fun Modifier.fadeInOnChange(vararg keys: Any?): Modifier {
    val alpha = remember { Animatable(1f) }
    val animate = LocalAnimationsEnabled.current
    val current = keys.toList()
    val shown = remember { arrayOf(current) }
    LaunchedEffect(current) {
        if (current == shown[0]) return@LaunchedEffect
        shown[0] = current
        if (!animate) return@LaunchedEffect
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(CHANGE_FADE_MS, easing = LinearOutSlowInEasing))
    }
    return graphicsLayer { this.alpha = alpha.value }
}
