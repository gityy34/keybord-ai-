package dev.raabta.keyboard.ui

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import org.json.JSONObject
import org.json.JSONArray

data class ImportedTheme(val name: String, val palette: Palette, val definition: String)

/** Declarative local colours only: no images, URLs, scripts, fonts or executable theme content. */
object ThemeImport {
    const val MAX_BYTES = 16_384
    private val fields = setOf("background", "surface", "key", "text", "muted", "accent", "secondary", "border", "backgroundGradient", "keyGradient")
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
        fun gradient(key: String): List<Int>? = if (!colors.has(key)) null else {
            val values = colors.getJSONArray(key)
            require(values.length() in 2..4) { "A gradient needs two to four colours." }
            (0 until values.length()).map { value ->
                val text = values.getString(value)
                require(text.matches(Regex("#[0-9a-fA-F]{6}"))) { "Use opaque #RRGGBB colours." }
                text.toColorInt()
            }
        }
        val palette = Palette(color("background"), surface, color("text"), color("muted"), accent,
            color("key", surface), color("border", surface), color("secondary", accent), json.getBoolean("dark"), true,
            backgroundGradient = gradient("backgroundGradient"), keyGradient = gradient("keyGradient"))
        require(listOf(palette.background, palette.surface, palette.key).all { ColorUtils.calculateContrast(palette.text, it) >= 4.5 }) { "Text needs stronger contrast against the surfaces." }
        require(ColorUtils.calculateContrast(palette.muted, palette.surface) >= 4.5 && ColorUtils.calculateContrast(palette.accent, palette.background) >= 3) { "Labels and icons need stronger contrast." }
        return ImportedTheme(name, palette, json.toString())
    }
    fun save(context: Context, theme: ImportedTheme) {
        val verified = parse(theme.definition)
        context.getSharedPreferences("keyboard_theme_import", Context.MODE_PRIVATE).edit { putString("definition", verified.definition) }
        KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = KeyboardTheme.CUSTOM))
    }
    fun delete(context: Context) { context.getSharedPreferences("keyboard_theme_import", Context.MODE_PRIVATE).edit { clear() } }
    fun definition(name: String, palette: Palette): String {
        fun color(value: Int) = "#%06X".format(0xFFFFFF and value)
        val colors = JSONObject().apply {
            put("background", color(palette.background)); put("surface", color(palette.surface)); put("key", color(palette.key))
            put("text", color(palette.text)); put("muted", color(palette.muted)); put("accent", color(palette.accent))
            put("secondary", color(palette.secondary)); put("border", color(palette.border))
            palette.backgroundGradient?.let { put("backgroundGradient", JSONArray(it.map(::color))) }
            palette.keyGradient?.let { put("keyGradient", JSONArray(it.map(::color))) }
        }
        return JSONObject().put("version",1).put("name",name.take(40)).put("dark",palette.dark).put("colors",colors).toString()
    }
    fun load(context: Context): ImportedTheme? = try {
        context.getSharedPreferences("keyboard_theme_import", Context.MODE_PRIVATE).getString("definition", null)?.let(::parse)
    } catch (_: Exception) { null }
}
