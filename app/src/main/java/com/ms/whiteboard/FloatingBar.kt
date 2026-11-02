package com.ms.whiteboard

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// شريط عائم: بيتسحب بالمقبض، وبيتحول أفقي/راسي، وبيتطوى
class FloatingBar(context: Context) : LinearLayout(context) {

    var vertical = true
        private set
    var collapsed = false
        private set
    var onChanged: (() -> Unit)? = null

    // المحتوى اللي بيملاه MainActivity
    val body = LinearLayout(context)

    private val header = LinearLayout(context)
    private val grip = FrameLayout(context)
    private val orientBtn = GlyphView(context, GlyphView.ORIENT)
    private val toggleBtn = TextView(context)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startTx = 0f
    private var startTy = 0f

    private fun dp(v: Int): Int = Theme.dp(context, v.toFloat())

    init {
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(6), dp(6), dp(6))

        // المقبض: أي سحب عليه بيحرك الشريط كله
        val pill = View(context).apply { background = Theme.box(context, Theme.MUTED, 3f) }
        grip.addView(pill, FrameLayout.LayoutParams(dp(30), dp(6), Gravity.CENTER))
        grip.setOnTouchListener { _, e -> drag(e) }

        orientBtn.setOnClickListener { setVertical(!vertical) }

        toggleBtn.apply {
            textSize = 20f
            setTextColor(Theme.TEXT)
            gravity = Gravity.CENTER
            setOnClickListener { setCollapsed(!collapsed) }
        }

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER
        header.addView(toggleBtn, LayoutParams(dp(32), dp(32)))
        header.addView(orientBtn, LayoutParams(dp(32), dp(32)))
        header.addView(grip, LayoutParams(dp(44), dp(32)))

        body.gravity = Gravity.CENTER

        addView(header, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        applyLayout()
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clampPosition() }
    }

    // لون الزجاج حسب الوضع النهاري أو الليلي
    private fun glassFill(): Int = if (Theme.dark) 0xB8111A2E.toInt() else 0xC0FFFFFF.toInt()

    fun setVertical(v: Boolean) {
        if (vertical == v) return
        vertical = v
        applyLayout()
        onChanged?.invoke()
    }

    fun setCollapsed(c: Boolean) {
        if (collapsed == c) return
        collapsed = c
        applyLayout()
        onChanged?.invoke()
    }

    private fun applyLayout() {
        val o = if (vertical) VERTICAL else HORIZONTAL
        orientation = o
        body.orientation = o
        background = Theme.box(context, glassFill(), 26f, Theme.BORDER, 1f)
        toggleBtn.text = if (collapsed) "+" else "−"

        val m = dp(4)
        header.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            .apply { setMargins(m, m, m, m) }
        body.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            .apply { setMargins(m, m, m, m) }
    }

    // السحب: بيتحرك مع الإصبع وما بيخرجش برا الشاشة
    private fun drag(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                startTx = translationX
                startTy = translationY
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                translationX = startTx + (e.rawX - downRawX)
                translationY = startTy + (e.rawY - downRawY)
                clampPosition()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun clampPosition() {
        val p = parent as? View ?: return
        val minX = -left.toFloat()
        val maxX = (p.width - right).toFloat()
        val minY = -top.toFloat()
        val maxY = (p.height - bottom).toFloat()
        val nx = if (maxX >= minX) translationX.coerceIn(minX, maxX) else translationX
        val ny = if (maxY >= minY) translationY.coerceIn(minY, maxY) else translationY
        if (nx != translationX) translationX = nx
        if (ny != translationY) translationY = ny
    }
}
