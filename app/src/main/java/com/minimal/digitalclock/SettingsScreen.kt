package com.minimal.digitalclock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.ZoneOffset

private val ForegroundPresets = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFFFFB74D, 0xFFFF8A65, 0xFFE57373,
    0xFFF48FB1, 0xFFBA68C8, 0xFF7986CB, 0xFF64B5F6, 0xFF4DD0E1, 0xFF81C784, 0xFFDCE775,
).map { it.toInt() }

private val BackgroundPresets = listOf(
    0xFF000000, 0xFF101010, 0xFF1C1C1E, 0xFF0D1B2A, 0xFF1B2631, 0xFF14201A,
    0xFF2A1515, 0xFF2B2118, 0xFFF5F5F0, 0xFFFFFFFF,
).map { it.toInt() }

@Composable
fun SettingsScreen(settings: ClockSettings, onClose: () -> Unit) {
    var pickingZone by rememberSaveable { mutableStateOf(false) }
    var customColorFor by rememberSaveable { mutableStateOf<String?>(null) } // "fg" | "bg"
    var showingAbout by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E), contentColor = Color(0xFFE6E1E5)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (pickingZone) {
                BackHandler { pickingZone = false }
                ZonePicker(
                    selected = settings.zoneIdString,
                    onPick = { settings.zoneIdString = it; pickingZone = false },
                    onBack = { pickingZone = false },
                )
            } else if (showingAbout) {
                BackHandler { showingAbout = false }
                AboutScreen(onBack = { showingAbout = false })
            } else {
                SettingsList(settings, onClose, onPickZone = { pickingZone = true },
                    onCustomColor = { customColorFor = it }, onAbout = { showingAbout = true })
            }
            customColorFor?.let { which ->
                ColorDialog(
                    initial = Color(if (which == "fg") settings.foreground else settings.background),
                    onDismiss = { customColorFor = null },
                    onConfirm = {
                        if (which == "fg") settings.foreground = it.toArgb() else settings.background = it.toArgb()
                        customColorFor = null
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun SettingsList(
    settings: ClockSettings,
    onClose: () -> Unit,
    onPickZone: () -> Unit,
    onCustomColor: (String) -> Unit,
    onAbout: () -> Unit,
) {
    // Title and preview stay pinned while the settings scroll, so changes are always visible.
    Column(Modifier.fillMaxSize()) {
    TopBar("Settings", onClose)
    Preview(settings)
    HorizontalDivider(Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = 0.08f))
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {

        item { Section("Style") }
        item {
            Text("Digits", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp))
        }
        items(ClockStyle.entries.size) { i ->
            val st = ClockStyle.entries[i]
            Row(
                Modifier.fillMaxWidth().clickable { settings.clockStyle = st }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings.clockStyle == st, onClick = { settings.clockStyle = st })
                Column {
                    Text(st.label, style = MaterialTheme.typography.bodyLarge)
                    Text(st.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        if (settings.clockStyle.isSegment) item {
            SwitchRow("Show unlit segments", settings.showUnlitSegments,
                "Faint outline of the unused segments, like a real LED display") { settings.showUnlitSegments = it }
        }
        item {
            Text("Effect", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp))
        }
        items(VisualEffect.entries.size) { i ->
            val e = VisualEffect.entries[i]
            Row(
                Modifier.fillMaxWidth().clickable { settings.visualEffect = e }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings.visualEffect == e, onClick = { settings.visualEffect = e })
                Column {
                    Text(e.label, style = MaterialTheme.typography.bodyLarge)
                    Text(e.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        if (settings.visualEffect == VisualEffect.TINTED) item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Tint", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTint.entries.forEach { t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Swatch(Color(t.argb), settings.glassTint == t) { settings.glassTint = t }
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
        if (settings.visualEffect != VisualEffect.NONE) item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Amount", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${(settings.effectAmount * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                Slider(value = settings.effectAmount, onValueChange = { settings.effectAmount = it })
                Row {
                    Text("Subtle", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.weight(1f))
                    Text("Strong", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        item { Section("Time") }
        item { SwitchRow("24-hour format", settings.use24Hour) { settings.use24Hour = it } }
        item { SwitchRow("Show seconds", settings.showSeconds) { settings.showSeconds = it } }
        item { SwitchRow("Show date", settings.showDate) { settings.showDate = it } }
        item { SwitchRow("Show time zone name", settings.showZoneName) { settings.showZoneName = it } }
        item {
            SwitchRow("Show battery level", settings.showBattery,
                "Battery icon and percentage next to the date") { settings.showBattery = it }
        }
        item {
            val label = if (settings.zoneIdString.isEmpty())
                "System default (${ZoneId.systemDefault().id})" else settings.zoneIdString
            Row(Modifier.fillMaxWidth().clickable(onClick = onPickZone).padding(horizontal = 24.dp, vertical = 14.dp)) {
                Column {
                    Text("Time zone", style = MaterialTheme.typography.bodyLarge)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }

        item { Section("Colors") }
        item { ColorRow("Digits", ForegroundPresets, settings.foreground, { settings.foreground = it }) { onCustomColor("fg") } }
        item { ColorRow("Background", BackgroundPresets, settings.background, { settings.background = it }) { onCustomColor("bg") } }
        item {
            TextButton(
                onClick = {
                    val f = settings.foreground; settings.foreground = settings.background; settings.background = f
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("Swap colors") }
        }

        item { Section("Display") }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Orientation", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = ClockOrientation.entries
                    options.forEachIndexed { i, o ->
                        SegmentedButton(
                            selected = settings.orientation == o,
                            onClick = { settings.orientation = o },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(o.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
        }
        item {
            SwitchRow("Keep screen on", settings.keepScreenOn,
                "Screen never turns off while the clock is open") { settings.keepScreenOn = it }
        }
        if (settings.keepScreenOn) item {
            SwitchRow("Only while charging", settings.keepOnOnlyWhileCharging,
                "On battery, the screen turns off normally to save power") { settings.keepOnOnlyWhileCharging = it }
        }
        if (settings.keepScreenOn && settings.keepOnOnlyWhileCharging) item {
            SwitchRow("Black screen on battery", settings.blackOnBattery,
                "When unplugged, the screen goes black and dim right away. Tap to see the time for 10 seconds; " +
                    "plugging in shows the clock again") { settings.blackOnBattery = it }
        }
        item {
            SwitchRow("Custom brightness", settings.brightness >= 0f,
                "Override system brightness — dim it for night use") {
                settings.brightness = if (it) 0.3f else -1f
            }
        }
        if (settings.brightness >= 0f) item {
            Slider(
                value = settings.brightness,
                onValueChange = { settings.brightness = it },
                valueRange = 0.01f..1f,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        item {
            SwitchRow("Burn-in protection", settings.burnInProtection,
                "Slowly moves the clock to protect OLED screens") { settings.burnInProtection = it }
        }
        item {
            SwitchRow("Show over lock screen", settings.showWhenLocked,
                "Clock stays visible after the phone locks") { settings.showWhenLocked = it }
        }

        item { Section("Screen saver") }
        item { ScreenSaverRows() }

        item { Section("About") }
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAbout).padding(horizontal = 24.dp, vertical = 14.dp)) {
                Column {
                    Text("About", style = MaterialTheme.typography.bodyLarge)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }
    }
    }
}

/** Offers the clock as Android's screen saver, and opens Android's settings to choose it. */
@Composable
private fun ScreenSaverRows() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(isScreenSaverEnabled(context)) }
    SwitchRow("Use as screen saver", enabled,
        "Show this clock automatically while the phone is charging or docked") {
        setScreenSaverEnabled(context, it); enabled = it
    }
    if (enabled) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text(
                "To start it, choose \"Clock\" as the screen saver in Android's settings and set when it starts " +
                    "(for example, while charging).",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            TextButton(
                onClick = {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS))
                    } catch (_: android.content.ActivityNotFoundException) {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("Open Android screen saver settings") }
        }
    }
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Preview(settings: ClockSettings) {
    Box(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(96.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(settings.background))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val now = java.time.ZonedDateTime.now(settings.zoneId)
        val text = now.format(java.time.format.DateTimeFormatter.ofPattern(if (settings.use24Hour) "HH:mm" else "h:mm"))
        val fg = Color(settings.foreground)
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { 56.sp.toPx() }
        val style = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 56.sp, fontWeight = FontWeight.Thin)
        EffectLayers(settings.visualEffect, settings.effectAmount) { glow, a ->
            if (settings.clockStyle.isSegment) SegmentText(
                text, px, fg.copy(alpha = fg.alpha * a),
                unlitAlpha = if (glow == null && settings.showUnlitSegments) 0.08f else 0f,
                blurPx = glow?.let { it.relRadius * px } ?: 0f,
                look = settings.clockStyle.segmentLook ?: SegmentLook.THIN,
            ) else Text(text, style = style.forLayer(glow, a, px))
        }
        GlassOverlay(
            settings.visualEffect, Modifier.matchParentSize(),
            amount = { settings.effectAmount },
            tint = { Color(settings.glassTint.argb) },
        )
    }
}

@Composable
private fun Section(title: String) {
    Column {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorRow(title: String, presets: List<Int>, selected: Int, onPick: (Int) -> Unit, onCustom: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
        ) {
            items(presets) { c -> Swatch(Color(c), c == selected) { onPick(c) } }
            item {
                val isCustom = selected !in presets
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (isCustom) Color(selected) else Color.Transparent)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable(onClick = onCustom),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, "Custom color",
                        tint = if (isCustom && Color(selected).luminance() > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color)
            .border(if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
    }
}

@Composable
private fun ColorDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(color))
                Spacer(Modifier.height(16.dp))
                LabeledSlider("Hue", h, 0f..360f) { h = it }
                LabeledSlider("Saturation", s, 0f..1f) { s = it }
                LabeledSlider("Brightness", v, 0f..1f) { v = it }
                Text("#%06X".format(color.toArgb() and 0xFFFFFF), color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private data class ZoneEntry(val id: String, val offsetSeconds: Int, val offsetLabel: String)

private fun formatOffset(offset: ZoneOffset): String =
    if (offset.totalSeconds == 0) "GMT" else "GMT" + offset.id

@Composable
private fun ZonePicker(selected: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    val zones = remember {
        val now = Instant.now()
        ZoneId.getAvailableZoneIds()
            .filter { (it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/")) || it == "UTC" }
            .map { id ->
                val off = ZoneId.of(id).rules.getOffset(now)
                ZoneEntry(id, off.totalSeconds, formatOffset(off))
            }
            .sortedWith(compareBy({ it.offsetSeconds }, { it.id }))
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().replace(' ', '_')
        if (q.isEmpty()) zones
        else zones.filter { it.id.contains(q, ignoreCase = true) || it.offsetLabel.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Time zone", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search city or GMT offset") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isBlank()) item {
                ZoneRow("System default", ZoneId.systemDefault().id, selected.isEmpty()) { onPick("") }
            }
            items(filtered, key = { it.id }) { z ->
                ZoneRow(zoneLabel(z.id), "${z.id}  ·  ${z.offsetLabel}", z.id == selected) { onPick(z.id) }
            }
        }
    }
}

@Composable
private fun ZoneRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
