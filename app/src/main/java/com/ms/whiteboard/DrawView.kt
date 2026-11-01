package com.ms.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class DrawView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private val seg = Path()
    private val dirty = RectF()
    private var hasDirty = false

    private val density = resources.displayMetrics.density
    private val palmSize = 100f * density   // أقل حجم لمسة تعتبر كف
    var penWidth = 7f * density
    var fingerDraws = true                  // الإصبع الصغير يكتب (للتجربة على التليفون)

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var stylusId = -1
    private var realPressure = true
    private var sx = 0f; private var sy = 0f
    private var mx = 0f; private var my = 0f
    private var sw = 0f
    private var eraseId = -1
    private var ex = 0f; private var ey = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nc = Canvas(nb)
        bitmap?.let { nc.drawBitmap(it, 0f, 0f, null) }
        bitmap = nb
        bmpCanvas = nc
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
    }

    private fun addDirty(x: Float, y: Float, pad: Float) {
        val l = x - pad; val t = y - pad; val r = x + pad; val b = y + pad
        if (!hasDirty) {
            dirty.set(l, t, r, b); hasDirty = true
        } else {
            dirty.set(min(dirty.left, l), min(dirty.top, t), max(dirty.right, r), max(dirty.bottom, b))
        }
    }

    private fun flushDirty() {
        if (hasDirty) {
            invalidate(
                dirty.left.toInt() - 2, dirty.top.toInt() - 2,
                dirty.right.toInt() + 2, dirty.bottom.toInt() + 2
            )
            hasDirty = false
        }
    }

    private fun widthFor(p: Float): Float {
        val q = if (realPressure) p else 0.65f
        return penWidth * (0.35f + q.coerceIn(0f, 1f))
    }

    private fun startStroke(x: Float, y: Float, p: Float) {
        sx = x; sy = y; mx = x; my = y
        sw = widthFor(p)
        inkPaint.strokeWidth = sw
        bmpCanvas?.drawPoint(x, y, inkPaint)
        addDirty(x, y, sw)
    }

    private fun addPoint(x: Float, y: Float, p: Float) {
        val midX = (x + sx) / 2f
        val midY = (y + sy) / 2f
        val w = sw * 0.5f + widthFor(p) * 0.5f
        seg.reset()
        seg.moveTo(mx, my)
        seg.quadTo(sx, sy, midX, midY)
        inkPaint.strokeWidth = w
        bmpCanvas?.drawPath(seg, inkPaint)
        val pad = w + 2f
        addDirty(mx, my, pad); addDirty(sx, sy, pad); addDirty(midX, midY, pad)
        mx = midX; my = midY
        sx = x; sy = y
        sw = w
    }

    private fun endStroke() {
        seg.reset()
        seg.moveTo(mx, my)
        seg.lineTo(sx, sy)
        inkPaint.strokeWidth = sw
        bmpCanvas?.drawPath(seg, inkPaint)
        addDirty(mx, my, sw + 2f); addDirty(sx, sy, sw + 2f)
    }

    private fun eraseTo(x: Float, y: Float, size: Float) {
        erasePaint.strokeWidth = size
        bmpCanvas?.drawLine(ex, ey, x, y, erasePaint)
        val pad = size / 2f + 2f
        addDirty(ex, ey, pad); addDirty(x, y, pad)
        ex = x; ey = y
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(e)
                val isStylus = e.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS
                val major = e.getTouchMajor(i)
                val free = stylusId == -1 && eraseId == -1
                if (isStylus || (fingerDraws && free && major < palmSize)) {
                    eraseId = -1
                    stylusId = id
                    realPressure = isStylus
                    startStroke(e.getX(i), e.getY(i), e.getPressure(i))
                } else if (free && major >= palmSize) {
                    eraseId = id
                    ex = e.getX(i); ey = e.getY(i)
                    eraseTo(ex, ey, major)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == stylusId) {
                        for (h in 0 until e.historySize) {
                            addPoint(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalPressure(i, h))
                        }
                        addPoint(e.getX(i), e.getY(i), e.getPressure(i))
                    } else if (id == eraseId) {
                        for (h in 0 until e.historySize) {
                            eraseTo(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalTouchMajor(i, h))
                        }
                        eraseTo(e.getX(i), e.getY(i), e.getTouchMajor(i))
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == stylusId) {
                    endStroke(); stylusId = -1
                } else if (id == eraseId) {
                    eraseId = -1
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                stylusId = -1; eraseId = -1
            }
        }
        flushDirty()
        return true
    }
}
