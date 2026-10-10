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
            KeyboardTheme.DARK -> true
            KeyboardTheme.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }

        fun from(context: Context): Palette =
            if (isDark(context)) {
                Palette(0xFF1E2029.toInt(), 0xFF30333D.toInt(), 0xFFF0F5F2.toInt(), 0xFFBACBC5.toInt(), 0xFFA9BFFF.toInt())
            } else {
                Palette(0xFFF1F5F1.toInt(), Color.WHITE, 0xFF182C28.toInt(), 0xFF516860.toInt(), 0xFF176B60.toInt())
            }
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}
