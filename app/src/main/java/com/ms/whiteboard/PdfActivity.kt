package com.ms.whiteboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class PdfActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private lateinit var docId: String
    private lateinit var draw: DrawView
    private lateinit var pageLabel: TextView
    private lateinit var undoIcon: GlyphView
    private lateinit var redoIcon: GlyphView
    private lateinit var penBtn: TextView
    private lateinit var hlBtn: TextView
    private lateinit var eraseBtn: TextView
    private val io = Executors.newSingleThreadExecutor()
    private var page = 0
    private val pageCount: Int get() = renderer?.pageCount ?: 0

    private fun dp(v: Int): Int = Theme.dp(this, v.toFloat())
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)

        val id = intent.getStringExtra("id")
        val doc = PdfStore.list(this).firstOrNull { it.id == id }
        if (id == null || doc == null) {
            finish()
            return
        }
        docId = id

        try {
            val p = ParcelFileDescriptor.open(PdfStore.file(this, id), ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = p
            renderer = PdfRenderer(p)
        } catch (e: Exception) {
            Toast.makeText(this, "ما قدرتش أفتح الملف", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        draw = DrawView(this).apply {
            setPaper(Theme.BG, 0)
            onChanged = { saveCurrent() }
            onHistoryChanged = { updateHistory() }
            // لما تكبّر، نعيد رسم الصفحة بدقة أعلى
            onZoomSettled = { s -> underlay = renderPage(page, s) }
        }

        pageLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), 0, dp(6), 0)
        }

        val title = TextView(this).apply {
            text = doc.name
            textSize = 14f
            setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(170)
            setPadding(dp(6), 0, dp(10), 0)
        }

        undoIcon = glyph(GlyphView.UNDO) { draw.undo() }
        redoIcon = glyph(GlyphView.REDO) { draw.redo() }
        penBtn = btn("قلم") {
            draw.selectPen(Pen.INK)
            updateTools()
        }
        hlBtn = btn("فلو") {
            draw.selectPen(Pen.HIGHLIGHTER)
            updateTools()
        }
        eraseBtn = btn("ممحاة") {
            draw.eraserMode = true
            updateTools()
        }

        val topBar = pill(
            glyph(GlyphView.BACK) { finish() },
            title,
            undoIcon,
            redoIcon
        )
        val bottomBar = pill(
            btn("‹") { goTo(page - 1) },
            pageLabel,
            btn("›") { goTo(page + 1) },
            penBtn,
            hlBtn,
            eraseBtn
        )

        val root = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        root.addView(draw, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(0, dp(70), 0, dp(70)) })
        root.addView(
            topBar,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(8), dp(10), dp(8), 0) }
        )
        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(8), 0, dp(8), dp(12)) }
        )
        setContentView(root)

        updateHistory()
        updateTools()
        draw.post { loadPage(0) }
    }

    private fun pill(vararg items: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(4))
        background = Theme.box(this@PdfActivity, Theme.PANEL, 24f, Theme.BORDER, 1f)
        for (v in items) addView(v)
    }

    private fun glyph(kind: Int, onClick: () -> Unit): GlyphView = GlyphView(this, kind).apply {
        minimumWidth = dp(40)
        minimumHeight = dp(40)
        setOnClickListener { onClick() }
    }

    private fun btn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minimumWidth = dp(40)
        minimumHeight = dp(40)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick() }
    }

    private fun updateHistory() {
        if (!::draw.isInitialized) return
        undoIcon.alpha = if (draw.canUndo) 1f else 0.3f
        redoIcon.alpha = if (draw.canRedo) 1f else 0.3f
    }

    private fun updateTools() {
        penBtn.setTextColor(if (!draw.eraserMode && draw.pen == Pen.INK) Theme.ACCENT else Theme.TEXT)
        hlBtn.setTextColor(if (!draw.eraserMode && draw.pen == Pen.HIGHLIGHTER) Theme.ACCENT else Theme.TEXT)
        eraseBtn.setTextColor(if (draw.eraserMode) Theme.ACCENT else Theme.TEXT)
    }

    // كل صفحة ليها ملف رسم لوحدها
    private fun annotFile(i: Int): File = File(filesDir, "pdfs/annot_${docId}_$i.json")

    private fun saveCurrent() {
        if (!::draw.isInitialized) return
        val json = draw.snapshotJson()
        val f = annotFile(page)
        io.execute { BoardStore(f).write(json) }
    }

    private fun goTo(i: Int) {
        if (i < 0 || i >= pageCount || i == page) return
        saveCurrent()
        loadPage(i)
    }

    private fun loadPage(i: Int) {
        if (pageCount == 0) return
        page = i.coerceIn(0, pageCount - 1)
        draw.underlay = renderPage(page, 1f)

        val f = annotFile(page)
        val saved = if (f.exists()) f.readText() else null
        if (saved != null) {
            draw.restore(saved)
        } else {
            draw.restore(BoardStore.toJson(ArrayList(), draw.width.coerceAtLeast(1), draw.height.coerceAtLeast(1)))
        }

        pageLabel.text = "${page + 1} / $pageCount"
        updateHistory()
        updateTools()
    }

    // scale = 1 للعرض العادي، وأكبر لما تكبّر عشان الصفحة تفضل واضحة
    private fun renderPage(index: Int, scale: Float): Bitmap? {
        val r = renderer ?: return null
        synchronized(r) {
            val p = r.openPage(index)
            val w = (resources.displayMetrics.widthPixels * scale).toInt()
            val h = (w * p.height / p.width.toFloat()).toInt()
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bm.eraseColor(Color.WHITE)
            p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            p.close()
            return bm
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrent()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.close()
        pfd?.close()
        io.shutdown()
    }
}
