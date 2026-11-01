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
    var onZoomChanged: ((Float) -> Unit)? = null

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
    var eraserMode = false

    // الكتابة بالقلم بس. لو true الإصبع الصغير هيرسم كمان (للتجربة)
    var fingerDraws = false

    // ---- التكبير والتحريك ----
    var zoom = 1f
        private set
    private var tx = 0f
    private var ty = 0f
    private val minZoom = 1f
    private val maxZoom = 5f

    private var gesture = false
    private var gIdA = -1
    private var gIdB = -1
    private var gDist = 1f
    private var gScale = 1f
    private var gTx = 0f
    private var gTy = 0f
    private var gMx = 0f
    private var gMy = 0f
    private var panId = -1
    private var lastX = 0f
    private var lastY = 0f

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

    // ---- التكبير ----
    fun zoomIn() = zoomBy(1.25f)
    fun zoomOut() = zoomBy(0.8f)

    fun zoomBy(factor: Float) {
        setZoom(zoom * factor, width / 2f, height / 2f)
    }

    fun resetZoom() {
        zoom = 1f
        tx = 0f
        ty = 0f
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    private fun setZoom(z: Float, cx: Float, cy: Float) {
        val nz = z.coerceIn(minZoom, maxZoom)
        val ox = (cx - tx) / zoom
        val oy = (cy - ty) / zoom
        zoom = nz
        tx = cx - ox * nz
        ty = cy - oy * nz
        clampPan()
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    // ما نسيبش الصفحة تطلع بره الحدود
    private fun clampPan() {
        if (zoom <= 1f) {
            tx = 0f
            ty = 0f
            return
        }
        tx = tx.coerceIn(width * (1f - zoom), 0f)
        ty = ty.coerceIn(height * (1f - zoom), 0f)
    }

    private fun cX(x: Float) = (x - tx) / zoom
    private fun cY(y: Float) = (y - ty) / zoom

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
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(zoom, zoom)
        val clip = canvas.clipBounds
        val step = 40f * density
        var x = (clip.left / step).toInt() * step
        while (x <= clip.right) {
            canvas.drawLine(x, clip.top.toFloat(), x, clip.bottom.toFloat(), gridPaint)
            x += step
        }
        var y = (clip.top / step).toInt() * step
        while (y <= clip.bottom) {
            canvas.drawLine(clip.left.toFloat(), y, clip.right.toFloat(), y, gridPaint)
            y += step
        }
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        if (hlActive) canvas.drawPath(hlPath, hlPaint)
        canvas.restore()
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

    private fun eraseSize(major: Float): Float {
        val base = if (eraserMode) max(penWidth * 4f, major) else major
        return base / zoom
    }

    private fun addDirty(x: Float, y: Float, pad: Float) {
        val l = x - pad; val t = y - pad; val r = x + pad; val b = y + pad
        if (!hasDirty) {
            dirty.set(l, t, r, b)
            hasDirty = true
        } else {
            dirty.set(min(dirty.left, l), min(dirty.top, t), max(dirty.right, r), max(dirty.bottom, b))
        }
    }

    // نحوّل المنطقة المتغيرة من إحداثيات الصفحة لإحداثيات الشاشة
    private fun flushDirty() {
        if (!hasDirty) return
        invalidate(
            (dirty.left * zoom + tx).toInt() - 2, (dirty.top * zoom + ty).toInt() - 2,
            (dirty.right * zoom + tx).toInt() + 2, (dirty.bottom * zoom + ty).toInt() + 2
        )
        hasDirty = false
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
        // السرعة بالشاشة مش بالصفحة، عشان الخط ميتغيرش مع التكبير
        val speedDp = hypot(x - sx, y - sy) * zoom / dt / density
        val speedF = (1.3f - speedDp * 0.5f).coerceIn(0.3f, 1.3f)
        val target = widthFor(pr, speedF, p0)
        val k = if (p0 == Pen.BRUSH) 0.3f else 0.5f
        val w = sw * (1f - k) + target * k
        if (p0 == Pen.HIGHLIGHTER) {
            hlPath.quadTo(sx, sy, midX, midY)
        } else {
            seg.reset()
            seg.m
