package dev.raabta.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

/** Semantic colours shared by keys, tools, settings, menus, voice and result cards. */
data class Palette(
    val background: Int, val surface: Int, val text: Int, val muted: Int, val accent: Int,
    val key: Int = surface, val border: Int = surface, val secondary: Int = accent,
    val dark: Boolean = true, val colorful: Boolean = false,
    val style: SurfaceStyle = SurfaceStyle.FLAT,
    val actionStart: Int = accent, val actionEnd: Int = accent,
    private val iconPalette: List<Int> = emptyList(),
    val backgroundGradient: List<Int>? = null,
    val keyGradient: List<Int>? = null,
    val keyBorders: Boolean = true,
) {
    val ripple: Int get() = ColorUtils.setAlphaComponent(accent, 55)
    val onAccent: Int get() = if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 4.5) Color.WHITE else Color.BLACK
    fun icon(index: Int = 0): Int = if (iconPalette.isNotEmpty()) iconPalette[index.mod(iconPalette.size)] else text
    fun shape(color: Int = surface, radius: Float = 12f, outlined: Boolean = true): Drawable {
        if (style != SurfaceStyle.FLAT) return ThemeSurfaceDrawable(this, color, radius, outlined)
        return rounded(color, radius).apply { if (outlined) setStroke(1, border) }
    }
    fun backgroundDrawable(): Drawable = backgroundGradient?.takeIf { it.size > 1 }?.let {
        GradientDrawable(GradientDrawable.Orientation.TL_BR, it.toIntArray()).apply { cornerRadius = 0f }
    } ?: rounded(background, 0f)
    fun pressed(color: Int = surface, radius: Float = 12f) = RippleDrawable(ColorStateList.valueOf(ripple), shape(color, radius), null)
    fun keyBackground(role: KeySurface, radius: Float): Drawable {
        val color = when {
            role == KeySurface.ENTER -> accent
            role == KeySurface.FUNCTION && style == SurfaceStyle.WHITE -> hex("#E4E7EC")
            else -> key
        }
        val gradient = if (role == KeySurface.ENTER && actionStart != actionEnd) listOf(actionStart, actionEnd) else keyGradient
        val content = if (gradient != null && gradient.size > 1) {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, gradient.toIntArray()).apply {
                cornerRadius = radius
                if (keyBorders) setStroke(1, border)
            }
        } else if (style == SurfaceStyle.FLAT) shape(color, radius, keyBorders) else ThemeSurfaceDrawable(this, color, radius, keyBorders, role)
        return RippleDrawable(ColorStateList.valueOf(ripple), content, null)
    }
    fun toolbarBackground(index: Int, selected: Boolean, radius: Float): Drawable {
        if (style == SurfaceStyle.FLAT) return pressed(if (selected) accent else background, radius)
        val color = if (selected) accent else if (index == 5 && dark) ColorUtils.blendARGB(surface, icon(5), 0.09f) else surface
        val face = ThemeSurfaceDrawable(this, color, if (index == 5 && style == SurfaceStyle.GLASS) radius * 2.4f else radius, true, edgeColor = if (selected || index == 5) icon(index) else null)
        val inset = (radius / 5f).toInt().coerceAtLeast(1)
        return RippleDrawable(ColorStateList.valueOf(ripple), InsetDrawable(face, inset, inset, inset, inset), null)
    }
    fun applySystemBars(window:android.view.Window) {
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark }
        @Suppress("DEPRECATION")
        window.navigationBarColor=background
    }
    companion object {
        private fun hex(value: String) = value.toColorInt()
        fun forTheme(theme: KeyboardTheme, phoneDark: Boolean = false): Palette = when (theme) {
            KeyboardTheme.SYSTEM -> forTheme(if (phoneDark) KeyboardTheme.DARK else KeyboardTheme.LIGHT)
            KeyboardTheme.PAPER -> Palette(hex("#FFFFFF"), hex("#F5F7FA"), hex("#18212F"), hex("#536070"), hex("#2563C9"), hex("#EDF1F7"), hex("#C4CEDC"), hex("#116B6F"), false)
            KeyboardTheme.LIGHT -> Palette(hex("#F6F7FB"), Color.WHITE, hex("#202331"), hex("#606575"), hex("#2068BD"), hex("#E9EDF5"), hex("#CCD3E1"), hex("#186B80"), false)
            KeyboardTheme.NEON -> Palette(hex("#080E1D"), hex("#131E34"), hex("#EDF8FF"), hex("#AABED4"), hex("#176BB8"), hex("#16263C"), hex("#28506C"), hex("#39C5D9"))
            KeyboardTheme.SPECTRUM -> Palette(hex("#F8FAFC"), Color.WHITE, hex("#202B38"), hex("#596878"), hex("#2068BD"), hex("#EDF2F7"), hex("#CBD5E1"), hex("#147C85"), false, true)
            KeyboardTheme.GLASS -> Palette(hex("#050B18"), hex("#111D31"), hex("#F4F8FF"), hex("#AAB9D2"), hex("#1988FF"), hex("#111D31"), hex("#4B8BD7"), hex("#37E8FF"), style=SurfaceStyle.GLASS, actionStart=hex("#18B9FF"), actionEnd=hex("#FF57D7"),
                iconPalette=listOf(hex("#FF7CF4"), hex("#39D9FF"), hex("#2EE6C6"), hex("#FFB85C"), hex("#4EA4FF"), hex("#FF5C9E"), hex("#70C8FF")))
            KeyboardTheme.CHARCOAL -> Palette(hex("#11141B"), hex("#181C25"), hex("#F5F6FA"), hex("#AEB5C2"), hex("#1D4ED8"), hex("#181C25"), hex("#2B303B"), hex("#43D7F2"), style=SurfaceStyle.CHARCOAL, actionStart=hex("#1D4ED8"), actionEnd=hex("#1E40AF"),
                iconPalette=listOf(hex("#C77DFF"), hex("#30C9FF"), hex("#2ED8B6"), hex("#FFB257"), hex("#4DA3FF"), hex("#FF4F88"), hex("#DDE4F0")))
            KeyboardTheme.CLOUD -> Palette(hex("#FAFAFB"), hex("#FFFFFF"), hex("#101722"), hex("#566170"), hex("#0F6FD8"), hex("#FFFFFF"), hex("#E6E9EF"), hex("#2388FF"), false, true, SurfaceStyle.WHITE, hex("#0F6FD8"), hex("#0E5FBD"),
                iconPalette=listOf(hex("#5B21B6"), hex("#0F5CA8"), hex("#047857"), hex("#303846"), hex("#92400E"), hex("#BE123C"), hex("#1D4ED8")))
            else -> Palette(Color.BLACK, hex("#242428"), hex("#F4F2FA"), hex("#ABA8B5"), hex("#2068BD"), border=hex("#38353F"))
        }
        fun from(context: Context): Palette {
            val theme = KeyboardPreferences.load(context).theme
            if (theme == KeyboardTheme.CUSTOM) return (ThemeImport.load(context)?.palette ?: forTheme(KeyboardTheme.MIDNIGHT)).copy(keyBorders = KeyboardPreferences.load(context).keyBorders)
            val phoneDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return forTheme(theme, phoneDark).copy(keyBorders = KeyboardPreferences.load(context).keyBorders)
        }
        fun isDark(context: Context) = from(context).dark
    }
}
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
