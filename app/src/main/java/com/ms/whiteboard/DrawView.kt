package com.ms.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class DrawView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val path = Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var lastX = 0f
    private var lastY = 0f
    private var prevX = 0f
    private var prevY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nc = Canvas(nb)
        nc.drawColor(Color.WHITE)
        bitmap?.let { nc.drawBitmap(it, 0f, 0f, null) }
        bitmap = nb
        bitmapCanvas = nc
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        canvas.drawPath(path, paint)
    }

    private fun invalidateArea(l: Float, t: Float, r: Float, b: Float) {
        val pad = paint.strokeWidth * 2f + 4f
        invalidate(
            (l - pad).toInt(), (t - pad).toInt(),
            (r + pad).toInt() + 1, (b + pad).toInt() + 1
        )
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                val x = e.x
                val y = e.y
                path.reset()
                path.moveTo(x, y)
                path.lineTo(x + 0.01f, y)
                lastX = x; lastY = y
                prevX = x; prevY = y
                invalidateArea(x, y, x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                var l = min(prevX, lastX)
                var t = min(prevY, lastY)
                var r = max(prevX, lastX)
                var b = max(prevY, lastY)

                val n = e.historySize
                for (i in 0..n) {
                    val x = if (i < n) e.getHistoricalX(i) else e.x
                    val y = if (i < n) e.getHistoricalY(i) else e.y
                    path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                    prevX = lastX; prevY = lastY
                    lastX = x; lastY = y
                    l = min(l, x); t = min(t, y)
                    r = max(r, x); b = max(b, y)
                }
                invalidateArea(l, t, r, b)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.lineTo(lastX, lastY)
                bitmapCanvas?.drawPath(path, paint)
                path.reset()
                invalidateArea(
                    min(prevX, lastX), min(prevY, lastY),
                    max(prevX, lastX), max(prevY, lastY)
                )
            }
        }
        return true
    }
}
