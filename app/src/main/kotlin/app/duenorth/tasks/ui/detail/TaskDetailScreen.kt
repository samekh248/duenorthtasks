package app.duenorth.tasks.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroLinkifiedText
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.motion.continuumTarget
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.DueText

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
                EditForm(
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
                if (!state.details.isNullOrBlank()) {
                    Label("details")
                    MetroLinkifiedText(state.details, type.body)
                }
            }

            Label("steps")
            state.steps.forEach { step ->
                StepRow(step, onDone = {
                    viewModel.setStepDone(step.id, it)
                }, onRemove = { viewModel.removeStep(step.id) })
            }
            AddStepField(onAdd = viewModel::addStep)

            Label("due date")
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

            Label("list")
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
            menuItems = listOf(AppBarMenuItem("move to list") { moving = true })
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

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(12.dp))
    MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
}

@Composable
private fun EditForm(title: String, details: String, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var newTitle by rememberSaveable { mutableStateOf(title) }
    var newDetails by rememberSaveable { mutableStateOf(details) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            placeholder = "title",
            textStyle = MetroTheme.typography.subheader,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        MetroTextField(
            value = newDetails,
            onValueChange = { newDetails = it },
            placeholder = "details, links",
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetroLink("cancel", onCancel)
            Spacer(Modifier.weight(1f))
            MetroButton("save", { onSave(newTitle, newDetails) }, enabled = newTitle.isNotBlank())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepRow(step: StepUi, onDone: (Boolean) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.TouchTarget)
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onLongClick = { menu = true },
                    onClick = { onDone(!step.done) }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroCheckBox(checked = step.done, onCheckedChange = onDone, label = step.title)
            MetroText(
                step.title,
                MetroTheme.typography.body.copy(
                    textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (step.done) MetroTheme.colors.secondary else MetroTheme.colors.foreground
            )
        }
        MetroContextMenu(expanded = menu, onDismiss = {
            menu = false
        }, items = listOf(ContextMenuItem("remove", onRemove)))
    }
}

@Composable
private fun AddStepField(onAdd: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    MetroTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = "add a step",
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            onAdd(draft.trim())
            draft = ""
        })
    )
}
