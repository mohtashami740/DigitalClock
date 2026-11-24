package com.minimal.digitalclock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

private const val SIDE_RATIO = 0.26f   // AM/PM + seconds size relative to the digits
private const val DATE_RATIO = 0.11f   // date line size relative to the digits
private const val FILL = 0.90f         // fraction of the screen the clock may occupy

private fun digitStyle(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Thin) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontFeatureSettings = "tnum",       // fixed-width digits: no jitter as time changes
    lineHeight = 1.0.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    textAlign = TextAlign.Center,
)

@Composable
fun ClockScreen(settings: ClockSettings, onOpenSettings: () -> Unit) {
    val fg = Color(settings.foreground)
    val bg = Color(settings.background)
    val zone = settings.zoneId

    // Tick exactly on each second boundary.
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    // Controls overlay: shown on tap, hides itself after a few seconds.
    var controlsVisible by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapCount) {
        if (controlsVisible) { delay(4000); controlsVisible = false }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
                tapCount++
            }
    ) {
        val density = LocalDensity.current
        val portrait = maxHeight > maxWidth
        val availW = with(density) { maxWidth.toPx() } * FILL
        val availH = with(density) { maxHeight.toPx() } * FILL

        val showSide = !settings.use24Hour || settings.showSeconds
        val showFooter = settings.showDate || settings.showZoneName

        // Compute the biggest font size that fits, using a fixed "00" template so the size never
        // changes from one minute to the next. Text scales linearly, so measure once at 100sp.
        val measurer = rememberTextMeasurer()
        val digitsSp: Float = remember(portrait, showSide, showFooter, settings.showDate, settings.showZoneName, availW, availH) {
            val ref = 100.sp
            val line = measurer.measure(if (portrait) "00" else "00:00", digitStyle(ref, fg)).size
            val sideW = if (showSide) {
                val s = digitStyle(ref * SIDE_RATIO, fg, FontWeight.Light)
                maxOf(measurer.measure("00", s).size.width, measurer.measure("PM", s).size.width) +
                    with(density) { 0.04f * ref.toPx() }
            } else 0f
            val footerH = if (showFooter)
                measurer.measure("Ag", digitStyle(ref * DATE_RATIO, fg, FontWeight.Light)).size.height * (if (portrait && settings.showDate && settings.showZoneName) 2.8f else 1.8f)
            else 0f
            val totalW = line.width + sideW.toFloat()
            val totalH = line.height * (if (portrait) 2 else 1) + footerH
            100f * min(availW / totalW, availH / totalH)
        }
        val digitSize = digitsSp.sp
        val sideSize = digitSize * SIDE_RATIO
        val dateSize = digitSize * DATE_RATIO

        // Burn-in protection: drift the whole clock a little every minute.
        val slackX = with(density) { maxWidth.toPx() } * (1 - FILL) / 2
        val slackY = with(density) { maxHeight.toPx() } * (1 - FILL) / 2
        val minuteKey = now.toEpochSecond() / 60
        val target = remember(minuteKey, settings.burnInProtection, slackX, slackY) {
            if (!settings.burnInProtection) IntOffset.Zero
            else IntOffset(
                (Random.nextFloat() * 2 - 1).times(slackX * 0.8f).toInt(),
                (Random.nextFloat() * 2 - 1).times(slackY * 0.8f).toInt(),
            )
        }
        val drift by animateIntOffsetAsState(target, tween(3000), label = "drift")

        val hourPattern = if (settings.use24Hour) "HH" else "h"
        val hours = now.format(DateTimeFormatter.ofPattern(hourPattern))
        val minutes = now.format(DateTimeFormatter.ofPattern("mm"))
        val seconds = now.format(DateTimeFormatter.ofPattern("ss"))
        val amPm = now.format(DateTimeFormatter.ofPattern("a", Locale.US))

        Column(
            Modifier.align(Alignment.Center).offset { drift },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val big = digitStyle(digitSize, fg)
            val side = digitStyle(sideSize, fg.copy(alpha = 0.75f), FontWeight.Light)

            @Composable
            fun DigitsLine(digits: String, sideVisible: Boolean) = Row {
                Text(digits, big, Modifier.alignBy(LastBaseline))
                if (showSide) {
                    Spacer(Modifier.width(with(density) { (sideSize.toPx() * 0.15f).toDp() }))
                    // Invisible copy on the hours line keeps both portrait lines centred identically.
                    Column(
                        Modifier.alignBy(LastBaseline).alpha(if (sideVisible) 1f else 0f),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (!settings.use24Hour) Text(amPm, side)
                        if (settings.showSeconds) Text(if (sideVisible) seconds else "00", side)
                    }
                }
            }

            if (portrait) {
                DigitsLine(hours, sideVisible = false)
                DigitsLine(minutes, sideVisible = true)
            } else {
                DigitsLine("$hours:$minutes", sideVisible = true)
            }
            if (showFooter) {
                val parts = buildList {
                    if (settings.showDate) {
                        val pattern = android.text.format.DateFormat
                            .getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
                        add(now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())))
                    }
                    if (settings.showZoneName) add(zoneLabel(zone.id))
                }
                // Portrait: one item per line. Either way, shrink the text if it would be wider than the screen.
                val footer = parts.joinToString(if (portrait) "\n" else "  ·  ")
                val footerStyle = digitStyle(dateSize, fg.copy(alpha = 0.6f), FontWeight.Light).copy(letterSpacing = 0.08.em)
                val footerW = measurer.measure(footer, footerStyle).size.width
                val shrink = if (footerW > availW) availW / footerW else 1f
                androidx.compose.material3.Text(
                    footer,
                    style = footerStyle.copy(fontSize = dateSize * shrink),
                    modifier = Modifier.padding(top = with(density) { (dateSize.toPx() * 0.6f).toDp() }),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = fg.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Text(text: String, style: TextStyle, modifier: Modifier = Modifier) =
    androidx.compose.material3.Text(text = text, style = style, modifier = modifier, maxLines = 1, softWrap = false)

/** "America/New_York" -> "New York", "Etc/UTC" -> "UTC". */
fun zoneLabel(id: String): String = id.substringAfterLast('/').replace('_', ' ')
