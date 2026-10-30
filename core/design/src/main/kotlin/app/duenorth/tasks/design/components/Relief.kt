package app.duenorth.tasks.design.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroSurface
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.design.theme.Relief

/** A corner large enough to make any shape a circle or a pill. */
val PillCorner: Dp = 999.dp

/** Mica's corners: a layer card (a tile, a menu), a control on one (a field, a button), and a floating sheet. */
object LayerCorner {
    val Card = 8.dp
    val Control = 6.dp
    val Floating = 12.dp
}

/**
 * The shape of a surface under the current style: rounded by [corner] under mica, square under every other style,
 * as Metro draws everything.
 */
@Composable
@ReadOnlyComposable
fun layerShape(corner: Dp = LayerCorner.Card): Shape =
    if (MetroTheme.style.layers) RoundedCornerShape(corner) else RectangleShape

/**
 * Draws a relief surface behind the content: a panel ([small] false) or a control ([small] true), in the style's
 * light and shade, filled with [fill]. Corners are square, as in Metro, unless [corner] is given (the app bar's
 * round buttons). Under the Metro and Windows 10 Mobile styles it draws nothing, so callers add it unconditionally.
 */
@Composable
fun Modifier.relief(
    relief: Relief,
    small: Boolean = false,
    corner: Dp? = null,
    fill: Color = MetroTheme.colors.background
): Modifier {
    val surface = MetroTheme.surface
    if (!surface.style.relief) return this
    val radius = when {
        // Mica rounds everything a little, and turns the app bar's round buttons into rounded squares too.
        surface.style.layers && (corner == null || corner == PillCorner) ->
            if (small) LayerCorner.Control else LayerCorner.Card
        else -> corner ?: 0.dp
    }
    return drawBehind { drawRelief(surface, relief, size, radius.toPx(), fill, small) }
}

private fun DrawScope.drawRelief(
    surface: MetroSurface,
    relief: Relief,
    size: Size,
    radius: Float,
    fill: Color,
    small: Boolean
) {
    if (size.width <= 0f || size.height <= 0f) return
    if (surface.style.layers) {
        drawLayer(surface, relief, size, radius, fill)
        return
    }
    val scale = if (small) 0.5f else 1f
    val depth = surface.depth.toPx() * scale
    val blur = (surface.blur.toPx() * scale).coerceAtLeast(1f)
    val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
    val corner = CornerRadius(r)
    when (relief) {
        Relief.Raised -> {
            drawIntoCanvas { canvas ->
                for ((color, shift) in listOf(surface.shade to depth, surface.highlight to -depth)) {
                    val rect = RectF(shift, shift, size.width + shift, size.height + shift)
                    canvas.nativeCanvas.drawRoundRect(rect, r, r, blurred(color, blur))
                }
            }
            drawRoundRect(fill, size = size, cornerRadius = corner)
        }
        Relief.Inset -> {
            drawRoundRect(fill, size = size, cornerRadius = corner)
            val shape = ComposePath().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
            clipPath(shape) {
                drawIntoCanvas { canvas ->
                    val pad = blur * 3 + depth
                    for ((color, shift) in listOf(surface.shade to depth, surface.highlight to -depth)) {
                        // A frame around the shape, moved by the light, so only its soft inner edge shows.
                        val frame = android.graphics.Path().apply {
                            fillType = android.graphics.Path.FillType.EVEN_ODD
                            addRect(-pad, -pad, size.width + pad, size.height + pad, android.graphics.Path.Direction.CW)
                            addRoundRect(
                                RectF(shift, shift, size.width + shift, size.height + shift),
                                r,
                                r,
                                android.graphics.Path.Direction.CW
                            )
                        }
                        canvas.nativeCanvas.drawPath(frame, blurred(color, blur * 0.6f))
                    }
                }
            }
        }
    }
}

/**
 * Mica's version of [drawRelief]: the [MetroSurface.layer] card (or [fill], when it is not the page, such as an
 * accent) edged by a hairline. Inset is a field: the same card with a line under it, as Windows 11 draws a text box.
 */
private fun DrawScope.drawLayer(surface: MetroSurface, relief: Relief, size: Size, radius: Float, fill: Color) {
    val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
    val corner = CornerRadius(r)
    val onPage = fill == surface.page
    drawRoundRect(if (onPage) surface.layer else fill, size = size, cornerRadius = corner)
    if (!onPage) return
    val hair = 1.dp.toPx()
    drawRoundRect(
        surface.stroke,
        Offset(hair / 2, hair / 2),
        Size(size.width - hair, size.height - hair),
        CornerRadius((r - hair / 2).coerceAtLeast(0f)),
        style = Stroke(hair)
    )
    if (relief == Relief.Inset) {
        val shape = ComposePath().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
        clipPath(shape) {
            drawRect(surface.underline, Offset(0f, size.height - hair), Size(size.width, hair))
        }
    }
}

/** Mica's mark of a field in use: a 2dp accent line along the bottom, over the resting underline. */
@Composable
fun Modifier.layerFocusLine(on: Boolean): Modifier {
    val accent = MetroTheme.accent.fill
    return drawWithContent {
        drawContent()
        if (on) {
            val line = 2.dp.toPx()
            val inset = LayerCorner.Control.toPx() / 2
            drawRect(accent, Offset(inset, size.height - line), Size(size.width - 2 * inset, line))
        }
    }
}

/**
 * Mica's check box, as Windows 11 draws one: a square rounded by 4dp, a faint fill edged by a strong 1dp line
 * when clear, the accent with a tick when [checked].
 */
@Composable
fun LayerCheckBox(checked: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val dark = MetroTheme.colors.isDark
    val accent = MetroTheme.accent.fill
    val tick = MetroTheme.accent.onFill
    val wash = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f)
    val edge = if (!enabled) {
        MetroTheme.colors.outline
    } else if (dark) {
        Color.White.copy(alpha = 0.6f)
    } else {
        Color.Black.copy(alpha = 0.6f)
    }
    Canvas(modifier) {
        val corner = CornerRadius(4.dp.toPx())
        if (checked) {
            drawRoundRect(accent, cornerRadius = corner)
            val w = size.width
            val h = size.height
            val path = ComposePath().apply {
                moveTo(w * 0.26f, h * 0.52f)
                lineTo(w * 0.43f, h * 0.68f)
                lineTo(w * 0.75f, h * 0.34f)
            }
            drawPath(path, tick, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val hair = 1.dp.toPx()
            drawRoundRect(wash, cornerRadius = corner)
            drawRoundRect(
                edge,
                Offset(hair / 2, hair / 2),
                Size(size.width - hair, size.height - hair),
                CornerRadius(4.dp.toPx() - hair / 2),
                style = Stroke(hair)
            )
        }
    }
}

/** A floating acrylic sheet (mica's app bar): the acrylic tint with a soft drop shadow, rounded and edged. */
fun Modifier.acrylicSheet(surface: MetroSurface): Modifier = drawBehind {
    val r = LayerCorner.Floating.toPx()
    drawIntoCanvas { canvas ->
        val rect = RectF(0f, surface.depth.toPx(), size.width, size.height + surface.depth.toPx())
        canvas.nativeCanvas.drawRoundRect(rect, r, r, blurred(surface.shade, surface.blur.toPx()))
    }
    drawRoundRect(surface.acrylic, cornerRadius = CornerRadius(r))
    val hair = 1.dp.toPx()
    drawRoundRect(
        surface.stroke,
        Offset(hair / 2, hair / 2),
        Size(size.width - hair, size.height - hair),
        CornerRadius(r - hair / 2),
        style = Stroke(hair)
    )
}

private fun blurred(color: Color, blur: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
}
