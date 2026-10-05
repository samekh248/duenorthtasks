package app.duenorth.tasks.ui.synclog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.AppBarButton
import app.duenorth.tasks.design.components.MetroAppBar
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.PageHeader

@Composable
fun SyncLogScreen(viewModel: SyncLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncLogContent(state, onClear = viewModel::clear)
}

/**
 * contracts/ui-screens.md "Sync log": entries newest first under day headers. A conflict that kept
 * the other version can be opened to show the version it replaced (FR-023). "clear" asks first.
 */
@Composable
fun SyncLogContent(state: SyncLogUiState, onClear: () -> Unit, initiallyOpen: List<Long> = emptyList()) {
    var clearing by rememberSaveable { mutableStateOf(false) }
    val open = remember { initiallyOpen.toMutableStateList() }
    val colors = MetroTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            Modifier.weight(1f).statusBarsPadding().testTag("sync log"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header", contentType = "header") { PageHeader("sync log") }
            if (state.isEmpty) {
                item(key = "empty") {
                    MetroText(
                        "Nothing here. When a task changes on this phone and in your account before they sync, " +
                            "or a sync runs into trouble, you'll see what happened here.",
                        MetroTheme.typography.body,
                        Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 16.dp),
                        color = colors.secondary
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day:${day.label}", contentType = "day") {
                    MetroText(
                        day.label,
                        MetroTheme.typography.subheader,
                        Modifier
                            .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = 16.dp, bottom = 4.dp)
                            .semantics { heading() }
                            .animateItem(),
                        color = MetroTheme.accent.text
                    )
                }
                items(day.rows, key = { it.id }, contentType = { "entry" }) { row ->
                    LogRow(row, open, Modifier.animateItem())
                }
            }
        }
        MetroAppBar(
            buttons = listOf(
                AppBarButton(MetroIcon.Delete, "clear", enabled = !state.isEmpty && !state.loading) {
                    clearing = true
                }
            )
        )
    }

    if (clearing) {
        MetroDialog(
            title = "clear the sync log?",
            message = "This removes these entries from this phone. Your tasks don't change.",
            confirmLabel = "clear",
            onConfirm = {
                clearing = false
                open.clear()
                onClear()
            },
            onDismiss = { clearing = false }
        )
    }
}

@Composable
private fun LogRow(row: SyncLogRow, open: SnapshotStateList<Long>, modifier: Modifier = Modifier) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    val expanded = row.id in open
    val toggle = Modifier
        .metroTilt()
        .clickable(interactionSource = null, indication = null, role = Role.Button) {
            if (expanded) open.remove(row.id) else open.add(row.id)
        }
        .semantics { stateDescription = if (expanded) "replaced version shown" else "replaced version hidden" }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (row.replaced != null) toggle else Modifier)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
    ) {
        MetroText(
            "${row.kind.label} · ${row.time}",
            type.caption,
            color = if (row.kind == SyncLogKind.PROBLEM) colors.overdue else MetroTheme.accent.text,
            maxLines = 1
        )
        MetroText(row.summary, type.body, Modifier.padding(top = 2.dp))
        row.replaced?.let { replaced ->
            MetroText(
                if (expanded) "hide the replaced version" else "show the replaced version",
                type.caption,
                Modifier.padding(top = 4.dp),
                color = MetroTheme.accent.text
            )
            if (expanded) ReplacedCard(replaced, Modifier.padding(top = 8.dp))
        }
    }
}

/** The version that lost, set off by a thin rule on the left like a quote. */
@Composable
private fun ReplacedCard(version: ReplacedVersion, modifier: Modifier = Modifier) {
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(colors.outline))
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText(
                if (version.fromAccount) "FROM YOUR ACCOUNT" else "FROM THIS PHONE",
                type.pageTitle,
                color = colors.secondary
            )
            MetroText(version.title, type.subheader, strikethrough = version.completed)
            version.details?.let { MetroText(it, type.preview, color = colors.secondary) }
            val facts = listOfNotNull(
                version.due,
                if (version.completed) "done" else null,
                if (version.important) "important" else null
            )
            if (facts.isNotEmpty()) {
                MetroText(facts.joinToString(" · "), type.caption, color = colors.secondary)
            }
            if (version.steps.isNotEmpty()) {
                MetroText("steps", type.caption, Modifier.padding(top = 4.dp), color = colors.secondary)
            }
            // Done steps are struck through, as on the task page.
            version.steps.forEach { step -> MetroText(step.title, type.preview, strikethrough = step.done) }
        }
    }
}
