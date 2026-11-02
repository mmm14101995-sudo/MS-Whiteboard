package com.ms.whiteboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var draw: DrawView
    private lateinit var bar: FloatingBar
    private lateinit var dock: LinearLayout
    private lateinit var store: BoardStore
    private lateinit var undoIcon: GlyphView
    private lateinit var redoIcon: GlyphView
    private lateinit var eraserIcon: PenIconView
    private lateinit var eraserWrap: FrameLayout
    private lateinit var zoomLabel: TextView
    private var projectId = "first"
    private val io = Executors.newSingleThreadExecutor()
    private var popup: PopupWindow? = null
    private val vertical: Boolean get() = bar.vertical

    private val penIcons = HashMap<Pen, PenIconView>()
    private val penWraps = HashMap<Pen, FrameLayout>()
    private val swatches = ArrayList<Pair<Int, View>>()
    private var lastPen: Pen? = null
    private var lastTap = 0L
    private var penColors = IntArray(5)
    private val prefs by lazy { getSharedPreferences("pens", Context.MODE_PRIVATE) }

    private val visible = mutableListOf(Pen.INK, Pen.PENCIL, Pen.MARKER, Pen.HIGHLIGHTER)
    private val hidden = mutableListOf(Pen.BRUSH)

    private val dayPens = intArrayOf(0xFF000000.toInt(), 0xFF37474F.toInt(), 0xFF1E88E5.toInt(), 0xFFFFEB3B.toInt(), 0xFF212121.toInt())
    private val nightPens = intArrayOf(0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt(), 0xFF38BDF8.toInt(), 0xFFFACC15.toInt(), 0xFFF1F5F9.toInt())
    private val dayQuick = intArrayOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFFEB3B.toInt(), 0xFF8E24AA.toInt())
    private val nightQuick = intArrayOf(0xFFFFFFFF.toInt(), 0xFFEF5350.toInt(), 0xFF38BDF8.toInt(), 0xFF66BB6A.toInt(), 0xFFFFEB3B.toInt(), 0xFFAB47BC.toInt())
    private val paletteColors = intArrayOf(
        0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF475569.toInt(), 0xFF94A3B8.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(),
        0xFFDC2626.toInt(), 0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(),
        0xFF16A34A.toInt(), 0xFF10B981.toInt(), 0xFF14B8A6.toInt(), 0xFF06B6D4.toInt(), 0xFF0EA5E9.toInt(), 0xFF2563EB.toInt(),
        0xFF4F46E5.toInt(), 0xFF7C3AED.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFBE185D.toInt(), 0xFF78350F.toInt()
    )

    private fun dp(v: Int): Int = Theme.dp(this, v.toFloat())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun quickColors(): IntArray = if (Theme.dark) nightQuick else dayQuick

    // كل وضع (نهاري/ليلي) ليه ألوان أقلام محفوظة لوحده
    private fun colorKey(i: Int): String = (if (Theme.dark) "n" else "d") + "c$i"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        projectId = intent.getStringExtra("pid") ?: "first"
        store = BoardStore(ProjectStore.boardFile(this, projectId))

        penColors = IntArray(5) { i -> prefs.getInt(colorKey(i), (if (Theme.dark) nightPens else dayPens)[i]) }

        draw = DrawView(this)
        draw.setPaper(Theme.PAPER, Theme.GRID)
        for (p in Pen.values()) {
            draw.selectPen(p)
            draw.setColor(penColors[p.ordinal])
        }
        draw.selectPen(Pen.INK)
        draw.onChanged = { saveNow() }
        draw.onZoomChanged = { z -> zoomLabel.text = "${(z * 100).toInt()}%" }
        draw.onHistoryChanged = { updateHistoryIcons() }
        store.read()?.let { draw.restore(it) }

        eraserIcon = PenIconView(this, PenIconView.ERASER)
        eraserWrap = wrap(eraserIcon)
        bar = FloatingBar(this)
        dock = bar.body

        val root = FrameLayout(this)
        root.addView(draw, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(
            bar,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { setMargins(0, dp(70), dp(10), dp(12)) }
        )
        root.addView(
            topBar(),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(0, dp(8), 0, 0) }
        )
        root.addView(
            zoomBar(),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(dp(12), 0, dp(12), dp(12)) }
        )
        setContentView(root)

        bar.onChanged = { rebuildDock() }
        updateHistoryIcons()
        rebuildDock()
    }

    override fun onPause() {
        super.onPause()
        saveNow()
        ProjectStore.touch(this, projectId)
    }

    private fun saveNow() {
        val json = draw.snapshotJson()
        io.execute { store.write(json) }
    }

    private fun updateHistoryIcons() {
        if (!::undoIcon.isInitialized) return
        undoIcon.alpha = if (draw.canUndo) 1f else 0.3f
        redoIcon.alpha = if (draw.canRedo) 1f else 0.3f
    }

    // ---- الشريط العلوي ----
    private fun topBar(): LinearLayout {
        val name = ProjectStore.list(this).firstOrNull { it.id == projectId }?.name ?: "مشروع"
        undoIcon = GlyphView(this, GlyphView.UNDO).apply { setOnClickListener { draw.undo() } }
        redoIcon = GlyphView(this, GlyphView.REDO).apply { setOnClickListener { draw.redo() } }
        val back = GlyphView(this, GlyphView.BACK).apply { setOnClickListener { finish() } }
        val themeBtn = GlyphView(this, if (Theme.dark) GlyphView.SUN else GlyphView.MOON).apply {
            setOnClickListener {
                Theme.toggle(this@MainActivity)
                recreate()
            }
        }
        val title = TextView(this).apply {
            text = name
            textSize = 14f
            setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(10), 0)
            maxWidth = dp(150)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = Theme.box(this@MainActivity, Theme.PANEL, 24f, Theme.BORDER, 1f)
            addView(back, btnLp())
            addView(title)
            addView(sepView(), sepLp())
            addView(undoIcon, btnLp())
            addView(redoIcon, btnLp())
            addView(sepView(), sepLp())
            addView(themeBtn, btnLp())
        }
    }

    // ---- شريط التكبير ----
    private fun zoomBar(): LinearLayout {
        zoomLabel = TextView(this).apply {
            text = "100%"
            textSize = 12f
            setTextColor(Theme.TEXT)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { draw.resetZoom() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = Theme.box(this@MainActivity, Theme.PANEL, 22f, Theme.BORDER, 1f)
            addView(zoomBtn("+") { draw.zoomIn() }, btnLp())
            addView(zoomLabel, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(zoomBtn("−") { draw.zoomOut() }, btnLp())
        }
    }

    private fun zoomBtn(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 20f
        setTextColor(Theme.TEXT)
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun btnLp(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(40), dp(40))

    private fun sepView(): View = View(this).apply { setBackgroundColor(Theme.BORDER) }
    private fun sepLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(1), dp(22)).apply { setMargins(dp(4), 0, dp(4), 0) }

    private fun wrap(child: View): FrameLayout = FrameLayout(this).apply {
        addView(child, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun selectedBg(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x4038BDF8, 0x40FACC15)
    ).apply { cornerRadius = dp(12).toFloat() }

    private fun penLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(40), dp(56)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }

    private fun sizeLp(w: Int, h: Int, m: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(w), dp(h)).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }

    private fun divider(): View = View(this).apply { setBackgroundColor(Theme.BORDER) }

    private fun dividerLp(): LinearLayout.LayoutParams =
        if (vertical) LinearLayout.LayoutParams(dp(26), dp(1)).apply { setMargins(0, dp(6), 0, dp(6)) }
        else LinearLayout.LayoutParams(dp(1), dp(26)).apply { setMargins(dp(6), 0, dp(6), 0) }

    // ---- خلية لون مربعة ----
    private fun swatchBg(c: Int, selected: Boolean) = Theme.box(
        this, c, 7f,
        if (selected) Theme.ACCENT else Theme.BORDER,
        if (selected) 2.5f else 1f
    )

    // زرار اللون المخصص (مربع متدرج فيه +)
    private fun customBtn(): View = TextView(this).apply {
        text = "+"
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF38BDF8.toInt(), 0xFFFACC15.toInt(), 0xFFEF4444.toInt())
        ).apply {
            cornerRadius = dp(7).toFloat()
            setStroke(dp(1), Theme.BORDER)
        }
        setOnClickListener { showCustomColor() }
    }

    // الخلايا في صفين: صفين جنب بعض في الشريط الرأسي، وعمودين في الأفقي
    private fun swatchGrid(items: List<View>): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val innerOrient = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        var i = 0
        while (i < items.size) {
            val line = LinearLayout(this).apply {
                orientation = innerOrient
                gravity = Gravity.CENTER
            }
            for (k in i until minOf(i + 2, items.size)) line.addView(items[k], sizeLp(26, 26, 3))
            outer.addView(line)
            i += 2
        }
        return outer
    }

    // ---- محتوى الشريط: مفرود أو مضموم ----
    private fun rebuildDock() {
        dock.removeAllViews()
        dock.gravity = Gravity.CENTER
        penIcons.clear()
        penWraps.clear()
        swatches.clear()

        eraserIcon.setOnClickListener {
            draw.eraserMode = true
            refresh()
        }

        // مضموم: القلم الحالي والممحاة بس
        val shown = if (bar.collapsed) listOf(draw.pen) else visible
        for (p in shown) {
            val ic = PenIconView(this, p.ordinal).apply { color = penColors[p.ordinal] }
            ic.setOnClickListener {
                if (bar.collapsed) bar.setCollapsed(false) else onPenTap(p)
            }
            penIcons[p] = ic
            val w = wrap(ic)
            penWraps[p] = w
            dock.addView(w, penLp())
        }

        if (bar.collapsed) {
            dock.addView(eraserWrap, penLp())
            refresh()
            return
        }

        dock.addView(
            TextView(this).apply {
                text = "+"
                textSize = 20f
                setTextColor(Theme.MUTED)
                gravity = Gravity.CENTER
                background = Theme.box(this@MainActivity, 0x1AFFFFFF, 0f, oval = true)
                setOnClickListener { showMore() }
            },
            sizeLp(30, 30, 3)
        )
        dock.addView(divider(), dividerLp())

        val items = ArrayList<View>()
        for (c in quickColors()) {
            val cell = View(this)
            cell.setOnClickListener { applyColor(c) }
            swatches.add(Pair(c, cell))
            items.add(cell)
        }
        items.add(customBtn())
        dock.addView(swatchGrid(items), LinearLayout.LayoutParams(WRAP, WRAP))
        dock.addView(divider(), dividerLp())

        dock.addView(eraserWrap, penLp())
        dock.addView(
            TextView(this).apply {
                text = "مسح"
                textSize = 12f
                setTextColor(Theme.DANGER)
                gravity = Gravity.CENTER
                background = Theme.box(this@MainActivity, 0x33EF4444, 13f)
                setOnClickListener { confirmClear() }
            },
            LinearLayout.LayoutParams(dp(44), dp(26)).apply { setMargins(dp(3), dp(8), dp(3), dp(2)) }
        )

        refresh()
    }

    private fun refresh() {
        for ((p, w) in penWraps) w.background = if (!draw.eraserMode && draw.pen == p) selectedBg() else null
        eraserWrap.background = if (draw.eraserMode) selectedBg() else null
        val current = penColors[draw.pen.ordinal]
        for ((c, v) in swatches) {
            v.background = swatchBg(c, !draw.eraserMode && c == current)
        }
    }

    // ضغطة = اختيار القلم، ضغطتين بسرعة = منتقي ألوان القلم
    private fun onPenTap(p: Pen) {
        val now = System.currentTimeMillis()
        val isDouble = lastPen == p && now - lastTap < 450L
        lastPen = if (isDouble) null else p
        lastTap = now
        if (isDouble) showPalette(p) else {
            draw.selectPen(p)
            refresh()
        }
    }

    private fun applyColor(c: Int) {
        val p = draw.pen
        penColors[p.ordinal] = c
        prefs.edit().putInt(colorKey(p.ordinal), c).apply()
        draw.setColor(c)
        penIcons[p]?.color = c
        draw.eraserMode = false
        refresh()
    }

    // لون مخصص: 3 سلايدرز (أحمر وأخضر وأزرق) مع معاينة
    private fun showCustomColor() {
        val start = penColors[draw.pen.ordinal]
        val preview = View(this).apply { background = Theme.box(this@MainActivity, start, 12f, Theme.BORDER, 1f) }
        val seeks = listOf(
            SeekBar(this).apply { max = 255; progress = Color.red(start) },
            SeekBar(this).apply { max = 255; progress = Color.green(start) },
            SeekBar(this).apply { max = 255; progress = Color.blue(start) }
        )
        fun picked(): Int = Color.rgb(seeks[0].progress, seeks[1].progress, seeks[2].progress)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                preview.background = Theme.box(this@MainActivity, picked(), 12f, Theme.BORDER, 1f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        seeks.forEach { it.setOnSeekBarChangeListener(listener) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(6))
            addView(preview, LinearLayout.LayoutParams(MATCH, dp(56)).apply { setMargins(0, 0, 0, dp(12)) })
            seeks.forEach { addView(it) }
        }

        AlertDialog.Builder(this)
            .setTitle("لون مخصص")
            .setView(content)
            .setPositiveButton("اختار") { _, _ -> applyColor(picked()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPalette(p: Pen) {
        draw.selectPen(p)
        refresh()

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        for (row in 0 until 4) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 6) {
                val c = paletteColors[row * 6 + col]
                val sw = View(this).apply {
                    background = Theme.box(this@MainActivity, c, 0f, Theme.BORDER, 1f, oval = true)
                    setOnClickListener {
                        applyColor(c)
                        popup?.dismiss()
                    }
                }
                line.addView(sw, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            grid.addView(line)
        }
        showPopup(grid)
    }

    private fun showMore() {
        if (hidden.isEmpty()) return
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        for (p in hidden) {
            val ic = PenIconView(this, p.ordinal).apply { color = penColors[p.ordinal] }
            ic.setOnClickListener { pickFromMore(p) }
            list.addView(ic, penLp())
        }
        showPopup(list)
    }

    private fun pickFromMore(p: Pen) {
        val old = visible[visible.size - 1]
        visible[visible.size - 1] = p
        hidden.remove(p)
        hidden.add(old)
        popup?.dismiss()
        draw.selectPen(p)
        rebuildDock()
    }

    private fun showPopup(content: View) {
        popup?.dismiss()
        val pop = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pop.setOutsideTouchable(true)
        pop.setBackgroundDrawable(Theme.box(this, Theme.PANEL, 16f, Theme.BORDER, 1f))
        pop.showAtLocation(window.decorView, Gravity.CENTER, 0, 0)
        popup = pop
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("متأكد إنك عايز تمسح السبورة كلها؟")
            .setPositiveButton("امسح") { _, _ -> draw.clearAll() }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
