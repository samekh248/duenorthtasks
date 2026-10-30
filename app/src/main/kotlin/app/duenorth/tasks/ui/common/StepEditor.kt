package app.duenorth.tasks.ui.common

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroCheckBox
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroQuickAddField
import app.duenorth.tasks.design.components.MetroReorderList
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

/*
 * The parts a task and a task template share on their pages: section labels, the title and
 * details form, step rows, the "add a step" box and the reorder steps page.
 */

/** A small secondary caption over a section of a task or template page. */
@Composable
fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
}

/** Edits a title and its details in place; save stays off while the title is blank. */
@Composable
fun TitleDetailsForm(title: String, details: String, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
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

/**
 * A step: check box and title; long-press for reorder and remove. A task's step ticks with a tap
 * and is struck through once done. A template's step has no [onDone]: its check box is dimmed,
 * since template steps always start unticked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StepRow(
    title: String,
    done: Boolean,
    onDone: ((Boolean) -> Unit)?,
    onRemove: () -> Unit,
    onReorder: (() -> Unit)?
) {
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
                    onClick = { onDone?.invoke(!done) }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            if (onDone != null) {
                MetroCheckBox(checked = done, onCheckedChange = onDone, label = title)
            } else {
                MetroCheckBox(checked = false, onCheckedChange = null, modifier = Modifier.alpha(DIMMED))
            }
            MetroText(
                title,
                MetroTheme.typography.body.copy(
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (done) MetroTheme.colors.secondary else MetroTheme.colors.foreground
            )
        }
        MetroContextMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOfNotNull(
                onReorder?.let { ContextMenuItem("reorder", it) },
                ContextMenuItem("remove", onRemove)
            )
        )
    }
}

@Composable
fun AddStepField(onAdd: (String) -> Unit) {
    MetroQuickAddField(placeholder = "add a step", onAdd = onAdd, modifier = Modifier.fillMaxWidth())
}

/** Reorder steps mode (specs/003-reordering US3): the steps get the page; done or back ends it. */
@Composable
fun <T> ReorderStepsPage(
    overline: String,
    title: String,
    steps: List<T>,
    key: (T) -> String,
    onMove: (moved: String, order: List<String>) -> Unit,
    initialKey: String?,
    onDone: () -> Unit,
    testTag: String,
    row: @Composable (T) -> Unit
) {
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            MetroText(
                overline,
                MetroTheme.typography.pageTitle,
                Modifier.padding(start = MetroDimens.Gutter, top = 16.dp, end = MetroDimens.Gutter),
                maxLines = 1
            )
            MetroText(
                title,
                MetroTheme.typography.detailTitle,
                Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 4.dp).semantics { heading() }
            )
            MetroReorderList(
                items = steps,
                key = key,
                onMove = onMove,
                modifier = Modifier.fillMaxSize().testTag(testTag),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                initialKey = initialKey,
                headerItems = 1,
                header = { item(key = "hint", contentType = "hint") { ReorderHint("drag a step to move it") } }
            ) { step -> row(step) }
        }
        MetroAppBar(buttons = listOf(AppBarButton(MetroIcon.Check, "done", onClick = onDone)))
    }
}

private const val DIMMED = 0.35f
