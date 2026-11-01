package app.nightlight.filter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

/**
 * View phủ toàn màn hình: một lớp màu hổ phách (giảm ánh sáng xanh)
 * và một lớp đen mờ (giảm độ chói).
 */
class FilterView(context: Context) : View(context) {

    private var warmColor = Color.TRANSPARENT
    private var dimColor = Color.TRANSPARENT

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setLevels(warmth: Int, dim: Int) {
        val w = warmth.coerceIn(0, 100) / 100f
        val d = dim.coerceIn(0, 100) / 100f

        // Càng ấm thì càng chuyển từ hổ phách sang cam đậm và càng đặc.
        val green = lerp(150, 90, w)
        val blue = lerp(40, 0, w)
        warmColor = Color.argb((w * 255).toInt(), 255, green, blue)

        // Giới hạn lớp tối ở 70% để màn hình luôn đọc được.
        dimColor = Color.argb((d * 0.7f * 255).toInt(), 0, 0, 0)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (Color.alpha(warmColor) > 0) canvas.drawColor(warmColor)
        if (Color.alpha(dimColor) > 0) canvas.drawColor(dimColor)
    }

    private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).toInt()
}
