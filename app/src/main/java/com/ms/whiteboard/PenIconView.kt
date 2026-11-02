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

// أقلام معدنية مرسومة بالكود: جسم بلمعة كروم، وسن، ومشبك، والخط بيتوزع على الجسم
class PenIconView(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val ERASER = 5
    }

    var color: Int = 0xFF1E88E5.toInt()
        set(v) {
            field = v
            invalidate()
        }

    var active: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val activeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val slit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.45f
        color = 0x66000000
    }

    override fun onDraw(canvas: Canvas) {
        if (active) {
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 10f * density, 10f * density, activeBg)
        }
        canvas.save()
        // كل الرسم بإحداثيات 26×40 وفيه السن لفوق
        canvas.scale(width / 26f, height / 40f)
        when (kind) {
            0 -> fountain(canvas)
            1 -> mechanical(canvas)
            2 -> marker(canvas)
            3 -> highlighter(canvas)
            4 -> brush(canvas)
            else -> eraser(canvas)
        }
        canvas.restore()
    }

    // قلم حبر: سن مدبب وجسم معدني ومشبك
    private fun fountain(c: Canvas) {
        val tint = mix(color, 0xFFCBD5E1.toInt(), 0.35f)
        path.reset()
        path.moveTo(9.5f, 14f)
        path.lineTo(16.5f, 14f)
        path.lineTo(13f, 2f)
        path.close()
        fillPath(c, path, metal(9f, 17f, 0xFFD1D5DB.toInt()))
        c.drawLine(13f, 4f, 13f, 13f, slit)
        fillRect(c, 8f, 14f, 18f, 31f, 1.5f, metal(8f, 18f, tint))
        fillRect(c, 8f, 27f, 18f, 30f, 0.5f, chrome(8f, 18f))
        fillRect(c, 9f, 31f, 17f, 35f, 2f, metal(9f, 17f, tint))
        fillRect(c, 17.2f, 16f, 18.4f, 27f, 0.6f, chrome(17f, 19f))
    }

    // قلم رصاص ميكانيكي: سن رفيع وغطا كروم
    private fun mechanical(c: Canvas) {
        val tint = mix(color, 0xFFCBD5E1.toInt(), 0.35f)
        path.reset()
        path.moveTo(10.5f, 12f)
        path.lineTo(15.5f, 12f)
        path.lineTo(13.6f, 3f)
        path.lineTo(12.4f, 3f)
        path.close()
        fillPath(c, path, chrome(10f, 16f))
        fillRect(c, 8f, 12f, 18f, 32f, 1.5f, metal(8f, 18f, tint))
        fillRect(c, 8f, 32f, 18f, 35f, 1f, chrome(8f, 18f))
        fillRect(c, 17.2f, 15f, 18.4f, 27f, 0.6f, chrome(17f, 19f))
    }

    // قلم تحديد: جسم عريض وسن عريض وشريط لون
    private fun marker(c: Canvas) {
        val tint = mix(color, 0xFFCBD5E1.toInt(), 0.25f)
        path.reset()
        path.moveTo(6.5f, 14f)
        path.lineTo(19.5f, 14f)
        path.lineTo(16f, 5f)
        path.lineTo(10f, 5f)
        path.close()
        fillPath(c, path, metal(6f, 20f, tint))
        fillRect(c, 9f, 2f, 17f, 6f, 1f, chrome(9f, 17f))
        fillRect(c, 6.5f, 14f, 19.5f, 32f, 2f, metal(6f, 20f, tint))
        fillRect(c, 6.5f, 20f, 19.5f, 24f, 0f, metal(6f, 20f, color))
        fillRect(c, 6.5f, 32f, 19.5f, 35f, 1.5f, chrome(6f, 20f))
    }

    // هايلايتر: سن مسطح عريض بلون واضح
    private fun highlighter(c: Canvas) {
        val tint = mix(color, 0xFFCBD5E1.toInt(), 0.2f)
        fillRect(c, 6f, 14f, 20f, 33f, 2f, metal(6f, 20f, tint))
        fillRect(c, 4.5f, 3f, 21.5f, 14f, 2.5f, metal(4f, 22f, color))
        fillRect(c, 6f, 33f, 20f, 36f, 1.5f, chrome(6f, 20f))
    }

    // فرشاة: رأس مدبب بلون القلم وجسم معدني
    private fun brush(c: Canvas) {
        val tint = mix(color, 0xFFCBD5E1.toInt(), 0.35f)
        path.reset()
        path.moveTo(9f, 14f)
        path.lineTo(17f, 14f)
        path.lineTo(15f, 7f)
        path.quadTo(13f, 0f, 11f, 7f)
        path.close()
        fillPath(c, path, metal(9f, 17f, color))
        fillRect(c, 9f, 14f, 17f, 32f, 1.5f, metal(9f, 17f, tint))
        fillRect(c, 8f, 27f, 18f, 30f, 0.5f, chrome(8f, 18f))
        fillRect(c, 9f, 32f, 17f, 35f, 2f, metal(9f, 17f, tint))
    }

    // ممحاة: جسم وردي معدني وغطا كروم
    private fun eraser(c: Canvas) {
        fillRect(c, 7f, 9f, 19f, 28f, 4f, metal(7f, 19f, 0xFFF472B6.toInt()))
        fillRect(c, 7f, 28f, 19f, 33f, 0.5f, chrome(7f, 19f))
    }

    private fun fillRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, sh: Shader) {
        paint.shader = sh
        paint.style = Paint.Style.FILL
        rect.set(l, t, r, b)
        if (radius > 0f) c.drawRoundRect(rect, radius, radius, paint) else c.drawRect(rect, paint)
    }

    private fun fillPath(c: Canvas, p: Path, sh: Shader) {
        paint.shader = sh
        paint.style = Paint.Style.FILL
        c.drawPath(p, paint)
    }

    // تدرج معدني: ظل ثم لمعة ثم لون ثم ظل
    private fun metal(x0: Float, x1: Float, base: Int): Shader = LinearGradient(
        x0, 0f, x1, 0f,
        intArrayOf(shade(base, 0.5f), lighten(base, 0.7f), base, shade(base, 0.6f)),
        floatArrayOf(0f, 0.3f, 0.62f, 1f),
        Shader.TileMode.CLAMP
    )

    // كروم رمادي للحلقات والأغطية
    private fun chrome(x0: Float, x1: Float): Shader = LinearGradient(
        x0, 0f, x1, 0f,
        intArrayOf(0xFF4B5563.toInt(), 0xFFF3F4F6.toInt(), 0xFF9CA3AF.toInt(), 0xFF374151.toInt()),
        floatArrayOf(0f, 0.3f, 0.65f, 1f),
        Shader.TileMode.CLAMP
    )

    private fun shade(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255),
        (Color.green(c) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    private fun lighten(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    )

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}
