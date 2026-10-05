package app.duenorth.tasks.design.components

import android.os.Build
import android.os.Trace
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme

private const val FILL_MS = 100

/**
 * WP8.1 check box: a flat square with a 2dp border that fills with the accent when checked.
 * The touch target is 48dp around a 24dp box. [label] names what it checks off for TalkBack
 * (the task title), since the box itself has no text.
 */
@Composable
fun MetroCheckBox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val colors = MetroTheme.colors
    val accent = MetroTheme.accent
    val fill by animateColorAsState(if (checked) accent.fill else Color.Transparent, tween(FILL_MS), label = "fill")
    val border = if (checked) accent.fill else colors.foreground
    val tick = remember { TickTrace() }
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = null,
            indication = null,
            onValueChange = { value ->
                tick.begin(value)
                onCheckedChange(value)
            }
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .metroTilt(enabled = enabled && onCheckedChange != null)
            .then(toggle)
            .drawBehind { tick.drawn(checked) }
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .size(MetroDimens.TouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(fill)
                .border(2.dp, if (enabled) border else colors.outline),
            contentAlignment = Alignment.Center
        ) {
            if (checked) MetroIconGlyph(MetroIcon.Check, color = accent.onFill, size = 20.dp)
        }
    }
}

/**
 * Trace section from a tap on the box to the first frame that draws the new state (SC-008:
 * feedback within 100 ms). The tap-to-tick macrobenchmark measures it. A plain holder, not
 * snapshot state, so recording it never causes another frame.
 */
private class TickTrace {
    private var cookie = 0
    private var waitingFor: Boolean? = null

    fun begin(value: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (waitingFor != null) Trace.endAsyncSection(TICK_TRACE_SECTION, cookie)
        cookie = nextCookie++
        waitingFor = value
        Trace.beginAsyncSection(TICK_TRACE_SECTION, cookie)
    }

    fun drawn(checked: Boolean) {
        if (waitingFor != checked || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        waitingFor = null
        Trace.endAsyncSection(TICK_TRACE_SECTION, cookie)
    }

    private companion object {
        var nextCookie = 1
    }
}

/** The trace section [MetroCheckBox] writes per tap; the benchmark module matches this name. */
const val TICK_TRACE_SECTION = "MetroCheckBox tick"
