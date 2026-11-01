package com.ms.whiteboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

// أيقونات بسيطة مرسومة بالكود (من غير صور)، بتتلوّن بلون الثيم
class GlyphView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val UNDO = 0
        const val REDO = 1
        const val BACK = 2
        const val SUN = 3
        const val MOON = 4
        const val ORIENT = 5
    }

    var color: Int = Theme.TEXT
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        // الأيقونة بترسم في 56% من المساحة، والباقي مسافة حواليها
        val w = width * 0.56f
        val h = height * 0.56f
        canvas.translate(width * 0.22f, height * 0.22f)
        stroke.color = color
        fill.color = color
        when (kind) {
            UNDO, BACK -> arrow(canvas, w, h, false)
            REDO -> arrow(canvas, w, h, true)
            SUN -> sun(canvas, w, h)
            MOON -> moon(canvas, w, h)
            ORIENT -> orient(canvas, w, h)
        }
    }

    private fun arrow(c: Canvas, w: Float, h: Float, flip: Boolean) {
        val f = if (flip) -1f else 1f
        val cx = w / 2f
        val cy = h / 2f
        val tail = cx + 0.4f * w * f
        val tip = cx - 0.4f * w * f
        c.drawLine(tail, cy, tip, cy, stroke)
        c.drawLine(tip, cy, tip + 0.3f * w * f, cy - 0.3f * h, stroke)
        c.drawLine(tip, cy, tip + 0.3f * w * f, cy + 0.3f * h, stroke)
    }

    private fun sun(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        c.drawCircle(cx, cy, w * 0.18f, fill)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val cs = Math.cos(a).toFloat()
            val sn = Math.sin(a).toFloat()
            c.drawLine(cx + cs * w * 0.3f, cy + sn * h * 0.3f, cx + cs * w * 0.46f, cy + sn * h * 0.46f, stroke)
        }
    }

    private fun moon(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.38f
        val big = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.4f, r * 0.85f, Path.Direction.CW) }
        path.reset()
        path.op(big, cut, Path.Op.DIFFERENCE)
        c.drawPath(path, fill)
    }

    private fun orient(c: Canvas, w: Float, h: Float) {
        c.drawRoundRect(w * 0.3f, h * 0.12f, w * 0.7f, h * 0.88f, w * 0.1f, w * 0.1f, stroke)
        c.drawLine(w * 0.3f, h * 0.5f, w * 0.7f, h * 0.5f, stroke)
    }
}
