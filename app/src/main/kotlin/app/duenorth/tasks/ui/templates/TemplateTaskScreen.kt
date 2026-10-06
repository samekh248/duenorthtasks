package app.duenorth.tasks.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroReorderList
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.theme.LocalAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.ReorderHint
import app.duenorth.tasks.ui.common.ReorderRow

@Composable
fun TemplateTaskScreen(onClosed: () -> Unit, viewModel: TemplateTaskViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exists) { if (!state.exists) onClosed() }
    CompositionLocalProvider(LocalAccent provides shadeAccent(state.shadeStep)) {
        TemplateTaskContent(state, viewModel)
    }
}

private fun overline(state: TemplateTaskUiState) =
    state.listName?.let { "LIST TEMPLATE · ${it.uppercase()}" } ?: "TASK TEMPLATE"

/** spec 004 FR-331: a task page with a "TASK TEMPLATE" overline and a due offset instead of a date. */
@Composable
fun TemplateTaskContent(state: TemplateTaskUiState, viewModel: TemplateTaskViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var pickingDue by rememberSaveable { mutableStateOf(false) }
    val type = MetroTheme.typography

    if (state.reordering) {
        ReorderTemplateSteps(state, viewModel)
        return
    }

    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background).imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MetroDimens.Gutter)
                .padding(top = 16.dp, bottom = 24.dp)
                .testTag("template task"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MetroText(overline(state), type.pageTitle, maxLines = 1)
            if (state.loading) {
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
                MetroText(state.title, type.detailTitle, Modifier.semantics { heading() })
                if (!state.details.isNullOrBlank()) {
                    Label("details")
                    MetroText(state.details, type.body)
                }
            }

            Label("steps")
            state.steps.forEach { step ->
                StepRow(
                    step,
                    onRemove = { viewModel.removeStep(step.id) },
                    onReorder = { viewModel.startReorder(step.id) }.takeIf { state.steps.size > 1 }
                )
            }
            AddStepField(onAdd = viewModel::addStep)

            Label("due")
            Chip(TemplateText.choiceLabel(state.dueOffset, state.inList)) { pickingDue = true }
            MetroText(
                if (state.inList) {
                    "Counted from the start date you pick when you use the template."
                } else {
                    "Counted from the day you add the task."
                },
                type.caption,
                color = MetroTheme.colors.secondary
            )

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
                AppBarButton(MetroIcon.Edit, "edit") { editing = true },
                AppBarButton(MetroIcon.Delete, "delete") { deleting = true }
            ),
            menuItems = listOfNotNull(
                AppBarMenuItem("reorder steps") { viewModel.startReorder() }.takeIf { state.steps.size > 1 }
            )
        )
    }

    if (pickingDue) {
        MetroPickerDialog(
            title = "due",
            options = TemplateText.choices(state.inList),
            selected = state.dueOffset,
            label = { TemplateText.choiceLabel(it, state.inList) },
            onPick = {
                pickingDue = false
                viewModel.setDueOffset(it)
            },
            onDismiss = { pickingDue = false }
        )
    }
    if (deleting) {
        MetroDialog(
            title = if (state.inList) "delete task?" else "delete template?",
            message = if (state.inList) {
                "\"${state.title}\" will be removed from the template."
            } else {
                "\"${state.title}\" will be deleted from this phone. Tasks made from it stay as they are."
            },
            confirmLabel = "delete",
            onConfirm = {
                deleting = false
                viewModel.delete()
            },
            onDismiss = { deleting = false }
        )
    }
}

@Composable
private fun ReorderTemplateSteps(state: TemplateTaskUiState, viewModel: TemplateTaskViewModel) {
    BackHandler(onBack = viewModel::endReorder)
    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            MetroText(
                overline(state),
                MetroTheme.typography.pageTitle,
                Modifier.padding(start = MetroDimens.Gutter, top = 16.dp, end = MetroDimens.Gutter),
                maxLines = 1
            )
            MetroText(
                state.title,
                MetroTheme.typography.detailTitle,
                Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 4.dp).semantics { heading() }
            )
            MetroReorderList(
                items = state.steps,
                key = { it.id },
                onMove = viewModel::reorderSteps,
                modifier = Modifier.fillMaxSize().testTag("reorder-template-steps"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                initialKey = state.reorderFrom,
                headerItems = 1,
                header = { item(key = "hint", contentType = "hint") { ReorderHint("drag a step to move it") } }
            ) { step ->
                ReorderRow(title = step.title, caption = null)
            }
        }
        MetroAppBar(buttons = listOf(AppBarButton(MetroIcon.Check, "done", onClick = viewModel::endReorder)))
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

/** A template step: a dimmed check box (steps start unticked) and its title; long-press for more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepRow(step: TemplateStepUi, onRemove: () -> Unit, onReorder: (() -> Unit)?) {
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
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            MetroCheckBox(checked = false, onCheckedChange = null, modifier = Modifier.alpha(DIMMED))
            MetroText(step.title, MetroTheme.typography.body)
        }
        MetroContextMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOfNotNull(
                onReorder?.let {
                    ContextMenuItem("reorder", it)
                },
                ContextMenuItem("remove", onRemove)
            )
        )
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

private const val DIMMED = 0.35f
