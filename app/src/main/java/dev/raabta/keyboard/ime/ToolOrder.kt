package dev.raabta.keyboard.ime

import android.content.Context
import org.json.JSONArray
import androidx.core.content.edit

data class KeyboardTool(val id: String, val label: String, val glyph: String, val command: String)

/** One source of truth for toolbar and More; every entry routes to an existing action. */
object KeyboardTools {
    val all = listOf(
        KeyboardTool("AI", "AI", "✦", "ai"),
        KeyboardTool("Grammar", "Fix Grammar", "✎", "grammar"),
        KeyboardTool("Rewrite", "Rewrite", "↻", "rewrite"),
        KeyboardTool("Tone", "Tone", "☷", "tone"),
        KeyboardTool("Translation", "Translate", "⇄", "translate"),
        KeyboardTool("Voice", "Voice", "🎙", "voice"),
        KeyboardTool("Settings", "Settings", "⚙", "settings"),
        KeyboardTool("Undo", "Undo", "↶", "edit:undo"),
        KeyboardTool("Redo", "Redo", "↷", "edit:redo"),
        KeyboardTool("Language", "Language", "🌐", "languages"),
        KeyboardTool("Theme", "Themes", "◈", "themes"),
        KeyboardTool("AI Chat", "AI Chat", "✦", "ai"),
        KeyboardTool("AI Write", "AI Write", "✎", "ai"),
        KeyboardTool("Clipboard", "Clipboard", "▤", "clipboard"),
        KeyboardTool("Emoji", "Emoji", "☺", "emoji"),
        KeyboardTool("Text editing", "Text editing", "✎", "editing"),
        KeyboardTool("Resize", "Resize", "□", "resize"),
        KeyboardTool("Connections", "Connections", "⚙", "connections"),
        KeyboardTool("Roman Urdu → Urdu", "Roman Urdu", "⇄", "roman"),
        KeyboardTool("Stickers / GIFs", "Stickers / GIFs", "☺", "media"),
        KeyboardTool("Floating", "Floating", "⌨", "floating"),
        KeyboardTool("Share", "Share", "⇄", "share"),
        KeyboardTool("One-handed", "One-handed", "□", "one_handed"),
    )
    val defaultToolbar = listOf("AI", "Grammar", "Rewrite", "Tone", "Translation", "Voice")
}

class ToolOrder(context: Context) {
    private val prefs = context.getSharedPreferences("tool_order", Context.MODE_PRIVATE)
    private val valid = KeyboardTools.all.map(KeyboardTool::id).toSet()
    private fun read(name: String): List<String>? = try {
        val raw = prefs.getString(name, null) ?: return null
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getString(it) }.filter { it in valid }.distinct()
    } catch (_: Exception) { null }
    private fun save(toolbar: List<String>, more: List<String>) {
        prefs.edit { putString("toolbar", JSONArray(toolbar).toString()); putString("more", JSONArray(more).toString()) }
    }
    fun toolbar(): List<KeyboardTool> {
        val ids = read("toolbar") ?: KeyboardTools.defaultToolbar
        return ids.distinct().mapNotNull { id -> KeyboardTools.all.firstOrNull { it.id == id } }
    }
    fun more(): List<KeyboardTool> {
        val bar = toolbar().map(KeyboardTool::id).toSet()
        val saved = read("more").orEmpty()
        return (saved + KeyboardTools.all.map(KeyboardTool::id)).distinct().filter { it !in bar }
            .mapNotNull { id -> KeyboardTools.all.firstOrNull { it.id == id } }
    }
    fun move(id: String, toToolbar: Boolean) {
        if (id !in valid) return
        val bar = toolbar().map(KeyboardTool::id).toMutableList().apply { remove(id) }
        val menu = more().map(KeyboardTool::id).toMutableList().apply { remove(id) }
        if (toToolbar) bar.add(id) else menu.add(0, id)
        save(bar, menu)
    }
    fun reorder(source: String, target: String, inToolbar: Boolean) {
        val bar = toolbar().map(KeyboardTool::id).toMutableList()
        val menu = more().map(KeyboardTool::id).toMutableList()
        val section = if (inToolbar) bar else menu
        if (source !in section || target !in section || source == target) return
        val destination = section.indexOf(target)
        section.remove(source)
        section.add(destination, source)
        save(bar, menu)
    }
}
