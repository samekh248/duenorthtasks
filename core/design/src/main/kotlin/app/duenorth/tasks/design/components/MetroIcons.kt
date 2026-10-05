package app.duenorth.tasks.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.MetroTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's glyphs, drawn as thin flat strokes like the WP8.1 app bar icons. Drawn in code so the
 * design module needs no Material icon library.
 */
enum class MetroIcon { Add, Sync, Search, Settings, Check, Delete, Edit, Sort, Close, More, Back, Star }

@Composable
fun MetroIconGlyph(
    icon: MetroIcon,
    modifier: Modifier = Modifier,
    color: Color = MetroTheme.colors.foreground,
    size: Dp = 24.dp,
    /** Fills closed shapes (the star when a task is important) instead of outlining them. */
    filled: Boolean = false
) {
    Canvas(modifier.size(size)) {
        drawGlyph(icon, color, filled)
    }
}

/** Glyphs are designed on a 24 x 24 grid and scaled to the canvas. */
internal fun DrawScope.drawGlyph(icon: MetroIcon, color: Color, filled: Boolean = false) {
    val u = this.size.minDimension / 24f
    val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Square)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = if (filled) Fill else stroke)

    when (icon) {
        MetroIcon.Add -> {
            line(12f, 4f, 12f, 20f)
            line(4f, 12f, 20f, 12f)
        }

        MetroIcon.Close -> {
            line(5f, 5f, 19f, 19f)
            line(19f, 5f, 5f, 19f)
        }

        MetroIcon.Check -> path {
            moveTo(4f * u, 12.5f * u)
            lineTo(9.5f * u, 18f * u)
            lineTo(20f * u, 6.5f * u)
        }

        MetroIcon.Back -> {
            line(4f, 12f, 20f, 12f)
            path {
                moveTo(10f * u, 6f * u)
                lineTo(4f * u, 12f * u)
                lineTo(10f * u, 18f * u)
            }
        }

        MetroIcon.Search -> {
            drawCircle(color, radius = 6f * u, center = p(14f, 10f), style = stroke)
            line(9.8f, 14.2f, 4f, 20f)
        }

        MetroIcon.Sync -> {
            drawArc(color, 200f, 150f, false, topLeft = p(4f, 4f), size = Size(16f * u, 16f * u), style = stroke)
            drawArc(color, 20f, 150f, false, topLeft = p(4f, 4f), size = Size(16f * u, 16f * u), style = stroke)
            // Arrow heads at the end of each arc.
            path {
                moveTo(16.5f * u, 3.5f * u)
                lineTo(19.5f * u, 5.5f * u)
                lineTo(17.5f * u, 8.5f * u)
            }
            path {
                moveTo(7.5f * u, 20.5f * u)
                lineTo(4.5f * u, 18.5f * u)
                lineTo(6.5f * u, 15.5f * u)
            }
        }

        MetroIcon.Settings -> {
            drawCircle(color, radius = 3f * u, center = p(12f, 12f), style = stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val inner = 6f
                val outer = 9f
                line(
                    12f + inner * cos(a).toFloat(),
                    12f + inner * sin(a).toFloat(),
                    12f + outer * cos(a).toFloat(),
                    12f + outer * sin(a).toFloat()
                )
            }
            drawCircle(color, radius = 6f * u, center = p(12f, 12f), style = stroke)
        }

        MetroIcon.Delete -> {
            line(4f, 6f, 20f, 6f)
            line(9f, 6f, 9f, 3.5f)
            line(9f, 3.5f, 15f, 3.5f)
            line(15f, 3.5f, 15f, 6f)
            path {
                moveTo(6f * u, 6f * u)
                lineTo(7f * u, 20.5f * u)
                lineTo(17f * u, 20.5f * u)
                lineTo(18f * u, 6f * u)
            }
            line(10f, 10f, 10f, 17f)
            line(14f, 10f, 14f, 17f)
        }

        MetroIcon.Edit -> {
            path {
                moveTo(15.5f * u, 4.5f * u)
                lineTo(19.5f * u, 8.5f * u)
                lineTo(8.5f * u, 19.5f * u)
                lineTo(4.5f * u, 19.5f * u)
                lineTo(4.5f * u, 15.5f * u)
                close()
            }
            line(13f, 7f, 17f, 11f)
        }

        MetroIcon.Sort -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 12f, 15f, 12f)
            line(4f, 17f, 10f, 17f)
        }

        MetroIcon.More -> {
            for (x in listOf(6f, 12f, 18f)) drawCircle(color, radius = 1.4f * u, center = p(x, 12f))
        }

        MetroIcon.Star -> path {
            val points = (0 until 10).map { i ->
                val r = if (i % 2 == 0) 9f else 3.8f
                val a = Math.toRadians(-90.0 + i * 36.0)
                Offset((12f + r * cos(a).toFloat()) * u, (12.5f + r * sin(a).toFloat()) * u)
            }
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    }
}
