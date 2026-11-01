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
            setBackgroundColor(Color.parseColor("#CC111A2E"))
            setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        }

        fun addBtn(label: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { onClick() }
            bar.addView(b)
        }

        addBtn("قلم") { draw.eraserMode = false }
        addBtn("ممحاة") { draw.eraserMode = true }
        addBtn("مسح الكل") { draw.clearAll() }

        val colors = intArrayOf(
            Color.BLACK,
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt()
        )
        for (c in colors) {
            val sw = View(this)
            sw.setBackgroundColor(c)
            sw.setOnClickListener {
                draw.penColor = c
                draw.eraserMode = false
            }
            val lp = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            lp.setMargins((8 * d).toInt(), 0, (4 * d).toInt(), 0)
            bar.addView(sw, lp)
        }

        root.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        setContentView(root)
    }
}
