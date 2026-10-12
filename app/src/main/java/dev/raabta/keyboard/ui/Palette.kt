package dev.raabta.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

/** Semantic colours shared by keys, tools, settings, menus, voice and result cards. */
data class Palette(
    val background: Int, val surface: Int, val text: Int, val muted: Int, val accent: Int,
    val key: Int = surface, val border: Int = surface, val secondary: Int = accent,
    val dark: Boolean = true, val colorful: Boolean = false,
) {
    val ripple: Int get() = ColorUtils.setAlphaComponent(accent, 55)
    val onAccent: Int get() = if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 4.5) Color.WHITE else Color.BLACK
    fun icon(index: Int = 0): Int = if (colorful && index % 2 == 1) secondary else accent
    fun shape(color: Int = surface, radius: Float = 12f, outlined: Boolean = true) = rounded(color, radius).apply {
        if (outlined) setStroke(1, border)
    }
    fun pressed(color: Int = surface, radius: Float = 12f) = RippleDrawable(ColorStateList.valueOf(ripple), shape(color, radius), null)
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
            KeyboardTheme.LIGHT -> Palette(hex("#F6F7FB"), Color.WHITE, hex("#202331"), hex("#606575"), hex("#5949CC"), hex("#E9EDF5"), hex("#CCD3E1"), hex("#186B80"), false)
            KeyboardTheme.NEON -> Palette(hex("#080E1D"), hex("#131E34"), hex("#EDF8FF"), hex("#AABED4"), hex("#63E5FF"), hex("#16263C"), hex("#28506C"), hex("#BC98FF"))
            KeyboardTheme.SPECTRUM -> Palette(hex("#F8F4FC"), Color.WHITE, hex("#292038"), hex("#655A73"), hex("#6840C2"), hex("#ECE7F7"), hex("#D6CDE8"), hex("#B12468"), false, true)
            else -> Palette(Color.BLACK, hex("#242428"), hex("#F4F2FA"), hex("#ABA8B5"), hex("#6553EB"), border=hex("#38353F"))
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
