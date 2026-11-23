package app.duenorth.tasks.ui.share

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duenorth.tasks.design.components.MetroButton
import app.duenorth.tasks.design.components.MetroIcon
import app.duenorth.tasks.design.components.MetroIconGlyph
import app.duenorth.tasks.design.components.MetroLink
import app.duenorth.tasks.design.components.MetroProgressDots
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.components.MetroTextField
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.shadeAccent
import app.duenorth.tasks.share.Answer
import app.duenorth.tasks.share.CardOutcome
import app.duenorth.tasks.share.CardState
import app.duenorth.tasks.share.ListResult
import app.duenorth.tasks.share.ReaderState
import app.duenorth.tasks.share.Refusal
import app.duenorth.tasks.share.TapController
import app.duenorth.tasks.share.TapResult
import app.duenorth.tasks.share.TileFace
import app.duenorth.tasks.ui.common.PageHeader

/**
 * The reader phone's page (spec 011 US1 and US2, mockup columns 1, 2 and 5): "share by tap" for
 * [TapViewModel.listId], or "tap to sync" when there is none.
 */
@Composable
fun TapScreen(onDone: () -> Unit, viewModel: TapViewModel = hiltViewModel()) {
    val state by viewModel.reader.collectAsStateWithLifecycle()
    val title by viewModel.listTitle.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(viewModel.savedName.orEmpty()) }
    var named by rememberSaveable { mutableStateOf(viewModel.savedName != null) }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val sharing = viewModel.listId != null
    val nfcOn = viewModel.nfcOn()

    if (named && nfcOn && activity != null) {
        DisposableEffect(activity) {
            viewModel.start(activity)
            onDispose { viewModel.stop(activity) }
        }
    }

    TapPage(
        title = if (sharing) "share by tap" else "tap to sync",
        overline = if (sharing) "DUE NORTH · ${title.uppercase()}" else "DUE NORTH"
    ) {
        when {
            !named -> NameStep(name, { name = it }) {
                viewModel.saveName(name)
                named = true
            }
            !nfcOn -> {
                Big("NFC is off.")
                Note("Share by tap uses NFC. Turn it on, then come back.")
                MetroButton("NFC settings", { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) })
            }
            else -> ReaderContent(state, sharing, onAnswer = viewModel::answer, onDone = onDone)
        }
    }
}

@Composable
private fun ReaderContent(state: ReaderState, sharing: Boolean, onAnswer: (Boolean) -> Unit, onDone: () -> Unit) {
    when (state) {
        ReaderState.Idle, is ReaderState.Waiting -> {
            if (state is ReaderState.Waiting && state.retry) {
                Big("You pulled apart before the end. Touch again to finish.")
            } else {
                Big("Hold the back of your phone against theirs.")
            }
            TapPicture()
            MetroProgressDots(Modifier.padding(vertical = 8.dp))
            Note(
                if (sharing) {
                    "Their phone only needs to be unlocked, with Due North set to “this phone only”. " +
                        "Nothing is sent until you both confirm the tiles."
                } else {
                    "Every list you share with them catches up, both ways, in about a second."
                }
            )
        }
        is ReaderState.Meeting -> when {
            state.tiles != null && !state.answered -> {
                Big("Keep holding. Do ${state.name}’s tiles look like these?")
                SafetyTiles(state.tiles)
                Note("Both of you need to say they match before anything is sent.")
                Spacer(Modifier.height(MetroDimens.Grid))
                Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                    MetroButton("they match", { onAnswer(true) }, Modifier.weight(1f), primary = true)
                    MetroButton("they don’t", { onAnswer(false) }, Modifier.weight(1f))
                }
            }
            else -> {
                Big("Keep holding. Waiting for ${state.name} to say yes.")
                state.tiles?.let { SafetyTiles(it) }
                MetroProgressDots(Modifier.padding(vertical = 8.dp))
            }
        }
        ReaderState.NotDueNorth -> {
            Big("That phone didn’t answer.")
            Note("It needs Due North, unlocked, with NFC on. Touch again when it’s ready.")
        }
        is ReaderState.Done -> {
            ResultContent(state.result)
            Spacer(Modifier.height(MetroDimens.Grid))
            MetroButton("done", onDone)
        }
    }
}

@Composable
private fun ResultContent(result: TapResult) {
    when (result) {
        is TapResult.Shared -> {
            Big(if (result.mode == Answer.SYNC) "${result.peerName} is in." else "${result.peerName} has a copy.")
            Note(
                if (result.mode == Answer.SYNC) {
                    "Changes either of you make swap the next time your phones touch. Only your phones can read them."
                } else {
                    "It’s their own list now; your changes won’t reach it."
                }
            )
        }
        is TapResult.Synced -> {
            Big("Synced with ${result.peerName}.")
            if (result.lists.isEmpty()) Note("You don’t share any lists yet.")
            result.lists.forEach { SyncedList(it, result.peerName) }
            val conflicts = result.lists.flatMap { it.conflicts }
            if (conflicts.isNotEmpty()) {
                Label("conflicts")
                conflicts.forEach { Body("$it. The other version is in the sync log.") }
            }
        }
        is TapResult.Refused -> {
            Big(
                when (result.reason) {
                    Refusal.NOT_LOCAL_ONLY -> "${result.peerName}’s phone syncs with a service."
                    Refusal.NOT_LINKED -> "You haven’t shared a list with ${result.peerName} yet."
                }
            )
            Note(
                when (result.reason) {
                    Refusal.NOT_LOCAL_ONLY -> "Share by tap needs “this phone only” on both phones. Nothing was sent."
                    Refusal.NOT_LINKED -> "Open a list and pick “share by tap” first."
                }
            )
        }
        is TapResult.Declined -> {
            Big(if (result.byMe) "Stopped. Nothing was sent." else "They said not now. Nothing was sent.")
            if (result.byMe) {
                Note(
                    "If the tiles didn’t match, someone else’s phone may have been in the way. Try again somewhere quieter."
                )
            }
        }
        TapResult.Lost -> Big("You pulled apart before the end. Nothing changed on this phone.")
        TapResult.TimedOut -> Big("That took too long. Nothing was sent.")
        is TapResult.Failed -> {
            Big("That phone answered strangely, so nothing was sent.")
            Note(result.reason)
        }
    }
}

@Composable
private fun SyncedList(result: ListResult, peerName: String) {
    Label(result.title.ifEmpty { "a list" })
    val received = result.receivedBy.values.sum()
    Row(Modifier.fillMaxWidth()) {
        Body("from $peerName and others", Modifier.weight(1f))
        Body(if (received == 0) "up to date" else "$received ${changes(received)}", color = true)
    }
    Row(Modifier.fillMaxWidth()) {
        Body("sent to $peerName", Modifier.weight(1f))
        Body(if (result.sent == 0) "nothing new" else "${result.sent} ${changes(result.sent)}", color = true)
    }
}

private fun changes(n: Int) = if (n == 1) "change" else "changes"

/** The tapped phone's invite page (mockup column 3). */
@Composable
fun InviteScreen(onOpenList: (String) -> Unit, onClosed: () -> Unit, viewModel: InviteViewModel = hiltViewModel()) {
    val state by viewModel.card.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(Answer.SYNC) }
    var name by rememberSaveable { mutableStateOf(viewModel.savedName.orEmpty()) }
    DisposableEffect(Unit) { onDispose { viewModel.dismiss() } }
    LaunchedEffect(state) { if (state == CardState.None) onClosed() }

    when (val s = state) {
        CardState.None -> Box(Modifier.fillMaxSize().background(MetroTheme.colors.background))
        is CardState.Invite -> TapPage("add list", "DUE NORTH") {
            Big("Add “${s.preview.title}” from ${s.fromName}?")
            Note(
                "${s.preview.tasks} ${if (s.preview.tasks == 1) "task" else "tasks"}, shared with " +
                    s.preview.members.joinToString(" and ") + ". It stays on your phones only."
            )
            if (s.answered != null) {
                Big("Keep holding…")
                MetroProgressDots(Modifier.padding(vertical = 8.dp))
                return@TapPage
            }
            MetroRadio(mode == Answer.SYNC, {
                mode = Answer.SYNC
            }, "keep in sync", caption = "changes swap each time you tap")
            MetroRadio(mode == Answer.COPY, {
                mode = Answer.COPY
            }, "just a copy", caption = "yours to change, no link back")
            if (viewModel.savedName == null) {
                Label("your name, as the others will see it")
                MetroTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "your name")
            }
            if (s.tiles != null) {
                Label("do these match ${s.fromName}’s screen?")
                SafetyTiles(s.tiles)
            }
            if (s.sharerAnswer == true) Note("${s.fromName} has confirmed.")
            Spacer(Modifier.height(MetroDimens.Grid))
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                MetroButton(
                    if (s.tiles != null) "they match, add" else "add",
                    {
                        if (viewModel.savedName == null) viewModel.saveName(name)
                        viewModel.answer(mode)
                    },
                    Modifier.weight(1f),
                    enabled = viewModel.savedName != null || name.isNotBlank(),
                    primary = true
                )
                MetroButton("not now", { viewModel.answer(Answer.NO) }, Modifier.weight(1f))
            }
        }
        is CardState.Finished -> TapPage("add list", "DUE NORTH") {
            when (val o = s.outcome) {
                is CardOutcome.Added -> {
                    Big("“${o.title}” is here.")
                    Note(
                        if (o.mode == Answer.SYNC) {
                            "Changes swap whenever you tap phones with ${o.fromName} or anyone else in it."
                        } else {
                            "It’s your own copy."
                        }
                    )
                    Spacer(Modifier.height(MetroDimens.Grid))
                    MetroButton("open list", { onOpenList(o.listId) }, primary = true)
                }
                CardOutcome.Cancelled -> {
                    Big("Nothing was added.")
                    Spacer(Modifier.height(MetroDimens.Grid))
                    MetroButton("done", onClosed)
                }
                else -> MetroButton("done", onClosed)
            }
        }
        CardState.Parted -> TapPage("add list", "DUE NORTH") {
            Big("You pulled apart before the list arrived.")
            Note("Nothing was added. Ask them to start again and keep the phones together.")
            Spacer(Modifier.height(MetroDimens.Grid))
            MetroButton("done", onClosed)
        }
    }
}

/** The four safety tiles (FR-803): a word on a shade of the accent, with a shape. */
@Composable
fun SafetyTiles(tiles: List<TileFace>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "safety tiles: " + tiles.joinToString(", ") { it.word } },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { face ->
            val shade = shadeAccent(SHADES[face.ordinal % 4])
            Column(
                Modifier.size(74.dp).background(shade.fill).padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TileShape(face.ordinal / 4, shade.onFill)
                MetroText(face.word, MetroTheme.typography.caption, color = shade.onFill, maxLines = 1)
            }
        }
    }
}

private val SHADES = listOf(0, 2, -2, 1)

@Composable
private fun TileShape(kind: Int, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        when (kind) {
            0 -> drawCircle(color, radius = w / 2.4f)
            1 -> drawRect(
                color,
                topLeft = Offset(w * 0.1f, w * 0.1f),
                size = androidx.compose.ui.geometry.Size(
                    w * 0.8f,
                    w * 0.8f
                )
            )
            2 -> drawPath(
                Path().apply {
                    moveTo(w / 2, w * 0.05f)
                    lineTo(w * 0.95f, w * 0.9f)
                    lineTo(w * 0.05f, w * 0.9f)
                    close()
                },
                color
            )
            else -> drawPath(
                Path().apply {
                    moveTo(w / 2, 0f)
                    lineTo(w, w / 2)
                    lineTo(w / 2, w)
                    lineTo(0f, w / 2)
                    close()
                },
                color
            )
        }
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    Big("First, your name.")
    Note("People you share with see it next to your changes. It stays on the phones you tap.")
    MetroTextField(name, onName, Modifier.fillMaxWidth().padding(vertical = 8.dp), placeholder = "your name")
    MetroButton("next", onNext, enabled = name.isNotBlank(), primary = true)
}

/** Two phones back to back, the near one in the accent, with the tap glyph between them. */
@Composable
private fun TapPicture() {
    val accent = MetroTheme.accent.fill
    val outline = MetroTheme.colors.foreground
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp, 180.dp)) {
            val u = size.width / 220f
            drawRect(
                accent,
                topLeft = Offset(30 * u, 10 * u),
                size = androidx.compose.ui.geometry.Size(80 * u, 150 * u)
            )
            rotate(8f, pivot = Offset(165 * u, 85 * u)) {
                drawRect(
                    outline,
                    topLeft = Offset(125 * u, 10 * u),
                    size = androidx.compose.ui.geometry.Size(80 * u, 150 * u),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3 * u)
                )
            }
        }
        MetroIconGlyph(MetroIcon.Tap, color = accent, size = 36.dp)
    }
}

@Composable
private fun TapPage(title: String, overline: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("tap")
    ) {
        PageHeader(title, overline = overline)
        Column(
            Modifier.padding(horizontal = MetroDimens.Gutter).padding(top = 8.dp, bottom = MetroDimens.Grid),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { content() }
    }
}

@Composable
private fun Big(text: String) = MetroText(text, MetroTheme.typography.subheader)

@Composable
private fun Note(text: String) = MetroText(text, MetroTheme.typography.caption, color = MetroTheme.colors.secondary)

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, color: Boolean = false) = MetroText(
    text,
    MetroTheme.typography.body,
    modifier,
    color = if (color) MetroTheme.colors.secondary else MetroTheme.colors.foreground
)

@Composable
private fun Label(text: String) =
    MetroText(text, MetroTheme.typography.caption, Modifier.padding(top = 12.dp), color = MetroTheme.colors.secondary)

/** For a link line on pages outside this file. */
@Composable
fun TapLink(text: String, onClick: () -> Unit) = MetroLink(text, onClick)

/** Whether share by tap shows at all, and which lists are tap-shared (for glyphs and menus). */
data class TapAvailability(val available: Boolean = false, val shared: Set<String> = emptySet())

internal fun TapController.canOffer(localOnly: Boolean) = hasNfc && localOnly
