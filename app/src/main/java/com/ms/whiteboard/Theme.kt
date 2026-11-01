package com.ms.whiteboard

import android.content.Context
import android.graphics.drawable.GradientDrawable

// كل ألوان وأشكال التطبيق في مكان واحد، والوضع الليلي/النهاري بيتحكم فيه من هنا
object Theme {
    var dark = true
        private set

    fun load(ctx: Context) {
        dark = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("dark", true)
    }

    fun toggle(ctx: Context) {
        dark = !dark
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
    }

    val BG get() = if (dark) 0xFF0B1120.toInt() else 0xFFF1F5F9.toInt()
    val PANEL get() = if (dark) 0xE6111A2E.toInt() else 0xF2FFFFFF.toInt()
    val BORDER get() = if (dark) 0x1FFFFFFF else 0x1F0F172A
    val TEXT get() = if (dark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt()
    val MUTED get() = if (dark) 0x99E2E8F0.toInt() else 0x990F172A.toInt()
    val PAPER get() = if (dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
    val GRID get() = if (dark) 0x1AFFFFFF else 0x14000000
    val ACCENT = 0xFF38BDF8.toInt()
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
