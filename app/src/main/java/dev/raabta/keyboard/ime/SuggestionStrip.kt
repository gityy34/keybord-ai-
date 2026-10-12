package dev.raabta.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.SurfaceStyle
import dev.raabta.keyboard.ui.dp

/** Dividers follow the actual visible suggestion slots, including the user's 2/3/4 choice. */
internal class SuggestionStrip(context: Context) : LinearLayout(context) {
    private val palette = Palette.from(context)
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(palette.muted, 100)
        strokeWidth = resources.displayMetrics.density
    }
    init {
        if (palette.style != SurfaceStyle.FLAT) background = palette.shape(radius=context.dp(21).toFloat())
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (palette.style == SurfaceStyle.FLAT) return
        val visible = (0 until childCount).map(::getChildAt).filter { it.isVisible }
        visible.dropLast(1).forEach { child ->
            canvas.drawLine(child.right.toFloat(), height * 0.28f, child.right.toFloat(), height * 0.72f, divider)
        }
    }
}
