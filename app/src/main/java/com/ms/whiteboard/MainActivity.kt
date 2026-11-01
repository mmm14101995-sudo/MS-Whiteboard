package com.ms.whiteboard

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        val draw = DrawView(this)
        val root = FrameLayout(this)
        root.addView(
            draw,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // نفس ألوان البداية في DrawView
        val penColors = intArrayOf(
            0xFF000000.toInt(), 0xFF37474F.toInt(), 0xFF1E88E5.toInt(),
            0xFFFFEB3B.toInt(), 0xFF212121.toInt()
        )
        val icons = ArrayList<PenIconView>()
        val eraserIcon = PenIconView(this, PenIconView.ERASER)

        fun refresh() {
            for (i in icons.indices) {
                icons[i].active = !draw.eraserMode && draw.pen.ordinal == i
            }
            eraserIcon.active = draw.eraserMode
        }

        fun iconLp(): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(dp(40), dp(58)).apply { setMargins(0, dp(2), 0, dp(2)) }

        fun pillBg(fill: Int): GradientDrawable = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
        }

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }

        // الأقلام
        for (p in Pen.values()) {
            val ic = PenIconView(this, p.ordinal)
            ic.color = penColors[p.ordinal]
            ic.setOnClickListener {
                draw.selectPen(p)
                refresh()
            }
            icons.add(ic)
            dock.addView(ic, iconLp())
        }

        // الممحاة
        eraserIcon.setOnClickListener {
            draw.eraserMode = true
            refresh()
        }
        dock.addView(eraserIcon, iconLp())

        // السُمك
        val sizeBtn = TextView(this).apply {
            text = "100%"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = pillBg(0x33FFFFFF)
        }
        sizeBtn.setOnClickListener {
            val s = draw.cycleSize()
            sizeBtn.text = (s * 100).toInt().toString() + "%"
        }
        dock.addView(
            sizeBtn,
            LinearLayout.LayoutParams(dp(44), dp(34)).apply { setMargins(0, dp(8), 0, dp(6)) }
        )

        // الألوان
        val swatches = intArrayOf(
            Color.BLACK,
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt(),
            0xFFFFEB3B.toInt(),
            0xFF8E24AA.toInt()
        )
        for (c in swatches) {
            val dot = View(this)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                setStroke(dp(1), 0x99FFFFFF.toInt())
            }
            dot.setOnClickListener {
                val i = draw.pen.ordinal
                penColors[i] = c
                draw.setColor(c)
                icons[i].color = c
                draw.eraserMode = false
                refresh()
            }
            dock.addView(
                dot,
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(0, dp(4), 0, dp(4)) }
            )
        }

        // مسح الكل
        val clearBtn = TextView(this).apply {
            text = "مسح"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = pillBg(0x33E53935)
        }
        clearBtn.setOnClickListener { draw.clearAll() }
        dock.addView(
            clearBtn,
            LinearLayout.LayoutParams(dp(44), dp(34)).apply { setMargins(0, dp(8), 0, 0) }
        )

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = GradientDrawable().apply {
                setColor(0xE6111A2E.toInt())
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            addView(dock)
        }

        // زرار إخفاء/إظهار الشريط
        val toggle = TextView(this).apply {
            text = "☰"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6111A2E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        toggle.setOnClickListener {
            scroll.visibility = if (scroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        refresh()

        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { setMargins(dp(8), dp(64), dp(8), dp(12)) }
        )
        root.addView(
            toggle,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END)
                .apply { setMargins(dp(8), dp(8), dp(8), 0) }
        )
        setContentView(root)
    }
}
