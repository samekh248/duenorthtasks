package app.duenorth.tasks.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.AppBarMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.theme.LocalAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.ui.common.AddStepField
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.ReorderRow
import app.duenorth.tasks.ui.common.ReorderStepsPage
import app.duenorth.tasks.ui.common.SectionLabel
import app.duenorth.tasks.ui.common.StepRow
import app.duenorth.tasks.ui.common.TitleDetailsForm

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
        ReorderStepsPage(
            overline = overline(state),
            title = state.title,
            steps = state.steps,
            key = { it.id },
            onMove = viewModel::reorderSteps,
            initialKey = state.reorderFrom,
            onDone = viewModel::endReorder,
            testTag = "reorder-template-steps"
        ) { step -> ReorderRow(title = step.title, caption = null) }
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
                MetroText(state.title, type.detailTitle, Modifier.semantics { heading() })
                if (!state.details.isNullOrBlank()) {
                    SectionLabel("details")
                    MetroText(state.details, type.body)
                }
            }

            SectionLabel("steps")
            state.steps.forEach { step ->
                StepRow(
                    title = step.title,
                    done = false,
                    onDone = null,
                    onRemove = { viewModel.removeStep(step.id) },
                    onReorder = { viewModel.startReorder(step.id) }.takeIf { state.steps.size > 1 }
                )
            }
            AddStepField(onAdd = viewModel::addStep)

            SectionLabel("due")
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
