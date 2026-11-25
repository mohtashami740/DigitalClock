package com.minimal.digitalclock

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatteryFeaturesTest {

    @Test
    fun keepScreenOnRules() {
        val plugged = BatteryState(50, plugged = true)
        val onBattery = BatteryState(50, plugged = false)
        // Keep screen on switched off: never.
        assertFalse(shouldKeepScreenOn(false, false, plugged))
        assertFalse(shouldKeepScreenOn(false, true, plugged))
        // Always on (the default): regardless of charging.
        assertTrue(shouldKeepScreenOn(true, false, onBattery))
        assertTrue(shouldKeepScreenOn(true, false, plugged))
        // Only while charging.
        assertTrue(shouldKeepScreenOn(true, true, plugged))
        assertFalse(shouldKeepScreenOn(true, true, onBattery))
        // Unknown battery state: stay on rather than turn off unexpectedly.
        assertTrue(shouldKeepScreenOn(true, true, null))
    }

    @Test
    fun screenSaverIsOffUntilEnabled() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(isScreenSaverEnabled(ctx))
        setScreenSaverEnabled(ctx, true)
        assertTrue(isScreenSaverEnabled(ctx))
        setScreenSaverEnabled(ctx, false)
        assertFalse(isScreenSaverEnabled(ctx))
    }

    @Test
    fun newSettingsDefaultOff() {
        val s = ClockSettings(ApplicationProvider.getApplicationContext())
        assertFalse(s.showBattery)
        assertFalse(s.keepOnOnlyWhileCharging)
        assertEquals(true, s.keepScreenOn)
    }
}
