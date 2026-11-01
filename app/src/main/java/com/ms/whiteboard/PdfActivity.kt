package com.ms.whiteboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class PdfActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private val cache = LruCache<Int, Bitmap>(6)

    private fun dp(v: Int): Int = Theme.dp(this, v.toFloat())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)

        val id = intent.getStringExtra("id")
        val doc = PdfStore.list(this).firstOrNull { it.id == id }
        if (id == null || doc == null) {
            finish()
            return
        }

        try {
            val p = ParcelFileDescriptor.open(PdfStore.file(this, id), ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = p
            renderer = PdfRenderer(p)
        } catch (e: Exception) {
            Toast.makeText(this, "ما قدرتش أفتح الملف", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pages = renderer?.pageCount ?: 0

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = dp(12)
            setBackgroundColor(Theme.BG)
            clipToPadding = false
            setPadding(dp(12), dp(84), dp(12), dp(16))
            isVerticalScrollBarEnabled = false
            adapter = PageAdapter()
        }

        val back = GlyphView(this, GlyphView.BACK).apply { setOnClickListener { finish() } }
        val title = TextView(this).apply {
            text = "${doc.name}  ·  $pages صفحة"
            textSize = 14f
            setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(230)
            setPadding(dp(10), 0, dp(12), 0)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = Theme.box(this@PdfActivity, Theme.PANEL, 24f, Theme.BORDER, 1f)
            addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(title)
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        root.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(dp(8), dp(10), dp(8), 0) }
        )
        setContentView(root)
    }

    private inner class PageAdapter : BaseAdapter() {
        override fun getCount(): Int = renderer?.pageCount ?: 0
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val img = (convertView as? ImageView) ?: ImageView(this@PdfActivity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                elevation = dp(2).toFloat()
            }
            img.setImageBitmap(pageBitmap(position))
            return img
        }
    }

    // نرسم الصفحة لما تحتاجها بس، وبنحفظ آخر 6 في الذاكرة
    private fun pageBitmap(index: Int): Bitmap? {
        cache.get(index)?.let { return it }
        val r = renderer ?: return null
        synchronized(r) {
            cache.get(index)?.let { return it }
            val page = r.openPage(index)
            val w = resources.displayMetrics.widthPixels
            val h = (w * page.height / page.width.toFloat()).toInt()
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bm.eraseColor(Color.WHITE)
            page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            cache.put(index, bm)
            return bm
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.close()
        pfd?.close()
        cache.evictAll()
    }
}
