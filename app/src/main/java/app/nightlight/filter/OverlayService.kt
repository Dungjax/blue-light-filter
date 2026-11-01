package app.nightlight.filter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import java.util.Calendar

/**
 * Dịch vụ nền giữ lớp phủ màu trên màn hình.
 * Chạy khi người dùng bật bộ lọc; lớp phủ chỉ hiện trong khoảng giờ hẹn (nếu có bật hẹn giờ).
 */
class OverlayService : Service() {

    private lateinit var prefs: FilterPrefs
    private lateinit var windowManager: WindowManager

    private var overlay: FilterView? = null
    private var lastActive: Boolean? = null
    private var stopping = false

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            applyState()
            handler.postDelayed(this, TICK_MS)
        }
    }

    // Phải giữ tham chiếu mạnh, nếu không listener có thể bị thu hồi.
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyState() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = FilterPrefs(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        goForeground(scheduleAllows())
        prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.enabled = false
            shutdown()
            return START_NOT_STICKY
        }
        // Luôn gọi lại startForeground để đáp ứng startForegroundService().
        goForeground(scheduleAllows())
        applyState()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
        removeOverlay()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- trạng thái

    private fun scheduleAllows(): Boolean {
        if (!prefs.scheduleEnabled) return true
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return prefs.isInSchedule(minutes)
    }

    private fun applyState() {
        if (stopping) return

        if (!prefs.enabled) {
            shutdown()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            // Người dùng đã thu hồi quyền hiển thị trên ứng dụng khác.
            prefs.enabled = false
            shutdown()
            return
        }

        val active = scheduleAllows()
        if (active) showOrUpdateOverlay() else removeOverlay()

        if (lastActive != active) {
            lastActive = active
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(active))
        }
    }

    private fun shutdown() {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(ticker)
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- lớp phủ

    private fun showOrUpdateOverlay() {
        val existing = overlay
        if (existing != null) {
            existing.setLevels(prefs.warmth, prefs.dim)
            return
        }
        val view = FilterView(this)
        view.setLevels(prefs.warmth, prefs.dim)
        try {
            windowManager.addView(view, overlayParams())
            overlay = view
        } catch (e: Exception) {
            // Hệ thống từ chối (ví dụ quyền vừa bị thu hồi); lần cập nhật sau sẽ thử lại.
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // View đã bị gỡ.
        }
        overlay = null
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        // Android 12+ chặn cảm ứng xuyên qua lớp phủ có độ mờ > 0.8,
        // nên giữ alpha cửa sổ dưới ngưỡng này để thao tác vẫn đi xuyên qua.
        params.alpha = OVERLAY_WINDOW_ALPHA

        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.setFitInsetsTypes(0)
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    // ---------------------------------------------------------------- thông báo

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
    }

    private fun goForeground(active: Boolean) {
        val notification = buildNotification(active)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(active: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            getString(R.string.notif_turn_off),
            stop
        ).build()

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(if (active) R.string.notif_on else R.string.notif_waiting)
            )
            .setContentIntent(openApp)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_STOP = "app.nightlight.filter.action.STOP"

        private const val CHANNEL_ID = "night_light_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 30_000L
        private const val OVERLAY_WINDOW_ALPHA = 0.79f
    }
}
