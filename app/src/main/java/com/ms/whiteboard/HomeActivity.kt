package com.ms.whiteboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {

    private val d: Float by lazy { resources.displayMetrics.density }
    private fun dp(v: Int): Int = (v * d).toInt()

    private lateinit var list: LinearLayout
    private val fmt = SimpleDateFormat("d MMM yyyy - HH:mm", Locale("ar"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProjectStore.migrateIfNeeded(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1120.toInt())
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "مشاريعي"
            textSize = 26f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(16))
        })

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(
            TextView(this).apply {
                text = "+  مشروع جديد"
                textSize = 16f
                setTextColor(0xFF0B1120.toInt())
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                background = roundBg(0xFF38BDF8.toInt(), 14)
                setOnClickListener { askNewName() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { setMargins(0, dp(12), 0, 0) }
        )

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun roundBg(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun refresh() {
        list.removeAllViews()
        val projects = ProjectStore.list(this)
        if (projects.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "مفيش مشاريع لسه.\nاضغط \"مشروع جديد\" عشان تبدأ."
                textSize = 15f
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(60), 0, 0)
            })
            return
        }
        for (p in projects) {
            list.addView(
                card(p),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, dp(12)) }
            )
        }
    }

    private fun card(p: Project): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = roundBg(0xFF111A2E.toInt(), 16).apply { setStroke(dp(1), 0x33FFFFFF) }
        addView(TextView(context).apply {
            text = p.name
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = "آخر تعديل: " + fmt.format(Date(p.updated))
            textSize = 12f
            setTextColor(0x99FFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        setOnClickListener { openProject(p.id) }
    }

    private fun openProject(id: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("pid", id))
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
