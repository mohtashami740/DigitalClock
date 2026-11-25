package com.minimal.digitalclock

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/** Battery level in percent (0..100) and whether the phone is plugged in. */
data class BatteryState(val level: Int, val plugged: Boolean)

private fun Intent.toBatteryState(): BatteryState? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return BatteryState(level * 100 / scale, getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
}

/** The current battery state, updated as it changes; null if the device does not report one. */
@Composable
fun rememberBatteryState(): BatteryState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                intent.toBatteryState()?.let { state = it }
            }
        }
        // ACTION_BATTERY_CHANGED is sticky: registering returns the current state right away.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.toBatteryState()?.let { state = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * A small battery icon: outline, fill for the level and a cut-out lightning bolt while charging.
 * With [blurPx] > 0 only a soft blurred shape is drawn (for glow layers).
 */
@Composable
fun BatteryGlyph(battery: BatteryState, color: Color, modifier: Modifier = Modifier, blurPx: Float = 0f) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val nub = size.width * 0.08f
        val bodyW = size.width - nub
        val h = size.height
        val stroke = h * 0.12f
        if (blurPx > 0f) {
            drawIntoCanvas { c ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.nativeCanvas.drawRoundRect(0f, 0f, bodyW, h, h * 0.2f, h * 0.2f, paint)
            }
            return@Canvas
        }
        // Outline and terminal nub.
        drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, h - stroke),
            CornerRadius(h * 0.2f), style = Stroke(stroke))
        drawRoundRect(color, Offset(bodyW, h * 0.3f), Size(nub, h * 0.4f), CornerRadius(nub * 0.5f))
        // Level fill, inset from the outline.
        val inset = stroke * 1.8f
        val fillW = (bodyW - 2 * inset) * (battery.level.coerceIn(0, 100) / 100f)
        if (fillW > 0f) drawRoundRect(color, Offset(inset, inset), Size(fillW, h - 2 * inset), CornerRadius(h * 0.08f))
        if (battery.plugged) {
            // Lightning bolt, cut out of the icon so it shows whatever the fill level.
            val cx = bodyW / 2; val bw = h * 0.42f
            val bolt = Path().apply {
                moveTo(cx + bw * 0.15f, h * 0.10f)
                lineTo(cx - bw * 0.55f, h * 0.56f)
                lineTo(cx - bw * 0.02f, h * 0.56f)
                lineTo(cx - bw * 0.20f, h * 0.90f)
                lineTo(cx + bw * 0.55f, h * 0.42f)
                lineTo(cx + bw * 0.02f, h * 0.42f)
                close()
            }
            drawPath(bolt, Color.Black, blendMode = BlendMode.Clear)
            drawPath(bolt, color, style = Stroke(stroke * 0.7f))
        }
    }
}

/**
 * Whether the screen should be kept on. With [onlyWhileCharging], only while plugged in; an unknown
 * battery state counts as plugged in so the screen never turns off unexpectedly.
 */
fun shouldKeepScreenOn(keepScreenOn: Boolean, onlyWhileCharging: Boolean, battery: BatteryState?): Boolean =
    keepScreenOn && (!onlyWhileCharging || battery?.plugged != false)

// ---- Screen saver (Android "Dream") switch -------------------------------------------------

private fun dreamComponent(context: Context) = ComponentName(context, ClockDreamService::class.java)

/** Whether the clock is offered as a screen saver (its service is disabled until the user opts in). */
fun isScreenSaverEnabled(context: Context): Boolean =
    context.packageManager.getComponentEnabledSetting(dreamComponent(context)) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED

fun setScreenSaverEnabled(context: Context, enabled: Boolean) {
    context.packageManager.setComponentEnabledSetting(
        dreamComponent(context),
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}
