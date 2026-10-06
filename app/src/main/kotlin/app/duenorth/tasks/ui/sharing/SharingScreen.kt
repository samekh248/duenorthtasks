package app.duenorth.tasks.ui.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.ListAccent
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.ListSharing
import app.duenorth.tasks.ui.common.PageHeader

@Composable
fun SharingScreen(onClosed: () -> Unit, onShade: (String) -> Unit, viewModel: SharingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.exists) { if (!state.exists) onClosed() }
    SharingContent(
        state,
        onShade = { onShade(viewModel.listId) },
        onHandOff = { state.handOff?.let { openToDo(context, it) } }
    )
}

/**
 * spec 002 US2 and mockups/shared-lists-mockups.png column 3: status, "who's in it", "what you can
 * do here", and the list's shade. Nothing here names or counts the people in a list, because the
 * services don't tell other apps (research R2).
 */
@Composable
fun SharingContent(state: SharingUiState, onShade: () -> Unit, onHandOff: () -> Unit) {
    ListAccent(state.listId) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MetroTheme.colors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag("sharing")
        ) {
            PageHeader("sharing", overline = state.title.ifEmpty { "DUE NORTH" })
            Column(
                Modifier.padding(horizontal = MetroDimens.Gutter).padding(bottom = MetroDimens.Grid),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Label("status")
                Status(state)
                if (state.serviceShares) {
                    Label("who's in it")
                    MetroText(
                        "${state.serviceName} doesn't tell other apps who a list is shared with, " +
                            "so the names aren't here.",
                        MetroTheme.typography.body
                    )
                    if (state.handOff != null) {
                        LinkRow(
                            if (state.sharing.isShared) "see people in microsoft to do" else "share in microsoft to do",
                            MetroIcon.OpenOutside,
                            onHandOff
                        )
                    }
                    Label("what you can do here")
                    Can(true, "add, edit, complete and delete tasks")
                    Can(true, "pick this list's shade (only on this phone)")
                    if (state.sharing.canManage) {
                        Can(true, "rename or delete the list")
                    } else {
                        Can(false, "rename or delete the list: only the owner")
                    }
                    Can(false, "invite people or leave: in ${state.serviceName.lowercase()}")
                }
                Label("list shade")
                LinkRow("pick a lighter or darker shade", null, onShade)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    MetroText(
        text,
        MetroTheme.typography.caption,
        Modifier.padding(top = MetroDimens.Grid),
        color = MetroTheme.colors.secondary
    )
}

@Composable
private fun Status(state: SharingUiState) {
    val (title, detail) = when {
        !state.serviceShares -> "only you" to "${state.serviceName} lists can't be shared"
        state.sharing == ListSharing.OWNED -> "shared by you" to "you own this list"
        state.sharing == ListSharing.WITH_YOU -> "shared with you" to "someone else owns this list"
        else -> "only you" to "this list isn't shared"
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
        MetroIconGlyph(MetroIcon.People, Modifier.padding(top = 4.dp), size = 24.dp)
        Column {
            MetroText(title, MetroTheme.typography.subheader)
            MetroText(detail, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
        }
    }
}

@Composable
private fun Can(allowed: Boolean, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        if (allowed) {
            MetroIconGlyph(MetroIcon.Check, size = 18.dp)
        } else {
            MetroText("–", MetroTheme.typography.body, color = MetroTheme.colors.secondary)
        }
        MetroText(
            text,
            MetroTheme.typography.body,
            color = if (allowed) MetroTheme.colors.foreground else MetroTheme.colors.secondary
        )
    }
}

@Composable
private fun LinkRow(text: String, icon: MetroIcon?, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) MetroIconGlyph(icon, color = MetroTheme.accent.text, size = 20.dp)
        MetroText(text, MetroTheme.typography.body, color = MetroTheme.accent.text)
    }
}

/** The To Do app when it's installed, otherwise To Do on the web (spec 002 FR-112). */
private fun openToDo(context: Context, handOff: HandOff) {
    val app = context.packageManager.getLaunchIntentForPackage(handOff.appPackage)
    val intent = app ?: Intent(Intent.ACTION_VIEW, Uri.parse(handOff.webUrl))
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser either; nothing sensible to open.
    }
}
