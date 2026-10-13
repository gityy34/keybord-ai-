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
) {
    val ripple: Int get() = ColorUtils.setAlphaComponent(accent, 55)
    val onAccent: Int get() = if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 4.5) Color.WHITE else Color.BLACK
    // Toolbar and tool-grid artwork stays monochrome; the action surface carries the theme accent.
    @Suppress("UNUSED_PARAMETER")
    fun icon(index: Int = 0): Int = text
    fun shape(color: Int = surface, radius: Float = 12f, outlined: Boolean = true): Drawable {
        if (style != SurfaceStyle.FLAT) return ThemeSurfaceDrawable(this, color, radius, outlined)
        return rounded(color, radius).apply { if (outlined) setStroke(1, border) }
    }
    fun pressed(color: Int = surface, radius: Float = 12f) = RippleDrawable(ColorStateList.valueOf(ripple), shape(color, radius), null)
    fun keyBackground(role: KeySurface, radius: Float): Drawable {
        val color = when {
            role == KeySurface.ENTER -> accent
            role == KeySurface.FUNCTION && style == SurfaceStyle.WHITE -> hex("#E4E7EC")
            else -> key
        }
        val content = if (style == SurfaceStyle.FLAT) shape(color, radius) else ThemeSurfaceDrawable(this, color, radius, true, role)
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
            KeyboardTheme.GLASS -> Palette(hex("#070E1C"), hex("#111E30"), hex("#F1F6FF"), hex("#B1BFD4"), hex("#1768B8"), hex("#142235"), hex("#528DB7"), hex("#41D7F3"), style=SurfaceStyle.GLASS, actionStart=hex("#1768B8"), actionEnd=hex("#10549C"))
            KeyboardTheme.CHARCOAL -> Palette(hex("#0D1016"), hex("#191D25"), hex("#F2F3FA"), hex("#ADB3C1"), hex("#2067B0"), hex("#1C2029"), hex("#383E49"), hex("#58A5D8"), style=SurfaceStyle.CHARCOAL, actionStart=hex("#2067B0"), actionEnd=hex("#134D89"))
            KeyboardTheme.CLOUD -> Palette(hex("#F5F6F8"), hex("#FFFFFF"), hex("#141821"), hex("#556071"), hex("#2468CB"), hex("#FFFFFF"), hex("#E0E4EB"), hex("#287785"), false, true, SurfaceStyle.WHITE, hex("#2468CB"), hex("#1660D0"))
            else -> Palette(Color.BLACK, hex("#242428"), hex("#F4F2FA"), hex("#ABA8B5"), hex("#2068BD"), border=hex("#38353F"))
        }
        fun from(context: Context): Palette {
            val theme = KeyboardPreferences.load(context).theme
            if (theme == KeyboardTheme.CUSTOM) return ThemeImport.load(context)?.palette ?: forTheme(KeyboardTheme.MIDNIGHT)
            val phoneDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return forTheme(theme, phoneDark)
        }
        fun isDark(context: Context) = from(context).dark
    }
}
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
