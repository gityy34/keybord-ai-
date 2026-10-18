package dev.raabta.keyboard.ui

import androidx.core.content.edit
import android.content.Context

/** Settings and explicitly saved dictionary entries. Learned frequencies use a separate local store. */
class KeyboardOptions(context: Context) {
    private val store = context.getSharedPreferences("keyboard_options", Context.MODE_PRIVATE)
    fun enabled(key: String) = store.getBoolean(key, defaults[key] ?: false)
    fun set(key: String, value: Boolean) { store.edit { putBoolean(key, value) } }
    fun number(key: String) = store.getInt(key, numbers[key]?.first ?: 0)
        .coerceIn(numbers[key]?.second ?: 0..100)
    fun setNumber(key: String, value: Int) {
        store.edit { putInt(key, value.coerceIn(numbers[key]?.second ?: 0..100)) }
    }
    fun fingerprint() = store.all.hashCode()
    fun dictionary(): List<String> = store.getStringSet("dictionary", emptySet())!!.sorted()
    fun addWord(word: String) {
        val value = word.trim().take(40)
        if (value.isNotEmpty() && value.none { it.isWhitespace() })
            store.edit { putStringSet("dictionary", (dictionary() + value).takeLast(200).toSet()) }
    }
    fun removeWord(word: String) { store.edit { putStringSet("dictionary", dictionary().minus(word).toSet()) } }
    fun emoji(): List<String> = store.getString("emoji_recent", "")!!.split('|').filter { it.isNotEmpty() }
    fun usedEmoji(emoji: String) {
        store.edit { putString("emoji_recent", (listOf(emoji) + emoji().minus(emoji)).take(24).joinToString("|")) }
    }
    fun clearLocalData() { store.edit { remove("dictionary").remove("emoji_recent") } }
    companion object {
        /** The reference keyboard is the 100% baseline; never shrink below 96%. */
        const val HEIGHT_SCALE_MIN = 96
        /** Free-hand resize may grow to roughly two thirds of a portrait IME window. */
        const val HEIGHT_SCALE_MAX = 180
        val defaults = mapOf(
            "clipboard_enabled" to true, "emoji_key" to true, "language_key" to true, "comma_key" to true, "full_stop_key" to true,
            "suggestion_strip" to true, "word_suggestions" to true, "next_word" to true,
            "emoji_suggestions" to true, "offensive_filter" to true, "auto_capitalization" to true,
            "auto_space" to false, "apostrophe_space" to false, "double_space" to false,
            "symbols_hold" to true, "flick_symbols" to false, "popup" to false, "cursor_slide" to true,
            "learn_words" to true, "roman_suggestions" to true, "delete_swipe" to true,
            "auto_correction" to false, "spell_check" to false, "grammar_check" to false,
            "contacts" to false, "smart_replies" to false, "emoji_row" to false,
            "symbols_emoji" to false, "clipboard_suggestions" to true, "clipboard_items" to false,
            "screenshots" to false, "glide" to true, "app_icon" to true, "symbols_number_row" to true, "floating" to false,
        )
        val numbers = mapOf("font_size" to (22 to 16..32), "emoji_size" to (26 to 18..40),
            "hold_duration" to (400 to 200..800), "flick_sensitivity" to (24 to 12..48),
            "sound_volume" to (50 to 0..100), "vibration_strength" to (40 to 1..100),
            "typing_sensitivity" to (80 to 0..100), "suggestion_delay" to (0 to 0..200), "suggestion_count" to (3 to 2..4), "language" to (0 to 0..2), "one_handed" to (0 to 0..2),
            "height_scale" to (100 to HEIGHT_SCALE_MIN..HEIGHT_SCALE_MAX))
    }
}
