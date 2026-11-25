package com.minimal.digitalclock

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Still (non-animated) looks applied to the whole clock. */
enum class VisualEffect(val label: String, val description: String) {
    NONE("None", "Clean, sharp digits"),
    HALO("Halo", "Soft light glowing around the digits"),
    DIFFUSION("Diffusion", "Light spreading softly, as through frosted glass"),
    FOG("Foggy glass", "Digits seen through a misted-up window"),
    DIRTY("Dirty glass", "Dust, smudges and streaks on the glass"),
    CRACKED("Cracked glass", "A shattered pane in front of the clock"),
    TINTED("Tinted glass", "Coloured glass in front of the clock"),
    REFLECTIVE("Reflective glass", "Glossy glass with streaks of reflected light"),
}

/** Tint presets for [VisualEffect.TINTED]. */
enum class GlassTint(val label: String, val argb: Int) {
    SMOKE("Smoke", 0xFF8A8A8A.toInt()),
    BRONZE("Bronze", 0xFFD9A066.toInt()),
    BLUE("Blue", 0xFF6FA3E0.toInt()),
    GREEN("Green", 0xFF7CC48A.toInt()),
    ROSE("Rose", 0xFFE08A9A.toInt()),
}

/** A blurred copy of the content. [relRadius] is relative to the element's font size. */
data class GlowLayer(val relRadius: Float, val alpha: Float)

/** Glow layers drawn behind, and the opacity of the sharp content drawn on top (0 = none). */
class EffectRecipe(val glows: List<GlowLayer>, val coreAlpha: Float)

/**
 * Glow layers for an effect at [amount] (0 = subtle, 1 = strong; 0.5 is the default look).
 * Bigger amounts give wider, brighter glows and (for diffusion/fog) less of the sharp digit.
 */
fun VisualEffect.recipe(amount: Float): EffectRecipe {
    val t = amount.coerceIn(0f, 1f)
    val size = 0.5f + t                      // 0.5x .. 1.5x the default radius
    fun g(r: Float, a: Float) = GlowLayer(r * size, (a * (0.5f + t)).coerceAtMost(1f))
    return when (this) {
        VisualEffect.HALO -> EffectRecipe(listOf(g(0.16f, 0.55f), g(0.05f, 0.75f)), coreAlpha = 1f)
        VisualEffect.DIFFUSION -> EffectRecipe(
            listOf(g(0.25f, 0.35f), g(0.08f, 0.7f), g(0.025f, 0.9f)), coreAlpha = 0.85f - 0.8f * t,
        )
        VisualEffect.FOG -> EffectRecipe(
            listOf(g(0.30f, 0.35f), g(0.10f, 0.7f), g(0.04f, 0.9f)), coreAlpha = (0.6f - 1.2f * t).coerceAtLeast(0f),
        )
        VisualEffect.DIRTY -> EffectRecipe(listOf(g(0.08f, 0.3f)), coreAlpha = 1f)
        else -> EffectRecipe(emptyList(), 1f) // NONE and the pure glass effects
    }
}

/**
 * Draws [content] once per glow layer and once sharp, stacked in a Box. Every copy has the same
 * layout, so the Box keeps the content's size and baselines.
 * [content] receives the glow layer (null for the sharp copy) and the opacity to draw with.
 */
@Composable
fun EffectLayers(
    effect: VisualEffect,
    amount: Float,
    modifier: Modifier = Modifier,
    content: @Composable (glow: GlowLayer?, alpha: Float) -> Unit,
) {
    val recipe = effect.recipe(amount)
    Box(modifier) {
        recipe.glows.forEach { content(it, it.alpha) }
        // The sharp copy is always composed (it may be invisible) so the size never depends on the effect.
        content(null, recipe.coreAlpha)
    }
}

/** Text style for one effect layer: either sharp text at [alpha], or only its glow. */
fun TextStyle.forLayer(glow: GlowLayer?, alpha: Float, fontPx: Float): TextStyle {
    val c = color
    return if (glow == null) copy(color = c.copy(alpha = c.alpha * alpha))
    else copy(
        color = Color.Transparent,
        // A non-opaque shadow colour keeps its own alpha even though the text itself is transparent.
        shadow = Shadow(c.copy(alpha = min(0.99f, c.alpha * alpha)), Offset.Zero, glow.relRadius * fontPx),
    )
}

/** Opacity of a glass texture at [amount]: 0.15 (subtle) .. 1 (strong), about 0.6 by default. */
private fun overlayAlpha(amount: Float) = 0.15f + 0.85f * amount.coerceIn(0f, 1f)

/**
 * The glass in front of the clock (fog, dirt, cracks, tint or reflections); nothing for the other
 * effects. [amount] and [tint] are read while drawing, so dragging the amount slider only redraws.
 * [shift] moves textures slightly (burn-in protection), so they are generated a bit larger than
 * the screen.
 */
@Composable
fun GlassOverlay(
    effect: VisualEffect,
    modifier: Modifier = Modifier,
    amount: () -> Float = { 0.5f },
    tint: () -> Color = { Color(GlassTint.BRONZE.argb) },
    shift: () -> IntOffset = { IntOffset.Zero },
) {
    when (effect) {
        VisualEffect.FOG, VisualEffect.DIRTY, VisualEffect.CRACKED -> Spacer(
            modifier.drawWithCache {
                val margin = 0.04f
                val w = (size.width * (1 + 2 * margin)).roundToInt().coerceAtLeast(1)
                val h = (size.height * (1 + 2 * margin)).roundToInt().coerceAtLeast(1)
                val image = when (effect) {
                    VisualEffect.FOG -> fogBitmap(w, h)
                    VisualEffect.DIRTY -> dirtBitmap(w, h)
                    else -> crackBitmap(w, h)
                }.asImageBitmap()
                val origin = Offset(-size.width * margin, -size.height * margin)
                onDrawBehind {
                    val s = shift()
                    drawImage(
                        image,
                        dstOffset = IntOffset((origin.x + s.x).roundToInt(), (origin.y + s.y).roundToInt()),
                        dstSize = IntSize(w, h),
                        alpha = overlayAlpha(amount()),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        )
        VisualEffect.TINTED -> Spacer(
            modifier.drawBehind {
                val t = amount().coerceIn(0f, 1f)
                // Multiply colours what is behind (white digits take the tint, black stays black),
                // then a faint wash makes the glass itself visible.
                drawRect(tint(), alpha = 0.35f + 0.65f * t, blendMode = BlendMode.Multiply)
                drawRect(tint(), alpha = 0.02f + 0.08f * t)
            }
        )
        VisualEffect.REFLECTIVE -> Spacer(
            modifier.drawBehind {
                val a = overlayAlpha(amount())
                val w = size.width; val h = size.height
                val white = Color.White
                // Soft light from above, as if the glass reflects a bright room.
                drawRect(Brush.verticalGradient(
                    0f to white.copy(alpha = 0.16f * a), 0.45f to white.copy(alpha = 0.03f * a), 1f to Color.Transparent,
                ))
                // Diagonal sheen: one broad band and two narrow streaks.
                fun band(center: Float, width: Float, peak: Float) {
                    val d = (w + h) * width
                    val c = Offset(w * center, h * center * 0.6f)
                    drawRect(Brush.linearGradient(
                        0f to Color.Transparent, 0.5f to white.copy(alpha = peak * a), 1f to Color.Transparent,
                        start = Offset(c.x - d, c.y - d), end = Offset(c.x + d, c.y + d),
                    ))
                }
                band(0.30f, 0.16f, 0.12f)
                band(0.62f, 0.025f, 0.16f)
                band(0.70f, 0.012f, 0.12f)
                // Slightly brighter edges, like the rim of a glass pane.
                drawRect(Brush.radialGradient(
                    0.6f to Color.Transparent, 1f to white.copy(alpha = 0.06f * a),
                    center = Offset(w / 2, h / 2), radius = maxOf(w, h) * 0.75f,
                ))
            }
        )
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// Procedural textures. Fixed seeds: the glass looks the same every time.

/** Misty haze with uneven density (fractal value noise), smoothly upscaled. */
private fun fogBitmap(w: Int, h: Int): Bitmap {
    val scale = 6
    val lw = (w / scale).coerceAtLeast(2); val lh = (h / scale).coerceAtLeast(2)
    val pixels = IntArray(lw * lh)
    val cell = min(lw, lh) / 3f // size of the largest fog patches
    for (y in 0 until lh) for (x in 0 until lw) {
        val n = fbm(x / cell, y / cell)                          // 0..1
        val density = (0.34f + 0.28f * smooth(0.2f, 0.8f, n))    // fairly even mist, gently thicker in places
        val a = (density * 255).roundToInt().coerceIn(0, 255)
        pixels[y * lw + x] = (a shl 24) or 0xD4DAE0              // pale blue-grey mist
    }
    val small = Bitmap.createBitmap(pixels, lw, lh, Bitmap.Config.ARGB_8888)
    // Fine condensation droplets on top of the haze.
    val out = Bitmap.createScaledBitmap(small, w, h, true)
    val canvas = android.graphics.Canvas(out)
    val rnd = Random(7)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val unit = min(w, h) / 400f
    // Soft droplets: a few, blurred, barely brighter than the mist (not star-like points).
    repeat((w * h / 40000).coerceAtMost(120)) {
        val r = unit * (1.5f + rnd.nextFloat() * 3f)
        paint.color = android.graphics.Color.argb(30 + rnd.nextInt(30), 230, 236, 240)
        paint.maskFilter = BlurMaskFilter(r * 0.7f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, paint)
    }
    return out
}

/** Smudges, wipe streaks, dust, water spots and fine scratches. */
private fun dirtBitmap(w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val rnd = Random(11)
    val m = min(w, h).toFloat()
    val grime = intArrayOf(220, 218, 205) // warm light grey, as dirt looks when lit

    // Drawn ~1.7x stronger than the default look; the amount slider scales it down.
    fun argb(a: Int) = android.graphics.Color.argb((a * 1.7f).toInt().coerceIn(0, 255), grime[0], grime[1], grime[2])

    // Smudges: large, very soft blotches.
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(14) {
        val cx = rnd.nextFloat() * w; val cy = rnd.nextFloat() * h
        val r = m * (0.08f + rnd.nextFloat() * 0.25f)
        paint.shader = RadialGradient(cx, cy, r, argb(18 + rnd.nextInt(24)), argb(0), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
    }
    paint.shader = null

    // Wipe streaks: wide, faint arcs left by a cloth.
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    repeat(6) {
        stroke.strokeWidth = m * (0.03f + rnd.nextFloat() * 0.05f)
        stroke.color = argb(10 + rnd.nextInt(10))
        stroke.maskFilter = BlurMaskFilter(stroke.strokeWidth * 0.6f, BlurMaskFilter.Blur.NORMAL)
        val path = android.graphics.Path().apply {
            val x0 = rnd.nextFloat() * w; val y0 = rnd.nextFloat() * h
            moveTo(x0, y0)
            quadTo(
                x0 + (rnd.nextFloat() - 0.5f) * m, y0 + (rnd.nextFloat() - 0.5f) * m,
                x0 + (rnd.nextFloat() - 0.5f) * m * 1.4f, y0 + (rnd.nextFloat() - 0.5f) * m * 1.4f,
            )
        }
        canvas.drawPath(path, stroke)
    }
    stroke.maskFilter = null

    // Water spots: faint rings.
    stroke.strokeWidth = m / 700f
    repeat(28) {
        stroke.color = argb(22 + rnd.nextInt(30))
        canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, m * (0.004f + rnd.nextFloat() * 0.012f), stroke)
    }

    // Fine scratches.
    stroke.strokeWidth = m / 900f
    repeat(9) {
        stroke.color = argb(30 + rnd.nextInt(35))
        val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
        val len = m * (0.04f + rnd.nextFloat() * 0.14f)
        val angle = rnd.nextFloat() * Math.PI.toFloat()
        canvas.drawLine(x, y, x + len * kotlin.math.cos(angle), y + len * kotlin.math.sin(angle), stroke)
    }

    // Dust: many tiny specks, a few larger soft ones.
    val dust = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat((w * h / 9000).coerceAtMost(450)) {
        val big = rnd.nextFloat() < 0.12f
        val r = m / 1000f * (if (big) 2.5f + rnd.nextFloat() * 3f else 0.6f + rnd.nextFloat() * 1.2f)
        dust.color = argb(if (big) 20 + rnd.nextInt(25) else 25 + rnd.nextInt(45))
        dust.maskFilter = if (big) BlurMaskFilter(r * 0.8f, BlurMaskFilter.Blur.NORMAL) else null
        canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, dust)
    }
    return out
}

/** A pane cracked by an impact: radial cracks, rings of cracks between them and a shattered centre. */
private fun crackBitmap(w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val rnd = Random(23)
    val m = min(w, h).toFloat()
    val impact = android.graphics.PointF(w * 0.68f, h * 0.30f)
    val reach = kotlin.math.hypot(w.toFloat(), h.toFloat())

    // Crack lines: a soft, wide light-scattering edge plus a fine bright core.
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.argb(70, 225, 235, 245)
        strokeWidth = m / 160f
        maskFilter = BlurMaskFilter(m / 220f, BlurMaskFilter.Blur.NORMAL)
    }
    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.argb(235, 240, 246, 252)
        strokeWidth = m / 900f
    }
    fun crack(path: android.graphics.Path) { canvas.drawPath(path, glow); canvas.drawPath(path, core) }

    // Radial cracks: jagged lines from the impact point, some branching.
    val rays = 14
    val angles = (0 until rays).map { i -> (i + rnd.nextFloat() * 0.6f) / rays * 2 * Math.PI.toFloat() }
    val rayPoints = angles.map { a0 ->
        val pts = mutableListOf(android.graphics.PointF(impact.x, impact.y))
        var a = a0; var r = 0f
        val length = reach * (0.35f + rnd.nextFloat() * 0.6f)
        while (r < length) {
            r += m * (0.03f + rnd.nextFloat() * 0.06f)
            a += (rnd.nextFloat() - 0.5f) * 0.18f
            pts += android.graphics.PointF(impact.x + r * kotlin.math.cos(a), impact.y + r * kotlin.math.sin(a))
        }
        pts
    }
    for (pts in rayPoints) {
        val path = android.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        crack(path)
        // A short branch or two.
        repeat(rnd.nextInt(3)) {
            val from = pts[(1 + rnd.nextInt((pts.size - 1).coerceAtLeast(1))).coerceAtMost(pts.size - 1)]
            var a = rnd.nextFloat() * 2 * Math.PI.toFloat()
            val b = android.graphics.Path().apply { moveTo(from.x, from.y) }
            var p = from
            repeat(3 + rnd.nextInt(4)) {
                a += (rnd.nextFloat() - 0.5f) * 0.5f
                val step = m * (0.02f + rnd.nextFloat() * 0.04f)
                p = android.graphics.PointF(p.x + step * kotlin.math.cos(a), p.y + step * kotlin.math.sin(a))
                b.lineTo(p.x, p.y)
            }
            crack(b)
        }
    }

    // Rings: short jagged cracks joining neighbouring rays at a few distances.
    for (ring in listOf(0.05f, 0.11f, 0.19f, 0.30f)) {
        for (i in 0 until rays) {
            if (rnd.nextFloat() < 0.3f) continue
            val p1 = rayPoints[i]; val p2 = rayPoints[(i + 1) % rays]
            val target = m * ring * (0.85f + rnd.nextFloat() * 0.3f)
            fun at(pts: List<android.graphics.PointF>) = pts.minByOrNull {
                kotlin.math.abs(kotlin.math.hypot(it.x - impact.x, it.y - impact.y) - target)
            }!!
            val a = at(p1); val b = at(p2)
            val mid = android.graphics.PointF(
                (a.x + b.x) / 2 + (rnd.nextFloat() - 0.5f) * m * 0.02f,
                (a.y + b.y) / 2 + (rnd.nextFloat() - 0.5f) * m * 0.02f,
            )
            crack(android.graphics.Path().apply { moveTo(a.x, a.y); lineTo(mid.x, mid.y); lineTo(b.x, b.y) })
        }
    }

    // Shattered centre: a frosted spot of tiny chips around the impact.
    val chips = Paint(Paint.ANTI_ALIAS_FLAG)
    chips.shader = RadialGradient(impact.x, impact.y, m * 0.05f,
        android.graphics.Color.argb(90, 235, 240, 245), android.graphics.Color.argb(0, 235, 240, 245), Shader.TileMode.CLAMP)
    canvas.drawCircle(impact.x, impact.y, m * 0.05f, chips)
    chips.shader = null
    repeat(60) {
        val r = m * 0.05f * kotlin.math.sqrt(rnd.nextFloat())
        val a = rnd.nextFloat() * 2 * Math.PI.toFloat()
        chips.color = android.graphics.Color.argb(80 + rnd.nextInt(120), 240, 245, 250)
        canvas.drawCircle(impact.x + r * kotlin.math.cos(a), impact.y + r * kotlin.math.sin(a), m / 1200f * (1 + rnd.nextInt(3)), chips)
    }
    return out
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt(); val yi = floor(y).toInt()
    val xf = x - xi; val yf = y - yi
    val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
    val a = hash(xi, yi, seed); val b = hash(xi + 1, yi, seed)
    val c = hash(xi, yi + 1, seed); val d = hash(xi + 1, yi + 1, seed)
    return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v
}

/** Fractal noise, 0..1. */
private fun fbm(x: Float, y: Float): Float {
    var sum = 0f; var amp = 0.5f; var freq = 1f; var norm = 0f
    repeat(5) { i ->
        sum += amp * valueNoise(x * freq, y * freq, 31 + i)
        norm += amp; amp *= 0.5f; freq *= 2f
    }
    return sum / norm
}
