package dev.raabta.keyboard.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.ToolbarButton

/** Only routes already supported by the IME are exposed. Three equal touch areas per row. */
class ToolsPanel @JvmOverloads constructor(context: Context, back: () -> Unit = {}, action: (String) -> Unit = {}) : LinearLayout(context) {
    private val palette = Palette.from(context)
    data class Tool(val label: String, val tagName: String, val icon: Int, val command: String)
    private val historyButtons = mutableMapOf<String, View>()
    init {
        orientation = VERTICAL; tag = "SettingsPanel"; setBackgroundColor(palette.background)
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ToolbarButton(context).apply {
            text = "‹"; tag = "SettingsBack"; contentDescription = "Back to keyboard"
            setTextColor(palette.text); background = palette.pressed(radius=context.dp(10).toFloat()); setOnClickListener { back() }
        }, LayoutParams(context.dp(48), context.dp(48)))
        heading.addView(TextView(context).apply { setText(R.string.tools_menu_title); textSize = 18f; setTextColor(palette.text) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(heading)
        val grid = LinearLayout(context).apply { orientation = VERTICAL; setPadding(context.dp(6), 0, context.dp(6), context.dp(6)); tag = "ToolsGrid" }
        val tools = listOf(
            Tool("Settings", "Settings", R.drawable.ic_settings, "settings"),
            Tool("Undo", "Undo", R.drawable.ic_undo, "edit:undo"), Tool("Redo", "Redo", R.drawable.ic_redo, "edit:redo"),
            Tool("Language", "Language", R.drawable.ic_globe, "languages"), Tool("Themes", "Theme", R.drawable.ic_theme, "themes"),
            Tool("AI Chat", "AI Chat", R.drawable.ic_ai, "ai"), Tool("AI Write", "AI Write", R.drawable.ic_grammar, "ai"), Tool("Translate", "Translation", R.drawable.ic_translate, "translate"),
            Tool("Clipboard", "Clipboard", R.drawable.ic_clipboard, "clipboard"), Tool("Emoji", "Emoji", R.drawable.ic_emoji, "emoji"),
            Tool("Text editing", "Text editing", R.drawable.ic_grammar, "editing"), Tool("Resize", "Resize", R.drawable.ic_resize, "resize"),
            Tool("Connections", "Connections", R.drawable.ic_connection, "connections"),
            Tool("Roman Urdu", "Roman Urdu → Urdu", R.drawable.ic_translate, "roman"), Tool("Stickers / GIFs", "Stickers and GIFs", R.drawable.ic_emoji, "media"),
            Tool("Floating", "Floating", R.drawable.ic_keyboard, "floating"), Tool("Share", "Share Raabta", R.drawable.ic_share, "share"),
            Tool("One-handed", "One-handed", R.drawable.ic_resize, "one_handed")
        )
        tools.chunked(3).forEachIndexed { rowIndex, items ->
            val row = LinearLayout(context).apply { isBaselineAligned = false; tag = "ToolRow:$rowIndex" }
            items.forEachIndexed { column, tool ->
                val tile = LinearLayout(context).apply {
                    orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
                    tag = "settings:${tool.tagName}"; contentDescription = tool.label
                    background = palette.pressed(radius=context.dp(12).toFloat())
                    val tint = palette.icon(rowIndex * 3 + column)
                    addView(ImageView(context).apply {
                        setImageDrawable(AppCompatResources.getDrawable(context, tool.icon)); imageTintList = android.content.res.ColorStateList.valueOf(tint)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(context.dp(25), context.dp(25)))
                    addView(TextView(context).apply { text = tool.label; textSize = 12f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(palette.text); setPadding(context.dp(2),context.dp(5),context.dp(2),0) })
                    setOnClickListener { action(tool.command) }
                }
                if (tool.command in listOf("edit:undo", "edit:redo")) historyButtons[tool.command] = tile
                row.addView(tile, LayoutParams(0,context.dp(80),1f).apply { setMargins(context.dp(3),context.dp(3),context.dp(3),context.dp(3)) })
            }
            repeat(3 - items.size) { row.addView(View(context), LayoutParams(0,context.dp(80),1f)) }
            grid.addView(row)
        }
        addView(ScrollView(context).apply { tag="ToolsScroll"; addView(grid) }, LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        historyAvailability(false,false)
    }
    fun historyAvailability(undo: Boolean, redo: Boolean) {
        for ((command, available) in listOf("edit:undo" to undo, "edit:redo" to redo)) historyButtons[command]?.apply {
            isEnabled = available; alpha = if (available) 1f else 0.38f
            contentDescription = if (available) command.substringAfter(':') else "${command.substringAfter(':')} unavailable"
        }
    }
}
