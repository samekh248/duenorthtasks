package app.duenorth.tasks.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.frozen
import app.duenorth.tasks.ui.common.rememberTouchHold
import app.duenorth.tasks.ui.common.touchHold

@Composable
internal fun TodaySection(
    state: HomeUiState,
    viewModel: HomeViewModel,
    addFocus: FocusRequester,
    continuum: ContinuumState,
    openTask: (String) -> Unit,
    taskMenu: (TaskRowUi) -> List<ContextMenuItem>,
    arrivals: () -> Int
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val dueToday = hold.frozen(state.dueToday, state.added)
    val tomorrow = hold.frozen(state.tomorrow, state.added)
    var addHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchHold(hold).testTag("today"),
            state = list,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "add", contentType = "add") {
                AddTaskBox(
                    serviceName = state.serviceName,
                    today = state.today,
                    lists = state.lists,
                    onAdd = { title, details, due, list -> viewModel.addTask(title, details, due, list) },
                    focusRequester = addFocus,
                    modifier = Modifier.onSizeChanged { addHeight = it.height }.padding(top = 8.dp, bottom = 4.dp),
                    templates = state.taskTemplates,
                    onUseTemplate = { viewModel.addFromTemplate(it) }
                )
            }
            if (state.loading) {
                item(key = "loading", contentType = "loading") { MetroTaskPlaceholders() }
                return@LazyColumn
            }
            items(dueToday, key = { it.id }, contentType = { "task" }) { row ->
                TaskRow(
                    row = row,
                    onToggle = { viewModel.setCompleted(row.id, it) },
                    onOpen = { openTask(row.id) },
                    menuItems = taskMenu(row),
                    continuum = continuum,
                    modifier = Modifier.animateItem()
                )
            }
            if (tomorrow.isNotEmpty()) {
                item(key = "tomorrow", contentType = "header") {
                    MetroText(
                        "tomorrow",
                        MetroTheme.typography.subheader,
                        Modifier.padding(top = 16.dp, bottom = 4.dp).animateItem(),
                        color = MetroTheme.colors.secondary
                    )
                }
                items(tomorrow, key = { it.id }, contentType = { "task" }) { row ->
                    TaskRow(
                        row = row,
                        onToggle = { viewModel.setCompleted(row.id, it) },
                        onOpen = { openTask(row.id) },
                        menuItems = taskMenu(row),
                        continuum = continuum,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        val empty = !state.loading && dueToday.isEmpty() && tomorrow.isEmpty()
        AnimatedVisibility(empty, enter = fadeIn(), exit = fadeOut()) {
            val top = with(LocalDensity.current) { addHeight.toDp() }
            EmptyToday(arrivals, Modifier.fillMaxSize().padding(top = top, bottom = 24.dp))
        }
    }
}
