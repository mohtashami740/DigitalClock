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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
}

/** A blurred copy of the content. [relRadius] is relative to the element's font size. */
data class GlowLayer(val relRadius: Float, val alpha: Float)

/** Glow layers drawn behind, and the opacity of the sharp content drawn on top (0 = none). */
class EffectRecipe(val glows: List<GlowLayer>, val coreAlpha: Float)

val VisualEffect.recipe: EffectRecipe
    get() = when (this) {
        VisualEffect.NONE -> EffectRecipe(emptyList(), 1f)
        VisualEffect.HALO -> EffectRecipe(
            listOf(GlowLayer(0.16f, 0.55f), GlowLayer(0.05f, 0.75f)), coreAlpha = 1f,
        )
        VisualEffect.DIFFUSION -> EffectRecipe(
            listOf(GlowLayer(0.25f, 0.35f), GlowLayer(0.08f, 0.7f), GlowLayer(0.025f, 0.9f)), coreAlpha = 0.45f,
        )
        VisualEffect.FOG -> EffectRecipe(
            listOf(GlowLayer(0.30f, 0.35f), GlowLayer(0.10f, 0.7f), GlowLayer(0.04f, 0.9f)), coreAlpha = 0f,
        )
        VisualEffect.DIRTY -> EffectRecipe(listOf(GlowLayer(0.08f, 0.3f)), coreAlpha = 1f)
    }

/**
 * Draws [content] once per glow layer and once sharp, stacked in a Box. Every copy has the same
 * layout, so the Box keeps the content's size and baselines.
 * [content] receives the glow layer (null for the sharp copy) and the opacity to draw with.
 */
@Composable
fun EffectLayers(
    effect: VisualEffect,
    modifier: Modifier = Modifier,
    content: @Composable (glow: GlowLayer?, alpha: Float) -> Unit,
) {
    val recipe = effect.recipe
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

/**
 * The glass in front of the clock: mist for [VisualEffect.FOG], dirt for [VisualEffect.DIRTY],
 * nothing otherwise. [shift] moves it slightly (burn-in protection), so it is generated a bit
 * larger than the screen.
 */
@Composable
fun GlassOverlay(effect: VisualEffect, modifier: Modifier = Modifier, shift: () -> IntOffset = { IntOffset.Zero }) {
    if (effect != VisualEffect.FOG && effect != VisualEffect.DIRTY) return
    Spacer(
        modifier.drawWithCache {
            val margin = 0.04f
            val w = (size.width * (1 + 2 * margin)).roundToInt().coerceAtLeast(1)
            val h = (size.height * (1 + 2 * margin)).roundToInt().coerceAtLeast(1)
            val image = (if (effect == VisualEffect.FOG) fogBitmap(w, h) else dirtBitmap(w, h)).asImageBitmap()
            val origin = Offset(-size.width * margin, -size.height * margin)
            onDrawBehind {
                val s = shift()
                drawImage(
                    image,
                    dstOffset = IntOffset((origin.x + s.x).roundToInt(), (origin.y + s.y).roundToInt()),
                    dstSize = IntSize(w, h),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    )
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
        val density = (0.20f + 0.16f * smooth(0.2f, 0.8f, n))    // fairly even mist, gently thicker in places
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
        paint.color = android.graphics.Color.argb(18 + rnd.nextInt(18), 230, 236, 240)
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

    fun argb(a: Int) = android.graphics.Color.argb(a.coerceIn(0, 255), grime[0], grime[1], grime[2])

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
