package com.ms.whiteboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
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
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.ms.whiteboard.compose.ToolbarActions
import com.ms.whiteboard.compose.ToolbarState
import com.ms.whiteboard.compose.WhiteboardToolbar
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {

    // وضع اللمس الحالي: قلم، ممحاة، إيد (تحريك)، تحديد
    private enum class Mode { PEN, ERASER, HAND, SELECT }

    private lateinit var draw: DrawView
    private lateinit var store: BoardStore
    private lateinit var undoIcon: GlyphView
    private lateinit var redoIcon: GlyphView
    private lateinit var topBarView: LinearLayout
    private lateinit var zoomBarView: LinearLayout
    private lateinit var zoomLabel: TextView
    private lateinit var toolbarView: ComposeView
    private val tb = ToolbarState()
    private var mode = Mode.PEN
    private var immersive = false
    private var projectId = "first"
    private val io = Executors.newSingleThreadExecutor()
    private var popup: PopupWindow? = null
    private val prefs by lazy { getSharedPreferences("pens", Context.MODE_PRIVATE) }

    private val penColors = IntArray(Pen.values().size)
    private var sizeIdx = 1
    private val sizeLabels = arrayOf("S", "M", "L", "XL")
    private var paperStyle = PaperStyle.GRID
    private var paperColor = 0xFFFFFFFF.toInt()

    // الأقلام الأربعة الظاهرة في الشريط (برتقالي، أسود، بنفسجي، سماوي)، والباقي في زرار +
    private val visible = mutableListOf(Pen.INK, Pen.FEATHER, Pen.HIGHLIGHTER, Pen.NEON)
    private val hidden = mutableListOf(Pen.MARKER, Pen.PENCIL, Pen.STRIPED)

    private val dayPens = intArrayOf(
        0xFFF97316.toInt(), 0xFF334155.toInt(), 0xFFDC2626.toInt(), 0xFF8B5CF6.toInt(),
        0xFF0F172A.toInt(), 0xFF0F172A.toInt(), 0xFF22D3EE.toInt(), 0xFF2563EB.toInt()
    )
    private val nightPens = intArrayOf(
        0xFFFB923C.toInt(), 0xFFCBD5E1.toInt(), 0xFFF87171.toInt(), 0xFFA78BFA.toInt(),
        0xFFF1F5F9.toInt(), 0xFFF1F5F9.toInt(), 0xFF22D3EE.toInt(), 0xFF60A5FA.toInt()
    )
    private val paletteColors = intArrayOf(
        0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF475569.toInt(), 0xFF94A3B8.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(),
        0xFFDC2626.toInt(), 0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(),
        0xFF16A34A.toInt(), 0xFF10B981.toInt(), 0xFF14B8A6.toInt(), 0xFF06B6D4.toInt(), 0xFF0EA5E9.toInt(), 0xFF2563EB.toInt(),
        0xFF4F46E5.toInt(), 0xFF7C3AED.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFBE185D.toInt(), 0xFF78350F.toInt()
    )
    private val dayPapers = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF8E7.toInt(), 0xFFE8F5E9.toInt(), 0xFFE3F2FD.toInt(), 0xFFF5F3FF.toInt(), 0xFFF1F5F9.toInt())
    private val nightPapers = intArrayOf(0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF1C2A1E.toInt(), 0xFF1E1B2E.toInt(), 0xFF1F2933.toInt(), 0xFF111827.toInt())

    private fun dp(v: Int): Int = Theme.dp(this, v.toFloat())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun papers(): IntArray = if (Theme.dark) nightPapers else dayPapers

    // كل وضع (نهاري/ليلي) ليه ألوان أقلام وورق محفوظة لوحده
    private fun colorKey(i: Int): String = (if (Theme.dark) "n" else "d") + "c$i"
    private fun paperKey(): String = if (Theme.dark) "pcn" else "pcd"

    // أفعال الشريط: كل زرار في الشريط بينادي هنا
    private val actions = object : ToolbarActions {
        override fun onPage() = showPaperPicker()
        override fun onDrag(dx: Float, dy: Float) = moveToolbar(dx, dy)
        override fun onToggleExpand() {
            tb.expanded = !tb.expanded
        }
        override fun onClose() {
            tb.visible = false
        }
        override fun onReopen() {
            tb.visible = true
        }
        override fun onPenTap(index: Int) = choosePen(visible[index])
        override fun onPenDoubleTap(index: Int) {
            val p = visible[index]
            choosePen(p)
            showPalette()
        }
        override fun onMore() = showMore()
        override fun onHand() = setMode(Mode.HAND)
        override fun onEraser() = setMode(Mode.ERASER)
        override fun onSelect() = setMode(Mode.SELECT)
        override fun onFullscreen() {
            immersive = !immersive
            val vis = if (immersive) View.GONE else View.VISIBLE
            topBarView.visibility = vis
            zoomBarView.visibility = vis
            tb.fullscreenOn = immersive
        }
        override fun onDeleteSelection() {
            draw.deleteSelected()
            syncToolbar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        projectId = intent.getStringExtra("pid") ?: "first"
        store = BoardStore(ProjectStore.boardFile(this, projectId))

        for (i in penColors.indices) {
            penColors[i] = prefs.getInt(colorKey(i), (if (Theme.dark) nightPens else dayPens)[i])
        }
        paperStyle = PaperStyle.values()[prefs.getInt("ps", PaperStyle.GRID.ordinal).coerceIn(0, PaperStyle.values().size - 1)]
        paperColor = prefs.getInt(paperKey(), papers()[0])

        draw = DrawView(this)
        applyPaper()
        for (p in Pen.values()) {
            draw.selectPen(p)
            draw.setColor(penColors[p.ordinal])
        }
        draw.selectPen(Pen.INK)
        draw.onChanged = { saveNow() }
        draw.onZoomChanged = { z -> zoomLabel.text = "${(z * 100).toInt()}%" }
        draw.onHistoryChanged = { updateHistoryIcons() }
        draw.onSelectionChanged = { syncToolbar() }
        store.read()?.let { draw.restore(it) }

        toolbarView = ComposeView(this).apply {
            setContent { WhiteboardToolbar(state = tb, actions = actions) }
        }
        topBarView = topBar()
        zoomBarView = zoomBar()

        val root = FrameLayout(this)
        root.addView(draw, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(
            toolbarView,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { setMargins(0, dp(60), dp(10), dp(12)) }
        )
        root.addView(
            topBarView,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(0, dp(8), 0, 0) }
        )
        root.addView(
            zoomBarView,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(dp(12), 0, dp(12), dp(12)) }
        )
        setContentView(root)

        updateHistoryIcons()
        syncToolbar()
    }

    // الشاشة كاملة دايماً: بنخفي شريط الحالة وشريط التنقل
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // بعد ما الشاشة تاخد فوكس تاني (مثلاً بعد نافذة أو الرجوع من تطبيق تاني) نرجّع الوضع الكامل
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
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

    // ---- الشريط العائم (Compose) ----
    private fun moveToolbar(dx: Float, dy: Float) {
        val v = toolbarView
        val parent = v.parent as? View ?: return
        val minX = -v.left.toFloat()
        val maxX = (parent.width - v.right).toFloat()
        val minY = -v.top.toFloat()
        val maxY = (parent.height - v.bottom).toFloat()
        val nx = v.translationX + dx
        val ny = v.translationY + dy
        v.translationX = if (maxX >= minX) nx.coerceIn(minX, maxX) else nx
        v.translationY = if (maxY >= minY) ny.coerceIn(minY, maxY) else ny
    }

    private fun choosePen(p: Pen) {
        draw.selectPen(p)
        setMode(Mode.PEN)
    }

    private fun setMode(m: Mode) {
        mode = m
        draw.eraserMode = m == Mode.ERASER
        draw.panMode = m == Mode.HAND
        draw.selectMode = m == Mode.SELECT
        if (m != Mode.SELECT) draw.clearSelection()
        syncToolbar()
    }

    // بنقرأ الحالة الحالية ونحطها في الشريط
    private fun syncToolbar() {
        tb.slotColors = visible.map { ComposeColor(penColors[it.ordinal]) }
        tb.activeSlot = if (mode == Mode.PEN) visible.indexOf(draw.pen) else -1
        tb.eraserOn = mode == Mode.ERASER
        tb.handOn = mode == Mode.HAND
        tb.selectOn = mode == Mode.SELECT
        tb.selectionCount = draw.selectionCount
    }

    private fun applyColor(c: Int) {
        val p = draw.pen
        penColors[p.ordinal] = c
        prefs.edit().putInt(colorKey(p.ordinal), c).apply()
        draw.setColor(c)
        setMode(Mode.PEN)
    }

    // ---- الصفحة (نوعها ولونها) ----
    private fun applyPaper() {
        draw.setPaperStyle(paperStyle)
        draw.setPaper(paperColor, gridFor(paperColor))
    }

    private fun saveAndApplyPaper() {
        prefs.edit().putInt("ps", paperStyle.ordinal).putInt(paperKey(), paperColor).apply()
        applyPaper()
    }

    // لون الخطوط بيتحسب من لون الورقة
    private fun gridFor(c: Int): Int {
        val lum = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        return if (lum > 128f) 0x1F0F172A else 0x2EFFFFFF
    }

    // ---- الشريط العلوي: رجوع، عنوان، تراجع/إعادة، وضع ----
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

    // ---- نوافذ منبثقة ----
    private fun menuRow(label: String, color: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(color)
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = Theme.box(this@MainActivity, 0x1AFFFFFF, 10f)
        setOnClickListener {
            popup?.dismiss()
            action()
        }
    }

    // زرار +: الأقلام المخفية، والحجم، ومسح السبورة
    private fun showMore() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        for (p in hidden) {
            val ic = PenIconView(this, p.ordinal).apply { color = penColors[p.ordinal] }
            ic.setOnClickListener { pickFromMore(p) }
            list.addView(ic, LinearLayout.LayoutParams(dp(40), dp(56)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
        }
        val margin = LinearLayout.LayoutParams(dp(110), WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        list.addView(menuRow("الحجم: ${sizeLabels[sizeIdx]}", Theme.TEXT) {
            draw.cycleSize()
            sizeIdx = (sizeIdx + 1) % sizeLabels.size
            showMore()
        }, margin)
        list.addView(menuRow("مسح السبورة", Theme.DANGER) { confirmClear() }, margin)
        showPopup(list)
    }

    private fun pickFromMore(p: Pen) {
        val old = visible[visible.size - 1]
        visible[visible.size - 1] = p
        hidden.remove(p)
        hidden.add(old)
        popup?.dismiss()
        choosePen(p)
    }

    // لوحة الألوان للقلم الحالي (بالدبل تاب على القلم)
    private fun showPalette() {
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
        grid.addView(
            menuRow("+ لون مخصص", Theme.TEXT) { showCustomColor() },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(3), dp(8), dp(3), dp(3)) }
        )
        showPopup(grid)
    }

    // نافذة الصفحة: نوعها (فاضية، مربعات، مسطرة، نقط) ولونها
    private fun showPaperPicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val styles = listOf(
            "فاضية" to PaperStyle.BLANK,
            "مربعات" to PaperStyle.GRID,
            "مسطرة" to PaperStyle.LINED,
            "نقط" to PaperStyle.DOTS
        )
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, st) in styles) {
            val tv = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Theme.TEXT)
                background = Theme.box(this@MainActivity, if (paperStyle == st) 0x4038BDF8 else 0x14FFFFFF, 10f)
                setOnClickListener {
                    paperStyle = st
                    saveAndApplyPaper()
                    popup?.dismiss()
                }
            }
            row.addView(tv, LinearLayout.LayoutParams(dp(64), dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        box.addView(row)

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        for (c in papers()) {
            val sw = View(this).apply {
                background = Theme.box(this@MainActivity, c, 0f, if (c == paperColor) Theme.ACCENT else Theme.BORDER, if (c == paperColor) 2.5f else 1f, oval = true)
                setOnClickListener {
                    paperColor = c
                    saveAndApplyPaper()
                    popup?.dismiss()
                }
            }
            colorRow.addView(sw, LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        box.addView(colorRow)
        showPopup(box)
    }

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
