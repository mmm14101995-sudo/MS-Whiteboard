package com.ms.whiteboard.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- الحالة: MainActivity بيحدّثها، والـ Composable بيقرأ منها ----
class ToolbarState {
    var slotColors by mutableStateOf<List<Color>>(emptyList())
    var activeSlot by mutableStateOf(-1)
    var eraserOn by mutableStateOf(false)
    var handOn by mutableStateOf(false)
    var selectOn by mutableStateOf(false)
    var expanded by mutableStateOf(true)
    var visible by mutableStateOf(true)
    var fullscreenOn by mutableStateOf(false)
    var selectionCount by mutableStateOf(0)
}

// ---- الأفعال: الشاشة اللي بتستخدم الشريط بتنفذها ----
interface ToolbarActions {
    fun onPage()
    fun onDrag(dx: Float, dy: Float)
    fun onToggleExpand()
    fun onClose()
    fun onReopen()
    fun onPenTap(index: Int)
    fun onPenDoubleTap(index: Int)
    fun onMore()
    fun onHand()
    fun onEraser()
    fun onSelect()
    fun onFullscreen()
    fun onDeleteSelection()
}

private val NavyTop = Color(0xF21E2A44)
private val NavyBottom = Color(0xF20F1729)
private val Border = Color(0x33FFFFFF)
private val Glyph = Color(0xFFE2E8F0)
private val ActiveBlue = Color(0xFF2563EB)
private val ActiveBorder = Color(0xFF60A5FA)

// الشريط الكامل: زرار الشاشة الكاملة فوق، وبعده الكبسولة الراسية، ولو اتقفل بيظهر زرار الفتح
@Composable
fun WhiteboardToolbar(state: ToolbarState, actions: ToolbarActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.visible) {
            FullscreenButton(on = state.fullscreenOn, onClick = { actions.onFullscreen() })
            Spacer(Modifier.height(8.dp))
            Capsule(state, actions)
            if (state.selectionCount > 0) {
                Spacer(Modifier.height(8.dp))
                DeleteChip(count = state.selectionCount, onClick = { actions.onDeleteSelection() })
            }
        } else {
            ReopenButton(onClick = { actions.onReopen() })
        }
    }
}

@Composable
private fun Capsule(state: ToolbarState, actions: ToolbarActions) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .width(56.dp)
            .heightIn(max = 460.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)), shape)
            .border(1.dp, Border, shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // المجموعة الأولى: صفحة، مقبض سحب، طي/فرد، إغلاق
        ToolButton(onClick = { actions.onPage() }) { PageGlyph() }
        DragHandle(onDrag = { dx, dy -> actions.onDrag(dx, dy) })
        ToolButton(onClick = { actions.onToggleExpand() }) { ChevronGlyph(up = state.expanded) }
        ToolButton(onClick = { actions.onClose() }) { CloseGlyph() }

        if (state.expanded) {
            Box(Modifier.width(30.dp).height(1.dp).background(Border))

            // الأقلام الأربعة بألوانها
            state.slotColors.forEachIndexed { i, c ->
                PenButton(
                    color = c,
                    active = state.activeSlot == i,
                    onTap = { actions.onPenTap(i) },
                    onDoubleTap = { actions.onPenDoubleTap(i) }
                )
            }
            // المجموعة الثانية: زيادة، يد، ممحاة، تحديد
            ToolButton(onClick = { actions.onMore() }) { PlusGlyph() }
            ToolButton(selected = state.handOn, onClick = { actions.onHand() }) { HandGlyph() }
            ToolButton(selected = state.eraserOn, onClick = { actions.onEraser() }) { EraserGlyph() }
            ToolButton(selected = state.selectOn, onClick = { actions.onSelect() }) { SelectGlyph() }
        }
    }
}

@Composable
private fun ToolButton(selected: Boolean = false, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (selected) ActiveBlue else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { glyph() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PenButton(color: Color, active: Boolean, onTap: () -> Unit, onDoubleTap: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 56.dp)
            .clip(shape)
            .background(if (active) ActiveBlue else Color.Transparent, shape)
            .then(if (active) Modifier.border(1.dp, ActiveBorder, shape) else Modifier)
            .combinedClickable(onClick = onTap, onDoubleClick = onDoubleTap),
        contentAlignment = Alignment.Center
    ) { PenGlyph(color) }
}

@Composable
private fun DragHandle(onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) { DotsGlyph() }
}

@Composable
private fun FullscreenButton(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (on) ActiveBlue else NavyTop, CircleShape)
            .border(1.dp, Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension
            val sw = 1.7f.dp.toPx()
            val m = s * 0.12f
            val len = s * 0.3f
            val corners = listOf(
                Offset(m, m) to Offset(1f, 1f),
                Offset(s - m, m) to Offset(-1f, 1f),
                Offset(m, s - m) to Offset(1f, -1f),
                Offset(s - m, s - m) to Offset(-1f, -1f)
            )
            for ((p, d) in corners) {
                drawLine(Glyph, p, Offset(p.x + d.x * len, p.y), sw, StrokeCap.Round)
                drawLine(Glyph, p, Offset(p.x, p.y + d.y * len), sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ReopenButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(NavyTop, CircleShape)
            .border(1.dp, Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PenGlyph(Color(0xFFF97316)) }
}

@Composable
private fun DeleteChip(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFFDC2626), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = "حذف $count",
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
    }
}

// ---- القلم: جسم كحلي، شريط ملون، سن مدبب، ولمعة خفيفة ----
@Composable
private fun PenGlyph(color: Color) {
    Canvas(Modifier.size(width = 24.dp, height = 40.dp)) {
        val w = size.width
        val h = size.height
        val left = w * 0.2f
        val right = w * 0.8f
        val top = h * 0.04f
        val bodyBottom = h * 0.72f
        val radius = CornerRadius(w * 0.18f)

        // الجسم
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF0B1224), Color(0xFF3B4A66), Color(0xFF0B1224)),
                startX = left,
                endX = right
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bodyBottom - top),
            cornerRadius = radius
        )
        // شريط اللون
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.85f), color), startX = left, endX = right),
            topLeft = Offset(left, top),
            size = Size(right - left, h * 0.18f),
            cornerRadius = radius
        )
        // السن
        val nib = Path().apply {
            moveTo(left, bodyBottom)
            lineTo(right, bodyBottom)
            lineTo(w * 0.5f, h * 0.98f)
            close()
        }
        drawPath(
            nib,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFFE2E8F0)),
                startX = left,
                endX = right
            )
        )
        // طرف الحبر بلون القلم
        val tip = Path().apply {
            moveTo(w * 0.44f, h * 0.9f)
            lineTo(w * 0.56f, h * 0.9f)
            lineTo(w * 0.5f, h * 0.98f)
            close()
        }
        drawPath(tip, color)
        // لمعة
        drawLine(
            Color.White.copy(alpha = 0.22f),
            Offset(left + w * 0.14f, top + h * 0.26f),
            Offset(left + w * 0.14f, bodyBottom - h * 0.06f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PageGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val sw = 1.7f.dp.toPx()
        drawRoundRect(
            color = Glyph,
            topLeft = Offset(s * 0.18f, s * 0.12f),
            size = Size(s * 0.64f, s * 0.76f),
            cornerRadius = CornerRadius(s * 0.1f),
            style = Stroke(width = sw)
        )
        for (k in 0..2) {
            val y = s * (0.38f + k * 0.18f)
            drawLine(Glyph, Offset(s * 0.3f, y), Offset(s * 0.7f, y), sw, StrokeCap.Round)
        }
    }
}

@Composable
private fun DotsGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val r = 1.6f.dp.toPx()
        for (x in listOf(0.36f, 0.64f)) {
            for (y in listOf(0.22f, 0.5f, 0.78f)) {
                drawCircle(Glyph, r, Offset(w * x, h * y))
            }
        }
    }
}

@Composable
private fun ChevronGlyph(up: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.7f.dp.toPx()
        val y1 = if (up) h * 0.62f else h * 0.38f
        val y2 = if (up) h * 0.38f else h * 0.62f
        val p = Path().apply {
            moveTo(w * 0.3f, y1)
            lineTo(w * 0.5f, y2)
            lineTo(w * 0.7f, y1)
        }
        drawPath(p, Glyph, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun CloseGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.7f.dp.toPx()
        drawLine(Glyph, Offset(w * 0.3f, h * 0.3f), Offset(w * 0.7f, h * 0.7f), sw, StrokeCap.Round)
        drawLine(Glyph, Offset(w * 0.7f, h * 0.3f), Offset(w * 0.3f, h * 0.7f), sw, StrokeCap.Round)
    }
}

@Composable
private fun PlusGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.7f.dp.toPx()
        drawLine(Glyph, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.75f), sw, StrokeCap.Round)
        drawLine(Glyph, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
private fun HandGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6f.dp.toPx()
        drawRoundRect(
            color = Glyph,
            topLeft = Offset(w * 0.26f, h * 0.46f),
            size = Size(w * 0.48f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.16f),
            style = Stroke(width = sw)
        )
        for (x in listOf(0.34f, 0.46f, 0.58f)) {
            drawLine(Glyph, Offset(w * x, h * 0.48f), Offset(w * x, h * 0.14f), sw, StrokeCap.Round)
        }
        drawLine(Glyph, Offset(w * 0.26f, h * 0.6f), Offset(w * 0.12f, h * 0.46f), sw, StrokeCap.Round)
    }
}

@Composable
private fun EraserGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        rotate(-40f) {
            drawRoundRect(
                color = Color(0xFFF472B6),
                topLeft = Offset(w * 0.3f, h * 0.12f),
                size = Size(w * 0.4f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.08f)
            )
            drawRoundRect(
                color = Glyph,
                topLeft = Offset(w * 0.3f, h * 0.48f),
                size = Size(w * 0.4f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.08f)
            )
        }
    }
}

@Composable
private fun SelectGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Glyph,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(
                width = 1.6f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
            )
        )
    }
}
