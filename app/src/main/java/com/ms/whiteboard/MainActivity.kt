package com.ms.whiteboard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = resources.displayMetrics.density

        val draw = DrawView(this)
        val root = FrameLayout(this)
        root.addView(
            draw,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#CC111A2E"))
            addView(bar)
        }

        val penButtons = ArrayList<Button>()
        var eraserBtn: Button? = null

        fun refresh() {
            for (i in penButtons.indices) {
                penButtons[i].alpha = if (!draw.eraserMode && draw.pen.ordinal == i) 1f else 0.5f
            }
            eraserBtn?.alpha = if (draw.eraserMode) 1f else 0.5f
        }

        fun smallBtn(label: String): Button {
            val b = Button(this)
            b.text = label
            b.textSize = 14f
            b.minWidth = 0
            b.minimumWidth = 0
            return b
        }

        fun penBtn(label: String, p: Pen) {
            val b = smallBtn(label)
            b.setOnClickListener { draw.selectPen(p); refresh() }
            penButtons.add(b)
            bar.addView(b)
        }

        // نفس ترتيب Pen
        penBtn("حبر", Pen.INK)
        penBtn("رصاص", Pen.PENCIL)
        penBtn("ماركر", Pen.MARKER)
        penBtn("تمييز", Pen.HIGHLIGHTER)
        penBtn("ريشة", Pen.BRUSH)

        val eb = smallBtn("ممحاة")
        eb.setOnClickListener { draw.eraserMode = true; refresh() }
        eraserBtn = eb
        bar.addView(eb)

        val sizeBtn = smallBtn("سُمك")
        sizeBtn.setOnClickListener {
            val s = draw.cycleSize()
            sizeBtn.text = "سُمك " + (s * 100).toInt() + "%"
        }
        bar.addView(sizeBtn)

        val clearBtn = smallBtn("مسح الكل")
        clearBtn.setOnClickListener { draw.clearAll() }
        bar.addView(clearBtn)

        val swatches = intArrayOf(
            Color.BLACK,
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt(),
            0xFFFFEB3B.toInt(),
            0xFF8E24AA.toInt()
        )
        for (c in swatches) {
            val sw = View(this)
            sw.setBackgroundColor(c)
            sw.setOnClickListener { draw.setColor(c) }
            val lp = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            lp.setMargins((8 * d).toInt(), 0, (2 * d).toInt(), 0)
            bar.addView(sw, lp)
        }

        refresh()
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        setContentView(root)
    }
}
