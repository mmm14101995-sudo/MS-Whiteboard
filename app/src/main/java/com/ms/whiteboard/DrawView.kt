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

    var onChanged: (() -> Unit)? = null
    var onHistoryChanged: (() -> Unit)? = null

    private class Action(val added: List<Stroke>, val removed: List<Stroke>)
    private val undoStack = ArrayList<Action>()
    private val redoStack = ArrayList<Action>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

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

    private val colors = intArrayOf(
        0xFF000000.toInt(), 0xFF37474F.toInt(), 0xFF1E88E5.toInt(),
        0xFFFFEB3B.toInt(), 0xFF212121.toInt()
    )
    private val sizes = floatArrayOf(0.6f, 1f, 1.6f, 2.4f)
    private var sizeIndex = 1
    private val widthScale: Float get() = sizes[sizeIndex]

    private val strokes = ArrayList<Stroke>()
    private var inkStroke: Stroke? = null
    private var eraseStroke: Stroke? = null
    private var coordW = 0
    private var coordH = 0
    private val pencilShaders = HashMap<Int, BitmapShader>()

    private var paperColor = 0xFFFFFFFF.toInt()
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = density }

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

    private var stylusId = -1
    private var eraseId = -1
    private var realPressure = true
    private var sx = 0f; private var sy = 0f
    private var mx = 0f; private var my = 0f
    private var sw = 0f
    private var lastT = 0L
    private var ex = 0f; private var ey = 0f

    // ---- واجهة للشريط ----
    fun selectPen(p: Pen) {
        pen = p
        eraserMode = false
    }

    fun setColor(c: Int) {
        colors[pen.ordinal] = c
    }

    fun cycleSize(): Float {
        sizeIndex = (sizeIndex + 1) % sizes.size
        return widthScale
    }

    fun setPaper(paper: Int, grid: Int) {
        paperColor = paper
        gridPaint.color = grid
        invalidate()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        val old = ArrayList(strokes)
        strokes.clear()
        inkStroke = null
        eraseStroke = null
        hlActive = false
        bitmap?.eraseColor(Color.TRANSPARENT)
        record(emptyList(), old)
        invalidate()
        onChanged?.invoke()
    }

    // ---- التراجع والإعادة ----
    private fun record(added: List<Stroke>, removed: List<Stroke>) {
        undoStack.add(Action(added, removed))
        redoStack.clear()
        onHistoryChanged?.invoke()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        strokes.removeAll(a.added)
        strokes.addAll(a.removed)
        redoStack.add(a)
        afterHistory()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        strokes.removeAll(a.removed)
        strokes.addAll(a.added)
        undoStack.add(a)
        afterHistory()
    }

    private fun afterHistory() {
        if (width > 0 && height > 0) layoutTo(width, height)
        invalidate()
        onChanged?.invoke()
        onHistoryChanged?.invoke()
    }

    // ---- الحفظ والاسترجاع ----
    fun snapshotJson(): String = BoardStore.toJson(strokes, coordW, coordH)

    fun restore(json: String) {
        val d = BoardStore.fromJson(json) ?: return
        strokes.clear()
        strokes.addAll(d.strokes)
        undoStack.clear()
        redoStack.clear()
        coordW = d.w
        coordH = d.h
        if (width > 0 && height > 0) layoutTo(width, height)
        invalidate()
    }

    private fun layoutTo(w: Int, h: Int) {
        if (coordW > 0 && coordH > 0 && (coordW != w || coordH != h)) {
            val fx = w.toFloat() / coordW
            val fy = h.toFloat() / coordH
            for (s in strokes) {
                var i = 0
                while (i < s.size) {
                    s.data[i] *= fx
                    s.data[i + 1] *= fy
                    i += 3
                }
            }
        }
        coordW = w
        coordH = h
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nc = Canvas(nb)
        for (s in strokes) drawStroke(nc, s)
        bitmap = nb
        bmpCanvas = nc
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) layoutTo(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paperColor)
        val step = 40f * density
        var x = step
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        if (hlActive) canvas.drawPath(hlPath, hlPaint)
    }

    private fun pencilShader(c: Int): BitmapShader = pencilShaders.getOrPut(c) {
        val n = 48
        val px = IntArray(n * n)
        val rnd = Random(7)
        val rgb = c and 0x00FFFFFF
        for (i in px.indices) {
            px[i] = if (rnd.nextFloat() < 0.8f) ((120 + rnd.nextInt(136)) shl 24) or rgb else 0
        }
        val bm = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun paintFor(p: Pen, color: Int, width: Float): Paint = when (p) {
        Pen.PENCIL -> pencilPaint.apply {
            shader = pencilShader(color)
            strokeWidth = width
        }
        Pen.HIGHLIGHTER -> hlPaint.apply {
            this.color = color
            strokeWidth = width
        }
        else -> inkPaint.apply {
            this.color = color
            strokeWidth = width
        }
    }

    private fun widthFor(pr: Float, speedF: Float, p: Pen): Float {
        val q = (if (realPressure) pr else 0.7f).coerceIn(0f, 1f)
        val base = penWidth * widthScale
        return when (p) {
            Pen.INK -> base * (0.35f + q)
            Pen.PENCIL -> base * 0.6f * (0.6f + 0.8f * q)
            Pen.MARKER -> base * 2.4f
            Pen.HIGHLIGHTER -> base * 4.5f
            Pen.BRUSH -> base * 2.4f * (0.2f + 0.9f * q) * speedF
        }
    }

    private fun eraseSize(major: Float): Float =
        if (eraserMode) max(penWidth * 4f, major) else major

    private fun addDirty(x: Float, y: Float, pad: Float) {
        val l = x - pad; val t = y - pad; val r = x + pad; val b = y + pad
        if (!hasDirty) {
            dirty.set(l, t, r, b)
            hasDirty = true
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

    // ---- رسم الخط مباشرة وقت الكتابة ----
    private fun startStroke(x: Float, y: Float, pr: Float, t: Long) {
        val p0 = pen
        val color = colors[p0.ordinal]
        val s = Stroke(p0, color, false)
        strokes.add(s)
        inkStroke = s
        record(listOf(s), emptyList())
        sx = x; sy = y; mx = x; my = y
        lastT = t
        sw = widthFor(pr, 1f, p0)
        val paint = paintFor(p0, color, sw)
        if (p0 == Pen.HIGHLIGHTER) {
            hlActive = true
            hlPath.reset()
            hlPath.moveTo(x, y)
            hlPath.lineTo(x + 0.01f, y)
        } else {
            bmpCanvas?.drawPoint(x, y, paint)
        }
        s.add(x, y, sw)
        addDirty(x, y, sw)
    }

    private fun addPoint(x: Float, y: Float, pr: Float, t: Long) {
        val s = inkStroke ?: return
        val p0 = s.pen
        val midX = (x + sx) / 2f
        val midY = (y + sy) / 2f
        val dt = max(1L, t - lastT).toFloat()
        val speedDp = hypot(x - sx, y - sy) / dt / density
        val speedF = (1.3f - speedDp * 0.5f).coerceIn(0.3f, 1.3f)
        val target = widthFor(pr, speedF, p0)
        val k = if (p0 == Pen.BRUSH) 0.3f else 0.5f
        val w = sw * (1f - k) + target * k
        if (p0 == Pen.HIGHLIGHTER) {
            hlPath.quadTo(sx, sy, midX, midY)
        } else {
            seg.reset()
            seg.moveTo(mx, my)
            seg.quadTo(sx, sy, midX, midY)
            bmpCanvas?.drawPath(seg, paintFor(p0, s.color, w))
        }
        val pad = w + 2f
        addDirty(mx, my, pad); addDirty(sx, sy, pad); addDirty(midX, midY, pad)
        mx = midX; my = midY
        sx = x; sy = y
        sw = w
        lastT = t
        s.add(x, y, target)
    }

    private fun endStroke() {
        val s = inkStroke ?: return
        if (s.pen == Pen.HIGHLIGHTER) {
            hlPath.lineTo(sx, sy)
            bmpCanvas?.drawPath(hlPath, paintFor(s.pen, s.color, sw))
            hlPath.reset()
            hlActive = false
        } else {
            seg.reset()
            seg.moveTo(mx, my)
            seg.lineTo(sx, sy)
            bmpCanvas?.drawPath(seg, paintFor(s.pen, s.color, sw))
        }
        addDirty(mx, my, sw + 2f); addDirty(sx, sy, sw + 2f)
        inkStroke = null
        onChanged?.invoke()
    }

    // ---- الممحاة ----
    private fun startErase(x: Float, y: Float, size: Float) {
        val s = Stroke(pen, 0, true)
        strokes.add(s)
        eraseStroke = s
        record(listOf(s), emptyList())
        ex = x; ey = y
        eraseTo(x, y, size)
    }

    private fun eraseTo(x: Float, y: Float, size: Float) {
        erasePaint.strokeWidth = size
        bmpCanvas?.drawLine(ex, ey, x, y, erasePaint)
        val pad = size / 2f + 2f
        addDirty(ex, ey, pad); addDirty(x, y, pad)
        ex = x; ey = y
        eraseStroke?.add(x, y, size)
    }

    // ---- إعادة رسم خط محفوظ (نفس طريقة الرسم المباشر) ----
    private fun drawStroke(c: Canvas, s: Stroke) {
        val n = s.size / 3
        if (n == 0) return
        val d = s.data

        if (s.erase) {
            for (i in 0 until n) {
                val k = i * 3
                erasePaint.strokeWidth = d[k + 2]
                if (i == 0) c.drawLine(d[0], d[1], d[0], d[1], erasePaint)
                else c.drawLine(d[k - 3], d[k - 2], d[k], d[k + 1], erasePaint)
            }
            return
        }

        if (n == 1) {
            c.drawPoint(d[0], d[1], paintFor(s.pen, s.color, d[2]))
            return
        }

        if (s.pen == Pen.HIGHLIGHTER) {
            val path = Path()
            var px0 = d[0]; var py0 = d[1]
            path.moveTo(d[0], d[1])
            for (i in 1 until n) {
                val k = i * 3
                val midX = (d[k] + px0) / 2f
                val midY = (d[k + 1] + py0) / 2f
                path.quadTo(px0, py0, midX, midY)
                px0 = d[k]; py0 = d[k + 1]
            }
            path.lineTo(px0, py0)
            c.drawPath(path, paintFor(s.pen, s.color, d[2]))
            return
        }

        val kf = if (s.pen == Pen.BRUSH) 0.3f else 0.5f
        var sx0 = d[0]; var sy0 = d[1]
        var mx0 = d[0]; var my0 = d[1]
        var sw0 = d[2]
        val paint = paintFor(s.pen, s.color, sw0)
        for (i in 1 until n) {
            val k = i * 3
            val x = d[k]; val y = d[k + 1]; val target = d[k + 2]
            val midX = (x + sx0) / 2f
            val midY = (y + sy0) / 2f
            val w = sw0 * (1f - kf) + target * kf
            seg.reset()
            seg.moveTo(mx0, my0)
            seg.quadTo(sx0, sy0, midX, midY)
            paint.strokeWidth = w
            c.drawPath(seg, paint)
            mx0 = midX; my0 = midY
            sx0 = x; sy0 = y
            sw0 = w
        }
        seg.reset()
        seg.moveTo(mx0, my0)
        seg.lineTo(sx0, sy0)
        paint.strokeWidth = sw0
        c.drawPath(seg, paint)
    }

    // ---- اللمس ----
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
                        if (eraseId == -1) {
                            eraseId = id
                            startErase(e.getX(i), e.getY(i), eraseSize(major))
                        }
                    } else if (stylusId == -1) {
                        realPressure = isStylus
                        stylusId = id
                        startStroke(e.getX(i), e.getY(i), e.getPressure(i), e.eventTime)
                    }
                } else if (free && major >= palmSize) {
                    eraseId = id
                    startErase(e.getX(i), e.getY(i), eraseSize(major))
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
                    endStroke()
                    stylusId = -1
                } else if (id == eraseId) {
                    eraseStroke = null
                    eraseId = -1
                    onChanged?.invoke()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (inkStroke != null) endStroke()
                stylusId = -1
                eraseId = -1
                eraseStroke = null
                onChanged?.invoke()
            }
        }
        flushDirty()
        return true
    }
}
