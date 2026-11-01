package com.ms.whiteboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var draw: DrawView
    private lateinit var dock: LinearLayout
    private lateinit var vScroll: ScrollView
    private lateinit var hScroll: HorizontalScrollView
    private lateinit var eraserIcon: PenIconView
    private lateinit var store: BoardStore
    private var projectId = "first"
    private val io = Executors.newSingleThreadExecutor()
    private var vertical = true
    private var popup: PopupWindow? = null

    private val penIcons = HashMap<Pen, PenIconView>()
    private var lastPen: Pen? = null
    private var lastTap = 0L

    // لون كل قلم (بنفس ترتيب Pen)
    private val penColors = intArrayOf(
        0xFF000000.toInt(), 0xFF37474F.toInt(), 0xFF1E88E5.toInt(),
        0xFFFFEB3B.toInt(), 0xFF212121.toInt()
    )
    // الأربعة الظاهرين، والباقي في زرار +
    private val visible = mutableListOf(Pen.INK, Pen.PENCIL, Pen.MARKER, Pen.HIGHLIGHTER)
    private val hidden = mutableListOf(Pen.BRUSH)

    private val quickColors = intArrayOf(
        Color.BLACK, 0xFFE53935.toInt(), 0xFF1E88E5.toInt(),
        0xFF43A047.toInt(), 0xFFFFEB3B.toInt(), 0xFF8E24AA.toInt()
    )
    private val paletteColors = intArrayOf(
        0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF475569.toInt(), 0xFF94A3B8.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(),
        0xFFDC2626.toInt(), 0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(),
        0xFF16A34A.toInt(), 0xFF10B981.toInt(), 0xFF14B8A6.toInt(), 0xFF06B6D4.toInt(), 0xFF0EA5E9.toInt(), 0xFF2563EB.toInt(),
        0xFF4F46E5.toInt(), 0xFF7C3AED.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFBE185D.toInt(), 0xFF78350F.toInt()
    )

    private val d: Float by lazy { resources.displayMetrics.density }
    private fun dp(v: Int): Int = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        projectId = intent.getStringExtra("pid") ?: "first"
        store = BoardStore(ProjectStore.boardFile(this, projectId))

        draw = DrawView(this)
        for (p in Pen.values()) {
            draw.selectPen(p)
            draw.setColor(penColors[p.ordinal])
        }
        draw.selectPen(Pen.INK)

        draw.onChanged = { saveNow() }
        store.read()?.let { draw.restore(it) }

        eraserIcon = PenIconView(this, PenIconView.ERASER)
        dock = LinearLayout(this).apply { setPadding(dp(6), dp(8), dp(6), dp(8)) }

        vScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = panelBg()
        }
        hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = panelBg()
        }

        val root = FrameLayout(this)
        root.addView(draw, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            vScroll,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { setMargins(dp(8), dp(64), dp(8), dp(12)) }
        )
        root.addView(
            hScroll,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(dp(8), dp(8), dp(8), dp(12)) }
        )

        // زرار الرجوع للمشاريع
        val back = TextView(this).apply {
            text = "→"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = panelBg(oval = true)
            setOnClickListener { finish() }
        }
        root.addView(
            back,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END)
                .apply { setMargins(dp(8), dp(8), dp(8), 0) }
        )

        // زرار تبديل الشريط أفقي / رأسي
        val toggle = TextView(this).apply {
            text = "⇄"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = panelBg(oval = true)
            setOnClickListener {
                vertical = !vertical
                applyOrientation()
            }
        }
        root.addView(
            toggle,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START)
                .apply { setMargins(dp(8), dp(8), dp(8), 0) }
        )

        setContentView(root)
        rebuildDock()
        applyOrientation()
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

    private fun panelBg(oval: Boolean = false): GradientDrawable = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(26).toFloat()
        setColor(0xE6111A2E.toInt())
        setStroke(dp(1), 0x33FFFFFF)
    }

    private fun roundBg(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(color)
    }

    private fun applyOrientation() {
        (dock.parent as? ViewGroup)?.removeView(dock)
        if (vertical) {
            dock.orientation = LinearLayout.VERTICAL
            vScroll.addView(dock)
            vScroll.visibility = View.VISIBLE
            hScroll.visibility = View.GONE
        } else {
            dock.orientation = LinearLayout.HORIZONTAL
            hScroll.addView(dock)
            hScroll.visibility = View.VISIBLE
            vScroll.visibility = View.GONE
        }
    }

    private fun iconLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(40), dp(58)).apply { setMargins(0, dp(2), 0, dp(2)) }

    private fun rebuildDock() {
        dock.removeAllViews()
        penIcons.clear()

        for (p in visible) {
            val ic = PenIconView(this, p.ordinal).apply { color = penColors[p.ordinal] }
            ic.setOnClickListener { onPenTap(p) }
            penIcons[p] = ic
            dock.addView(ic, iconLp())
        }

        dock.addView(
            TextView(this).apply {
                text = "+"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = roundBg(0x33FFFFFF)
                setOnClickListener { showMore() }
            },
            LinearLayout.LayoutParams(dp(40), dp(32)).apply { setMargins(0, dp(4), 0, dp(4)) }
        )

        eraserIcon.setOnClickListener {
            draw.eraserMode = true
            refresh()
        }
        dock.addView(eraserIcon, iconLp())

        for (c in quickColors) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(1), 0x99FFFFFF.toInt())
                }
                setOnClickListener { applyColor(c) }
            }
            dock.addView(dot, LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(0, dp(4), 0, dp(4)) })
        }

        dock.addView(
            TextView(this).apply {
                text = "مسح"
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = roundBg(0x66E53935)
                setOnClickListener { confirmClear() }
            },
            LinearLayout.LayoutParams(dp(40), dp(32)).apply { setMargins(0, dp(8), 0, 0) }
        )

        refresh()
    }

    private fun refresh() {
        for ((p, ic) in penIcons) ic.active = !draw.eraserMode && draw.pen == p
        eraserIcon.active = draw.eraserMode
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
        draw.setColor(c)
        penIcons[p]?.color = c
        draw.eraserMode = false
        refresh()
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
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        setStroke(dp(1), 0x66FFFFFF)
                    }
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
            list.addView(ic, LinearLayout.LayoutParams(dp(40), dp(58)).apply { setMargins(0, dp(2), 0, dp(2)) })
        }
        showPopup(list)
    }

    // القلم اللي اتختار من + بيحل محل الرابع الظاهر، والقديم بيروح للقائمة
    private fun pickFromMore(p: Pen) {
        val old = visible[visible.size - 1]
        visible[visible.size - 1] = p
        hidden.remove(p)
        hidden.add(old)
        popup?.dismiss()
        rebuildDock()
        draw.selectPen(p)
        refresh()
    }

    private fun showPopup(content: View) {
        popup?.dismiss()
        val pop = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pop.setOutsideTouchable(true)
        pop.setBackgroundDrawable(panelBg())
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
