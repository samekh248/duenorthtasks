package app.duenorth.tasks.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroListItem
import app.duenorth.tasks.design.components.MetroPickerDialog
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.templates.TaskTemplatePick
import java.time.LocalDate

/**
 * The "add a task" box at the top of today (FR-011a, contracts/ui-screens.md "Adding a task").
 * Enter adds a title-only task due today. Once there is text, an accent "add details" link grows
 * the same box in place with details, a due date chip and a list chip.
 */
@Composable
fun AddTaskBox(
    serviceName: String,
    today: LocalDate,
    lists: List<ListRowUi>,
    onAdd: (title: String, details: String?, due: LocalDate?, listId: String?) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    templates: List<TaskTemplatePick> = emptyList(),
    onUseTemplate: (TaskTemplatePick) -> Unit = {}
) {
    var title by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var dueTouched by rememberSaveable { mutableStateOf(false) }
    var listId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingList by rememberSaveable { mutableStateOf(false) }

    val effectiveDue = if (dueTouched) due else today
    val list = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull()

    fun submit() {
        if (title.isBlank()) return
        onAdd(title.trim(), details.takeIf { expanded && it.isNotBlank() }, effectiveDue, list?.id)
        title = ""
        details = ""
        expanded = false
        dueTouched = false
        due = null
        pickingDate = false
    }

    val titleField: @Composable () -> Unit = {
        MetroTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "add a task",
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
    }

    Column(modifier.fillMaxWidth().padding(end = MetroDimens.Gutter).animateContentSize()) {
        if (!expanded) {
            titleField()
            if (title.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MetroLink("+ add details", { expanded = true })
                    Spacer(Modifier.weight(1f))
                    MetroText("enter adds it", MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
                }
            } else {
                TemplatePicker(templates, onUse = onUseTemplate)
            }
        } else {
            Column(
                Modifier.border(2.dp, MetroTheme.accent.fill).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                titleField()
                MetroTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = "details, steps, links",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(effectiveDue?.let { DueText.relative(it, today) } ?: "no date") { pickingDate = !pickingDate }
                    Chip(list?.title ?: "Tasks") { pickingList = true }
                }
                if (pickingDate) {
                    MetroDatePicker(
                        date = effectiveDue ?: today,
                        onDateChange = {
                            due = it
                            dueTouched = true
                        }
                    )
                    MetroLink("no due date", {
                        due = null
                        dueTouched = true
                        pickingDate = false
                    })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetroLink("hide details", { expanded = false })
                Spacer(Modifier.weight(1f))
                MetroButton("add", { submit() }, enabled = title.isNotBlank())
            }
            if (serviceName.isNotEmpty()) {
                MetroText(
                    "Details sync as the task's notes in $serviceName.",
                    MetroTheme.typography.caption,
                    color = MetroTheme.colors.secondary
                )
            }
        }
    }

    if (pickingList && lists.isNotEmpty()) {
        MetroPickerDialog(
            title = "add to list",
            options = lists,
            selected = list,
            label = { it.title },
            onPick = {
                listId = it.id
                pickingList = false
            },
            onDismiss = { pickingList = false }
        )
    }
}

/**
 * spec 004 FR-324: an accent "use a template" link under an empty add box; it opens the task
 * templates right there, and tapping one adds it at once.
 */
@Composable
fun TemplatePicker(templates: List<TaskTemplatePick>, onUse: (TaskTemplatePick) -> Unit) {
    if (templates.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        MetroLink(if (open) "hide templates" else "use a template", { open = !open })
        if (open) {
            MetroText(
                "TASK TEMPLATES",
                MetroTheme.typography.caption,
                Modifier.padding(bottom = 4.dp),
                color = MetroTheme.colors.secondary
            )
            templates.forEach { pick ->
                MetroListItem(
                    title = pick.title,
                    caption = pick.caption,
                    captionColor = MetroTheme.colors.secondary,
                    onClick = {
                        open = false
                        onUse(pick)
                    }
                )
            }
        }
    }
}
