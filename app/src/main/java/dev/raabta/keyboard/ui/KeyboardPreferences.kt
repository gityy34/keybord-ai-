package dev.raabta.keyboard.ui

import android.content.Context

enum class KeyboardTheme { SYSTEM, LIGHT, DARK }
enum class KeyboardHeight { COMPACT, NORMAL, TALL }

/** Only presentation preferences are stored; never editor text or audio. */
data class KeyboardPreferences(
    val theme: KeyboardTheme = KeyboardTheme.SYSTEM,
    val height: KeyboardHeight = KeyboardHeight.NORMAL,
    val numberRow: Boolean = false,
    val vibration: Boolean = true,
    val sound: Boolean = false,
) {
    companion object {
        private fun store(context: Context) = context.getSharedPreferences("keyboard_preferences", Context.MODE_PRIVATE)

        fun load(context: Context): KeyboardPreferences {
            val preferences = store(context)
            val theme = KeyboardTheme.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: KeyboardTheme.SYSTEM
            val height = KeyboardHeight.entries.firstOrNull { it.name == preferences.getString("height", null) } ?: KeyboardHeight.NORMAL
            return KeyboardPreferences(theme, height, preferences.getBoolean("number_row", false),
                preferences.getBoolean("vibration", true), preferences.getBoolean("sound", false))
        }

        fun save(context: Context, preferences: KeyboardPreferences) {
            store(context).edit().putString("theme", preferences.theme.name).putString("height", preferences.height.name)
                .putBoolean("number_row", preferences.numberRow).putBoolean("vibration", preferences.vibration)
                .putBoolean("sound", preferences.sound).apply()
        }
    }
}
