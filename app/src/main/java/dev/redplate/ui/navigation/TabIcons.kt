package dev.redplate.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The tab icons, drawn rather than imported.
 *
 * Material's icon set would be a dependency for four glyphs, and its rounded, filled
 * house-and-clock idiom is the consumer-app look CLAUDE.md §3 rules out. These are stroked
 * at a constant weight and derive from the subject the way the rest of the app does: a
 * loaded bar, a week grid, a progress line, a plate.
 *
 * Every icon draws inside a 24 dp box so the four sit on the same optical baseline.
 */
private val ICON_SIZE = 24.dp
private val STROKE = 1.8.dp

@Composable
fun TodayIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON_SIZE)) {
        val stroke = STROKE.toPx()
        val mid = size.height / 2f
        // A loaded bar: sleeve, a plate each side, collar-to-collar shaft.
        drawLine(tint, Offset(0f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
        plate(tint, x = size.width * 0.22f, height = size.height * 0.86f, stroke = stroke)
        plate(tint, x = size.width * 0.78f, height = size.height * 0.86f, stroke = stroke)
        plate(tint, x = size.width * 0.36f, height = size.height * 0.54f, stroke = stroke)
        plate(tint, x = size.width * 0.64f, height = size.height * 0.54f, stroke = stroke)
    }
}

@Composable
fun PlanIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON_SIZE)) {
        val stroke = STROKE.toPx()
        val inset = size.width * 0.08f
        val top = size.height * 0.16f
        val boxSize = Size(size.width - inset * 2, size.height - top - inset)

        drawRoundRectStroke(tint, Offset(inset, top), boxSize, stroke)
        // The header rule, then two of the week's days filled in.
        val headerY = top + boxSize.height * 0.3f
        drawLine(
            tint,
            Offset(inset, headerY),
            Offset(inset + boxSize.width, headerY),
            stroke,
        )
        val cell = boxSize.width / 4f
        val dotY = headerY + boxSize.height * 0.34f
        drawCircle(tint, radius = stroke * 1.1f, center = Offset(inset + cell * 0.9f, dotY))
        drawCircle(tint, radius = stroke * 1.1f, center = Offset(inset + cell * 3.1f, dotY))
    }
}

@Composable
fun HistoryIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON_SIZE)) {
        val stroke = STROKE.toPx()
        val left = size.width * 0.12f
        val bottom = size.height * 0.84f

        // Axes, then a climbing line — the estimated-1RM curve, which is what History is.
        drawLine(tint, Offset(left, size.height * 0.12f), Offset(left, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left, bottom), Offset(size.width * 0.9f, bottom), stroke, StrokeCap.Round)

        val points = listOf(
            Offset(size.width * 0.26f, size.height * 0.66f),
            Offset(size.width * 0.46f, size.height * 0.48f),
            Offset(size.width * 0.64f, size.height * 0.54f),
            Offset(size.width * 0.84f, size.height * 0.26f),
        )
        points.zipWithNext { a, b -> drawLine(tint, a, b, stroke, StrokeCap.Round) }
        drawCircle(tint, radius = stroke * 1.3f, center = points.last())
    }
}

@Composable
fun YouIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON_SIZE)) {
        val stroke = STROKE.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        // A calibrated plate seen face on: rim, hub, and the bar through it.
        drawCircle(tint, radius = size.minDimension * 0.38f, center = centre, style = Stroke(stroke))
        drawCircle(tint, radius = size.minDimension * 0.12f, center = centre, style = Stroke(stroke))
        drawLine(
            tint,
            Offset(0f, centre.y),
            Offset(size.width * 0.12f, centre.y),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(size.width * 0.88f, centre.y),
            Offset(size.width, centre.y),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** One plate on the bar, drawn as a vertical capsule. */
private fun DrawScope.plate(tint: Color, x: Float, height: Float, stroke: Float) {
    val half = height / 2f
    drawLine(
        tint,
        Offset(x, size.height / 2f - half),
        Offset(x, size.height / 2f + half),
        stroke * 1.4f,
        StrokeCap.Round,
    )
}

private fun DrawScope.drawRoundRectStroke(
    tint: Color,
    topLeft: Offset,
    boxSize: Size,
    stroke: Float,
) {
    drawRoundRect(
        color = tint,
        topLeft = topLeft,
        size = boxSize,
        cornerRadius = CornerRadius(stroke * 2f, stroke * 2f),
        style = Stroke(stroke),
    )
}
