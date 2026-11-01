package app.nightlight.filter

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Ô trong bảng Cài đặt nhanh (kéo thanh thông báo xuống) để bật/tắt bộ lọc.
 * Người dùng cần kéo ô này vào bảng một lần (nút bút chì ở cuối bảng).
 */
class NightLightTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val prefs = FilterPrefs(this)

        if (prefs.enabled) {
            prefs.enabled = false
            stopService(Intent(this, OverlayService::class.java))
        } else if (!Settings.canDrawOverlays(this)) {
            // Chưa có quyền hiển thị trên ứng dụng khác: mở app để người dùng cấp quyền.
            openApp()
        } else {
            prefs.enabled = true
            try {
                startForegroundService(Intent(this, OverlayService::class.java))
            } catch (e: Exception) {
                // Hệ thống không cho phép khởi chạy dịch vụ từ đây; mở app,
                // app sẽ tự khởi động lại dịch vụ khi hiện lên.
                openApp()
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = FilterPrefs(this).enabled && Settings.canDrawOverlays(this)

        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.tile_label)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
