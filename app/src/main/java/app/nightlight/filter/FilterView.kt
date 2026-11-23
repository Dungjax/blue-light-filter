package app.nightlight.filter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class FilterView(context: Context) : View(context) {
    private var warmColor = Color.TRANSPARENT
    private var dimColor = Color.TRANSPARENT
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setLevels(warmth: Int, dim: Int) {
        val w = warmth.coerceIn(0, 100) / 100f
        val d = dim.coerceIn(0, 100) / 100f
        val green = lerp(150, 90, w)
        val blue = lerp(40, 0, w)
        warmColor = Color.argb((w * 255).toInt(), 255, green, blue)
        dimColor = Color.argb((d * 0.7f * 255).toInt(), 0, 0, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (Color.alpha(warmColor) > 0) canvas.drawColor(warmColor)
        if (Color.alpha(dimColor) > 0) canvas.drawColor(dimColor)
    }

    private fun lerp(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt()
}

/** Keep window alpha separate from color intensity; do not attenuate the filter. */
fun filterWindowParams(type: Int): WindowManager.LayoutParams {
    val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        type,
        flags,
        PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.TOP or Gravity.START
    params.alpha = 1.0f
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
