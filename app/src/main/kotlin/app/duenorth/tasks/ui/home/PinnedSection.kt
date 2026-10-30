package app.duenorth.tasks.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.GroupPosition
import app.duenorth.tasks.design.components.MetroQuickAddField
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.motion.ContinuumState
import app.duenorth.tasks.design.theme.ListAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.ui.common.TaskRow
import app.duenorth.tasks.ui.common.TaskRowUi
import app.duenorth.tasks.ui.common.frozen
import app.duenorth.tasks.ui.common.rememberTouchHold
import app.duenorth.tasks.ui.common.touchHold

/**
 * A pinned list's own section: an "add a task" box for that list and its open tasks in the list's
 * own order, all in the list's shade.
 */
@Composable
internal fun PinnedSection(
    pinned: PinnedListUi,
    state: HomeUiState,
    viewModel: HomeViewModel,
    addFocus: FocusRequester,
    continuum: ContinuumState,
    openTask: (String) -> Unit,
    taskMenu: (TaskRowUi) -> List<ContextMenuItem>
) {
    val list = rememberLazyListState()
    val hold = rememberTouchHold(list, viewModel::holdSync)
    val open = hold.frozen(pinned.open)
    val draft = rememberSaveable(pinned.list.id, saver = TextFieldState.Saver) { TextFieldState() }
    ListAccent(pinned.list.id) {
        LazyColumn(
            Modifier.fillMaxSize().touchHold(hold).testTag("pinned:${pinned.list.title}"),
            state = list,
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item(key = "add", contentType = "add") {
                MetroQuickAddField(
                    placeholder = "add a task",
                    onAdd = { viewModel.addTask(it, due = null, listId = pinned.list.id) },
                    modifier = Modifier.padding(end = MetroDimens.Gutter, bottom = 4.dp).focusRequester(addFocus),
                    state = draft
                )
            }
            if (state.loading) {
                item(key = "loading", contentType = "loading") { MetroTaskPlaceholders() }
                return@LazyColumn
            }
            if (open.isEmpty()) {
                item(key = "empty", contentType = "empty") { EmptyNote("nothing to do here") }
            }
            itemsIndexed(open, key = { _, row -> row.id }, contentType = { _, _ -> "task" }) { index, row ->
                TaskRow(
                    row = row,
                    onToggle = { viewModel.setCompleted(row.id, it) },
                    onOpen = { openTask(row.id) },
                    menuItems = taskMenu(row),
                    continuum = continuum,
                    modifier = Modifier.animateItem(),
                    position = GroupPosition.of(index, open.size)
                )
            }
        }
    }
}
