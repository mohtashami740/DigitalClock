package com.minimal.digitalclock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
/**
 * How segments are drawn. [thickness] is relative to the digit height; [led] adds the glowing
 * LED-behind-a-diffuser look (bright middle, dimmer ends, soft edges, grain and a glow around).
 */
data class SegmentLook(val thickness: Float, val led: Boolean = false) {
    companion object {
        val THIN = SegmentLook(0.085f)
        val THICK = SegmentLook(0.13f)
        val LED = SegmentLook(0.13f, led = true)
    }
}
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
    /** > 0: draw only a blurred copy of the lit segments (used for glow/halo layers). */
    blurPx: Float = 0f,
    look: SegmentLook = SegmentLook.THIN,
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
        // Collect every segment first, so the LED look can draw them in layers.
        val lit = mutableListOf<Seg>(); val unlit = mutableListOf<Seg>()
        var x = 0f
        for (c in text) {
            when {
                c.isDigit() -> digitSegments(c - '0', Offset(x, top), digitH, look.thickness, skew, lit, unlit)
                c == ':' -> colonSegments(Offset(x, top), digitH, look.thickness, skew, lit)
            }
            x += charWidth(c, digitH) + SPACING * digitH
        }
        val t = digitH * look.thickness
        when {
            blurPx > 0f -> lit.forEach { fill(it.path, color, blurPx) }
            look.led -> drawLed(lit, unlit, color, unlitAlpha, t)
            else -> {
                if (unlitAlpha > 0f) unlit.forEach { drawPath(it.path, color.copy(alpha = color.alpha * unlitAlpha)) }
                lit.forEach { drawPath(it.path, color) }
            }
        }
    }
}

/** One segment: its outline and the two ends of its axis (for the LED brightness gradient). */
private class Seg(val path: Path, val from: Offset, val to: Offset)

private fun digitSegments(
    digit: Int, origin: Offset, h: Float, thickness: Float, skew: (Offset) -> Offset,
    lit: MutableList<Seg>, unlit: MutableList<Seg>,
) {
    val w = h * DIGIT_WIDTH
    val t = h * thickness
    val gap = t * 0.18f
    val l = origin.x + t / 2; val r = origin.x + w - t / 2
    val tp = origin.y + t / 2; val m = origin.y + h / 2; val b = origin.y + h - t / 2
    val on = DIGIT_SEGMENTS[digit]
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
        val seg = Seg(segmentPath(ends.first, ends.second, t, gap, skew), skew(ends.first), skew(ends.second))
        if (name in on) lit += seg else unlit += seg
    }
}

private fun colonSegments(origin: Offset, h: Float, thickness: Float, skew: (Offset) -> Offset, lit: MutableList<Seg>) {
    val t = h * thickness * 1.1f
    val cx = origin.x + COLON_WIDTH * h / 2
    for (fy in floatArrayOf(0.30f, 0.70f)) {
        val l = cx - t / 2; val r = cx + t / 2
        val tp = origin.y + h * fy - t / 2; val b = tp + t
        val c = skew(Offset(cx, tp + t / 2))
        lit += Seg(polygon(listOf(Offset(l, tp), Offset(r, tp), Offset(r, b), Offset(l, b)).map(skew)), c, c)
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

/** Fills [path]; with [blurPx] > 0 draws a soft, blurred version instead (for glow layers). */
private fun DrawScope.fill(path: Path, color: Color, blurPx: Float) {
    if (blurPx <= 0f) {
        drawPath(path, color)
        return
    }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

// ---- LED look --------------------------------------------------------------------------------

private fun Color.withAlpha(f: Float) = copy(alpha = alpha * f)

/** A small tile of soft, blotchy alpha (0.68..1) used as the diffuser grain inside lit segments. */
private val grainTile: android.graphics.Bitmap by lazy {
    val n = 16
    val rnd = java.util.Random(5)
    val pixels = IntArray(n * n) { ((0.68f + 0.32f * rnd.nextFloat()) * 255).toInt() shl 24 or 0xFFFFFF }
    val small = android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    // Two smooth upscaling steps give soft blotches rather than square cells.
    val mid = android.graphics.Bitmap.createScaledBitmap(small, n * 2, n * 2, true)
    android.graphics.Bitmap.createScaledBitmap(mid, n * 8, n * 8, true)
}

/**
 * Draws lit segments like LEDs behind a plastic diffuser: a wide glow behind, a fill that is
 * brighter in the middle and dimmer at the ends with soft edges and grain, and a hot core.
 */
private fun DrawScope.drawLed(lit: List<Seg>, unlit: List<Seg>, color: Color, unlitAlpha: Float, t: Float) {
    fun paint(blur: Float) = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG, // smooth, not blocky, grain
    ).apply {
        if (blur > 0f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        // Light spilling into the panel around the digits.
        val wide = paint(t * 3.2f).apply { this.color = color.withAlpha(0.20f).toArgb() }
        val near = paint(t * 1.4f).apply { this.color = color.withAlpha(0.45f).toArgb() }
        lit.forEach { canvas.drawPath(it.path.asAndroidPath(), wide) }
        lit.forEach { canvas.drawPath(it.path.asAndroidPath(), near) }
        // Unlit segments, sharp and faint.
        if (unlitAlpha > 0f) {
            val ghost = paint(0f).apply { this.color = color.withAlpha(unlitAlpha).toArgb() }
            unlit.forEach { canvas.drawPath(it.path.asAndroidPath(), ghost) }
        }
        val grain = android.graphics.BitmapShader(grainTile, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            .apply { setLocalMatrix(android.graphics.Matrix().apply { val s = t * 0.08f; setScale(s, s) }) }
        val hot = lerp(color, Color.White, 0.5f)
        for (seg in lit) {
            val (x0, y0) = seg.from; val (x1, y1) = seg.to
            val along = x0 != x1 || y0 != y1
            // Brightness along the segment: dimmer ends, full in the middle.
            val ends = color.withAlpha(if (along) 0.5f else 1f).toArgb()
            val gradient = android.graphics.LinearGradient(
                x0, y0, if (along) x1 else x0 + 1f, if (along) y1 else y0,
                intArrayOf(ends, color.toArgb(), ends), floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            val body = paint(t * (if (along) 0.10f else 0.22f)).apply {
                shader = android.graphics.ComposeShader(gradient, grain, android.graphics.PorterDuff.Mode.DST_IN)
            }
            canvas.drawPath(seg.path.asAndroidPath(), body)
            // Over-driven, slightly whitened centre.
            val core = paint(t * 0.35f).apply {
                shader = android.graphics.LinearGradient(
                    x0, y0, if (along) x1 else x0 + 1f, if (along) y1 else y0,
                    intArrayOf(android.graphics.Color.TRANSPARENT, hot.withAlpha(0.55f).toArgb(), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0.1f, 0.5f, 0.9f), android.graphics.Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(seg.path.asAndroidPath(), core)
        }
    }
}
