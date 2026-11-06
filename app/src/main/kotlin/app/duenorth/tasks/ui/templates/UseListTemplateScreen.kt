package app.duenorth.tasks.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDatePicker
import app.duenorth.tasks.design.components.MetroIcon
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

/**
 * spec 004 FR-321: name (prefilled with the template's), start date when needed. A form by Due North
 * Budget's rules: save and cancel on the app bar, nothing else, and no buttons in the page.
 */
@Composable
fun UseListTemplateContent(
    state: UseListTemplateUiState,
    onCreate: (name: String, start: LocalDate) -> Unit,
    onCancel: () -> Unit
) {
    // Keyed on the template's name so the field fills in once it has loaded.
    var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
    var start by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val startDay = start ?: state.today
    Column(Modifier.fillMaxSize().background(MetroTheme.colors.background).imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag("use template")
        ) {
            UseListTemplateFields(state, name, { name = it }, startDay, picking, { picking = it }, { start = it })
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Check, "save", enabled = !state.loading && name.isNotBlank()) {
                    onCreate(name.trim(), startDay)
                },
                AppBarButton(MetroIcon.Close, "cancel", onClick = onCancel)
            )
        )
    }
}

@Composable
private fun UseListTemplateFields(
    state: UseListTemplateUiState,
    name: String,
    onName: (String) -> Unit,
    startDay: LocalDate,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
    onStart: (LocalDate) -> Unit
) {
    Column {
        PageHeader(state.name, overline = "NEW LIST FROM TEMPLATE")
        if (state.loading) {
            MetroTaskPlaceholders(count = 2)
            return@Column
        }
        Column(
            Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Caption("name")
            MetroTextField(
                value = name,
                onValueChange = onName,
                placeholder = "list name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("new list name")
            )
            if (state.asksStart) {
                Caption("start date")
                Chip(DueText.relative(startDay, state.today)) { onPicking(!picking) }
                if (picking) MetroDatePicker(date = startDay, onDateChange = onStart)
            }
            MetroText(
                listOfNotNull(
                    TemplateText.tasks(state.taskCount),
                    "${state.datedCount} with due dates".takeIf { state.asksStart }
                ).joinToString(", "),
                MetroTheme.typography.caption,
                color = MetroTheme.colors.secondary
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
}
