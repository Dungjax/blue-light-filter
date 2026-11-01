package app.nightlight.filter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Bật lại bộ lọc sau khi khởi động máy hoặc sau khi cập nhật ứng dụng. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = FilterPrefs(context)
        if (!prefs.enabled || !Settings.canDrawOverlays(context)) return

        try {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        } catch (e: Exception) {
            // Hệ thống không cho khởi chạy dịch vụ nền lúc này; bỏ qua để không làm crash.
        }
    }
}
