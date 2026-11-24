package com.minimal.digitalclock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
            burnInProtection = false; clockStyle = ClockStyle.MODERN; showUnlitSegments = true
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
    fun settingsScreen() { val s = settings { clockStyle = ClockStyle.SEGMENT }; rule.setContent { SettingsScreen(s) {} }; shot("settings") }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun segmentLandscape() {
        val s = settings { clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("segment_landscape")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun segmentLandscape12hNoGhost() {
        val s = settings { clockStyle = ClockStyle.SEGMENT; use24Hour = false; showUnlitSegments = false; foreground = 0xFF81C784.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("segment_landscape_12h")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun segmentPortrait() {
        val s = settings { clockStyle = ClockStyle.SEGMENT; foreground = 0xFFE57373.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("segment_portrait")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun segmentAllDigits() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.Black)
            ) {
                SegmentText("01234", 300f, androidx.compose.ui.graphics.Color.White, unlitAlpha = 0.08f)
                SegmentText("56789", 300f, androidx.compose.ui.graphics.Color.White, unlitAlpha = 0.08f)
            }
        }
        shot("segment_digits")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun slantComparison() {
        rule.mainClock.autoAdvance = false
        val white = androidx.compose.ui.graphics.Color.White
        rule.setContent {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.Black)
                    .fillMaxSize(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                for (deg in listOf(0f, 6f, 10f, 14f)) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        SegmentText("12:38", 260f, white, unlitAlpha = 0.08f, slantDegrees = deg)
                        androidx.compose.material3.Text("${deg.toInt()}°", color = white,
                            fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                }
            }
        }
        shot("slant_comparison")
    }

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
