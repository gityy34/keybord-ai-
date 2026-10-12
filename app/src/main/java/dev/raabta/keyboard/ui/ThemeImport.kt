package dev.raabta.keyboard.ui

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import org.json.JSONObject

data class ImportedTheme(val name: String, val palette: Palette, val definition: String)

/** Declarative local colours only: no images, URLs, scripts, fonts or executable theme content. */
object ThemeImport {
    const val MAX_BYTES = 16_384
    private val fields = setOf("background", "surface", "key", "text", "muted", "accent", "secondary", "border")
    fun parse(value: String): ImportedTheme {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Theme file is too large." }
        val json = JSONObject(value)
        require(json.keys().asSequence().all { it in setOf("version", "name", "dark", "colors") }) { "Unsupported theme content." }
        require(json.getInt("version") == 1) { "Unsupported theme version." }
        val name = json.getString("name").trim()
        require(name.length in 1..40 && name.none { it.isISOControl() }) { "Choose a short theme name." }
        val colors = json.getJSONObject("colors")
        require(colors.keys().asSequence().all { it in fields }) { "Only theme colours are allowed." }
        fun color(key: String, fallback: Int? = null): Int {
            if (!colors.has(key) && fallback != null) return fallback
            val text = colors.getString(key)
            require(text.matches(Regex("#[0-9a-fA-F]{6}"))) { "Use opaque #RRGGBB colours." }
            return text.toColorInt()
        }
        val surface = color("surface"); val accent = color("accent")
        val palette = Palette(color("background"), surface, color("text"), color("muted"), accent,
            color("key", surface), color("border", surface), color("secondary", accent), json.getBoolean("dark"), true)
        require(listOf(palette.background, palette.surface, palette.key).all { ColorUtils.calculateContrast(palette.text, it) >= 4.5 }) { "Text needs stronger contrast against the surfaces." }
        require(ColorUtils.calculateContrast(palette.muted, palette.surface) >= 4.5 && ColorUtils.calculateContrast(palette.accent, palette.background) >= 3) { "Labels and icons need stronger contrast." }
        return ImportedTheme(name, palette, json.toString())
    }
    fun save(context: Context, theme: ImportedTheme) {
        val verified = parse(theme.definition)
        context.getSharedPreferences("keyboard_theme_import", Context.MODE_PRIVATE).edit { putString("definition", verified.definition) }
        KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = KeyboardTheme.CUSTOM))
    }
    fun load(context: Context): ImportedTheme? = try {
        context.getSharedPreferences("keyboard_theme_import", Context.MODE_PRIVATE).getString("definition", null)?.let(::parse)
    } catch (_: Exception) { null }
}
