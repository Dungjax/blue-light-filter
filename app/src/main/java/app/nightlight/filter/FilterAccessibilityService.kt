package app.nightlight.filter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility overlay used to test coverage over system UI such as the
 * notification shade. Android and OEMs may still restrict secure surfaces.
 * The service does not subscribe to events or inspect window content.
 */
class FilterAccessibilityService : AccessibilityService() {
    private lateinit var prefs: FilterPrefs
    private lateinit var windowManager: WindowManager
    private var overlay: FilterView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var cleaningUp = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!connected || cleaningUp) return
            applyFilter()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (connected && !cleaningUp) applyFilter()
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = FilterPrefs(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        connected = true
        cleaningUp = false
        prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
        prefs.a11yActive = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        applyFilter()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun applyFilter() {
        if (!connected || cleaningUp) return
        if (prefs.enabled && prefs.scheduleAllowsNow()) showOrUpdateOverlay()
        else removeOverlay()
    }

    private fun showOrUpdateOverlay() {
        overlay?.let {
            it.setLevels(prefs.warmth, prefs.dim)
            return
        }
        val view = FilterView(this).apply { setLevels(prefs.warmth, prefs.dim) }
        try {
            windowManager.addView(
                view,
                filterWindowParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            )
            overlay = view
        } catch (_: WindowManager.BadTokenException) {
            // Retry on next tick if the current system surface rejects this window.
        } catch (_: SecurityException) {
            // Permission may have been revoked while connecting.
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Already removed.
        } catch (_: WindowManager.BadTokenException) {
            // Token is no longer valid.
        } finally {
            overlay = null
        }
    }

    private fun cleanup() {
        if (cleaningUp) return
        cleaningUp = true
        connected = false
        handler.removeCallbacks(ticker)
        if (::prefs.isInitialized) {
            prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
            removeOverlay()
            prefs.a11yActive = false
        } else {
            removeOverlay()
        }
    }

    companion object { private const val TICK_MS = 30_000L }
}
