package dev.raabta.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.raabta.keyboard.ui.dp

/** A crisp return arrow at a stable visual size across device fonts and keyboard widths. */
class EnterKeyButton(context: Context) : KeyButton(context) {
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shape = Path()

    override fun onDraw(canvas: Canvas) {
        val size = context.dp(27).toFloat().coerceAtMost((width - context.dp(10)).toFloat())
            .coerceAtMost((height - context.dp(10)).toFloat())
        if (size <= 0f) return
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        arrow.color = currentTextColor
        arrow.strokeWidth = context.dp(3).toFloat()
        shape.reset()
        shape.moveTo(left + size * .78f, top + size * .18f)
        shape.lineTo(left + size * .78f, top + size * .55f)
        shape.lineTo(left + size * .20f, top + size * .55f)
        shape.moveTo(left + size * .20f, top + size * .55f)
        shape.lineTo(left + size * .40f, top + size * .34f)
        shape.moveTo(left + size * .20f, top + size * .55f)
        shape.lineTo(left + size * .40f, top + size * .76f)
        canvas.drawPath(shape, arrow)
    }
}
