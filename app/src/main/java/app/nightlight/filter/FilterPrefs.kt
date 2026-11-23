package app.nightlight.filter

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

class FilterPrefs(context: Context) {
    private val appContext = context.applicationContext
    val raw: SharedPreferences =
        appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = raw.getBoolean(KEY_ENABLED, false)
        set(value) { raw.edit().putBoolean(KEY_ENABLED, value).apply() }
    var warmth: Int
        get() = raw.getInt(KEY_WARMTH, DEFAULT_WARMTH)
        set(value) { raw.edit().putInt(KEY_WARMTH, value.coerceIn(0, 100)).apply() }
    var dim: Int
        get() = raw.getInt(KEY_DIM, DEFAULT_DIM)
        set(value) { raw.edit().putInt(KEY_DIM, value.coerceIn(0, 100)).apply() }
    var scheduleEnabled: Boolean
        get() = raw.getBoolean(KEY_SCHEDULE, false)
        set(value) { raw.edit().putBoolean(KEY_SCHEDULE, value).apply() }
    var startMinutes: Int
        get() = raw.getInt(KEY_START, 21 * 60)
        set(value) { raw.edit().putInt(KEY_START, value.coerceIn(0, 1439)).apply() }
    var endMinutes: Int
        get() = raw.getInt(KEY_END, 6 * 60)
        set(value) { raw.edit().putInt(KEY_END, value.coerceIn(0, 1439)).apply() }

    /** Marker set by the AccessibilityService after it connects. */
    var a11yActive: Boolean
        get() = raw.getBoolean(KEY_A11Y, false) && isAccessibilityServiceEnabled()
        set(value) { raw.edit().putBoolean(KEY_A11Y, value).apply() }

    fun isAccessibilityServiceEnabled(): Boolean {
        val services = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "${appContext.packageName}/${FilterAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun scheduleAllowsNow(): Boolean {
        if (!scheduleEnabled) return true
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return isInSchedule(minutes)
    }

    fun isInSchedule(nowMinutes: Int): Boolean {
        val start = startMinutes
        val end = endMinutes
        if (start == end) return true
        return if (start < end) nowMinutes in start until end
        else nowMinutes >= start || nowMinutes < end
    }

    companion object {
        private const val FILE = "night_light_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WARMTH = "warmth"
        private const val KEY_DIM = "dim"
        private const val KEY_SCHEDULE = "schedule_enabled"
        private const val KEY_START = "schedule_start"
        private const val KEY_END = "schedule_end"
        private const val KEY_A11Y = "a11y_active"
        const val DEFAULT_WARMTH = 50
        const val DEFAULT_DIM = 20
        fun formatTime(minutes: Int): String =
            String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }
}
