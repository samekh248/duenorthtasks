package app.duenorth.tasks.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.data.db.AssignmentSource
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.GroupPosition
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroLinkifiedText
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroPill
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.components.groupSurface
import app.duenorth.tasks.design.components.relief
import app.duenorth.tasks.design.motion.continuumTarget
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.AccentColors
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.MetroWeights
import app.duenorth.tasks.design.theme.Relief
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.ui.common.AddStepField
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.OrderNoteDialog
import app.duenorth.tasks.ui.common.ReorderRow
import app.duenorth.tasks.ui.common.ReorderStepsPage
import app.duenorth.tasks.ui.common.StepRow
import app.duenorth.tasks.ui.common.TitleDetailsForm
import java.time.LocalDate
import java.util.Locale

/** A task's page (contracts/ui-screens.md "Task detail"); the title lands with the continuum. */
@Composable
fun TaskDetailScreen(
    onClosed: () -> Unit,
    animatedScope: AnimatedVisibilityScope?,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exists) { if (!state.exists) onClosed() }
    TaskDetailContent(state, viewModel, animatedScope)
}

@Composable
fun TaskDetailContent(
    state: TaskDetailUiState,
    viewModel: TaskDetailViewModel,
    animatedScope: AnimatedVisibilityScope?
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val type = MetroTheme.typography
    val colors = MetroTheme.colors
    val style = MetroTheme.style

    if (state.orderNote) OrderNoteDialog(state.serviceName, viewModel::dismissOrderNote)
    if (state.reordering) {
        LifecycleStartEffect(viewModel) {
            viewModel.holdForReorder(true)
            onStopOrDispose { viewModel.holdForReorder(false) }
        }
        ReorderStepsPage(
            overline = "DUE NORTH · ${state.listTitle.uppercase()}",
            title = state.title,
            steps = state.steps,
            key = { it.id },
            onMove = viewModel::reorderSteps,
            initialKey = state.reorderFrom,
            onDone = viewModel::endReorder,
            testTag = "reorder-steps"
        ) { step -> ReorderRow(title = step.title, caption = null, checked = step.done) }
        return
    }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MetroDimens.Gutter)
                .padding(top = 16.dp, bottom = 24.dp)
                .testTag("detail"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MetroText("DUE NORTH · ${state.listTitle.uppercase()}", type.pageTitle, maxLines = 1)
            if (state.loading) {
                // Task-shaped placeholders until Room answers, never empty fields (FR-009).
                MetroTaskPlaceholders(count = 2)
                return@Column
            }
            if (editing) {
                TitleDetailsForm(
                    title = state.title,
                    details = state.details.orEmpty(),
                    onSave = { title, details ->
                        viewModel.save(title, details)
                        editing = false
                    },
                    onCancel = { editing = false }
                )
            } else {
                MetroText(
                    state.title,
                    detailTitle().copy(
                        textDecoration = if (state.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    Modifier
                        .then(if (animatedScope != null) Modifier.continuumTarget(animatedScope) else Modifier)
                        .semantics { heading() }
                )
                if (state.due != null) DueLine(DueText.dueLine(state.due, state.today), listAccent(state.listId))
                if (state.assignedFrom != AssignmentSource.NONE) AssignedBox(state)
                if (!state.details.isNullOrBlank()) {
                    Label("details")
                    DetailsBox(state.details)
                }
            }

            val done = state.steps.count { it.done }
            Label(
                if (style.layers && state.steps.isNotEmpty()) "steps · $done of ${state.steps.size}" else "steps",
                count = if (style.relief && !style.layers &&
                    state.steps.isNotEmpty()
                ) {
                    "$done/${state.steps.size}"
                } else {
                    null
                }
            )
            // Tile relief puts the steps on one panel; mica on one card that ends in the add-a-step box.
            val grouped = style.relief
            val rows = state.steps.size + if (style.layers) 1 else 0
            state.steps.forEachIndexed { index, step ->
                StepRow(
                    title = step.title,
                    done = step.done,
                    onDone = { viewModel.setStepDone(step.id, it) },
                    onRemove = { viewModel.removeStep(step.id) },
                    onReorder = { viewModel.startReorder(step.id) }.takeIf { state.steps.size > 1 },
                    modifier = if (grouped) Modifier.groupSurface(GroupPosition.of(index, rows)) else Modifier
                )
            }
            if (style.layers) {
                AddStepField(
                    onAdd = viewModel::addStep,
                    modifier = Modifier
                        .groupSurface(GroupPosition.of(rows - 1, rows))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            } else {
                if (grouped && state.steps.isNotEmpty()) Spacer(Modifier.height(8.dp))
                AddStepField(onAdd = viewModel::addStep)
            }

            val dueLabel = state.due?.let { DueText.relative(it, state.today) } ?: "no date"
            val clearDue = {
                viewModel.setDue(null)
                pickingDate = false
            }
            if (style.layers) {
                // Mica: one "schedule" card, a row each for the due date and the list.
                Label("schedule")
                CardRow(MetroIcon.Calendar, "due date", dueLabel, GroupPosition.First) { pickingDate = !pickingDate }
                CardRow(MetroIcon.ListLines, "list", state.listTitle, GroupPosition.Last) { moving = true }
                if (state.due != null) MetroLink("clear due date", clearDue)
                if (pickingDate) {
                    MetroDatePicker(date = state.due ?: state.today, onDateChange = viewModel::setDue)
                }
            } else {
                Label("due date")
                if (style.mobile10) {
                    DateBox(state.due) { pickingDate = !pickingDate }
                    if (state.due != null) MetroLink("clear due date", clearDue)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Chip(dueLabel, icon = MetroIcon.Calendar) { pickingDate = !pickingDate }
                        if (state.due != null) MetroLink("clear", clearDue)
                    }
                }
                if (pickingDate) {
                    MetroDatePicker(date = state.due ?: state.today, onDateChange = viewModel::setDue)
                }

                Label("list")
                if (style.mobile10) {
                    ComboBox(state.listTitle) { moving = true }
                } else {
                    Chip(state.listTitle, icon = MetroIcon.ListLines) { moving = true }
                }
            }

            if (state.importance) {
                Spacer(Modifier.height(12.dp))
                MetroToggle(
                    checked = state.important,
                    onCheckedChange = viewModel::setImportant,
                    label = "important",
                    modifier = Modifier.fillMaxWidth().testTag("important")
                )
            }
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Check, if (state.completed) "mark not done" else "mark done") {
                    viewModel.setCompleted(!state.completed)
                },
                AppBarButton(MetroIcon.Edit, "edit") { editing = true },
                AppBarButton(MetroIcon.Delete, "delete") { deleting = true }
            ),
            menuItems = listOfNotNull(
                AppBarMenuItem("reorder steps") { viewModel.startReorder() }.takeIf { state.steps.size > 1 },
                AppBarMenuItem("move to list") { moving = true }
            )
        )
    }

    if (deleting) {
        MetroDialog(
            title = "delete task?",
            message = "\"${state.title}\" will be deleted here and in the connected account.",
            confirmLabel = "delete",
            onConfirm = {
                deleting = false
                viewModel.delete()
            },
            onDismiss = { deleting = false }
        )
    }
    if (moving) {
        MetroPickerDialog(
            title = "move to",
            options = state.lists,
            selected = state.lists.firstOrNull { it.id == state.listId },
            label = { it.title },
            onPick = {
                viewModel.moveTo(it.id)
                moving = false
            },
            onDismiss = { moving = false }
        )
    }
}

/**
 * A field's label: grey caption type in Metro and tile relief (with a raised [count] badge there), a small
 * semibold header under mica, and the ink color under Windows 10 Mobile, whose labels sit right over their fields.
 */
@Composable
private fun Label(text: String, count: String? = null) {
    val style = MetroTheme.style
    val type = MetroTheme.typography
    Spacer(Modifier.height(if (style.layers) 16.dp else 12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            style.layers -> MetroText(text, type.body.copy(fontWeight = MetroWeights.Semibold))
            style.mobile10 -> MetroText(text, type.body)
            else -> MetroText(text, type.caption, color = MetroTheme.colors.secondary)
        }
        if (count != null) {
            MetroText(
                count,
                type.caption.copy(fontWeight = MetroWeights.Semibold),
                Modifier.relief(Relief.Raised, small = true).padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
}

/** The task's title: Metro's light 38sp, mica's semibold, Windows 10 Mobile's regular. */
@Composable
private fun detailTitle(): TextStyle {
    val title = MetroTheme.typography.detailTitle
    val style = MetroTheme.style
    return when {
        style.layers -> title.copy(fontWeight = MetroWeights.Semibold, fontSize = 30.sp)
        style.mobile10 -> title.copy(fontWeight = MetroWeights.Regular, fontSize = 30.sp)
        else -> title
    }
}

/**
 * "due tuesday" under the title: in the list's accent in Metro, a raised accent chip under tile relief, a pill
 * under mica, and body type under Windows 10 Mobile.
 */
@Composable
private fun DueLine(text: String, accent: AccentColors) {
    val style = MetroTheme.style
    val type = MetroTheme.typography
    when {
        style.layers -> MetroPill(text, accent.text, Modifier.padding(top = 4.dp))
        style.relief -> MetroText(
            text,
            type.body.copy(fontWeight = MetroWeights.Semibold),
            Modifier
                .padding(top = 6.dp)
                .relief(Relief.Raised, small = true, fill = accent.fill)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            color = accent.onFill
        )
        style.mobile10 -> MetroText(text, type.body, color = accent.text)
        else -> MetroText(text, type.subheader, color = accent.text)
    }
}

/** The details: plain text in Metro, a pressed-in well under tile relief, a card under mica, a box under 10 Mobile. */
@Composable
private fun DetailsBox(details: String) {
    val style = MetroTheme.style
    val body = MetroTheme.typography.body
    when {
        style.layers -> Row(
            Modifier.fillMaxWidth().groupSurface(GroupPosition.Only).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroIconGlyph(MetroIcon.Notes, color = MetroTheme.colors.secondary, size = 20.dp)
            MetroLinkifiedText(details, body)
        }
        style.relief -> Box(Modifier.fillMaxWidth().relief(Relief.Inset).padding(12.dp)) {
            MetroLinkifiedText(details, body)
        }
        style.mobile10 -> Box(
            Modifier.fillMaxWidth().border(2.dp, MetroTheme.colors.outline).padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            MetroLinkifiedText(details, body)
        }
        else -> MetroLinkifiedText(details, body)
    }
}

/** A row of mica's schedule card: a glyph, what it sets, its value and a chevron, opening a picker. */
@Composable
private fun CardRow(icon: MetroIcon, label: String, value: String, position: GroupPosition, onClick: () -> Unit) {
    val colors = MetroTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .groupSurface(position)
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetroIconGlyph(icon, color = colors.secondary, size = 20.dp)
        MetroText(label, MetroTheme.typography.body, Modifier.weight(1f))
        MetroText(value, MetroTheme.typography.caption, color = colors.secondary, maxLines = 1)
        MetroIconGlyph(MetroIcon.ChevronRight, color = colors.secondary, size = 16.dp)
    }
}

/** Windows 10 Mobile's date picker at rest: day, date, month and year in an outlined box, split by lines. */
@Composable
private fun DateBox(due: LocalDate?, onClick: () -> Unit) {
    val colors = MetroTheme.colors
    val parts = due?.let {
        listOf(
            it.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            "${it.dayOfMonth}",
            it.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            "${it.year}"
        )
    } ?: listOf("no date")
    Row(
        Modifier
            .fillMaxWidth()
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = 4.dp)
            .border(2.dp, colors.outline)
            .height(IntrinsicSize.Min)
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(colors.outline))
            MetroText(
                part,
                MetroTheme.typography.body,
                Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                maxLines = 1
            )
        }
    }
}

/** Windows 10 Mobile's combo box at rest: the value in an outlined box with a chevron, opening the list picker. */
@Composable
private fun ComboBox(value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = 4.dp)
            .border(2.dp, MetroTheme.colors.outline)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetroText(value, MetroTheme.typography.body, Modifier.weight(1f), maxLines = 1)
        MetroIconGlyph(MetroIcon.ChevronDown, size = 18.dp)
    }
}

/**
 * spec 002 US4: where a task assigned to the user came from, with a way back there. Google says
 * the doc or space, not the person, so the note says that plainly (FR-132).
 */
@Composable
private fun AssignedBox(state: TaskDetailUiState) {
    val uriHandler = LocalUriHandler.current
    val document = state.assignedFrom == AssignmentSource.DOCUMENT
    Label("assigned to you")
    Column(
        Modifier.fillMaxWidth().border(2.dp, MetroTheme.colors.secondary).padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroIconGlyph(MetroIcon.People, size = 24.dp)
            MetroText(
                when (state.assignedFrom) {
                    AssignmentSource.DOCUMENT -> "from a google doc"
                    AssignmentSource.SPACE -> "from a chat space"
                    else -> "by someone else"
                },
                MetroTheme.typography.subheader
            )
        }
        state.assignmentLink?.let { link ->
            Row(
                Modifier
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroTilt()
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        runCatching { uriHandler.openUri(link) }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetroIconGlyph(MetroIcon.OpenOutside, color = MetroTheme.accent.text, size = 20.dp)
                MetroText(
                    if (document) "open in google docs" else "open in google chat",
                    MetroTheme.typography.body,
                    color = MetroTheme.accent.text
                )
            }
        }
    }
    MetroText(
        "Google doesn't say who assigned it. Edits here sync back to the task.",
        MetroTheme.typography.caption,
        color = MetroTheme.colors.secondary
    )
}
