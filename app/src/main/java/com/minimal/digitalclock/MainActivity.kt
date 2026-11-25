package com.minimal.digitalclock

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var settings: ClockSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = ClockSettings(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            // Apply window-level settings whenever they change.
            val battery = rememberBatteryState()
            val keepOn = shouldKeepScreenOn(settings.keepScreenOn, settings.keepOnOnlyWhileCharging, battery)
            LaunchedEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Black screen on battery.
            var inSettings by rememberSaveable { mutableStateOf(false) }
            val blackout = remember { Blackout() }
            val blackEnabled = settings.keepScreenOn && settings.keepOnOnlyWhileCharging && settings.blackOnBattery
            LaunchedEffect(battery?.plugged) { blackout.onPower(battery?.plugged) }
            // Opening the app or waking the screen shows the clock for a moment.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) blackout.reveal() }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(blackEnabled, blackout.plugged, blackout.revealed, blackout.revealCount) {
                if (blackEnabled && blackout.plugged == false && blackout.revealed) {
                    delay(BLACKOUT_REVEAL_MS)
                    blackout.expire()
                }
            }
            val black = blackout.isBlack(blackEnabled) && !inSettings

            LaunchedEffect(settings.brightness, black) {
                window.attributes = window.attributes.apply {
                    screenBrightness = when {
                        black -> 0f // lowest the phone allows (on some phones the backlight switches off)
                        settings.brightness < 0f -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        else -> settings.brightness.coerceIn(0.01f, 1f)
                    }
                }
            }
            LaunchedEffect(settings.orientation) {
                requestedOrientation = when (settings.orientation) {
                    ClockOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ClockOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    ClockOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            LaunchedEffect(settings.showWhenLocked) { applyShowWhenLocked(settings.showWhenLocked) }

            BackHandler(enabled = inSettings) { inSettings = false }

            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    // Any touch restarts the 10 seconds (without taking the touch away from the clock).
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) blackout.reveal()
                        }
                    }
                }
            ) {
            AnimatedContent(
                targetState = inSettings,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen"
            ) { showSettings ->
                if (showSettings) SettingsScreen(settings, onClose = { inSettings = false })
                else ClockScreen(settings, onOpenSettings = { inSettings = true }, battery = battery)
            }
            // The black screen covers everything; a tap (handled above) brings the clock back.
            AnimatedVisibility(black, enter = fadeIn(tween(800)), exit = fadeOut(tween(300))) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyShowWhenLocked(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
        } else {
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
