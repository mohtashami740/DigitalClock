package com.minimal.digitalclock

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
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
            LaunchedEffect(settings.brightness) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (settings.brightness < 0f)
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    else settings.brightness.coerceIn(0.01f, 1f)
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

            var inSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = inSettings) { inSettings = false }

            AnimatedContent(
                targetState = inSettings,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen"
            ) { showSettings ->
                if (showSettings) SettingsScreen(settings, onClose = { inSettings = false })
                else ClockScreen(settings, onOpenSettings = { inSettings = true }, battery = battery)
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
