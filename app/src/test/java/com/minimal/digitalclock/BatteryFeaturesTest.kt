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

    @Test
    fun blackoutRules() {
        val b = Blackout()
        // Feature off: never black.
        b.onPower(false); b.expire()
        assertFalse(b.isBlack(enabled = false))

        val x = Blackout()
        // Battery state not known yet: never black.
        assertFalse(x.isBlack(true))
        // App opened on battery: the clock shows first...
        x.onPower(false)
        assertFalse(x.isBlack(true))
        // ...and goes black when the 10 seconds run out.
        x.expire()
        assertTrue(x.isBlack(true))
        // A tap shows it again.
        x.reveal()
        assertFalse(x.isBlack(true))
        // Plugging in shows the clock, and it stays (no timer while charging).
        x.onPower(true)
        assertFalse(x.isBlack(true))
        x.expire()
        assertFalse(x.isBlack(true))
        // Pulling the cable makes it black at once.
        x.onPower(true)
        x.onPower(false)
        assertTrue(x.isBlack(true))
        // Repeated battery updates while unplugged don't change anything.
        x.onPower(false)
        assertTrue(x.isBlack(true))
        // Unknown state reports are ignored.
        x.onPower(null)
        assertTrue(x.isBlack(true))
    }

    @Test
    fun blackScreenDefaultOff() {
        assertFalse(ClockSettings(ApplicationProvider.getApplicationContext()).blackOnBattery)
    }
}
