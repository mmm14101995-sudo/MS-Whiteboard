package com.ms.whiteboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min

class PenIconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val ERASER = 5
    }

    var color: Int = Color.BLACK
        set(v) {
            field = v
            rebuild()
            invalidate()
        }

    var active: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val rf = RectF()
    private val path = Path()
    private var grad: Shader? = null

    init {
        rebuild()
    }

    private fun shade(c: Int, a: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return if (a >= 0f) {
            Color.rgb((r + (255 - r) * a).toInt(), (g + (255 - g) * a).toInt(), (b + (255 - b) * a).toInt())
        } else {
            Color.rgb((r * (1 + a)).toInt(), (g * (1 + a)).toInt(), (b * (1 + a)).toInt())
        }
    }

    private fun rebuild() {
        val lt = shade(color, 0.45f)
        val dk = shade(color, -0.35f)
        grad = LinearGradient(
            6f, 0f, 20f, 0f,
            intArrayOf(dk, lt, color, dk),
            floatArrayOf(0f, 0.38f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun solid(c: Int) {
        p.shader = null
        p.color = c
    }

    private fun gradBody(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        p.shader = grad
        rf.set(l, t, r, b)
        canvas.drawRoundRect(rf, rad, rad, p)
    }

    private fun rect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, c: Int) {
        solid(c)
        rf.set(l, t, r, b)
        canvas.drawRect(rf, p)
    }

    private fun poly(canvas: Canvas, c: Int, vararg pts: Float) {
        solid(c)
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        canvas.drawPath(path, p)
    }

    override fun onDraw(canvas: Canvas) {
        if (active) {
            rf.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rf, 14f * d, 14f * d, bg)
        }
        val s = min(width / 26f, height / 40f) * 0.92f
        canvas.save()
        // نقلب الرسم عشان السن يبقى لفوق
        canvas.translate((width - 26f * s) / 2f, (height + 40f * s) / 2f)
        canvas.scale(s, -s)
        when (kind) {
            0 -> { // حبر
                gradBody(canvas, 8f, 2f, 18f, 21f, 3.5f)
                rect(canvas, 8f, 19f, 18f, 22f, 0xFF475569.toInt())
                solid(0xFFCBD5E1.toInt())
                path.reset()
                path.moveTo(8.5f, 22f); path.lineTo(17.5f, 22f); path.lineTo(15.2f, 35f)
                path.quadTo(13f, 39.5f, 10.8f, 35f); path.close()
                canvas.drawPath(path, p)
                rect(canvas, 12.6f, 26f, 13.4f, 35f, 0xFF334155.toInt())
            }
            1 -> { // رصاص
                solid(0xFFF9A8D4.toInt())
                rf.set(9f, 2f, 17f, 7f)
                canvas.drawRoundRect(rf, 1.5f, 1.5f, p)
                p.shader = grad
                rf.set(9f, 6f, 17f, 26f)
                canvas.drawRect(rf, p)
                poly(canvas, 0xFFFCD9B6.toInt(), 9f, 26f, 17f, 26f, 13f, 38.5f)
                poly(canvas, color, 11.5f, 33f, 14.5f, 33f, 13f, 38.5f)
            }
            2 -> { // ماركر
                gradBody(canvas, 6.3f, 2f, 19.7f, 28f, 3f)
                poly(canvas, 0xFF6B7280.toInt(), 6.3f, 28f, 19.7f, 28f, 17f, 38.5f, 9f, 38.5f)
                poly(canvas, color, 9.6f, 34f, 16.4f, 34f, 17f, 38.5f, 9f, 38.5f)
            }
            3 -> { // تمييز
                gradBody(canvas, 7f, 2f, 19f, 28f, 4f)
                rect(canvas, 7f, 26f, 19f, 28.5f, 0xFFCBD5E1.toInt())
                poly(canvas, color, 8f, 28.5f, 18f, 28.5f, 18f, 35f, 8f, 39f)
            }
            4 -> { // ريشة
                gradBody(canvas, 9f, 2f, 17f, 24f, 3f)
                rect(canvas, 9f, 22f, 17f, 26f, 0xFF9CA3AF.toInt())
                solid(color)
                path.reset()
                path.moveTo(9.5f, 26f); path.lineTo(16.5f, 26f)
                path.quadTo(17.5f, 33f, 13f, 39.5f)
                path.quadTo(8.5f, 33f, 9.5f, 26f)
                path.close()
                canvas.drawPath(path, p)
            }
            else -> { // ممحاة
                solid(0xFF64748B.toInt())
                rf.set(8f, 2f, 18f, 26f)
                canvas.drawRoundRect(rf, 3f, 3f, p)
                solid(0xFFF9A8D4.toInt())
                rf.set(8f, 24f, 18f, 38f)
                canvas.drawRoundRect(rf, 3f, 3f, p)
                rect(canvas, 8f, 24f, 18f, 26f, 0xFFFFFFFF.toInt())
            }
        }
        canvas.restore()
    }
}
