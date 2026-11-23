package app.duenorth.tasks.ui.sharing

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.components.MetroDialog
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroToggle
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.share.TapMember
import app.duenorth.tasks.ui.share.TapSharingState

/** What the status block says on "this phone only", where share by tap is the only sharing. */
internal fun tapStatus(tap: TapSharingState, available: Boolean): Pair<String, String> = when {
    tap.shared -> {
        val others = tap.members.count { !it.isMe }
        "shared by tap" to "with $others ${if (others == 1) "person" else "people"}, on your phones only"
    }
    available -> "only you" to "tap phones with someone to share it"
    else -> "only you" to "this phone can't share by tap"
}

/**
 * spec 011 US3 (mockup column 6): who's in a tap-shared list, who added them and when you last
 * tapped, then "add someone", "sync completed tasks" and "leave list".
 */
@Composable
internal fun TapSharingSection(
    tap: TapSharingState,
    available: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onSyncCompleted: (Boolean) -> Unit
) {
    var removing by remember { mutableStateOf<TapMember?>(null) }
    var leaving by remember { mutableStateOf(false) }
    Column(Modifier.testTag("tap-sharing"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tap.shared) {
            SectionLabel("who's in it")
            tap.members.forEach { m ->
                MemberRow(m, canRemove = tap.canRemove && !m.isMe) { removing = m }
            }
        }
        if (available) {
            when {
                tap.full -> Note("The list has 8 people, the most it can have.")
                else -> MetroLink(if (tap.shared) "add someone" else "share by tap", onAdd)
            }
        }
        if (tap.shared) {
            SectionLabel("what syncs")
            MetroToggle(
                tap.syncCompleted,
                onSyncCompleted,
                Modifier.fillMaxWidth(),
                label = "sync completed tasks"
            )
            Spacer4()
            MetroLink("leave list", { leaving = true })
            Note("The list and its tasks stay here as an ordinary list.")
        }
    }
    removing?.let { m ->
        MetroDialog(
            title = "remove ${m.name}?",
            message = "They stop getting changes the next time you tap anyone in the list. " +
                "What's already on their phone stays there.",
            confirmLabel = "remove",
            onConfirm = {
                onRemove(m.id)
                removing = null
            },
            onDismiss = { removing = null }
        )
    }
    if (leaving) {
        MetroDialog(
            title = "leave this list?",
            message = "You keep the list and its tasks, but changes stop swapping. " +
                "The others find out at your next tap with any of them.",
            confirmLabel = "leave",
            onConfirm = {
                onLeave()
                leaving = false
            },
            onDismiss = { leaving = false }
        )
    }
}

@Composable
private fun MemberRow(m: TapMember, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)
    ) {
        MetroIconGlyph(if (m.isStarter) MetroIcon.Lock else MetroIcon.Link, size = 20.dp)
        Column(Modifier.weight(1f)) {
            MetroText(if (m.isMe) "${m.name} (you)" else m.name, MetroTheme.typography.body)
            MetroText(memberDetail(m), MetroTheme.typography.caption, color = MetroTheme.colors.secondary)
        }
        if (canRemove) MetroLink("remove", onRemove)
    }
}

internal fun memberDetail(m: TapMember, now: Long = System.currentTimeMillis()): String {
    val who = when {
        m.isStarter -> "started the list"
        m.addedBy != null -> "added by ${m.addedBy}"
        else -> "member"
    }
    val tap = m.lastTap?.let {
        "last tap " + DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
    }
    return listOfNotNull(who, tap.takeIf { !m.isMe }).joinToString(" · ")
}

@Composable
private fun SectionLabel(text: String) = MetroText(
    text,
    MetroTheme.typography.caption,
    Modifier.padding(top = MetroDimens.Grid),
    color = MetroTheme.colors.secondary
)

@Composable
private fun Note(text: String) = MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
