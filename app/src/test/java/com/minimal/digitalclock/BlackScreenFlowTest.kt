package com.minimal.digitalclock

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Runs the real app and plugs/unplugs a simulated charger to check "Black screen on battery". */
@RunWith(RobolectricTestRunner::class)
class BlackScreenFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    private fun power(plugged: Boolean) {
        ctx.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 50)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_PLUGGED, if (plugged) BatteryManager.BATTERY_PLUGGED_AC else 0)
        )
        shadowOf(Looper.getMainLooper()).idle()
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun ActivityScenario<MainActivity>.window(check: (WindowManager.LayoutParams) -> Unit) =
        onActivity { check(it.window.attributes) }

    private val WindowManager.LayoutParams.keepOn get() = flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    private val WindowManager.LayoutParams.black get() = screenBrightness == 0f

    @Test
    fun unplugGoesBlackPlugShowsClockTapShowsTimeBriefly() {
        ClockSettings(ctx).apply {
            keepScreenOn = true; keepOnOnlyWhileCharging = true; blackOnBattery = true
        }
        rule.mainClock.autoAdvance = false
        power(true)
        ActivityScenario.launch(MainActivity::class.java).use { app ->
            rule.mainClock.advanceTimeBy(1_000)
            // Charging: clock shown, screen kept on.
            app.window { assertTrue(it.keepOn); assertFalse(it.black) }

            // Cable pulled: black and dimmed at once, and the screen may time out normally.
            power(false)
            app.window { assertFalse(it.keepOn); assertTrue(it.black) }

            // Cable plugged in again: clock back, screen kept on.
            power(true)
            app.window { assertTrue(it.keepOn); assertFalse(it.black) }

            // On battery a tap shows the time for 10 seconds, then it goes black again.
            power(false)
            app.window { assertTrue(it.black) }
            rule.onRoot().performClick()
            rule.mainClock.advanceTimeBy(1_000)
            app.window { assertFalse(it.black) }
            rule.mainClock.advanceTimeBy(BLACKOUT_REVEAL_MS + 1_000)
            app.window { assertTrue(it.black) }
        }
    }

    @Test
    fun withoutTheOptionUnpluggingDoesNotGoBlack() {
        ClockSettings(ctx).apply {
            keepScreenOn = true; keepOnOnlyWhileCharging = true; blackOnBattery = false
        }
        rule.mainClock.autoAdvance = false
        power(true)
        ActivityScenario.launch(MainActivity::class.java).use { app ->
            rule.mainClock.advanceTimeBy(1_000)
            power(false)
            rule.mainClock.advanceTimeBy(BLACKOUT_REVEAL_MS + 1_000)
            // Only the keep-on flag is released; the clock stays visible until the normal timeout.
            app.window { assertFalse(it.keepOn); assertFalse(it.black); assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, it.screenBrightness) }
        }
    }
}
