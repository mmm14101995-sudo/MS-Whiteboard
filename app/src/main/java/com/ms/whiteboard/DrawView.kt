package com.ms.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Pen { INK, PENCIL, MARKER, HIGHLIGHTER, BRUSH }

class DrawView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private val seg = Path()
    private val hlPath = Path()
    private var hlActive = false
    private val dirty = RectF()
    private var hasDirty = false

    private val density = resources.displayMetrics.density
    private val palmSize = 100f * density
    var penWidth = 7f * density
    var fingerDraws = true
    var eraserMode = false

    var pen = Pen.INK
        private set

    // لون كل قلم (بنفس ترتيب Pen)
    private val colors = intArrayOf(
        0xFF000000.toInt(), // حبر
        0xFF37474F.toInt(), // رصاص
        0xFF1E88E5.toInt(), // ماركر
        0xFFFFEB3B.toInt(), // تمييز
        0xFF212121.toInt()  // ريشة
    )
    private val sizes = floatArrayOf(0.6f, 1f, 1.6f, 2.4f)
    private var sizeIndex = 1
    private val widthScale: Float get() = sizes[sizeIndex]

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pencilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.ROUND
    }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var strokePen = Pen.INK
    private var stylusId = -1
    private var realPressure = true
    private var sx = 0f; private var sy = 0f
    private var mx = 0f; private var my = 0f
    private var sw = 0f
    private var lastT = 0L
    private var eraseId = -1
    private var ex = 0f; private var ey = 0f

    init {
        rebuildPencilShader()
    }

    private fun rebuildPencilShader() {
        val n = 48
        val px = IntArray(n * n)
        val rnd = Random(7)
        val c = colors[Pen.PENCIL.ordinal] and 0x00FFFFFF
        for (i in px.indices) {
            px[i] = if (rnd.nextFloat() < 0.8f) ((120 + rnd.nextInt(136)) shl 24) or c else 0
        }
        val bm = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        pencilPaint.shader = BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---- واجهة للشريط ----
    fun selectPen(p: Pen) {
        pen = p
        eraserMode = false
    }

    fun setColor(c: Int) {
        colors[pen.ordinal] = c
        if (pen == Pen.PENCIL) rebuildPencilShader()
    }

    fun cycleSize(): Float {
        sizeIndex = (sizeIndex + 1) % sizes.size
        return widthScale
    }

    fun clearAll() {
        bitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    // ---- الرسم ----
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
        if (hlActive) canvas.drawPath(hlPath, hlPaint)
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

    private fun widthFor(p: Float, speedF: Float): Float {
        val q = (if (realPressure) p else 0.7f).coerceIn(0f, 1f)
        val base = penWidth * widthScale
        return when (strokePen) {
            Pen.INK -> base * (0.35f + q)
            Pen.PENCIL -> base * 0.6f * (0.6f + 0.8f * q)
            Pen.MARKER -> base * 2.4f
            Pen.HIGHLIGHTER -> base * 4.5f
            Pen.BRUSH -> base * 2.4f * (0.2f + 0.9f * q) * speedF
        }
    }

    private fun eraseSize(major: Float): Float =
        if (eraserMode) max(penWidth * 4f, major) else major

    private fun startStroke(x: Float, y: Float, p: Float, t: Long) {
        strokePen = pen
        sx = x; sy = y; mx = x; my = y
        lastT = t
        sw = widthFor(p, 1f)
        hlActive = strokePen == Pen.HIGHLIGHTER
        when (strokePen) {
            Pen.HIGHLIGHTER -> {
                hlPaint.color = (colors[Pen.HIGHLIGHTER.ordinal] and 0x00FFFFFF) or 0x66000000
                hlPaint.strokeWidth = sw
                hlPath.reset()
                hlPath.moveTo(x, y)
                hlPath.lineTo(x + 0.01f, y)
            }
            Pen.PENCIL -> {
                pencilPaint.strokeWidth = sw
                bmpCanvas?.drawPoint(x, y, pencilPaint)
            }
            else -> {
                inkPaint.color = colors[strokePen.ordinal]
                inkPaint.strokeWidth = sw
                bmpCanvas?.drawPoint(x, y, inkPaint)
            }
        }
        addDirty(x, y, sw)
    }

    private fun addPoint(x: Float, y: Float, p: Float, t: Long) {
        val midX = (x + sx) / 2f
        val midY = (y + sy) / 2f
        val dt = max(1L, t - lastT).toFloat()
        val speedDp = hypot(x - sx, y - sy) / dt / density
        val speedF = (1.3f - speedDp * 0.5f).coerceIn(0.3f, 1.3f)
        val target = widthFor(p, speedF)
        val k = if (strokePen == Pen.BRUSH) 0.3f else 0.5f
        val w = sw * (1f - k) + target * k

        if (strokePen == Pen.HIGHLIGHTER) {
            hlPath.quadTo(sx, sy, midX, midY)
        } else {
            seg.reset()
            seg.moveTo(mx, my)
            seg.quadTo(sx, sy, midX, midY)
            val paint = if (strokePen == Pen.PENCIL) pencilPaint else inkPaint
            paint.strokeWidth = w
            bmpCanvas?.drawPath(seg, paint)
        }
        val pad = w + 2f
        addDirty(mx, my, pad); addDirty(sx, sy, pad); addDirty(midX, midY, pad)
        mx = midX; my = midY
        sx = x; sy = y
        sw = w
        lastT = t
    }

    private fun endStroke() {
        if (strokePen == Pen.HIGHLIGHTER) {
            hlPath.lineTo(sx, sy)
            bmpCanvas?.drawPath(hlPath, hlPaint)
            hlPath.reset()
            hlActive = false
        } else {
            seg.reset()
            seg.moveTo(mx, my)
            seg.lineTo(sx, sy)
            val paint = if (strokePen == Pen.PENCIL) pencilPaint else inkPaint
            paint.strokeWidth = sw
            bmpCanvas?.drawPath(seg, paint)
        }
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
                    if (eraserMode) {
                        stylusId = -1
                        eraseId = id
                        ex = e.getX(i); ey = e.getY(i)
                        eraseTo(ex, ey, eraseSize(major))
                    } else {
                        eraseId = -1
                        stylusId = id
                        realPressure = isStylus
                        startStroke(e.getX(i), e.getY(i), e.getPressure(i), e.eventTime)
                    }
                } else if (free && major >= palmSize) {
                    eraseId = id
                    ex = e.getX(i); ey = e.getY(i)
                    eraseTo(ex, ey, eraseSize(major))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == stylusId) {
                        for (h in 0 until e.historySize) {
                            addPoint(
                                e.getHistoricalX(i, h), e.getHistoricalY(i, h),
                                e.getHistoricalPressure(i, h), e.getHistoricalEventTime(h)
                            )
                        }
                        addPoint(e.getX(i), e.getY(i), e.getPressure(i), e.eventTime)
                    } else if (id == eraseId) {
                        for (h in 0 until e.historySize) {
                            eraseTo(e.getHistoricalX(i, h), e.getHistoricalY(i, h), eraseSize(e.getHistoricalTouchMajor(i, h)))
                        }
                        eraseTo(e.getX(i), e.getY(i), eraseSize(e.getTouchMajor(i)))
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
                if (hlActive) { hlPath.reset(); hlActive = false; invalidate() }
            }
        }
        flushDirty()
        return true
    }
}
