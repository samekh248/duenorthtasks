package app.duenorth.tasks.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.data.db.AssignmentSource
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroLinkifiedText
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.motion.continuumTarget
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.ui.common.AddStepField
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.OrderNoteDialog
import app.duenorth.tasks.ui.common.ReorderRow
import app.duenorth.tasks.ui.common.ReorderStepsPage
import app.duenorth.tasks.ui.common.SectionLabel
import app.duenorth.tasks.ui.common.StepRow
import app.duenorth.tasks.ui.common.TitleDetailsForm

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

    if (state.orderNote) OrderNoteDialog(state.serviceName, viewModel::dismissOrderNote)
    if (state.reordering) {
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
                    type.detailTitle.copy(
                        textDecoration = if (state.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    Modifier
                        .then(if (animatedScope != null) Modifier.continuumTarget(animatedScope) else Modifier)
                        .semantics { heading() }
                )
                if (state.due != null) {
                    MetroText(
                        DueText.dueLine(state.due, state.today),
                        type.subheader,
                        color = listAccent(state.listId).text
                    )
                }
                if (state.assignedFrom != AssignmentSource.NONE) AssignedBox(state)
                if (!state.details.isNullOrBlank()) {
                    SectionLabel("details")
                    MetroLinkifiedText(state.details, type.body)
                }
            }

            SectionLabel("steps")
            state.steps.forEach { step ->
                StepRow(
                    title = step.title,
                    done = step.done,
                    onDone = { viewModel.setStepDone(step.id, it) },
                    onRemove = { viewModel.removeStep(step.id) },
                    onReorder = { viewModel.startReorder(step.id) }.takeIf { state.steps.size > 1 }
                )
            }
            AddStepField(onAdd = viewModel::addStep)

            SectionLabel("due date")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(state.due?.let { DueText.relative(it, state.today) } ?: "no date") { pickingDate = !pickingDate }
                if (state.due != null) {
                    MetroLink("clear", {
                        viewModel.setDue(null)
                        pickingDate = false
                    })
                }
            }
            if (pickingDate) {
                MetroDatePicker(date = state.due ?: state.today, onDateChange = viewModel::setDue)
            }

            SectionLabel("list")
            Chip(state.listTitle) { moving = true }

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
 * spec 002 US4: where a task assigned to the user came from, with a way back there. Google says
 * the doc or space, not the person, so the note says that plainly (FR-132).
 */
@Composable
private fun AssignedBox(state: TaskDetailUiState) {
    val uriHandler = LocalUriHandler.current
    val document = state.assignedFrom == AssignmentSource.DOCUMENT
    SectionLabel("assigned to you")
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
