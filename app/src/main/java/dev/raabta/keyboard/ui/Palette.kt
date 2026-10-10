package dev.raabta.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

data class Palette(val background: Int, val surface: Int, val text: Int, val muted: Int, val accent: Int) {
    val ripple: Int get() = (accent and 0x00FFFFFF) or 0x33000000
    companion object {
        fun isDark(context: Context): Boolean = when (KeyboardPreferences.load(context).theme) {
            KeyboardTheme.LIGHT -> false
            KeyboardTheme.DARK, KeyboardTheme.MIDNIGHT -> true
            KeyboardTheme.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }

        fun from(context: Context): Palette =
            if (isDark(context)) {
                Palette(Color.BLACK, 0xFF242428.toInt(), 0xFFF4F2FA.toInt(), 0xFFABA8B5.toInt(), 0xFF7961FF.toInt())
            } else {
                Palette(0xFFF5F3FA.toInt(), Color.WHITE, 0xFF242030.toInt(), 0xFF676173.toInt(), 0xFF654BCF.toInt())
            }
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}
