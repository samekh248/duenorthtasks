package app.duenorth.tasks.design.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.LocalAnimationsEnabled
import app.duenorth.tasks.design.motion.MetroEasing
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.Relief

@Immutable
data class AppBarButton(
    val icon: MetroIcon,
    /** Shown under the button when the bar is expanded, and read by TalkBack. */
    val label: String,
    val enabled: Boolean = true,
    /** Turns the glyph while true (the sync button during a sync); it finishes its turn when set false. */
    val spinning: Boolean = false,
    val onClick: () -> Unit
)

@Immutable
data class AppBarMenuItem(val label: String, val onClick: () -> Unit)

private const val MAX_BUTTONS = 4
private const val EXPAND_MS = 200
private const val SPIN_MS = 1200

/**
 * The WP8.1 Application Bar: up to four round outlined buttons along the bottom and an ellipsis
 * that expands the bar to show their labels and the overflow menu (research R3). Back closes an
 * expanded bar, and so does touching anything outside it inside a [MetroAppBarHost]. Tile relief raises
 * the buttons and fills add with the accent; mica floats the bar as an acrylic sheet of bare glyphs;
 * Windows 10 Mobile draws its own flat command bar ([Mobile10CommandBar]).
 */
@Composable
fun MetroAppBar(
    buttons: List<AppBarButton>,
    modifier: Modifier = Modifier,
    menuItems: List<AppBarMenuItem> = emptyList(),
    initiallyExpanded: Boolean = false
) {
    require(buttons.size <= MAX_BUTTONS) { "The app bar holds at most $MAX_BUTTONS buttons" }
    val colors = MetroTheme.colors
    val type = MetroTheme.typography
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    BackHandler(enabled = expanded) { expanded = false }
    val host = LocalAppBarHost.current
    val registration = remember { ExpandedAppBar(collapse = { expanded = false }) }
    if (host != null) {
        DisposableEffect(host, expanded) {
            if (expanded) host.expanded = registration
            onDispose { if (host.expanded === registration) host.expanded = null }
        }
    }
    val style = MetroTheme.style
    val tracked = if (host != null) {
        Modifier.onGloballyPositioned { registration.boundsInWindow = it.boundsInWindow() }
    } else {
        Modifier
    }
    if (style.mobile10) {
        Mobile10CommandBar(buttons, menuItems, expanded, { expanded = it }, modifier.then(tracked))
        return
    }
    val chrome = if (style.layers) {
        // Mica: an acrylic sheet floating a gutter in from the screen's edges, over the page.
        Modifier
            .background(colors.background)
            .navigationBarsPadding()
            .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Gutter)
            .acrylicSheet(MetroTheme.surface)
    } else {
        Modifier.background(colors.chrome).navigationBarsPadding()
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(tracked)
            .then(chrome)
            .animateContentSize(tween(EXPAND_MS, easing = MetroEasing))
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                buttons.forEach { button ->
                    AppBarIconButton(button, showLabel = expanded) {
                        expanded = false
                        button.onClick()
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 56.dp, height = MetroDimens.TouchTarget)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        expanded = !expanded
                    }
                    .semantics { contentDescription = if (expanded) "less" else "more" }
                    // A full 48dp target, with the dots where WP8.1 draws them near the top edge.
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                MetroIconGlyph(MetroIcon.More, size = 20.dp)
            }
        }
        if (expanded && menuItems.isNotEmpty()) {
            Column(Modifier.padding(start = MetroDimens.Gutter * 2, bottom = MetroDimens.Gutter)) {
                menuItems.forEach { item ->
                    MetroText(
                        item.label,
                        type.subheader,
                        Modifier
                            .fillMaxWidth()
                            .metroTilt()
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                expanded = false
                                item.onClick()
                            }
                            .heightIn(min = MetroDimens.TouchTarget)
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBarIconButton(button: AppBarButton, showLabel: Boolean, onClick: () -> Unit) {
    val colors = MetroTheme.colors
    val color = if (button.enabled) colors.foreground else colors.outline
    Column(
        Modifier
            .width(64.dp)
            .metroTilt(button.enabled)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = button.enabled,
                onClickLabel = button.label,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = button.label
                if (button.spinning) stateDescription = "syncing"
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val style = MetroTheme.style
        val accent = MetroTheme.accent
        // Tile relief and mica fill add with the accent, so the page's main action leads the bar.
        val filled = style.filledAdd && button.icon == MetroIcon.Add && button.enabled
        Box(
            Modifier.size(MetroDimens.TouchTarget).then(
                when {
                    filled -> Modifier.relief(Relief.Raised, small = true, corner = PillCorner, fill = accent.fill)
                    // Mica's other buttons are bare glyphs on the acrylic.
                    style.layers -> Modifier
                    // The button's own face is the ground it stands on: the bar's chrome.
                    style.relief -> Modifier.relief(
                        Relief.Raised,
                        small = true,
                        corner = PillCorner,
                        fill = colors.chrome
                    )
                    else -> Modifier.border(2.dp, color, CircleShape)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            MetroIconGlyph(button.icon, Modifier.spin(button.spinning), color = if (filled) accent.onFill else color)
        }
        if (showLabel) {
            MetroText(
                button.label,
                MetroTheme.typography.caption,
                Modifier.padding(top = 4.dp, bottom = 6.dp),
                color = color,
                maxLines = 1
            )
        } else {
            Box(Modifier.height(4.dp))
        }
    }
}

/**
 * The Windows 10 Mobile command bar: bare glyphs right-aligned on a flat 48dp bar, the ellipsis last. Opened, it
 * shows each glyph's label under it and the menu below as a plain list in body type.
 */
@Composable
private fun Mobile10CommandBar(
    buttons: List<AppBarButton>,
    menuItems: List<AppBarMenuItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    val colors = MetroTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .navigationBarsPadding()
            .animateContentSize(tween(EXPAND_MS, easing = MetroEasing))
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = Mobile10CommandBarHeight), horizontalArrangement = Arrangement.End) {
            buttons.forEach { button ->
                val color = if (button.enabled) colors.foreground else colors.outline
                Column(
                    Modifier
                        .width(68.dp)
                        .metroTilt(button.enabled)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = button.enabled,
                            onClickLabel = button.label,
                            role = Role.Button
                        ) {
                            onExpandedChange(false)
                            button.onClick()
                        }
                        .semantics {
                            contentDescription = button.label
                            if (button.spinning) stateDescription = "syncing"
                        }
                        .padding(top = 12.dp, bottom = if (expanded) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MetroIconGlyph(button.icon, Modifier.spin(button.spinning), color = color, size = 24.dp)
                    if (expanded) {
                        MetroText(
                            button.label,
                            MetroTheme.typography.caption,
                            Modifier.padding(top = 4.dp),
                            color = color,
                            maxLines = 1
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(width = MetroDimens.TouchTarget, height = Mobile10CommandBarHeight)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        onExpandedChange(!expanded)
                    }
                    .semantics { contentDescription = if (expanded) "less" else "more" }
                    .padding(top = 4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                MetroIconGlyph(MetroIcon.More, size = 20.dp)
            }
        }
        if (expanded && menuItems.isNotEmpty()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                menuItems.forEach { item ->
                    MetroText(
                        item.label,
                        MetroTheme.typography.body,
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                onExpandedChange(false)
                                item.onClick()
                            }
                            .heightIn(min = MetroDimens.TouchTarget)
                            .padding(horizontal = MetroDimens.Gutter, vertical = 13.dp)
                    )
                }
            }
        }
    }
}

private val Mobile10CommandBarHeight = 48.dp

/**
 * A steady turn while [spinning], drawn in the layer only so nothing recomposes per frame. When it
 * stops, the glyph eases on to upright rather than snapping back. Still with animations off.
 */
@Composable
private fun Modifier.spin(spinning: Boolean): Modifier {
    if (!LocalAnimationsEnabled.current) return this
    // The angle the spin had reached, read when it stops; written from the draw phase.
    val reached = remember { floatArrayOf(0f) }
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(spinning) {
        if (spinning || reached[0] == 0f) return@LaunchedEffect
        settling = true
        settle.snapTo(reached[0])
        val left = (FULL_TURN - reached[0]) / FULL_TURN
        settle.animateTo(FULL_TURN, tween((SPIN_MS * left).toInt(), easing = MetroEasing))
        reached[0] = 0f
        settling = false
    }
    return when {
        spinning -> {
            val angle = rememberInfiniteTransition(label = "sync spin").animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "angle"
            )
            graphicsLayer {
                rotationZ = angle.value
                reached[0] = angle.value
            }
        }
        settling -> graphicsLayer { rotationZ = settle.value }
        else -> this
    }
}

private const val FULL_TURN = 360f
