package com.minimal.digitalclock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun settings(block: ClockSettings.() -> Unit = {}) =
        ClockSettings(ApplicationProvider.getApplicationContext()).apply {
            rule.mainClock.autoAdvance = false
            use24Hour = true; showSeconds = true; showDate = true; showZoneName = true
            zoneIdString = "Asia/Tokyo"; foreground = 0xFFFFFFFF.toInt(); background = 0xFF000000.toInt()
            burnInProtection = false
            block()
        }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File("build/screens").mkdirs()
        File("build/screens/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun portrait24() { val s = settings(); rule.setContent { ClockScreen(s) {} }; shot("portrait_24h") }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun landscape24() { val s = settings(); rule.setContent { ClockScreen(s) {} }; shot("landscape_24h") }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun landscape12Amber() {
        val s = settings { use24Hour = false; foreground = 0xFFFFB74D.toInt(); zoneIdString = "America/New_York" }
        rule.setContent { ClockScreen(s) {} }; shot("landscape_12h_amber")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun portraitMinimal() {
        val s = settings { showSeconds = false; showDate = false; showZoneName = false; foreground = 0xFF64B5F6.toInt(); background = 0xFF0D1B2A.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("portrait_minimal")
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun portraitLongZone() {
        val s = settings { use24Hour = false; zoneIdString = "America/Argentina/Buenos_Aires" }
        rule.setContent { ClockScreen(s) {} }; shot("portrait_long_zone")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsScreen() { val s = settings(); rule.setContent { SettingsScreen(s) {} }; shot("settings") }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun aboutScreen() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.ui.graphics.Color(0xFF0E0E0E),
                    contentColor = androidx.compose.ui.graphics.Color(0xFFE6E1E5),
                ) { AboutScreen {} }
            }
        }
        shot("about")
    }
}
