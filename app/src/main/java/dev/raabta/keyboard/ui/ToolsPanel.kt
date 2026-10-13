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

/** The edit control enables direct hold-and-drag reordering of the Tools grid. */
class ToolsPanel @JvmOverloads constructor(
    context: Context, back: () -> Unit = {}, action: (String) -> Unit = {},
    private val changed: () -> Unit = {},
) : LinearLayout(context) {
    private val palette = Palette.from(context)
    private val order = ToolOrder(context)
    private val grid = LinearLayout(context)
    private val historyButtons = mutableMapOf<String, View>()
    private val tileViews = mutableMapOf<String, View>()
    private val dragOrigins = mutableMapOf<String, Pair<Int, Int>>()
    private var undoAvailable = false
    private var redoAvailable = false
    private var editing = false
    private lateinit var editButton: ToolbarButton
    private lateinit var scroller: ScrollView
    private val toolAction = action
    init {
        orientation = VERTICAL; tag = "SettingsPanel"; setBackgroundColor(palette.background)
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ToolbarButton(context).apply {
            text = "‹"; tag = "SettingsBack"; contentDescription = "Back to keyboard"
            setTextColor(palette.text); background = palette.pressed(radius=context.dp(10).toFloat()); setOnClickListener { back() }
        }, LayoutParams(context.dp(48),context.dp(48)))
        heading.addView(TextView(context).apply { setText(R.string.tools_menu_title); textSize=18f; setTextColor(palette.text) },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        editButton = ToolbarButton(context).apply {
            text = "✎"; tag = "ToolsEdit"; contentDescription = "Edit tool order"
            setTextColor(palette.text)
            background = palette.toolbarBackground(0, false, context.dp(10).toFloat())
            setOnClickListener {
                editing = !editing
                isSelected = editing
                setTextColor(if (editing) palette.onAccent else palette.text)
                background = palette.toolbarBackground(0, editing, context.dp(10).toFloat())
                populate(toolAction)
            }
        }
        heading.addView(editButton, LayoutParams(context.dp(48), context.dp(48)))
        addView(heading)
        grid.apply { orientation=VERTICAL; setPadding(context.dp(6),0,context.dp(6),context.dp(6)); tag="ToolsGrid" }
        scroller = ScrollView(context).apply { tag="ToolsScroll"; addView(grid) }
        addView(scroller,LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
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
        val previousScroll = if (::scroller.isInitialized) scroller.scrollY else 0
        grid.removeAllViews(); historyButtons.clear(); tileViews.clear(); dragOrigins.clear()
        order.more().chunked(3).forEachIndexed { rowIndex, tools ->
            val row=LinearLayout(context).apply { isBaselineAligned=false; tag="ToolRow:$rowIndex" }
            tools.forEachIndexed { column,tool ->
                val tile=LinearLayout(context).apply {
                    orientation=VERTICAL; gravity=Gravity.CENTER; isClickable=true; isFocusable=true
                    tag="settings:${tool.id}"; contentDescription=tool.label
                    background=palette.pressed(if (editing) palette.surface else palette.background, context.dp(12).toFloat())
                    addView(ImageView(context).apply {
                        setImageDrawable(AppCompatResources.getDrawable(context,icon(tool)))
                        imageTintList=android.content.res.ColorStateList.valueOf(palette.icon(rowIndex*3+column))
                        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },LayoutParams(context.dp(25),context.dp(25)))
                    addView(TextView(context).apply {
                        text=tool.label; textSize=12f; maxLines=2; gravity=Gravity.CENTER; setTextColor(palette.text)
                        setPadding(context.dp(2),context.dp(5),context.dp(2),0)
                    })
                    if (editing) addView(TextView(context).apply {
                        text = "⋮⋮"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(palette.muted)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    setOnClickListener { if (!editing) action(tool.command) }
                    setOnLongClickListener { view ->
                        if (editing) {
                            rememberOrigins()
                            view.startDragAndDrop(ClipData.newPlainText("tool",tool.id),View.DragShadowBuilder(view),tool.id,0)
                        } else {
                            PopupMenu(context,view).apply {
                                menu.add("Move to Toolbar").setOnMenuItemClickListener {
                                    order.move(tool.id,true); changed(); populate(action); true
                                }
                                show()
                            }
                            true
                        }
                    }
                    setOnDragListener { _,event ->
                        when(event.action) {
                            DragEvent.ACTION_DRAG_STARTED -> editing && event.localState is String
                            DragEvent.ACTION_DRAG_ENTERED -> {
                                (event.localState as? String)?.let { previewOrder(it,tool.id) }; true
                            }
                            DragEvent.ACTION_DRAG_LOCATION -> {
                                val edge = context.dp(32)
                                val y = row.top + this.top + event.y - scroller.scrollY
                                when {
                                    y < edge -> scroller.scrollBy(0, -context.dp(22))
                                    y > scroller.height - edge -> scroller.scrollBy(0, context.dp(22))
                                }
                                true
                            }
                            DragEvent.ACTION_DROP -> {
                                val source=event.localState as? String ?: return@setOnDragListener false
                                order.reorder(source,tool.id,false); populate(action); true
                            }
                            DragEvent.ACTION_DRAG_ENDED -> { resetPreview(); true }
                            else -> true
                        }
                    }
                }
                if(tool.command=="edit:undo" || tool.command=="edit:redo") historyButtons[tool.command]=tile
                tileViews[tool.id]=tile
                row.addView(tile,LayoutParams(0,context.dp(80),1f).apply { setMargins(context.dp(3),context.dp(3),context.dp(3),context.dp(3)) })
            }
            repeat(3-tools.size) { row.addView(View(context),LayoutParams(0,context.dp(80),1f)) }
            grid.addView(row)
        }
        historyAvailability(undoAvailable,redoAvailable)
        if (previousScroll > 0) scroller.post { scroller.scrollTo(0, previousScroll) }
    }
    private fun rememberOrigins() {
        dragOrigins.clear()
        tileViews.forEach { (id,view) ->
            val point=IntArray(2); view.getLocationInWindow(point); dragOrigins[id]=point[0] to point[1]
        }
    }
    private fun previewOrder(source:String,target:String) {
        val ids=order.more().map(KeyboardTool::id)
        val from=ids.indexOf(source); val to=ids.indexOf(target)
        if(from<0 || to<0) return
        ids.forEachIndexed { index,id ->
            val destination=when {
                id==source -> id
                from<to && index in (from+1)..to -> ids[index-1]
                from>to && index in to until from -> ids[index+1]
                else -> id
            }
            val here=dragOrigins[id] ?: return@forEachIndexed
            val there=dragOrigins[destination] ?: return@forEachIndexed
            tileViews[id]?.animate()?.translationX((there.first-here.first).toFloat())
                ?.translationY((there.second-here.second).toFloat())?.setDuration(85)?.start()
        }
    }
    private fun resetPreview() {
        tileViews.values.forEach { it.animate().translationX(0f).translationY(0f).setDuration(100).start() }
        dragOrigins.clear()
    }
    fun historyAvailability(undo: Boolean, redo: Boolean) {
        undoAvailable=undo; redoAvailable=redo
        for((command,available) in listOf("edit:undo" to undo,"edit:redo" to redo)) historyButtons[command]?.apply {
            isEnabled=available || editing; alpha=if(available || editing) 1f else 0.38f
            contentDescription=if(available) command.substringAfter(':') else "${command.substringAfter(':')} unavailable"
        }
    }
}
