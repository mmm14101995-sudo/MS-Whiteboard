package com.ms.whiteboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {

    private fun dp(v: Int): Int = Theme.dp(this, v.toFloat())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private lateinit var addBtn: TextView
    private lateinit var tabProjects: GlyphView
    private lateinit var tabPdf: GlyphView
    private var tab = 0
    private val fmt = SimpleDateFormat("d MMM yyyy - HH:mm", Locale("ar"))
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val pickPdfCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        ProjectStore.migrateIfNeeded(this)
        tab = prefs.getInt("tab", 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        // الهيدر: العنوان + زرار التحديث + زرار الثيم
        title = TextView(this).apply {
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.TEXT)
        }
        val updateBtn = UpdateChecker.button(this)
        val themeBtn = GlyphView(this, if (Theme.dark) GlyphView.SUN else GlyphView.MOON).apply {
            background = Theme.box(this@HomeActivity, Theme.PANEL, 0f, Theme.BORDER, 1f, oval = true)
            setOnClickListener {
                Theme.toggle(this@HomeActivity)
                recreate()
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(updateBtn, LinearLayout.LayoutParams(WRAP, dp(44)).apply { setMargins(0, 0, dp(8), 0) })
            addView(themeBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(14)) })

        // التبويبات بأيقونات فوق
        tabProjects = GlyphView(this, GlyphView.PROJECTS).apply { setOnClickListener { setTab(0) } }
        tabPdf = GlyphView(this, GlyphView.PDF).apply { setOnClickListener { setTab(1) } }
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = Theme.box(this@HomeActivity, Theme.PANEL, 22f, Theme.BORDER, 1f)
            addView(tabProjects, LinearLayout.LayoutParams(dp(56), dp(44)))
            addView(tabPdf, LinearLayout.LayoutParams(dp(56), dp(44)))
        }
        root.addView(tabBar, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, 0, dp(14)) })

        // القائمة
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // زرار الإضافة: مشروع أو PDF حسب التبويب
        addBtn = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF0B1120.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = Theme.box(this@HomeActivity, Theme.ACCENT, 14f)
            setOnClickListener { if (tab == 0) askNewName() else pickPdf() }
        }
        root.addView(addBtn, LinearLayout.LayoutParams(MATCH, dp(52)).apply { setMargins(0, dp(12), 0, 0) })

        setContentView(root)
        updateTabs()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setTab(i: Int) {
        tab = i
        prefs.edit().putInt("tab", i).apply()
        updateTabs()
        refresh()
    }

    private fun updateTabs() {
        tabProjects.color = if (tab == 0) Theme.ACCENT else Theme.MUTED
        tabPdf.color = if (tab == 1) Theme.ACCENT else Theme.MUTED
        tabProjects.background = if (tab == 0) Theme.box(this, 0x2638BDF8, 16f) else null
        tabPdf.background = if (tab == 1) Theme.box(this, 0x2638BDF8, 16f) else null
        title.text = if (tab == 0) "مشاريعي" else "ملفات PDF"
        addBtn.text = if (tab == 0) "+  مشروع جديد" else "+  استيراد PDF"
    }

    private fun refresh() {
        list.removeAllViews()
        if (tab == 0) {
            val projects = ProjectStore.list(this)
            if (projects.isEmpty()) {
                empty("مفيش مشاريع لسه.\nاضغط \"مشروع جديد\" عشان تبدأ.")
                return
            }
            for (p in projects) {
                addCard(
                    name = p.name,
                    sub = "آخر تعديل: " + fmt.format(Date(p.updated)),
                    onTap = { openProject(p.id) }
                )
            }
        } else {
            val docs = PdfStore.list(this)
            if (docs.isEmpty()) {
                empty("مفيش ملفات PDF لسه.\nاضغط \"استيراد PDF\" عشان تضيف ملف.")
                return
            }
            for (d in docs) {
                addCard(
                    name = d.name,
                    sub = "أُضيف: " + fmt.format(Date(d.added)),
                    onTap = { openPdf(d.id) },
                    onLong = { confirmDeletePdf(d) }
                )
            }
        }
    }

    private fun empty(text: String) {
        list.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Theme.MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(60), 0, 0)
        })
    }

    private fun addCard(name: String, sub: String, onTap: () -> Unit, onLong: (() -> Unit)? = null) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = Theme.box(this@HomeActivity, Theme.PANEL, 16f, Theme.BORDER, 1f)
            addView(TextView(context).apply {
                text = name
                textSize = 17f
                setTextColor(Theme.TEXT)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = sub
                textSize = 12f
                setTextColor(Theme.MUTED)
                setPadding(0, dp(4), 0, 0)
            })
            setOnClickListener { onTap() }
            onLong?.let { f -> setOnLongClickListener { f(); true } }
        }
        list.addView(card, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(12)) })
    }

    private fun openProject(id: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("pid", id))
    }

    private fun openPdf(id: String) {
        startActivity(Intent(this, PdfActivity::class.java).putExtra("id", id))
    }

    private fun pickPdf() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(i, pickPdfCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickPdfCode && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                if (PdfStore.import(this, uri) == null) {
                    Toast.makeText(this, "ما قدرتش أستورد الملف", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
        }
    }

    private fun confirmDeletePdf(d: PdfDoc) {
        AlertDialog.Builder(this)
            .setMessage("تمسح الملف \"${d.name}\" من المكتبة؟")
            .setPositiveButton("امسح") { _, _ ->
                PdfStore.delete(this, d.id)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun askNewName() {
        val input = EditText(this).apply {
            setText("مشروع جديد")
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("اسم المشروع")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "مشروع جديد" }
                val p = ProjectStore.create(this, name)
                openProject(p.id)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
