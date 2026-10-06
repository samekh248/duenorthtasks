package app.duenorth.tasks.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.Chip
import app.duenorth.tasks.ui.common.DueText
import app.duenorth.tasks.ui.common.PageHeader
import java.time.LocalDate

@Composable
fun UseListTemplateScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: UseListTemplateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exists) { if (!state.exists) onCancel() }
    UseListTemplateContent(state, onCreate = { name, start ->
        viewModel.create(name, start, onCreated)
    }, onCancel = onCancel)
}

/** spec 004 FR-321: name (prefilled with the template's), start date when needed, create or cancel. */
@Composable
fun UseListTemplateContent(
    state: UseListTemplateUiState,
    onCreate: (name: String, start: LocalDate) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .testTag("use template")
    ) {
        PageHeader(state.name, overline = "NEW LIST FROM TEMPLATE")
        if (state.loading) {
            MetroTaskPlaceholders(count = 2)
            return@Column
        }
        // Keyed on the template's name so the field fills in once it has loaded.
        var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
        var start by rememberSaveable { mutableStateOf<LocalDate?>(null) }
        var picking by rememberSaveable { mutableStateOf(false) }
        val startDay = start ?: state.today
        Column(
            Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Caption("name")
            MetroTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "list name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("new list name")
            )
            if (state.asksStart) {
                Caption("start date")
                Chip(DueText.relative(startDay, state.today)) { picking = !picking }
                if (picking) MetroDatePicker(date = startDay, onDateChange = { start = it })
            }
            MetroText(
                listOfNotNull(
                    TemplateText.tasks(state.taskCount),
                    "${state.datedCount} with due dates".takeIf { state.asksStart }
                ).joinToString(", "),
                MetroTheme.typography.caption,
                color = MetroTheme.colors.secondary
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                MetroButton("create", { onCreate(name.trim(), startDay) }, enabled = name.isNotBlank())
                MetroButton("cancel", onCancel)
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
}
