package com.ms.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// الأقلام: الترتيب ده محفوظ في الملفات، فمتغيرش ترتيبه
enum class Pen { INK, PENCIL, MARKER, HIGHLIGHTER, BRUSH, FEATHER, NEON, STRIPED }

// أنواع الصفحات
enum class PaperStyle { BLANK, GRID, LINED, DOTS }

class DrawView(context: Context) : View(context) {

    // الأداة الحالية: قلم أو ممحاة
    private enum class Tool { PEN, ERASER }

    var onChanged: (() -> Unit)? = null
    var onHistoryChanged: (() -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null
    var onZoomSettled: ((Float) -> Unit)? = null

    var underlay: Bitmap? = null
        set(v) {
            field = v
            invalidate()
        }

    private class Action(val added: MutableList<Stroke>, val removed: MutableList<Stroke>)
    private val undoStack = ArrayList<Action>()
    private val redoStack = ArrayList<Action>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private val maxBmpScale = 2.5f
    private var bmpScale = 1f
    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private val path = Path()
    private val dirty = RectF()
    private var hasDirty = false

    private val density = resources.displayMetrics.density
    private val palmSize = 100f * density
    var penWidth = 7f * density

    private var tool = Tool.PEN

    // الممحاة: true لما الأداة الحالية ممحاة
    var eraserMode: Boolean
        get() = tool == Tool.ERASER
        set(v) {
            tool = if (v) Tool.ERASER else Tool.PEN
        }

    // ---- التكبير والتصغير ----
    var zoom = 1f
        private set
    private var tx = 0f
    private var ty = 0f
    private val minZoom = 0.5f
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

    var pen = Pen.INK
        private set

    // ألوان افتراضية زي المنصة
    private val colors = intArrayOf(
        0xFF0F172A.toInt(), // حبر
        0xFF334155.toInt(), // رصاص
        0xFFDC2626.toInt(), // ماركر عريض
        0xFF8B5CF6.toInt(), // تظليل
        0xFF0F172A.toInt(), // بروش
        0xFF0F172A.toInt(), // ريشة
        0xFF22D3EE.toInt(), // نيون
        0xFF2563EB.toInt()  // مخطط
    )
    private val sizes = floatArrayOf(0.6f, 1f, 1.6f, 2.4f)
    private var sizeIndex = 1
    private val widthScale: Float get() = sizes[sizeIndex]

    private val strokes = ArrayList<Stroke>()
    private var inkStroke: Stroke? = null
    private var painter: Painter? = null
    private var coordW = 0
    private var coordH = 0
    private val pencilShaders = HashMap<Int, BitmapShader>()

    private var paperColor = 0xFFFFFFFF.toInt()
    private var paperStyle = PaperStyle.GRID
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = density }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.BEVEL
    }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.ROUND
    }
    private val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val stripeOver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xAA94A3B8.toInt()
    }

    // ---- الممحاة (بتقص الخطوط اللي جوه الدايرة) ----
    private val eraserScreenRadius = 20f * density
    private val fingerEraseRadius = 24f * density
    private var eraseAction: Action? = null
    private var eraseRadius = 0f
    private var ex = 0f
    private var ey = 0f
    private var showCursor = false
    private var pendingRebuild = false
    private var lastRebuild = 0L

    private var stylusId = -1
    private var stylusIsFinger = false
    private var eraseId = -1
    private var realPressure = true
    private var lastT = 0L

    // ---- المرسوم حالياً ----
    // كل قلم ليه طريقة رسم، والخط المحفوظ بيترسم بنفس الطريقة بالظبط
    private inner class Painter(val pen: Pen, val col: Int) {
        var sx = 0f
        var sy = 0f
        var mx = 0f
        var my = 0f
        var penW = 0f
        var acc = 0f
        val strokePath = Path()

        fun begin(c: Canvas, x: Float, y: Float, w: Float) {
            sx = x; sy = y; mx = x; my = y
            penW = w
            acc = 0f
            if (pen == Pen.HIGHLIGHTER) {
                strokePath.reset()
                strokePath.moveTo(x, y)
                strokePath.lineTo(x + 0.01f, y)
            } else {
                drawSeg(c, pen, col, acc, x, y, x, y, x, y, w)
            }
        }

        fun step(c: Canvas, x: Float, y: Float, target: Float, k: Float) {
            val midX = (x + sx) / 2f
            val midY = (y + sy) / 2f
            val w = penW * (1f - k) + target * k
            if (pen == Pen.HIGHLIGHTER) {
                strokePath.quadTo(sx, sy, midX, midY)
            } else {
                drawSeg(c, pen, col, acc, mx, my, sx, sy, midX, midY, w)
            }
            acc += hypot(midX - mx, midY - my)
            mx = midX; my = midY
            sx = x; sy = y
            penW = w
        }

        fun end(c: Canvas) {
            if (pen == Pen.HIGHLIGHTER) {
                strokePath.lineTo(sx, sy)
                hlPaint.color = col
                hlPaint.strokeWidth = penW
                c.drawPath(strokePath, hlPaint)
                strokePath.reset()
            } else {
                drawSeg(c, pen, col, acc, mx, my, (mx + sx) / 2f, (my + sy) / 2f, sx, sy, penW)
            }
        }
    }

    // ---- واجهة للشريط ----
    fun selectPen(p: Pen) {
        pen = p
        tool = Tool.PEN
    }

    fun setColor(c: Int) {
        colors[pen.ordinal] = c
    }

    fun cycleSize(): Float {
        sizeIndex = (sizeIndex + 1) % sizes.size
        return widthScale
    }

    // لون الورقة ولون الخطوط
    fun setPaper(paper: Int, grid: Int) {
        paperColor = paper
        gridPaint.color = grid
        dotPaint.color = grid
        invalidate()
    }

    fun setPaperStyle(style: PaperStyle) {
        paperStyle = style
        invalidate()
    }

    // ---- التكبير والتصغير ----
    fun zoomIn() = zoomBy(1.25f)
    fun zoomOut() = zoomBy(0.8f)

    fun zoomBy(factor: Float) {
        setZoom(zoom * factor, width / 2f, height / 2f)
    }

    fun resetZoom() {
        zoom = 1f
        clampPan()
        settleZoom()
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
        settleZoom()
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    private fun clampPan() {
        if (zoom <= 1f) {
            tx = width * (1f - zoom) / 2f
            ty = height * (1f - zoom) / 2f
            return
        }
        tx = tx.coerceIn(width * (1f - zoom), 0f)
        ty = ty.coerceIn(height * (1f - zoom), 0f)
    }

    private fun settleZoom() {
        if (coordW <= 0) return
        val target = zoom.coerceIn(1f, maxBmpScale)
        if (abs(target - bmpScale) > 0.05f) {
            rebuildBitmap(target)
            onZoomSettled?.invoke(target)
        }
    }

    private fun cX(x: Float) = (x - tx) / zoom
    private fun cY(y: Float) = (y - ty) / zoom

    fun clearAll() {
        if (strokes.isEmpty()) return
        val old = ArrayList(strokes)
        strokes.clear()
        inkStroke = null
        painter = null
        bitmap?.eraseColor(Color.TRANSPARENT)
        record(emptyList(), old)
        invalidate()
        onChanged?.invoke()
    }

    // ---- التراجع والإعادة ----
    private fun record(added: List<Stroke>, removed: List<Stroke>) {
        undoStack.add(Action(ArrayList(added), ArrayList(removed)))
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
        if (coordW > 0 && coordH > 0) rebuildBitmap()
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
        onHistoryChanged?.invoke()
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
        rebuildBitmap()
    }

    private fun rebuildBitmap(sc: Float = bmpScale) {
        if (coordW <= 0 || coordH <= 0) return
        bmpScale = sc
        val nb = Bitmap.createBitmap(
            (coordW * sc).toInt().coerceAtLeast(1),
            (coordH * sc).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val nc = Canvas(nb)
        nc.scale(sc, sc)
        for (s in strokes) drawStroke(nc, s)
        bitmap = nb
        bmpCanvas = nc
        pendingRebuild = false
        lastRebuild = System.currentTimeMillis()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) layoutTo(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Theme.BG)
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(zoom, zoom)
        canvas.clipRect(0f, 0f, coordW.toFloat(), coordH.toFloat())
        canvas.drawColor(paperColor)

        val u = underlay
        if (u != null) {
            val s = min(width.toFloat() / u.width, height.toFloat() / u.height)
            val dw = u.width * s
            val dh = u.height * s
            val l = (width - dw) / 2f
            val t = (height - dh) / 2f
            canvas.drawBitmap(u, null, RectF(l, t, l + dw, t + dh), bitmapPaint)
        } else {
            drawPaper(canvas)
        }

        bitmap?.let {
            canvas.drawBitmap(it, null, RectF(0f, 0f, coordW.toFloat(), coordH.toFloat()), bitmapPaint)
        }

        // معاينة التظليل وهو بيتكتب
        val pt = painter
        if (pt != null && pt.pen == Pen.HIGHLIGHTER) {
            hlPaint.color = pt.col
            hlPaint.strokeWidth = pt.penW
            canvas.drawPath(pt.strokePath, hlPaint)
        }

        if (showCursor) {
            cursorPaint.strokeWidth = 1.5f * density / zoom
            canvas.drawCircle(ex, ey, eraseRadius, cursorPaint)
        }
        canvas.restore()
    }

    // رسم الصفحة حسب نوعها (فاضية، مربعات، مسطرة، نقط)
    private fun drawPaper(canvas: Canvas) {
        if (paperStyle == PaperStyle.BLANK) return
        val clip = canvas.clipBounds
        val step = (if (paperStyle == PaperStyle.LINED) 32f else 40f) * density

        if (paperStyle == PaperStyle.DOTS) {
            val r = 1.4f * density
            var y = (clip.top / step).toInt() * step
            while (y <= clip.bottom) {
                var x = (clip.left / step).toInt() * step
                while (x <= clip.right) {
                    canvas.drawCircle(x, y, r, dotPaint)
                    x += step
                }
                y += step
            }
            return
        }

        if (paperStyle == PaperStyle.GRID) {
            var x = (clip.left / step).toInt() * step
            while (x <= clip.right) {
                canvas.drawLine(x, clip.top.toFloat(), x, clip.bottom.toFloat(), gridPaint)
                x += step
            }
        }
        var y = (clip.top / step).toInt() * step
        while (y <= clip.bottom) {
            canvas.drawLine(clip.left.toFloat(), y, clip.right.toFloat(), y, gridPaint)
            y += step
        }
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

    private fun isLight(c: Int): Boolean {
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b > 0.7f
    }

    // رسم قطعة واحدة من الخط حسب نوع القلم
    private fun drawSeg(
        c: Canvas, p: Pen, color: Int, acc: Float,
        x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w: Float
    ) {
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo(cx, cy, x1, y1)
        when (p) {
            Pen.PENCIL -> {
                pencilPaint.shader = pencilShader(color)
                pencilPaint.strokeWidth = w
                c.drawPath(path, pencilPaint)
            }
            Pen.MARKER -> {
                markerPaint.color = color
                markerPaint.alpha = 235
                markerPaint.strokeWidth = w
                c.drawPath(path, markerPaint)
            }
            Pen.FEATHER -> {
                // سن مايل: الخط بيطلع عريض في اتجاه ورفيع في التاني
                featherPaint.color = color
                featherPaint.strokeWidth = max(1f, w * 0.3f)
                val ux = cos(-0.6f)
                val uy = sin(-0.6f)
                for (k in -3..3) {
                    val off = (k / 3f) * w * 0.9f
                    c.save()
                    c.translate(ux * off, uy * off)
                    c.drawPath(path, featherPaint)
                    c.restore()
                }
            }
            Pen.NEON -> {
                glowPaint.color = color
                glowPaint.strokeWidth = w * 1.2f
                glowPaint.setShadowLayer(w * 1.6f, 0f, 0f, color)
                c.drawPath(path, glowPaint)
                corePaint.strokeWidth = max(1f, w * 0.4f)
                c.drawPath(path, corePaint)
            }
            Pen.STRIPED -> {
                inkPaint.color = color
                inkPaint.strokeWidth = w
                c.drawPath(path, inkPaint)
                // شرطات بلون متباين بتتكرر على طول الخط
                val period = max(4f, w * 3.7f)
                if ((acc % period) < period * 0.6f) {
                    stripeOver.color = if (isLight(color)) 0xFFF59E0B.toInt() else 0xFFF8FAFC.toInt()
                    stripeOver.strokeWidth = max(1.5f, w * 0.47f)
                    c.drawPath(path, stripeOver)
                }
            }
            Pen.HIGHLIGHTER -> {
                // بيتعمل في Painter.end
            }
            else -> {
                inkPaint.color = color
                inkPaint.strokeWidth = w
                c.drawPath(path, inkPaint)
            }
        }
    }

    private fun widthFor(pr: Float, speedF: Float, p: Pen): Float {
        val q = (if (realPressure) pr else 0.7f).coerceIn(0f, 1f)
        val base = penWidth * widthScale
        return when (p) {
            Pen.INK -> base * (0.35f + q)
            Pen.PENCIL -> base * 0.6f * (0.6f + 0.8f * q)
            Pen.MARKER -> base * 2.6f
            Pen.HIGHLIGHTER -> base * 4.5f
            Pen.BRUSH -> base * 2.4f * (0.2f + 0.9f * q) * speedF
            Pen.FEATHER -> base * (0.9f + 0.5f * q)
            Pen.NEON -> base * (1.0f + 0.3f * q)
            Pen.STRIPED -> base * 1.7f
        }
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
        lastT = t
        val w = widthFor(pr, 1f, p0)
        val pt = Painter(p0, color)
        bmpCanvas?.let { pt.begin(it, x, y, w) }
        painter = pt
        s.add(x, y, w)
        addDirty(x, y, w + 4f)
    }

    private fun addPoint(x: Float, y: Float, pr: Float, t: Long) {
        val s = inkStroke ?: return
        val pt = painter ?: return
        val dt = max(1L, t - lastT).toFloat()
        val speedDp = hypot(x - pt.sx, y - pt.sy) * zoom / dt / density
        val speedF = (1.3f - speedDp * 0.5f).coerceIn(0.3f, 1.3f)
        val target = widthFor(pr, speedF, s.pen)
        val k = if (s.pen == Pen.BRUSH) 0.3f else 0.5f
        val px = pt.sx
        val py = pt.sy
        bmpCanvas?.let { pt.step(it, x, y, target, k) }
        addDirty(px, py, target + 6f)
        addDirty(x, y, target + 6f)
        lastT = t
        s.add(x, y, target)
    }

    private fun endStroke() {
        val pt = painter
        if (pt != null) bmpCanvas?.let { pt.end(it) }
        painter = null
        inkStroke = null
        onChanged?.invoke()
    }

    // لما إصبعين يتقفلوا والصبع كان بيكتب: نلغي الخط ده ونبدأ التكبير
    private fun cancelFingerStroke() {
        val s = inkStroke ?: return
        strokes.remove(s)
        if (undoStack.isNotEmpty() && undoStack.last().added.contains(s)) {
            undoStack.removeAt(undoStack.size - 1)
            onHistoryChanged?.invoke()
        }
        inkStroke = null
        painter = null
        stylusId = -1
        stylusIsFinger = false
        rebuildBitmap()
    }

    // ---- الممحاة ----
    private fun beginErase(id: Int, x: Float, y: Float, rBoard: Float) {
        if (eraseId != -1) return
        eraseId = id
        startErase(x, y, rBoard)
    }

    private fun startErase(x: Float, y: Float, r: Float) {
        val act = Action(ArrayList(), ArrayList())
        undoStack.add(act)
        redoStack.clear()
        eraseAction = act
        eraseRadius = r
        ex = x
        ey = y
        showCursor = true
        onHistoryChanged?.invoke()
        eraseTo(x, y, r)
    }

    private fun eraseTo(x: Float, y: Float, r: Float) {
        val dist = hypot(x - ex, y - ey)
        val steps = max(1, min(40, (dist / (r * 0.5f)).toInt()))
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            cutStrokes(ex + (x - ex) * t, ey + (y - ey) * t, r)
        }
        ex = x
        ey = y
        eraseRadius = r
        showCursor = true
        if (pendingRebuild && System.currentTimeMillis() - lastRebuild > 30L) rebuildBitmap()
        invalidate()
    }

    // بتقص الخطوط اللي جوه الدايرة وتسيب الباقي
    private fun cutStrokes(cx: Float, cy: Float, r: Float) {
        val act = eraseAction ?: return
        val snapshot = ArrayList(strokes)
        for (s in snapshot) {
            if (s.erase) continue
            val n = s.size / 3
            val d = s.data
            var hit = false
            for (i in 0 until n) {
                val k = i * 3
                if (hypot(d[k] - cx, d[k + 1] - cy) < r + d[k + 2] / 2f) { hit = true; break }
            }
            if (!hit) continue

            val pieces = ArrayList<Stroke>()
            var cur: Stroke? = null
            for (i in 0 until n) {
                val k = i * 3
                val inside = hypot(d[k] - cx, d[k + 1] - cy) < r + d[k + 2] / 2f
                if (inside) {
                    val c = cur
                    if (c != null && c.size / 3 >= 2) pieces.add(c)
                    cur = null
                } else {
                    var c = cur
                    if (c == null) {
                        c = Stroke(s.pen, s.color, false)
                        cur = c
                    }
                    c.add(d[k], d[k + 1], d[k + 2])
                }
            }
            val last = cur
            if (last != null && last.size / 3 >= 2) pieces.add(last)

            strokes.remove(s)
            if (!act.added.remove(s)) act.removed.add(s)
            strokes.addAll(pieces)
            act.added.addAll(pieces)
            pendingRebuild = true
        }
    }

    private fun endErase() {
        val act = eraseAction ?: return
        if (act.added.isEmpty() && act.removed.isEmpty()) undoStack.remove(act)
        eraseAction = null
        showCursor = false
        eraseId = -1
        rebuildBitmap()
        onHistoryChanged?.invoke()
        onChanged?.invoke()
        invalidate()
    }

    // ---- إعادة رسم خط محفوظ بنفس طريقة الرسم المباشر ----
    private fun drawStroke(c: Canvas, s: Stroke) {
        if (s.erase) return
        val n = s.size / 3
        if (n == 0) return
        val d = s.data
        val k = if (s.pen == Pen.BRUSH) 0.3f else 0.5f
        val p = Painter(s.pen, s.color)
        p.begin(c, d[0], d[1], d[2])
        for (i in 1 until n) {
            val j = i * 3
            p.step(c, d[j], d[j + 1], d[j + 2], k)
        }
        p.end(c)
    }

    // ---- بداية إصبعين: تكبير وتحريك ----
    private fun beginGesture(e: MotionEvent) {
        if (eraseId != -1) endErase()
        gesture = true
        gIdA = e.getPointerId(0)
        gIdB = e.getPointerId(1)
        gDist = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)).coerceAtLeast(1f)
        gScale = zoom
        gTx = tx
        gTy = ty
        gMx = (e.getX(0) + e.getX(1)) / 2f
        gMy = (e.getY(0) + e.getY(1)) / 2f
    }

    private fun moveGesture(e: MotionEvent) {
        val a = e.findPointerIndex(gIdA)
        val b = e.findPointerIndex(gIdB)
        if (a < 0 || b < 0) return
        val ax = e.getX(a); val ay = e.getY(a)
        val bx = e.getX(b); val by = e.getY(b)
        val dist = hypot(bx - ax, by - ay).coerceAtLeast(1f)
        val mx2 = (ax + bx) / 2f
        val my2 = (ay + by) / 2f
        val nz = (gScale * dist / gDist).coerceIn(minZoom, maxZoom)
        val ox = (gMx - gTx) / gScale
        val oy = (gMy - gTy) / gScale
        zoom = nz
        tx = mx2 - ox * nz
        ty = my2 - oy * nz
        clampPan()
        invalidate()
        onZoomChanged?.invoke(zoom)
    }

    // ---- اللمس ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(e)
                val tool0 = e.getToolType(i)
                val isStylus = tool0 == MotionEvent.TOOL_TYPE_STYLUS
                val isEraserTip = tool0 == MotionEvent.TOOL_TYPE_ERASER
                val major = e.getTouchMajor(i)
                val x = e.getX(i)
                val y = e.getY(i)
                val bx = cX(x)
                val by = cY(y)
                val bothSmall = e.pointerCount >= 2 &&
                        e.getTouchMajor(0) < palmSize && e.getTouchMajor(1) < palmSize

                when {
                    // طرف الممحاة في القلم، أو القلم نفسه وإحنا في وضع الممحاة
                    isEraserTip || (isStylus && tool == Tool.ERASER) -> {
                        beginErase(id, bx, by, eraserScreenRadius / zoom)
                    }
                    // القلم بيكتب
                    isStylus -> {
                        if (stylusId == -1) {
                            realPressure = true
                            stylusIsFinger = false
                            stylusId = id
                            startStroke(bx, by, e.getPressure(i), e.eventTime)
                        }
                    }
                    // كف اليد كبير: بيمسح
                    major >= palmSize -> {
                        beginErase(id, bx, by, (major / 2f) / zoom)
                    }
                    // إصبعين صغيرين: تكبير وتحريك
                    bothSmall && !gesture && (stylusId == -1 || stylusIsFinger) -> {
                        if (stylusIsFinger) cancelFingerStroke()
                        beginGesture(e)
                    }
                    // إصبع واحد في وضع الممحاة: بيمسح
                    !gesture && stylusId == -1 && eraseId == -1 && e.pointerCount == 1 && tool == Tool.ERASER -> {
                        beginErase(id, bx, by, fingerEraseRadius / zoom)
                    }
                    // إصبع واحد في وضع القلم: بيكتب
                    !gesture && stylusId == -1 && e.pointerCount == 1 -> {
                        realPressure = false
                        stylusIsFinger = true
                        stylusId = id
                        startStroke(bx, by, 0.7f, e.eventTime)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (gesture) moveGesture(e)
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    when {
                        !gesture && id == stylusId -> {
                            for (h in 0 until e.historySize) {
                                addPoint(
                                    cX(e.getHistoricalX(i, h)), cY(e.getHistoricalY(i, h)),
                                    e.getHistoricalPressure(i, h), e.getHistoricalEventTime(h)
                                )
                            }
                            addPoint(cX(e.getX(i)), cY(e.getY(i)), e.getPressure(i), e.eventTime)
                        }
                        !gesture && id == eraseId -> {
                            val r = eraseRadius
                            for (h in 0 until e.historySize) {
                                eraseTo(cX(e.getHistoricalX(i, h)), cY(e.getHistoricalY(i, h)), r)
                            }
                            eraseTo(cX(e.getX(i)), cY(e.getY(i)), r)
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == stylusId) {
                    endStroke()
                    stylusId = -1
                    stylusIsFinger = false
                } else if (id == eraseId) {
                    endErase()
                }
                if (gesture && (id == gIdA || id == gIdB)) {
                    gesture = false
                    settleZoom()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (inkStroke != null) endStroke()
                stylusId = -1
                stylusIsFinger = false
                if (eraseId != -1) endErase()
                eraseId = -1
                if (gesture) {
                    gesture = false
                    settleZoom()
                }
                onChanged?.invoke()
            }
        }
        flushDirty()
        return true
    }
}
