package dev.raabta.keyboard.ui

import android.content.ClipData
import android.content.Context
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.KeyboardTool
import dev.raabta.keyboard.ime.ToolOrder
import dev.raabta.keyboard.ime.ToolbarButton

/** Movable tools. Long-press to reorder or send a tool to the toolbar. */
class ToolsPanel @JvmOverloads constructor(
    context: Context, back: () -> Unit = {}, action: (String) -> Unit = {},
    private val changed: () -> Unit = {},
) : LinearLayout(context) {
    private val palette = Palette.from(context)
    private val order = ToolOrder(context)
    private val grid = LinearLayout(context)
    private val historyButtons = mutableMapOf<String, View>()
    private var undoAvailable = false
    private var redoAvailable = false
    init {
        orientation = VERTICAL; tag = "SettingsPanel"; setBackgroundColor(palette.background)
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ToolbarButton(context).apply {
            text = "‹"; tag = "SettingsBack"; contentDescription = "Back to keyboard"
            setTextColor(palette.text); background = palette.pressed(radius=context.dp(10).toFloat()); setOnClickListener { back() }
        }, LayoutParams(context.dp(48),context.dp(48)))
        heading.addView(TextView(context).apply { setText(R.string.tools_menu_title); textSize=18f; setTextColor(palette.text) },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        addView(heading)
        grid.apply { orientation=VERTICAL; setPadding(context.dp(6),0,context.dp(6),context.dp(6)); tag="ToolsGrid" }
        addView(ScrollView(context).apply { tag="ToolsScroll"; addView(grid) },LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        populate(action)
    }
    private fun icon(tool: KeyboardTool): Int = when(tool.id) {
        "Settings" -> R.drawable.ic_settings
        "Undo" -> R.drawable.ic_undo
        "Redo" -> R.drawable.ic_redo
        "Language" -> R.drawable.ic_globe
        "Theme" -> R.drawable.ic_theme
        "Clipboard" -> R.drawable.ic_clipboard
        "Emoji", "Stickers / GIFs" -> R.drawable.ic_emoji
        "Resize", "One-handed" -> R.drawable.ic_resize
        "Connections" -> R.drawable.ic_connection
        "Roman Urdu → Urdu", "Translation" -> R.drawable.ic_translate
        "Share" -> R.drawable.ic_share
        "Floating" -> R.drawable.ic_keyboard
        "Tone" -> R.drawable.ic_tone
        "Rewrite" -> R.drawable.ic_rewrite
        "Grammar", "Text editing", "AI Write" -> R.drawable.ic_grammar
        "Voice" -> R.drawable.ic_microphone
        else -> R.drawable.ic_ai
    }
    private fun populate(action: (String) -> Unit) {
        grid.removeAllViews(); historyButtons.clear()
        order.more().chunked(3).forEachIndexed { rowIndex, tools ->
            val row=LinearLayout(context).apply { isBaselineAligned=false; tag="ToolRow:$rowIndex" }
            tools.forEachIndexed { column,tool ->
                val tile=LinearLayout(context).apply {
                    orientation=VERTICAL; gravity=Gravity.CENTER; isClickable=true; isFocusable=true
                    tag="settings:${tool.id}"; contentDescription=tool.label
                    background=palette.pressed(radius=context.dp(12).toFloat())
                    addView(ImageView(context).apply {
                        setImageDrawable(AppCompatResources.getDrawable(context,icon(tool)))
                        imageTintList=android.content.res.ColorStateList.valueOf(palette.icon(rowIndex*3+column))
                        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },LayoutParams(context.dp(25),context.dp(25)))
                    addView(TextView(context).apply {
                        text=tool.label; textSize=12f; maxLines=2; gravity=Gravity.CENTER; setTextColor(palette.text)
                        setPadding(context.dp(2),context.dp(5),context.dp(2),0)
                    })
                    setOnClickListener { action(tool.command) }
                    setOnLongClickListener { view ->
                        PopupMenu(context,view).apply {
                            menu.add("Reorder").setOnMenuItemClickListener {
                                view.startDragAndDrop(ClipData.newPlainText("tool",tool.id),View.DragShadowBuilder(view),tool.id,0); true
                            }
                            menu.add("Move to Toolbar").setOnMenuItemClickListener {
                                order.move(tool.id,true); changed(); populate(action); true
                            }
                            show()
                        }; true
                    }
                    setOnDragListener { _,event ->
                        when(event.action) {
                            DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                            DragEvent.ACTION_DROP -> {
                                val source=event.localState as? String ?: return@setOnDragListener false
                                order.reorder(source,tool.id,false); populate(action); true
                            }
                            else -> true
                        }
                    }
                }
                if(tool.command=="edit:undo" || tool.command=="edit:redo") historyButtons[tool.command]=tile
                row.addView(tile,LayoutParams(0,context.dp(80),1f).apply { setMargins(context.dp(3),context.dp(3),context.dp(3),context.dp(3)) })
            }
            repeat(3-tools.size) { row.addView(View(context),LayoutParams(0,context.dp(80),1f)) }
            grid.addView(row)
        }
        historyAvailability(undoAvailable,redoAvailable)
    }
    fun historyAvailability(undo: Boolean, redo: Boolean) {
        undoAvailable=undo; redoAvailable=redo
        for((command,available) in listOf("edit:undo" to undo,"edit:redo" to redo)) historyButtons[command]?.apply {
            isEnabled=available; alpha=if(available) 1f else 0.38f
            contentDescription=if(available) command.substringAfter(':') else "${command.substringAfter(':')} unavailable"
        }
    }
}
