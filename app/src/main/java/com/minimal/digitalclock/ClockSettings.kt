package com.minimal.digitalclock

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.mutableStateOf
import java.time.ZoneId
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ClockOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** All user settings, persisted in SharedPreferences and observable by Compose. */
class ClockSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)

    var use24Hour by pref("use24h", DateFormat.is24HourFormat(context))
    var showSeconds by pref("showSeconds", true)
    var showDate by pref("showDate", true)
    var showZoneName by pref("showZoneName", true)

    /** Empty string = follow the system time zone. */
    var zoneIdString by pref("zoneId", "")

    var foreground by pref("fg", 0xFFFFFFFF.toInt())
    var background by pref("bg", 0xFF000000.toInt())

    var orientationOrdinal by pref("orientation", ClockOrientation.AUTO.ordinal)
    var keepScreenOn by pref("keepScreenOn", true)

    /** Negative = use system brightness; otherwise 0.01..1. */
    var brightness by pref("brightness", -1f)
    var burnInProtection by pref("burnIn", true)
    var showWhenLocked by pref("showWhenLocked", false)

    var orientation: ClockOrientation
        get() = ClockOrientation.entries[orientationOrdinal.coerceIn(0, 2)]
        set(value) { orientationOrdinal = value.ordinal }

    val zoneId: ZoneId
        get() = if (zoneIdString.isEmpty()) ZoneId.systemDefault()
        else runCatching { ZoneId.of(zoneIdString) }.getOrElse { ZoneId.systemDefault() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> pref(key: String, default: T) = object : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(
            when (default) {
                is Boolean -> prefs.getBoolean(key, default)
                is Int -> prefs.getInt(key, default)
                is Float -> prefs.getFloat(key, default)
                is String -> prefs.getString(key, default)
                else -> error("Unsupported type")
            } as T
        )

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            prefs.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }.apply()
        }
    }
}
