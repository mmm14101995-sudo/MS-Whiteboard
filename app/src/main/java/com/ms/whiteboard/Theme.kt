package com.ms.whiteboard

import android.content.Context
import android.graphics.drawable.GradientDrawable

// كل ألوان وأشكال التطبيق في مكان واحد
object Theme {
    val BG = 0xFF0B1120.toInt()
    val PANEL = 0xE6111A2E.toInt()
    val BORDER = 0x1FFFFFFF
    val ACCENT = 0xFF38BDF8.toInt()
    val TEXT = 0xFFE2E8F0.toInt()
    val MUTED = 0x99E2E8F0.toInt()
    val DANGER = 0xFFEF4444.toInt()

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun box(
        ctx: Context,
        fill: Int,
        radiusDp: Float,
        stroke: Int = 0,
        strokeDp: Float = 0f,
        oval: Boolean = false
    ): GradientDrawable = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(fill)
        if (stroke != 0 && strokeDp > 0f) setStroke(dp(ctx, strokeDp), stroke)
    }
}
