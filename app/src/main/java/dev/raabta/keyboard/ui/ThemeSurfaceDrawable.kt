package dev.raabta.keyboard.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

enum class SurfaceStyle { FLAT, GLASS, CHARCOAL, WHITE }
enum class KeySurface { NORMAL, FUNCTION, SPACE, ENTER }

/** Static vector surfaces. Shaders are cached on resize, with no bitmap or animation per key. */
internal class ThemeSurfaceDrawable(
    private val palette: Palette,
    private val color: Int,
    private val radius: Float,
    private val outlined: Boolean,
    private val role: KeySurface = KeySurface.NORMAL,
    private val edgeColor: Int? = null,
) : Drawable() {
    private val face = RectF()
    private val shadow = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val unit = (radius / 9f).coerceIn(1f, 3f)
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val inset = if (palette.style == SurfaceStyle.GLASS) unit * 1.4f else unit
        face.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset * 2)
        shadow.set(face); shadow.offset(0f, unit * 1.2f)
        val action = color == palette.accent
        val top = if (action) palette.actionStart else ColorUtils.blendARGB(color, Color.WHITE, if (palette.dark) 0.06f else 0.12f)
        val bottom = if (action) palette.actionEnd else ColorUtils.blendARGB(color, Color.BLACK, if (palette.dark) 0.30f else 0.035f)
        fill.shader = LinearGradient(face.left, face.top, if (action) face.right else face.left, face.bottom, top, bottom, Shader.TileMode.CLAMP)
        edge.strokeWidth = unit * if (palette.style == SurfaceStyle.GLASS) 0.9f else 0.55f
        val line = edgeColor ?: palette.border
        edge.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(ColorUtils.blendARGB(line, Color.WHITE, 0.22f), line, if (palette.style == SurfaceStyle.GLASS) palette.accent else line),
            null, Shader.TileMode.CLAMP)
        detail.shader = null
    }

    override fun draw(canvas: Canvas) {
        if (face.isEmpty) return
        detail.style = Paint.Style.FILL
        detail.color = ColorUtils.setAlphaComponent(if (palette.dark) Color.BLACK else 0xFF667085.toInt(), opacity * (if (palette.dark) 24 else 22) / 255)
        canvas.drawRoundRect(shadow, radius, radius, detail)
        fill.alpha = opacity
        canvas.drawRoundRect(face, radius, radius, fill)
        if (outlined) {
            if (palette.style == SurfaceStyle.GLASS) {
                edge.alpha = opacity * 30 / 255; edge.strokeWidth = unit * 2.7f
                canvas.drawRoundRect(face, radius, radius, edge)
                edge.strokeWidth = unit * 0.9f
            }
            edge.alpha = opacity * (if (palette.style == SurfaceStyle.GLASS) 195 else 150) / 255
            canvas.drawRoundRect(face, radius, radius, edge)
        }
        if (palette.style == SurfaceStyle.GLASS && role == KeySurface.SPACE) {
            detail.style = Paint.Style.STROKE; detail.strokeWidth = unit
            detail.color = ColorUtils.setAlphaComponent(palette.secondary, opacity * 180 / 255)
            val y = face.bottom - unit * 3f
            canvas.drawLine(face.centerX() - face.width() * 0.15f, y, face.centerX() + face.width() * 0.15f, y, detail)
        }
    }

    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; edge.colorFilter = colorFilter; detail.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is no longer used by Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
