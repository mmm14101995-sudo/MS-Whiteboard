package com.ms.whiteboard

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

// شريط عايم بخلفية زجاجية خفيفة: يتفرد ويتضم، ويتحرك بالإمساك بالمقبض
class FloatingBar(context: Context) : LinearLayout(context) {

    var vertical = true
        private set
    var collapsed = false
        private set
    var onChanged: (() -> Unit)? = null

    val body = LinearLayout(context)
    private val header = LinearLayout(context)
    private val grip = View(context)
    private val orientBtn = GlyphView(context, GlyphView.ORIENT)
    private val toggleBtn = TextView(context)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startTx = 0f
    private var startTy = 0f

    private fun dp(v: Int): Int = Theme.dp(context, v.toFloat())

    init {
        // زجاجي شفاف من غير blur عشان ميتقلش على الجهاز
        background = Theme.box(context, glassFill(), 22f, Theme.BORDER, 1f)
        setPadding(dp(6), dp(6), dp(6), dp(6))

        grip.background = Theme.box(context, Theme.MUTED, 3f)
        grip.setOnTouchListener { _, e -> drag(e) }

        orientBtn.setOnClickListener { setVertical(!vertical) }

        toggleBtn.apply {
            textSize = 18f
            setTextColor(Theme.TEXT)
            gravity = Gravity.CENTER
            setOnClickListener { setCollapsed(!collapsed) }
        }
        toggleBtn.text = "−"

        applyLayout()
    }

    private fun glassFill(): Int = if (Theme.dark) 0xB8111A2E.toInt() else 0xC0FFFFFF.toInt()

    fun setVertical(v: Boolean) {
        vertical = v
        applyLayout()
        onChanged?.invoke()
    }

    fun setCollapsed(c: Boolean) {
        collapsed = c
        toggleBtn.text = if (c) "+" else "−"
        onChanged?.invoke()
    }

    private fun applyLayout() {
        orientation = if (vertical) VERTICAL else HORIZONTAL
        body.orientation = orientation
        header.orientation = if (vertical) HORIZONTAL else VERTICAL
        header.gravity = Gravity.CENTER

        grip.layoutParams = if (vertical) {
            LinearLayout.LayoutParams(dp(28), dp(5)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        } else {
            LinearLayout.LayoutParams(dp(5), dp(28)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        }

        header.removeAllViews()
        header.addView(grip)
        header.addView(orientBtn, LinearLayout.LayoutParams(dp(34), dp(34)))
        header.addView(toggleBtn, LinearLayout.LayoutParams(dp(34), dp(34)))

        removeAllViews()
        addView(header)
        addView(body)
    }

    // سحب الشريط بالمقبض، ومايخرجش من حدود الشاشة
    private fun drag(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                startTx = translationX
                startTy = translationY
            }
            MotionEvent.ACTION_MOVE -> {
                val p = parent as? View ?: return true
                val minX = -left.toFloat()
                val maxX = (p.width - right).toFloat()
                val minY = -top.toFloat()
                val maxY = (p.height - bottom).toFloat()
                val nx = startTx + (e.rawX - downRawX)
                val ny = startTy + (e.rawY - downRawY)
                if (minX <= maxX) translationX = nx.coerceIn(minX, maxX)
                if (minY <= maxY) translationY = ny.coerceIn(minY, maxY)
            }
        }
        return true
    }
}
