package com.minimal.digitalclock

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.background
import androidx.compose.ui.test.performScrollToNode
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
            visualEffect = VisualEffect.NONE; effectAmount = 0.5f; glassTint = GlassTint.BRONZE
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

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectHaloModern() {
        val s = settings { visualEffect = VisualEffect.HALO; foreground = 0xFF64B5F6.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_halo_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectHaloSegment() {
        val s = settings { visualEffect = VisualEffect.HALO; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_halo_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectDiffusionModern() {
        val s = settings { visualEffect = VisualEffect.DIFFUSION; foreground = 0xFF64B5F6.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_diffusion_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectDiffusionSegment() {
        val s = settings { visualEffect = VisualEffect.DIFFUSION; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_diffusion_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectFogModern() {
        val s = settings { visualEffect = VisualEffect.FOG; foreground = 0xFF64B5F6.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_fog_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectFogSegment() {
        val s = settings { visualEffect = VisualEffect.FOG; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_fog_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectDirtyModern() {
        val s = settings { visualEffect = VisualEffect.DIRTY; foreground = 0xFF64B5F6.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_dirty_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectDirtySegment() {
        val s = settings { visualEffect = VisualEffect.DIRTY; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_dirty_segment")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun effectFogPortrait() {
        val s = settings { visualEffect = VisualEffect.FOG; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_fog_portrait")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun effectSettings() {
        val s = settings { visualEffect = VisualEffect.HALO; clockStyle = ClockStyle.SEGMENT }
        rule.setContent { SettingsScreen(s) {} }; shot("effect_settings")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectCrackedModern() {
        val s = settings { visualEffect = VisualEffect.CRACKED; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_cracked_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectCrackedSegment() {
        val s = settings { visualEffect = VisualEffect.CRACKED; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt(); glassTint = GlassTint.BLUE }
        rule.setContent { ClockScreen(s) {} }; shot("effect_cracked_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectTintedModern() {
        val s = settings { visualEffect = VisualEffect.TINTED; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_tinted_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectTintedSegment() {
        val s = settings { visualEffect = VisualEffect.TINTED; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt(); glassTint = GlassTint.BLUE }
        rule.setContent { ClockScreen(s) {} }; shot("effect_tinted_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectReflectiveModern() {
        val s = settings { visualEffect = VisualEffect.REFLECTIVE; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("effect_reflective_modern")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun effectReflectiveSegment() {
        val s = settings { visualEffect = VisualEffect.REFLECTIVE; clockStyle = ClockStyle.SEGMENT; foreground = 0xFFFF8A65.toInt(); glassTint = GlassTint.BLUE }
        rule.setContent { ClockScreen(s) {} }; shot("effect_reflective_segment")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountHalo0() {
        val s = settings { visualEffect = VisualEffect.HALO; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_halo_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountHalo100() {
        val s = settings { visualEffect = VisualEffect.HALO; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_halo_100")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountFog0() {
        val s = settings { visualEffect = VisualEffect.FOG; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_fog_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountFog100() {
        val s = settings { visualEffect = VisualEffect.FOG; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_fog_100")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountDirty0() {
        val s = settings { visualEffect = VisualEffect.DIRTY; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_dirty_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountDirty100() {
        val s = settings { visualEffect = VisualEffect.DIRTY; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_dirty_100")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountCracked0() {
        val s = settings { visualEffect = VisualEffect.CRACKED; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_cracked_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountCracked100() {
        val s = settings { visualEffect = VisualEffect.CRACKED; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_cracked_100")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountTinted0() {
        val s = settings { visualEffect = VisualEffect.TINTED; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_tinted_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountTinted100() {
        val s = settings { visualEffect = VisualEffect.TINTED; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_tinted_100")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountReflective0() {
        val s = settings { visualEffect = VisualEffect.REFLECTIVE; effectAmount = 0.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_reflective_0")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun amountReflective100() {
        val s = settings { visualEffect = VisualEffect.REFLECTIVE; effectAmount = 1.0f; foreground = 0xFFFFFFFF.toInt() }
        rule.setContent { ClockScreen(s) {} }; shot("amount_reflective_100")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun effectSettingsSlider() {
        val s = settings { visualEffect = VisualEffect.TINTED; glassTint = GlassTint.BLUE; effectAmount = 0.7f }
        rule.setContent { SettingsScreen(s) {} }
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Strong"))
        shot("effect_settings_slider")
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
