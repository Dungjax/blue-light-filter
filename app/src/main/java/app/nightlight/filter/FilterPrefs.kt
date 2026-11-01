package app.nightlight.filter

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/** Cài đặt của bộ lọc, lưu trong SharedPreferences. */
class FilterPrefs(context: Context) {

    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Người dùng đã bật bộ lọc (dịch vụ nền chạy khi giá trị này là true). */
    var enabled: Boolean
        get() = raw.getBoolean(KEY_ENABLED, false)
        set(value) {
            raw.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Độ ấm màu, 0..100. */
    var warmth: Int
        get() = raw.getInt(KEY_WARMTH, DEFAULT_WARMTH)
        set(value) {
            raw.edit().putInt(KEY_WARMTH, value.coerceIn(0, 100)).apply()
        }

    /** Độ tối, 0..100. */
    var dim: Int
        get() = raw.getInt(KEY_DIM, DEFAULT_DIM)
        set(value) {
            raw.edit().putInt(KEY_DIM, value.coerceIn(0, 100)).apply()
        }

    var scheduleEnabled: Boolean
        get() = raw.getBoolean(KEY_SCHEDULE, false)
        set(value) {
            raw.edit().putBoolean(KEY_SCHEDULE, value).apply()
        }

    /** Giờ bắt đầu, tính bằng phút kể từ 00:00. */
    var startMinutes: Int
        get() = raw.getInt(KEY_START, 21 * 60)
        set(value) {
            raw.edit().putInt(KEY_START, value).apply()
        }

    /** Giờ kết thúc, tính bằng phút kể từ 00:00. */
    var endMinutes: Int
        get() = raw.getInt(KEY_END, 6 * 60)
        set(value) {
            raw.edit().putInt(KEY_END, value).apply()
        }

    /** Khoảng giờ có thể qua nửa đêm (ví dụ 21:00 -> 06:00). */
    fun isInSchedule(nowMinutes: Int): Boolean {
        val start = startMinutes
        val end = endMinutes
        if (start == end) return true
        return if (start < end) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    companion object {
        private const val FILE = "night_light_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WARMTH = "warmth"
        private const val KEY_DIM = "dim"
        private const val KEY_SCHEDULE = "schedule_enabled"
        private const val KEY_START = "schedule_start"
        private const val KEY_END = "schedule_end"

        const val DEFAULT_WARMTH = 50
        const val DEFAULT_DIM = 20

        fun formatTime(minutes: Int): String =
            String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }
}
