package com.minimal.digitalclock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/*
 * A 7-segment display drawn with Canvas. Sizes are given as a "font size" in pixels so it can
 * replace text 1:1 in the clock layout: the box is fontPx tall (like a line with 1.0em line
 * height) and exposes a baseline at the bottom of the digits, so Row.alignBy(LastBaseline) works.
 *
 * Segment names:     a
 *                  f   b
 *                    g
 *                  e   c
 *                    d
 */

private const val DIGIT_HEIGHT = 0.74f   // digit height relative to the font size (≈ cap height)
private const val DIGIT_WIDTH = 0.50f    // digit width relative to digit height
private const val THICKNESS = 0.085f     // segment thickness relative to digit height
private const val SPACING = 0.16f        // gap between characters relative to digit height
private const val COLON_WIDTH = 0.14f    // colon advance relative to digit height (plus spacing)
const val DEFAULT_SLANT_DEGREES = 10f    // italic slant of the digits

private val DIGIT_SEGMENTS = arrayOf(
    "abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg",
)

private fun charWidth(c: Char, digitH: Float): Float = when (c) {
    ':' -> COLON_WIDTH * digitH
    ' ' -> DIGIT_WIDTH * digitH
    else -> DIGIT_WIDTH * digitH
}

private fun slantFactor(degrees: Float) = kotlin.math.tan(Math.toRadians(degrees.toDouble())).toFloat()

/** Width in pixels of [text] drawn at [fontPx] with [slantDegrees] italic slant. */
fun segmentTextWidth(text: String, fontPx: Float, slantDegrees: Float = DEFAULT_SLANT_DEGREES): Float {
    if (text.isEmpty()) return 0f
    val d = fontPx * DIGIT_HEIGHT
    return text.sumOf { charWidth(it, d).toDouble() }.toFloat() + SPACING * d * (text.length - 1) +
        d * slantFactor(slantDegrees) // the top of the last character leans out by this much
}

@Composable
fun SegmentText(
    text: String,
    fontPx: Float,
    color: Color,
    modifier: Modifier = Modifier,
    unlitAlpha: Float = 0f,
    slantDegrees: Float = DEFAULT_SLANT_DEGREES,
) {
    val digitH = fontPx * DIGIT_HEIGHT
    val top = (fontPx - digitH) / 2f
    val width = segmentTextWidth(text, fontPx, slantDegrees)
    // Italic: shift every point right in proportion to its height above the digit baseline.
    val k = slantFactor(slantDegrees)
    val baseY = top + digitH
    val skew = { p: Offset -> Offset(p.x + (baseY - p.y) * k, p.y) }
    Canvas(
        modifier
            .layout { measurable, _ ->
                val w = width.roundToInt(); val h = fontPx.roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                val baseline = (top + digitH).roundToInt()
                layout(w, h, mapOf(FirstBaseline to baseline, LastBaseline to baseline)) {
                    placeable.place(0, 0)
                }
            }
    ) {
        var x = 0f
        for (c in text) {
            when {
                c.isDigit() -> drawDigit(c - '0', Offset(x, top), digitH, color, unlitAlpha, skew)
                c == ':' -> drawColon(Offset(x, top), digitH, color, skew)
            }
            x += charWidth(c, digitH) + SPACING * digitH
        }
    }
}

private fun DrawScope.drawDigit(
    digit: Int, origin: Offset, h: Float, color: Color, unlitAlpha: Float, skew: (Offset) -> Offset,
) {
    val w = h * DIGIT_WIDTH
    val t = h * THICKNESS
    val gap = t * 0.18f
    val l = origin.x + t / 2; val r = origin.x + w - t / 2
    val tp = origin.y + t / 2; val m = origin.y + h / 2; val b = origin.y + h - t / 2
    val lit = DIGIT_SEGMENTS[digit]
    val segments = mapOf(
        'a' to (Offset(l, tp) to Offset(r, tp)),
        'g' to (Offset(l, m) to Offset(r, m)),
        'd' to (Offset(l, b) to Offset(r, b)),
        'f' to (Offset(l, tp) to Offset(l, m)),
        'b' to (Offset(r, tp) to Offset(r, m)),
        'e' to (Offset(l, m) to Offset(l, b)),
        'c' to (Offset(r, m) to Offset(r, b)),
    )
    for ((name, ends) in segments) {
        val on = name in lit
        if (!on && unlitAlpha <= 0f) continue
        drawPath(segmentPath(ends.first, ends.second, t, gap, skew), if (on) color else color.copy(alpha = color.alpha * unlitAlpha))
    }
}

/** A hexagonal segment between two corner points (pointed ends, like a real LED/LCD segment). */
private fun segmentPath(p0: Offset, p1: Offset, t: Float, gap: Float, skew: (Offset) -> Offset): Path {
    val half = t / 2
    val pts = if (p0.y == p1.y) { // horizontal
        val x0 = p0.x + gap; val x1 = p1.x - gap; val y = p0.y
        listOf(Offset(x0, y), Offset(x0 + half, y - half), Offset(x1 - half, y - half),
            Offset(x1, y), Offset(x1 - half, y + half), Offset(x0 + half, y + half))
    } else { // vertical
        val y0 = p0.y + gap; val y1 = p1.y - gap; val x = p0.x
        listOf(Offset(x, y0), Offset(x + half, y0 + half), Offset(x + half, y1 - half),
            Offset(x, y1), Offset(x - half, y1 - half), Offset(x - half, y0 + half))
    }
    return polygon(pts.map(skew))
}

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}

private fun DrawScope.drawColon(origin: Offset, h: Float, color: Color, skew: (Offset) -> Offset) {
    val t = h * THICKNESS * 1.1f
    val cx = origin.x + COLON_WIDTH * h / 2
    for (fy in floatArrayOf(0.30f, 0.70f)) {
        val l = cx - t / 2; val r = cx + t / 2
        val tp = origin.y + h * fy - t / 2; val b = tp + t
        drawPath(polygon(listOf(Offset(l, tp), Offset(r, tp), Offset(r, b), Offset(l, b)).map(skew)), color)
    }
}
