package dev.raabta.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

data class Palette(val background: Int, val surface: Int, val text: Int, val muted: Int, val accent: Int) {
    companion object {
        fun from(context: Context): Palette =
            if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                Palette(0xFF172625.toInt(), 0xFF2C3B39.toInt(), 0xFFF0F5F2.toInt(), 0xFFBACBC5.toInt(), 0xFF99E4CE.toInt())
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
