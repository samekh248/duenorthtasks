package app.duenorth.tasks.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.ContextMenuItem
import app.duenorth.tasks.design.components.MetroContextMenu
import app.duenorth.tasks.design.components.MetroListTile
import app.duenorth.tasks.design.components.MetroTaskPlaceholders
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.listAccent
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
internal fun ListsSection(
    state: HomeUiState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (ListRowUi) -> Unit,
    onShade: (ListRowUi) -> Unit,
    onInfo: (ListRowUi) -> Unit,
    onDelete: (ListRowUi) -> Unit,
    onReorder: (ListRowUi) -> Unit,
    onPin: (ListRowUi) -> Unit,
    onUnpin: (ListRowUi) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("lists"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        if (state.loading) {
            item(key = "loading") { MetroTaskPlaceholders(count = 3) }
            return@LazyColumn
        }
        items(state.lists, key = { it.id }, contentType = { "list" }) { list ->
            ListRow(
                list,
                onOpen = { onOpen(list.id) },
                onRename = { onRename(list) },
                onShade = { onShade(list) },
                onInfo = { onInfo(list) },
                onDelete = { onDelete(list) },
                onReorder = { onReorder(list) }.takeIf { state.lists.size > 1 },
                onPin = { if (list.pinned) onUnpin(list) else onPin(list) }
            )
        }
        item(key = "new", contentType = "new") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .metroTilt()
                    .combinedClickable(interactionSource = null, indication = null, onClick = onNew),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
            ) {
                MetroListTile(count = null)
                MetroText("new list", MetroTheme.typography.listName)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: ListRowUi,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShade: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (() -> Unit)?,
    onPin: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .metroTilt()
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onLongClick = { menu = true },
                    onClick = onOpen
                )
                // TalkBack reads the name first, then "shared with you, 3 open" instead of a bare
                // number from the tile.
                .semantics {
                    stateDescription = listOf(list.sharing.spoken, "${list.openCount} open")
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                },
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
        ) {
            val shade = listAccent(list.id)
            MetroListTile(
                count = list.openCount,
                modifier = Modifier.clearAndSetSemantics {},
                fill = shade.fill,
                onFill = shade.onFill,
                shared = list.sharing.isShared
            )
            Column(Modifier.weight(1f)) {
                MetroText(list.title, MetroTheme.typography.listName, maxLines = 1)
                MetroText(
                    listOfNotNull(
                        list.sharing.caption.ifEmpty { null },
                        list.next?.let { "next: $it" } ?: "no open tasks"
                    ).joinToString(" · "),
                    MetroTheme.typography.caption,
                    color = MetroTheme.colors.secondary,
                    maxLines = 1
                )
            }
        }
        MetroContextMenu(
            expanded = menu,
            onDismiss = { menu = false },
            // Only the owner can rename or delete a shared list; "list info" says so (spec 002 FR-120).
            items = listOfNotNull(
                onReorder?.let { ContextMenuItem("reorder", it) },
                ContextMenuItem(if (list.pinned) "unpin from home" else "pin to home", onPin),
                ContextMenuItem("rename", onRename).takeIf { list.sharing.canManage },
                ContextMenuItem("list shade", onShade),
                ContextMenuItem("list info", onInfo),
                ContextMenuItem("delete", onDelete).takeIf { list.sharing.canManage }
            )
        )
    }
}
