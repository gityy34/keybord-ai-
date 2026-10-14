package dev.raabta.keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
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
    private val dragTouch = android.os.Handler(android.os.Looper.getMainLooper())
    private var dragId: String? = null
    private var dragTarget: String? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartScroll = 0
    private var dragGrabOffsetX = 0f
    private var dragGrabOffsetY = 0f
    private var undoAvailable = false
    private var redoAvailable = false
    private var editing = false
    private lateinit var editButton: ToolbarButton
    private lateinit var scroller: ScrollView
    private val toolAction = action
    init {
        orientation = VERTICAL; tag = "SettingsPanel"; background = palette.backgroundDrawable()
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ToolbarButton(context).apply {
            text = "‹"; tag = "SettingsBack"; contentDescription = "Back to keyboard"
            setTextColor(palette.text); background = palette.pressed(radius=context.dp(10).toFloat()); setOnClickListener { back() }
        }, LayoutParams(context.dp(48),context.dp(48)))
        heading.addView(TextView(context).apply { setText(R.string.tools_menu_title); textSize=18f; setTextColor(palette.text) },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        val resetButton = ToolbarButton(context).apply {
            text = "↺"; tag = "ToolsReset"; contentDescription = "Reset tool layout"
            setTextColor(palette.text); background = palette.toolbarBackground(0, false, context.dp(10).toFloat())
            setOnClickListener { toolAction("tools:reset") }
        }
        heading.addView(resetButton, LayoutParams(context.dp(44), context.dp(48)))
        heading.addView(ToolbarButton(context).apply {
            text = "⌕"; tag = "ToolsSearch"; contentDescription = "Search settings"
            setTextColor(palette.text); background = palette.toolbarBackground(0, false, context.dp(10).toFloat())
            setOnClickListener { toolAction("settings:search") }
        }, LayoutParams(context.dp(44), context.dp(48)))
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
        val toolbarPreview = HorizontalScrollView(context).apply {
            tag = "ToolbarPreview"
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                order.toolbar().forEachIndexed { index, tool ->
                    addView(ToolbarButton(context).apply {
                        text = tool.glyph; tag = "ToolbarPreview:${tool.id}"; caption = null
                        contentDescription = tool.label; setTextColor(palette.icon(index))
                        background = palette.toolbarBackground(index, false, context.dp(9).toFloat())
                        setOnClickListener { if (!editing) action(tool.command) }
                    }, LayoutParams(context.dp(44), context.dp(42)).apply { setMargins(context.dp(2), 0, context.dp(2), 0) })
                }
            })
        }
        addView(toolbarPreview, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))
        grid.apply { orientation=VERTICAL; clipChildren=false; setPadding(context.dp(6),0,context.dp(6),context.dp(6)); tag="ToolsGrid" }
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
        "Translation" -> R.drawable.ic_translate
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
            val row=LinearLayout(context).apply { isBaselineAligned=false; clipChildren=false; tag="ToolRow:$rowIndex" }
            tools.forEachIndexed { column,tool ->
                val tile=LinearLayout(context).apply {
                    orientation=VERTICAL; gravity=Gravity.CENTER; isClickable=true; isFocusable=true
                    tag="settings:${tool.id}"; contentDescription=tool.label
                    background=palette.pressed(if (editing) palette.surface else palette.background, context.dp(12).toFloat())
                    scaleX=if(editing) 0.98f else 1f; scaleY=if(editing) 0.98f else 1f
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
                        if (!editing) {
                            PopupMenu(context,view).apply {
                                menu.add("Move to Toolbar").setOnMenuItemClickListener {
                                    order.move(tool.id,true); changed(); populate(action); true
                                }
                                show()
                            }
                        }
                        true
                    }
                    attachGridReorderTouch(this, tool.id)
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
    @SuppressLint("ClickableViewAccessibility")
    private fun attachGridReorderTouch(tile:View, id:String) {
        tile.setOnTouchListener { view,event ->
            if(!editing) return@setOnTouchListener false
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragTouch.removeCallbacksAndMessages(null)
                    dragId=null; dragTarget=id; dragStartRawX=event.rawX; dragStartRawY=event.rawY; dragStartScroll=scroller.scrollY
                    val location=IntArray(2); view.getLocationOnScreen(location)
                    dragGrabOffsetX=event.rawX-location[0]; dragGrabOffsetY=event.rawY-location[1]
                    view.isPressed=true
                    dragTouch.postDelayed({ beginGridReorder(id,view) },800)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val active=dragId==id
                    if(!active && (kotlin.math.abs(event.rawX-dragStartRawX)>context.dp(8) || kotlin.math.abs(event.rawY-dragStartRawY)>context.dp(8))) dragTouch.removeCallbacksAndMessages(null)
                    if(active) updateGridReorder(view,event.rawX,event.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragTouch.removeCallbacksAndMessages(null)
                    view.isPressed=false
                    if(dragId==id) finishGridReorder(true)
                    else view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragTouch.removeCallbacksAndMessages(null)
                    view.isPressed=false
                    if(dragId==id) finishGridReorder(false)
                    true
                }
                else -> true
            }
        }
    }
    private fun beginGridReorder(id:String, view:View) {
        if(tileViews[id] !== view || !view.isPressed || !editing) return
        dragId=id; dragTarget=id; rememberOrigins()
        // Keep the tile in its row. bringToFront() reorders the row's children and
        // caused tiles to vanish or jump while the grid was being rearranged.
        view.elevation=context.dp(6).toFloat(); view.alpha=0.92f
        view.parent?.requestDisallowInterceptTouchEvent(true)
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }
    private fun updateGridReorder(view:View, rawX:Float, rawY:Float) {
        val source=dragId ?: return
        val origin=dragOrigins[source] ?: return
        val scrollDelta=scroller.scrollY-dragStartScroll
        // Preserve the point where the finger grabbed the tile. This prevents a
        // visible jump on the first move and keeps the tile directly under it.
        view.translationX=rawX-dragGrabOffsetX-origin.first
        view.translationY=rawY-dragGrabOffsetY-origin.second+scrollDelta
        val scrollerLocation=IntArray(2); scroller.getLocationOnScreen(scrollerLocation)
        val localY=rawY-scrollerLocation[1]
        val edge=context.dp(34)
        when {
            localY<edge -> scroller.scrollBy(0,-context.dp(18))
            localY>scroller.height-edge -> scroller.scrollBy(0,context.dp(18))
        }
        val target=tileViews.minByOrNull { (id,tile) ->
            val location=dragOrigins[id]
            if (location==null) return@minByOrNull Float.MAX_VALUE
            val centerX=location.first+tile.width/2f
            val centerY=location.second+tile.height/2f-scrollDelta
            kotlin.math.abs(rawX-centerX)+kotlin.math.abs(rawY-centerY)
        }?.key ?: source
        if(target!=dragTarget) {
            dragTarget=target
            previewOrder(source,target)
        }
    }
    private fun finishGridReorder(commit:Boolean) {
        val source=dragId
        val target=dragTarget
        resetPreview()
        tileViews.values.forEach {
            it.translationX=0f; it.translationY=0f; it.elevation=0f; it.alpha=1f
            it.parent?.requestDisallowInterceptTouchEvent(false)
        }
        dragId=null; dragTarget=null
        if(commit && source!=null && target!=null) {
            order.reorder(source,target,false)
            changed()
            populate(toolAction)
        }
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
            tileViews[id]?.apply {
                if (id==source) return@apply
                translationX = (there.first-here.first).toFloat()
                translationY = (there.second-here.second).toFloat()
            }
        }
    }
    private fun resetPreview() {
        tileViews.values.forEach { it.translationX=0f; it.translationY=0f; it.elevation=0f; it.alpha=1f }
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
