package app.duenorth.tasks.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.PageHeader
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.home.EmptyNote

/** "search" (contracts/ui-screens.md): the field is focused on arrival, results grouped by list. */
@Composable
fun SearchScreen(onOpenTask: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        PageHeader("search")
        MetroTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = "titles and details",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
                .focusRequester(focus)
                .testTag("search field"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = MetroDimens.Gutter, bottom = 24.dp)
        ) {
            if (state.searched && state.groups.isEmpty()) {
                item(key = "none") { EmptyNote("no tasks match \"${state.query}\"") }
            }
            state.groups.forEach { group ->
                item(key = "list:${group.listId}", contentType = "header") {
                    MetroText(
                        group.listTitle.lowercase(),
                        MetroTheme.typography.subheader,
                        Modifier.padding(top = 16.dp, bottom = 4.dp).animateItem(),
                        color = MetroTheme.accent.text
                    )
                }
                items(group.rows, key = { it.id }, contentType = { "task" }) { row ->
                    TaskRow(
                        row = row,
                        onToggle = { viewModel.setCompleted(row.id, it) },
                        onOpen = { onOpenTask(row.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
