package dev.raabta.keyboard.ime

import android.annotation.SuppressLint
import androidx.core.view.isVisible
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.view.MotionEvent
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.ui.KeyboardSettingsPanel
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.KeySurface
import dev.raabta.keyboard.ui.SurfaceStyle
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.rounded
import dev.raabta.keyboard.ui.KeyboardPreferences
import dev.raabta.keyboard.ui.WritingToolPanel
import dev.raabta.keyboard.ui.KeyboardHeight
import dev.raabta.keyboard.ui.KeyFeedback
import dev.raabta.keyboard.ui.FeedbackKey
import dev.raabta.keyboard.voice.VoiceState

sealed interface Key {
    data class Text(val text: String) : Key
    data object Shift : Key
    data object Delete : Key
    data object Enter : Key
    data object Space : Key
    data object Emoji : Key
    data object Language : Key
    data class DeleteSwipe(val words: Int) : Key
    data class Cursor(val steps: Int) : Key
    data class Glide(val path: String) : Key
    data class Mode(val mode: KeyboardMode) : Key
}

/** Layout constants shared by the IME surface and inset regression tests. */
internal object KeyboardInsetMetrics {
    const val OUTER_GAP_DP = 8
    fun bottomSpacerPx(density: Float, navigationInsetPx: Int): Int =
        (OUTER_GAP_DP * density + 0.5f).toInt() + navigationInsetPx.coerceAtLeast(0)
}

/** Native buttons provide accessibility semantics and avoid a custom text-rendering engine. */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    private val state: KeyboardState = KeyboardState(),
    private val onKey: (Key) -> Unit = {},
    private val onCaps: () -> Unit = {},
    private val onSettings: () -> Unit = {},
    private val onSwitch: () -> Unit = {},
    private val onVoice: () -> Unit = {},
    private val onCancelVoice: () -> Unit = {},
    private val onSettingsChanged: (String) -> Unit = {},
    private val onTool: (String) -> Unit = {},
    private val onCandidate: (Candidate) -> Unit = {},
) : LinearLayout(context) {
    private val preferences = KeyboardPreferences.load(context)
    private val options = KeyboardOptions(context)
    private val optionFingerprint = options.fingerprint()
    private val candidateRow = SuggestionStrip(context)
    private val candidateButtons = mutableListOf<Button>()
    private var candidateSignature = emptyList<String>()
    private var panel: View? = null
    var settingsPage = ""
        private set
    private val gestureButtons = mutableListOf<GestureKeyButton>()
    private var glidePath = ""
    private var header: LinearLayout? = null
    private var toolbarScroller: android.widget.HorizontalScrollView? = null
    private var toolbarCenter: FrameLayout? = null
    private var toolbarContainer: View? = null
    private val toolbarButtons=mutableMapOf<String,Button>()
    private val toolOrder=ToolOrder(context)
    private val toolbarDragOrigins=mutableMapOf<String,Int>()
    private val toolbarTouch = android.os.Handler(android.os.Looper.getMainLooper())
    private var toolbarDragId: String? = null
    private var toolbarDragTarget: String? = null
    private var toolbarDragStartRawX = 0f
    private var toolbarDragStartScroll = 0
    private var toolbarGrabOffsetX = 0f
    private var keyPopup: android.widget.PopupWindow? = null
    val floating get() = options.enabled("floating") && settingsPage.isEmpty()
    fun contentBounds():android.graphics.Rect {
        val location=IntArray(2)
        if(content.isAttachedToWindow) content.getLocationInWindow(location)
        else { location[0]=(content.left+content.translationX).toInt(); location[1]=(content.top+content.translationY).toInt() }
        return android.graphics.Rect(location[0],location[1],location[0]+content.width,location[1]+content.height)
    }
    private val palette = Palette.from(context)
    private val feedback = KeyFeedback(context, preferences)
    private val content = LinearLayout(context)
    private val bottomSafeSpacer = View(context).apply {
        tag = "KeyboardBottomSafeSpacer"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }
    private val rows = LinearLayout(context)
    private val letterButtons = mutableListOf<Pair<Button, String>>()
    private var shiftButton: Button? = null
    private var deleteButton: RepeatingDeleteButton? = null
    private var enterLabel = R.string.key_enter
    private val title = DragHandle(context)
    private val voiceMessage = TextView(context)
    private val voiceStatus get()=recorder.status
    private val voiceTimer get()=recorder.timer
    private val voiceUi=android.os.Handler(android.os.Looper.getMainLooper())
    private var voiceStarted=0L
    private val timerTick=object:Runnable { override fun run() { val seconds=((android.os.SystemClock.elapsedRealtime()-voiceStarted)/1000).coerceAtLeast(0); voiceTimer.text=context.getString(R.string.voice_elapsed,seconds/60,seconds%60); if(voiceState!=VoiceState.IDLE) voiceUi.postDelayed(this,1000) } }
    private val voiceLevel get()=recorder.level
    private val recorder = dev.raabta.keyboard.ui.VoicePanel(context,onVoice,onCancelVoice,onTool).apply { visibility=View.GONE }
    private val microphone = headerButton("🎙", R.string.voice_microphone, onVoice).apply { tag = "Voice" }
    private val cancelVoice = headerButton("×", R.string.voice_cancel, onCancelVoice).apply {
        tag = "CancelVoice"
        visibility = View.GONE
    }
    private var voiceAllowed = false
    private var voiceState = VoiceState.IDLE
    private var languageBar: View? = null
    private var writingPanel: WritingToolPanel? = null
    private var writingKeyboardVisible = true
    private var emojiPanel: dev.raabta.keyboard.ui.EmojiPanel? = null
    private var emojiSearching=false
    private var emojiQuery=""
    private var clipboardPinnedOnly=false
    private var resizeOriginalScale=options.number("height_scale")
    private var typingActive=false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = palette.backgroundDrawable()
        // Keep a stable breathing room above the system navigation bar. The spacer
        // is a separate child, so the blank area cannot dispatch key touches.
        setPadding(context.dp(3), context.dp(KeyboardInsetMetrics.OUTER_GAP_DP), context.dp(3), 0)
        content.orientation = VERTICAL
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bottomSafeSpacer, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(KeyboardInsetMetrics.OUTER_GAP_DP)))
        setOnApplyWindowInsetsListener { _, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else { @Suppress("DEPRECATION") insets.systemWindowInsetBottom }
            setPadding(context.dp(3), context.dp(KeyboardInsetMetrics.OUTER_GAP_DP), context.dp(3), 0)
            bottomSafeSpacer.layoutParams = bottomSafeSpacer.layoutParams.apply {
                height = KeyboardInsetMetrics.bottomSpacerPx(resources.displayMetrics.density, bottom)
            }
            bottomSafeSpacer.requestLayout()
            insets
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned=false }
        this.header = header
        var initialX=0f; var initialY=0f
        title.onStart={ initialX=content.translationX; initialY=content.translationY }
        title.onDrag={ dx,dy ->
            val left=-content.left.toFloat(); val top=-content.top.toFloat()
            content.translationX=(initialX+dx).coerceIn(left,(width-content.right).toFloat().coerceAtLeast(left))
            content.translationY=(initialY+dy).coerceIn(top,(height-content.bottom).toFloat().coerceAtLeast(top))
            requestLayout()
        }
        title.contentDescription="Raabta title; drag to move floating keyboard"
        title.setText(R.string.keyboard_title); title.textSize=11f; title.setTextColor(palette.muted)
        title.visibility=if(options.enabled("floating")) View.VISIBLE else View.GONE
        content.addView(title,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(28)))
        toolbarScroller=android.widget.HorizontalScrollView(context).apply { tag="KeyboardToolbar"; isHorizontalScrollBarEnabled=false; addView(header) }
        val center = FrameLayout(context).apply {
            tag = "ToolbarCenter"
            clipChildren = false
            clipToPadding = false
        }
        toolbarCenter = center
        center.addView(toolbarScroller, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))
        val toolbarRow=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        toolbarContainer=toolbarRow
        val more=headerButton("▦",R.string.keyboard_title) { onTool("tools") }.apply {
            tag="Tools"; caption=null; contentDescription="More tools"
        }
        toolbarButtons["Tools"]=more
        // Tools is a stable left anchor and Voice is a stable right anchor.  Only
        // the center strip changes between the tool icons and suggestions.
        toolbarRow.addView(more,LayoutParams(context.dp(46),context.dp(44)))
        toolbarRow.addView(center,LayoutParams(0,context.dp(44),1f))
        (microphone as? ToolbarButton)?.caption = null
        toolbarRow.addView(microphone,LayoutParams(context.dp(44),context.dp(44)))
        candidateRow.tag="SuggestionStrip"
        candidateRow.isBaselineAligned=false
        candidateRow.gravity=Gravity.CENTER
        content.addView(toolbarRow,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(44)))
        refreshToolbar()
        center.addView(candidateRow, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))
        candidateRow.visibility = View.GONE
        content.addView(voiceMessage.apply {
            tag = "VoiceMessage"
            textSize = 13f
            setTextColor(palette.text)
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        })
        content.addView(recorder)
        rows.orientation = VERTICAL
        content.addView(rows)
        setVoiceState(VoiceState.IDLE)
    }

    private fun refreshToolbar() {
        val bar=header ?: return
        bar.removeAllViews()
        toolbarDragOrigins.clear()
        toolbarButtons.keys.filter { it!="Tools" }.toList().forEach(toolbarButtons::remove)
        toolOrder.toolbar().filter { it.id != "Voice" }.forEach { tool ->
            val button=if(tool.id=="Voice") microphone else headerButton(tool.glyph,R.string.keyboard_title) { onTool(tool.command) }
            button.tag=tool.id; button.contentDescription=tool.label
            (button as? ToolbarButton)?.caption=null
            (button.parent as? android.view.ViewGroup)?.removeView(button)
            bar.addView(button,LayoutParams(context.dp(46),context.dp(44)))
            toolbarButtons[tool.id]=button
            attachToolbarReorderTouch(button, tool.id)
        }
        toolbarButtons["Voice"] = microphone
        setActiveTool("")
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun attachToolbarReorderTouch(button:Button, id:String) {
        button.setOnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toolbarTouch.removeCallbacksAndMessages(null)
                    toolbarDragId=null; toolbarDragTarget=id; toolbarDragStartRawX=event.rawX
                    toolbarDragStartScroll=toolbarScroller?.scrollX ?: 0
                    val location=IntArray(2); view.getLocationOnScreen(location)
                    toolbarGrabOffsetX=event.rawX-location[0]
                    view.isPressed=true
                    toolbarTouch.postDelayed({ beginToolbarReorder(id,button) },800)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val active=toolbarDragId==id
                    if(!active && kotlin.math.abs(event.rawX-toolbarDragStartRawX)>context.dp(8)) toolbarTouch.removeCallbacksAndMessages(null)
                    if(active) updateToolbarReorder(button,event.rawX)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    toolbarTouch.removeCallbacksAndMessages(null)
                    view.isPressed=false
                    if(toolbarDragId==id) finishToolbarReorder(commit=true) else view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    toolbarTouch.removeCallbacksAndMessages(null)
                    view.isPressed=false
                    if(toolbarDragId==id) finishToolbarReorder(commit=false)
                    true
                }
                else -> true
            }
        }
    }
    private fun beginToolbarReorder(id:String, button:Button) {
        if(toolbarButtons[id] !== button || !button.isPressed) return
        toolbarDragId=id; toolbarDragTarget=id; rememberToolbarOrigins()
        // Do not reparent or bring the child to front: doing so changes the
        // HorizontalScrollView's child order and was the source of disappearing
        // icons during a drag. Elevation keeps the active item readable instead.
        button.elevation=context.dp(6).toFloat(); button.alpha=0.92f
        button.parent?.requestDisallowInterceptTouchEvent(true)
        feedback.perform(button)
    }
    private fun updateToolbarReorder(button:Button, rawX:Float) {
        val source=toolbarDragId ?: return
        val scroller=toolbarScroller ?: return
        val headerLocation=IntArray(2); header?.getLocationOnScreen(headerLocation)
        val pointerInHeader=rawX-headerLocation[0]
        val originalLeft=toolbarDragOrigins[source] ?: button.left
        button.translationX=pointerInHeader-toolbarGrabOffsetX-originalLeft
        val edge=context.dp(38)
        val localX=rawX-IntArray(2).also { scroller.getLocationOnScreen(it) }[0]
        when {
            localX<edge -> scroller.scrollBy(-context.dp(18),0)
            localX>scroller.width-edge -> scroller.scrollBy(context.dp(18),0)
        }
        val target=toolOrder.toolbar().map(KeyboardTool::id).minByOrNull { id ->
            val view=toolbarButtons[id] ?: return@minByOrNull Float.MAX_VALUE
            val origin=toolbarDragOrigins[id] ?: view.left
            kotlin.math.abs(pointerInHeader-(origin+view.width/2f))
        } ?: source
        if(target!=toolbarDragTarget) {
            toolbarDragTarget=target
            previewToolbarOrder(source,target)
        }
    }
    private fun finishToolbarReorder(commit:Boolean) {
        val source=toolbarDragId
        val target=toolbarDragTarget
        toolbarButtons.values.forEach { it.translationX=0f; it.elevation=0f; it.alpha=1f }
        toolbarDragOrigins.clear(); toolbarDragId=null; toolbarDragTarget=null
        toolbarButtons.values.forEach { it.parent?.requestDisallowInterceptTouchEvent(false) }
        if(commit && source!=null && target!=null) {
            toolOrder.reorder(source,target,true)
            refreshToolbar()
        }
    }
    private fun rememberToolbarOrigins() {
        toolbarDragOrigins.clear()
        toolOrder.toolbar().forEach { tool -> toolbarButtons[tool.id]?.let { toolbarDragOrigins[tool.id]=it.left } }
    }
    private fun previewToolbarOrder(source:String,target:String) {
        val ids=toolOrder.toolbar().map(KeyboardTool::id)
        val from=ids.indexOf(source); val to=ids.indexOf(target)
        if(from<0 || to<0) return
        ids.forEachIndexed { index,id ->
            val destination=when {
                id==source -> id
                from<to && index in (from+1)..to -> ids[index-1]
                from>to && index in to until from -> ids[index+1]
                else -> id
            }
            val here=toolbarDragOrigins[id] ?: return@forEachIndexed
            val there=toolbarDragOrigins[destination] ?: return@forEachIndexed
            toolbarButtons[id]?.apply {
                if (id==source) return@apply
                translationX = (there-here).toFloat()
            }
        }
    }
    private fun toolbarVisible(visible: Boolean) {
        toolbarTouch.removeCallbacksAndMessages(null)
        if(toolbarDragId!=null) finishToolbarReorder(false)
        toolbarContainer?.visibility=if(visible) View.VISIBLE else View.GONE
        if (!visible) {
            toolbarScroller?.visibility = View.GONE
            candidateRow.visibility = View.GONE
        } else {
            applyCenterMode()
        }
    }

    private fun applyCenterMode() {
        val enabled = options.enabled("suggestion_strip")
        val showSuggestions = typingActive && enabled && settingsPage.isEmpty() && panel == null && writingPanel == null && !recorder.isVisible
        toolbarScroller?.visibility = if (showSuggestions) View.GONE else View.VISIBLE
        candidateRow.visibility = if (showSuggestions) View.VISIBLE else View.GONE
    }

    /** Once composition starts, the center toolbar area becomes the suggestion area. */
    fun setTypingActive(active: Boolean) {
        val changed = typingActive != active
        typingActive = active
        if (settingsPage.isEmpty() && panel == null && writingPanel == null && !recorder.isVisible) {
            applyCenterMode()
            if (changed) requestLayout()
        }
    }

    private fun headerButton(label: String, description: Int, action: () -> Unit) = ToolbarButton(context).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        isSoundEffectsEnabled = false
        stateListAnimator = null
        isHapticFeedbackEnabled = preferences.vibration
        contentDescription = context.getString(description)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(palette.text)
        background = palette.pressed(if(palette.style==SurfaceStyle.FLAT) palette.background else palette.surface, context.dp(8).toFloat())
        layoutParams = LayoutParams(context.dp(40), context.dp(44))
        setOnClickListener { feedback.perform(this); action() }
    }

    fun render(enterLabel: Int) {
        cancelRepeats()
        this.enterLabel = enterLabel
        candidateSignature = emptyList()
        rows.removeAllViews()
        letterButtons.clear()
        gestureButtons.clear()
        shiftButton = null
        when (state.mode) {
            KeyboardMode.LETTERS -> {
                if (options.enabled("emoji_row")) addEmojiRow()
                if (preferences.numberRow) addRow("1234567890".map { Key.Text(it.toString()) })
                val urdu = options.number("language") == 1
                addRow((if(urdu) "قوعرتےءیپ" else "qwertyuiop").map { Key.Text(it.toString()) })
                addRow((if(urdu) "اسدفگھجکل" else "asdfghjkl").map { Key.Text(it.toString()) }, inset = 12)
                addRow(listOf(Key.Shift) + (if(urdu) "زشچطبنم" else "zxcvbnm").map { Key.Text(it.toString()) } + Key.Delete)
                if(urdu) addRow(listOf("ٹ","ڈ","ڑ","ں","ہ","ح","خ","غ","ض","ذ").map { Key.Text(it) })
            }
            KeyboardMode.NUMBERS -> {
                if(options.enabled("symbols_emoji")) addEmojiRow()
                if(options.enabled("symbols_number_row")) addRow("1234567890".map { Key.Text(it.toString()) })
                addRow(listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map { Key.Text(it) })
                addRow(listOf(Key.Mode(KeyboardMode.SYMBOLS)) + listOf("*", "\"", "'", ":", ";", "!", "?").map { Key.Text(it) } + Key.Delete)
            }
            KeyboardMode.SYMBOLS -> {
                if(options.enabled("symbols_emoji")) addEmojiRow()
                addRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}").map { Key.Text(it) })
                addRow(listOf("\\", "_", "=", "<", ">", "[", "]", "€", "£", "¥").map { Key.Text(it) })
                addRow(listOf(Key.Mode(KeyboardMode.NUMBERS)) + listOf("^", "°", "©", "®", "™", "…", "§").map { Key.Text(it) } + Key.Delete)
            }
            KeyboardMode.NUMERIC -> {
                addRow("123".map { Key.Text(it.toString()) })
                addRow("456".map { Key.Text(it.toString()) })
                addRow("789".map { Key.Text(it.toString()) })
                addRow(listOf(Key.Text("+"), Key.Text("0"), Key.Delete))
            }
        }
        addRow(listOf(
            Key.Mode(if (state.mode == KeyboardMode.LETTERS) KeyboardMode.NUMBERS else KeyboardMode.LETTERS),
            *(if(options.enabled("emoji_key")) arrayOf(Key.Emoji) else emptyArray()),
            *(if(options.enabled("language_key")) arrayOf(Key.Language) else emptyArray()),
            *(if(options.enabled("comma_key")) arrayOf(Key.Text(if(options.number("language")==1) "،" else ",")) else emptyArray()), Key.Space,
            *(if(options.enabled("full_stop_key")) arrayOf(Key.Text(if(options.number("language")==1) "۔" else ".")) else emptyArray()), Key.Enter,
        ))
        updateShift()
        if(emojiPanel!=null && !emojiSearching) rows.visibility=View.GONE
    }

    fun updateShift() {
        for ((button, letter) in letterButtons) {
            val label=state.display(letter)
            if(button.text.toString()!=label) button.text = label
            (button as? KeyButton)?.symbolHint = if(options.enabled("symbols_hold")) symbols[letter.firstOrNull()]?.toString().orEmpty() else ""
        }
        shiftButton?.apply {
            setText(R.string.key_shift_short)
            contentDescription = context.getString(if (state.shift == ShiftMode.LOCKED) R.string.key_caps else R.string.key_shift)
            setTextColor(android.graphics.Color.WHITE)
            // Keep Shift on the same themed surface; selected state is communicated by
            // pressed/selected feedback and accessibility state rather than a color jump.
            background = shiftBackground(false)
            isSelected = state.shift != ShiftMode.OFF
        }
    }


    private fun keyHeightBase(landscape:Boolean):Int {
        val base = if (landscape) 42 else 54
        return context.dp((base * options.number("height_scale") / 100f).toInt().coerceIn(if(landscape) 40 else 50, if(landscape) 64 else 82))
    }
    private fun shiftBackground(active:Boolean) = palette.keyBackground(KeySurface.FUNCTION, context.dp(11).toFloat())

    private fun addRow(keys: List<Key>, inset: Int = 0) {
        val row = LinearLayout(context).apply {
            isBaselineAligned=false
            setPadding(context.dp(inset), 0, context.dp(inset), 0)
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = keyHeightBase(landscape)
        for (key in keys) {
            val button = (if (key == Key.Delete) RepeatingDeleteButton(context) else if (key == Key.Enter) EnterKeyButton(context) else if(key==Key.Space || key==Key.Language || key is Key.Text) GestureKeyButton(context) else KeyButton(context)).apply {
                isAllCaps = false
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = preferences.vibration
                textSize = if (key == Key.Enter) 28f else if (key is Key.Text && key.text in LocalSuggestions.popularEmoji + options.emoji()) options.number("emoji_size").toFloat().coerceAtMost(if(landscape) 30f else 40f) else if (key is Key.Text) (if (landscape) options.number("font_size").toFloat().coerceAtMost(22f) else options.number("font_size").toFloat()) else 14f
                if (key == Key.Enter) typeface = android.graphics.Typeface.DEFAULT_BOLD
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(if (key == Key.Enter) palette.onAccent else if (key == Key.Shift) android.graphics.Color.WHITE else palette.text)
                background = palette.keyBackground(when (key) {
                    Key.Enter -> KeySurface.ENTER
                    Key.Space -> KeySurface.SPACE
                    is Key.Text -> KeySurface.NORMAL
                    else -> KeySurface.FUNCTION
                }, context.dp(11).toFloat())
                if (key == Key.Shift) background = shiftBackground(false)
                stateListAnimator = null
                // Stable identifiers are also used by regression tests.
                tag = when (key) {
                    is Key.Text -> "text:${key.text}"
                    is Key.Mode -> "mode:${key.mode}"
                    else -> key.javaClass.simpleName
                }
                text = when (key) {
                    is Key.Text -> key.text
                    Key.Shift -> context.getString(R.string.key_shift_short)
                    Key.Delete -> "⌫"
                    Key.Enter -> "↵"
                    Key.Space -> if(options.number("language")==1) "اردو" else if(options.number("language")==2) "Roman Urdu" else context.getString(R.string.key_space)
                    Key.Emoji -> "☺"
                    Key.Language -> "🌐"
                    is Key.Cursor, is Key.Glide, is Key.DeleteSwipe -> ""
                    is Key.Mode -> context.getString(when (key.mode) {
                        KeyboardMode.LETTERS -> R.string.key_letters
                        KeyboardMode.NUMBERS -> if (state.mode == KeyboardMode.LETTERS) R.string.key_numbers else R.string.key_digits
                        KeyboardMode.SYMBOLS -> R.string.key_symbols
                        KeyboardMode.NUMERIC -> R.string.key_digits
                    })
                }
                (this as? KeyButton)?.apply { hintColor=palette.muted; fitLabel(if(key == Key.Enter) 28f else if(key is Key.Text) (if(key.text in LocalSuggestions.popularEmoji + options.emoji()) options.number("emoji_size").toFloat() else options.number("font_size").toFloat()) else 14f) }
                contentDescription = when (key) {
                    Key.Delete -> context.getString(R.string.key_delete)
                    Key.Shift -> context.getString(R.string.key_shift)
                    Key.Language -> context.getString(R.string.keyboard_switch)
                    Key.Enter -> context.getString(enterLabel)
                    else -> text
                }
                setOnClickListener {
                    feedback.perform(this, when (key) {
                        Key.Space -> FeedbackKey.SPACE
                        Key.Delete -> FeedbackKey.DELETE
                        Key.Enter -> FeedbackKey.ENTER
                        else -> FeedbackKey.STANDARD
                    })
                    onKey(key)
                }
                if (key == Key.Shift) {
                    shiftButton = this
                    setOnLongClickListener { onCaps(); true }
                }
                if (key is Key.Text && state.mode == KeyboardMode.LETTERS) letterButtons.add(this to key.text)
                if(this is GestureKeyButton) configureGesture(this,key)
                if (this is RepeatingDeleteButton) {
                    holdDelayMillis = preferences.holdDelayMillis
                    repeatMillis = preferences.repeatMillis
                    if(options.enabled("delete_swipe")) {
                        onSwipe={ onKey(Key.DeleteSwipe(it)) }
                        onSwipeEnd={ onKey(Key.DeleteSwipe(-1)) }
                        onSwipeCancel={ onKey(Key.DeleteSwipe(-2)) }
                    }
                    deleteButton = this
                }
            }
            val weight = when (key) {
                Key.Space -> 4f
                Key.Enter, is Key.Mode -> 1.5f
                Key.Shift, Key.Delete -> 1.25f
                else -> 1f
            }
            row.addView(button, LayoutParams(0, height, weight).apply { setMargins(context.dp(2), context.dp(3), context.dp(2), context.dp(3)) })
        }
        rows.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun cancelRepeats() {
        deleteButton?.cancelRepeat()
        gestureButtons.forEach { it.cancelGesture() }
    }

    fun setVoiceAllowed(allowed: Boolean) {
        voiceAllowed = allowed
        for(tag in listOf("AI","Grammar","Rewrite","Tone","Translation")) toolbarButtons[tag]?.apply { isEnabled=allowed; alpha=if(allowed) 1f else 0.4f }
        setVoiceState(voiceState)
    }

    fun setVoiceState(state:VoiceState) {
        val previous=voiceState; voiceState=state; voiceUi.removeCallbacks(timerTick)
        recorder.findViewWithTag<View>("VoiceRetry")?.visibility=View.GONE
        voiceLevel.setListening(state==VoiceState.LISTENING)
        voiceStatus.text=when(state) { VoiceState.IDLE -> "Ready"; VoiceState.LISTENING -> context.getString(R.string.voice_starting); VoiceState.PROCESSING -> context.getString(R.string.voice_processing); VoiceState.PAUSED -> context.getString(R.string.voice_paused) }
        if(state==VoiceState.LISTENING && previous==VoiceState.IDLE) {
            showVoiceMessage(null)
            voiceStarted=android.os.SystemClock.elapsedRealtime(); voiceTimer.text=context.getString(R.string.voice_elapsed,0L,0L)
        }
        if(state!=VoiceState.IDLE) { showVoiceWorkspace(); voiceUi.post(timerTick) }
        else {
            recorder.visibility=View.GONE
            writingPanel?.visibility=View.VISIBLE
            if(writingPanel==null && panel==null) restoreTypingViews()
        }
        recorder.findViewWithTag<View>("VoiceStop")?.apply { visibility=View.VISIBLE; isEnabled=state!=VoiceState.PROCESSING }
        microphone.isEnabled=voiceAllowed && state!=VoiceState.PROCESSING
        cancelVoice.visibility=if(state==VoiceState.IDLE) View.GONE else View.VISIBLE
        requestLayout()
    }
    private fun restoreTypingViews() {
        rows.visibility=View.VISIBLE; header?.visibility=View.VISIBLE; toolbarVisible(true)
        title.visibility=if(options.enabled("floating")) View.VISIBLE else View.GONE
        applyCenterMode()
        setActiveTool("")
    }
    private fun showVoiceWorkspace() {
        recorder.visibility=View.VISIBLE; rows.visibility=View.GONE; candidateRow.visibility=View.GONE
        header?.visibility=View.GONE; toolbarVisible(false); title.visibility=View.GONE
        writingPanel?.visibility=View.GONE; panel?.visibility=View.GONE
    }
    fun voiceReady() { if(voiceState==VoiceState.LISTENING) voiceStatus.setText(R.string.voice_listening) }
    fun dismissVoiceStatus() { recorder.visibility=View.GONE; voiceLevel.setListening(false); voiceUi.removeCallbacks(timerTick); showVoiceMessage(null); if(writingPanel==null) showSettings("") else writingPanel?.visibility=View.VISIBLE }
    fun showVoiceCompleted(text:String="") {
        showVoiceWorkspace(); voiceStatus.setText(R.string.voice_completed); recorder.transcript.text=text
        recorder.findViewWithTag<View>("VoiceStop")?.visibility=View.GONE; recorder.findViewWithTag<View>("VoiceRetry")?.visibility=View.VISIBLE
    }
    fun showVoiceDeleted() {
        showVoiceWorkspace()
        voiceStatus.setText(R.string.voice_ready)
        recorder.transcript.text = ""
        recorder.showTranslation("")
        recorder.findViewWithTag<View>("VoiceStop")?.visibility = View.VISIBLE
        recorder.findViewWithTag<View>("VoiceRetry")?.visibility = View.GONE
    }
    fun showVoiceFailure() { showVoiceWorkspace(); voiceStatus.setText(R.string.voice_error_label); recorder.findViewWithTag<View>("VoiceRetry")?.visibility=View.VISIBLE }
    fun setVoiceLevel(rms:Float) { voiceLevel.setLevel(rms) }

    fun showVoiceMessage(message: Int?) {
        voiceMessage.maxLines = 3
        voiceMessage.ellipsize = android.text.TextUtils.TruncateAt.END
        voiceMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) voiceMessage.setText(message)
    }

    fun showTextMessage(text:String) { voiceMessage.text=text; voiceMessage.maxLines=3; voiceMessage.visibility=View.VISIBLE }

    fun showVoicePreview(text:String) { recorder.transcript.text=text; recorder.transcript.visibility=View.VISIBLE }
    fun showVoiceTranslation(text:String) { recorder.showTranslation(text) }
    fun showVoiceLanguages(selected:String=dev.raabta.keyboard.voice.VoiceSettings.language(context)) { recorder.language(selected,onTool) }
    fun hideVoiceLanguages() { languageBar?.let { content.removeView(it) }; languageBar=null }

    fun showWritingTool(mode: String, source: String, target: String, style: String) {
        closeWritingTool(); showSettings(""); hideVoiceLanguages()
        candidateRow.visibility=View.GONE
        val box=WritingToolPanel(context,mode,source,target,style,onTool)
        writingPanel=box; writingKeyboardVisible=false; rows.visibility=View.GONE; header?.visibility=View.GONE; toolbarVisible(false); title.visibility=View.GONE
        content.addView(box,content.indexOfChild(rows),LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
    }
    fun updateWritingTool(draft: String, result: String, status: String, busy: Boolean, variants:List<String> = emptyList()) {
        writingPanel?.update(draft,result,status,busy,variants)
        rows.visibility=if(writingKeyboardVisible) View.VISIBLE else View.GONE
    }
    fun toggleWritingKeyboard() {
        writingKeyboardVisible=!writingKeyboardVisible
        writingPanel?.setEditing(writingKeyboardVisible)
        rows.visibility=if(writingKeyboardVisible) View.VISIBLE else View.GONE; requestLayout()
    }
    fun showCustomInstruction(value:String) {
        writingKeyboardVisible=true; rows.visibility=View.VISIBLE
        writingPanel?.customInstruction(value); requestLayout()
    }
    fun closeWritingTool() {
        writingPanel?.let { content.removeView(it) }; writingPanel=null
        writingKeyboardVisible=true
        if(panel==null && !recorder.isVisible) restoreTypingViews()
        requestLayout()
    }

    private val symbols = "qwertyuiopasdfghjklzxcvbnm".zip("%\\|=[]<>{}@#$-&-+()*\"':;!?").toMap() + mapOf(
        'ا' to 'آ', 'د' to 'ڈ', 'ر' to 'ڑ', 'ت' to 'ٹ', 'ن' to 'ں', 'ز' to 'ژ', 'س' to 'ص',
        'ح' to 'خ', 'ط' to 'ظ', 'ض' to 'ث', 'ی' to 'ئ', 'و' to 'ؤ')
    private fun configureGesture(button:GestureKeyButton,key:Key) {
        gestureButtons.add(button)
        button.tapSlop=android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat() *
            (0.72f + options.number("typing_sensitivity") / 100f * 0.55f)
        if(key==Key.Space && options.enabled("cursor_slide")) button.onMoveCursor={ onKey(Key.Cursor(it)) }
        if(key==Key.Space || key==Key.Language) {
            // Match the system keyboard's quick language-switch affordance while
            // keeping a normal tap a normal space/language-key tap.
            button.stationaryHoldMillis=800L
            button.onStationaryHold=onSwitch
        }
        if(key is Key.Text && state.mode==KeyboardMode.LETTERS) {
            val symbol=symbols[key.text.firstOrNull()]
            if(symbol!=null && options.enabled("symbols_hold")) {
                button.holdDuration=options.number("hold_duration").toLong()
                button.onSymbol={ onKey(Key.Text(symbol.toString())) }
                button.setOnLongClickListener { onKey(Key.Text(symbol.toString())); true }
                button.enableFlick=options.enabled("flick_symbols")
                button.flickDistance=context.dp(options.number("flick_sensitivity")).toFloat()
            }
            button.onPreview={ keyPopup?.dismiss(); keyPopup=null }
            if(options.enabled("glide")) {
                button.onCancel={ glidePath="" }
                button.onGlideMove={ x,y ->
                    val keyAt=letterButtons.firstOrNull { (v,_) ->
                        val location=IntArray(2); v.getLocationOnScreen(location)
                        x>=location[0] && x<location[0]+v.width && y>=location[1] && y<location[1]+v.height
                    }?.second
                    if(keyAt!=null && !glidePath.endsWith(keyAt)) glidePath+=keyAt
                }
                button.onGlideEnd={
                    val value=glidePath; glidePath=""
                    if(value.length>1) { onKey(Key.Glide(value)); true } else false
                }
            }
        }
    }
    fun showCandidates(candidates:List<Candidate>) {
        val ordered=if(options.enabled("suggestion_strip") && panel==null) candidates.take(options.number("suggestion_count")) else emptyList()
        val visible=ordered
        val signature=visible.map { "${it.text}\u0000${it.label}\u0000${it.replace}\u0000${it.emoji}\u0000${it.imageUri}" }
        if (signature == candidateSignature) return
        candidateSignature=signature
        while(candidateButtons.size<options.number("suggestion_count")) {
            val button=Button(context).apply {
                isAllCaps=false; setTextColor(palette.text); minWidth=0; minimumWidth=0
                minHeight=0; minimumHeight=0; setPadding(context.dp(8),0,context.dp(8),0)
                background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(if(palette.style==SurfaceStyle.FLAT) palette.background else android.graphics.Color.TRANSPARENT,context.dp(21).toFloat()),rounded(android.graphics.Color.WHITE,context.dp(21).toFloat()))
                maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; gravity=Gravity.CENTER; typeface=android.graphics.Typeface.DEFAULT
                isSoundEffectsEnabled=false; stateListAnimator=null
            }
            candidateButtons.add(button); candidateRow.addView(button,LayoutParams(0,context.dp(42),1f))
        }
        candidateButtons.forEachIndexed { index,button ->
            val candidate=visible.getOrNull(index)
            button.visibility=if(candidate==null) View.GONE else View.VISIBLE
            if(candidate!=null) {
                button.text=candidate.label; button.tag="suggestion:${candidate.text}"
                button.textSize=if(candidate.emoji) options.number("emoji_size").toFloat().coerceAtMost(32f) else 15f
                button.setOnClickListener { feedback.perform(button); onCandidate(candidate) }
            } else { button.text=""; button.tag=null; button.setOnClickListener(null) }
        }
    }
    private fun addEmojiRow() {
        val emoji=(options.emoji()+LocalSuggestions.popularEmoji).distinct().take(if(options.number("emoji_size")>30) 6 else 8)
        addRow(emoji.map { Key.Text(it) })
    }
    fun setActiveTool(name:String) {
        toolbarButtons.forEach { (tag,button) ->
            button.isSelected=tag==name
            button.background=palette.toolbarBackground(toolbarButtons.keys.indexOf(tag),tag==name,context.dp(11).toFloat())
            button.setTextColor(if(tag==name) palette.onAccent else palette.icon(toolbarButtons.keys.indexOf(tag)))
        }
        toolbarButtons[name]?.let { button ->
            fun reveal() {
                val scroll=toolbarScroller ?: return
                val x=when { button.left<scroll.scrollX -> button.left; button.right>scroll.scrollX+scroll.width -> button.right-scroll.width; else -> scroll.scrollX }
                scroll.scrollTo(x.coerceAtLeast(0),0)
            }
            if(button.width>0) reveal() else button.addOnLayoutChangeListener(object:View.OnLayoutChangeListener { override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int) { if(r>l) { button.removeOnLayoutChangeListener(this); reveal() } } })
        }
    }
    fun showSettings(page:String="Raabta settings") {
        cancelRepeats()
        emojiPanel?.let { content.removeView(it) }; emojiPanel=null; emojiSearching=false; emojiQuery=""
        if(rows.parent!==content) { (rows.parent as? android.view.ViewGroup)?.removeView(rows); content.addView(rows) }
        panel?.let { content.removeView(it) }; panel=null
        settingsPage=page
        for(i in 0 until content.childCount) content.getChildAt(i).visibility=View.GONE
        if(page.isEmpty()) {
            header?.visibility=View.VISIBLE; toolbarVisible(true); title.visibility=if(options.enabled("floating")) View.VISIBLE else View.GONE; rows.visibility=View.VISIBLE
            applyCenterMode()
            render(enterLabel); return
        }
        val settings:View=if(page=="Tools") dev.raabta.keyboard.ui.ToolsPanel(context,{ showSettings("") },onTool,{ refreshToolbar() }) else KeyboardSettingsPanel(context,page,::showSettings,{ onSettingsChanged(page) },onTool,internal=true)
        panel=settings
        background = palette.backgroundDrawable()
        content.translationX=0f; content.translationY=0f
        content.addView(settings,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 220 else 360)))
        toolbarVisible(false); header?.visibility=View.GONE; setActiveTool("Tools")
    }
    private fun styleTool(button:Button) {
        button.setTextColor(palette.text); button.isAllCaps=false; button.isSoundEffectsEnabled=false; button.stateListAnimator=null
        button.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),palette.shape(radius=context.dp(10).toFloat()),null)
    }
    fun showEmojiPicker() {
        showSettings(""); candidateRow.visibility=View.GONE; rows.visibility=View.GONE; toolbarVisible(false); header?.visibility=View.GONE; title.visibility=View.GONE
        emojiPanel=dev.raabta.keyboard.ui.EmojiPanel(context,onCandidate,{ searching ->
            emojiSearching=searching; rows.visibility=if(searching) View.VISIBLE else View.GONE
        },{ showSettings("") },{ onTool("media") }).also { content.addView(it,content.indexOfChild(header)+1) }
    }
    fun handleEmojiKey(key:Key):Boolean {
        if(emojiPanel==null || !emojiSearching) return false
        emojiQuery=when(key) {
            is Key.Text -> (emojiQuery+state.display(key.text)).take(40)
            Key.Space -> (emojiQuery+" ").take(40)
            Key.Delete -> if(emojiQuery.isEmpty()) "" else emojiQuery.substring(0,emojiQuery.offsetByCodePoints(emojiQuery.length,-1))
            Key.Enter -> { emojiSearching=false; emojiPanel?.setSearchMode(false); rows.visibility=View.GONE; requestLayout(); return true }
            Key.Shift, is Key.Mode -> return false
            else -> return true
        }
        if(key is Key.Text) { state.afterCharacter(); updateShift() }
        emojiPanel?.setQuery(emojiQuery); return true
    }
    private fun utilityWorkspace() {
        (rows.parent as? android.view.ViewGroup)?.removeView(rows)
        val scroll=ScrollView(context).apply { addView(rows) }; panel=scroll; content.addView(scroll)
        candidateRow.visibility=View.GONE; toolbarVisible(false); header?.visibility=View.GONE; title.visibility=View.GONE
        settingsPage="Tools"; requestLayout()
    }
    fun showResizeMode() {
        showSettings("")
        resizeOriginalScale=options.number("height_scale")
        rows.visibility=View.VISIBLE; candidateRow.visibility=View.GONE; toolbarVisible(false); header?.visibility=View.GONE; title.visibility=View.GONE
        val controls=LinearLayout(context).apply { orientation=VERTICAL; tag="ResizePanel"; setPadding(context.dp(8),context.dp(6),context.dp(8),context.dp(6)); background=palette.backgroundDrawable() }
        val titleRow=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(context).apply { setText(R.string.resize_keyboard); textSize=15f; setTextColor(palette.text) },LayoutParams(0,context.dp(34),1f))
        fun ctl(label:String, tagValue:String, action:()->Unit)=Button(context).apply { text=label; tag=tagValue; textSize=12f; isAllCaps=false; setTextColor(palette.text); background=palette.pressed(radius=context.dp(8).toFloat()); setOnClickListener { action() } }
        titleRow.addView(ctl("Reset","ResizeReset") { options.setNumber("height_scale",100); render(enterLabel); showResizeMode() },LayoutParams(context.dp(70),context.dp(34)))
        titleRow.addView(ctl("Cancel","ResizeCancel") { options.setNumber("height_scale",resizeOriginalScale); showSettings("") },LayoutParams(context.dp(78),context.dp(34)))
        titleRow.addView(ctl("Done","ResizeDone") { showSettings("") },LayoutParams(context.dp(70),context.dp(34)))
        controls.addView(titleRow)
        controls.addView(SeekBar(context).apply {
            tag="ResizeSlider"
            max=KeyboardOptions.HEIGHT_SCALE_MAX-KeyboardOptions.HEIGHT_SCALE_MIN
            progress=(options.number("height_scale")-KeyboardOptions.HEIGHT_SCALE_MIN).coerceIn(0,max)
            progressTintList=ColorStateList.valueOf(palette.accent); thumbTintList=ColorStateList.valueOf(palette.accent)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?, progress:Int, fromUser:Boolean) { options.setNumber("height_scale",KeyboardOptions.HEIGHT_SCALE_MIN+progress); render(enterLabel); requestLayout() }
            override fun onStartTrackingTouch(bar:SeekBar?)=Unit
            override fun onStopTrackingTouch(bar:SeekBar?)=Unit
        }) },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(42)))
        panel?.let { content.removeView(it) }
        panel=controls
        content.addView(controls, content.indexOfChild(rows).coerceAtLeast(0), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(88)))
        settingsPage="Resize"
        requestLayout()
    }

    fun showEditingTools() {
        showSettings(""); rows.removeAllViews(); utilityWorkspace()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { showSettings("") } })
        listOf(listOf("left","right","home","end"),listOf("selectAll","copy","cut","paste"),listOf("undo","redo")).forEach { tools ->
            val row=LinearLayout(context)
            tools.forEach { tool -> row.addView(Button(context).apply { styleTool(this);
                text=tool.replaceFirstChar(Char::uppercase); tag="edit:$tool"; isAllCaps=false; minWidth=0; minimumWidth=0
                setOnClickListener { onTool("edit:$tool") }
            },LayoutParams(0,context.dp(52),1f)) }
            rows.addView(row)
        }
    }
    fun showMediaPicker() {
        showSettings(""); rows.removeAllViews(); utilityWorkspace()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { showSettings("") } })
        listOf("sticker:smile" to "Smile sticker", "sticker:heart" to "Heart sticker", "anim:smile" to "Smile GIF", "anim:heart" to "Heart GIF").forEach { (item,label) ->
            rows.addView(Button(context).apply { styleTool(this); text=label; setOnClickListener { onTool("media:$item") } })
        }
    }
    fun showConnections(settings:dev.raabta.keyboard.tools.AiConnection) {
        showSettings(""); rows.removeAllViews(); utilityWorkspace()
        rows.addView(TextView(context).apply {
            text=context.getString(R.string.connection_summary,if(settings.active() && settings.configured()) settings.provider().label else "None",settings.provider().label,settings.model().ifEmpty { "None" },settings.status(),settings.maskedKey())
            setTextColor(palette.text); textSize=15f; setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(8)); tag="ConnectionSummary"
        })
        fun item(label:String,tagValue:String,action:()->Unit) { rows.addView(Button(context).apply { styleTool(this); text=label; tag=tagValue; setOnClickListener { action() } }) }
        item("Test Connection","ConnectionTest") { onTool("connection:test") }
        item(if(settings.active()) "Deactivate" else "Activate","ConnectionToggle") { onTool("connection:toggle") }
        item("Change Provider / Use My Own API Key","ConnectionChange") { onTool("ai:connection") }
        item("Delete connection","ConnectionDelete") { onTool("ai:clear"); showConnections(dev.raabta.keyboard.tools.AiConnection(context)) }
        item(context.getString(R.string.back_to_keyboard),"ConnectionsBack") { showSettings("") }
    }
    fun showClipboard(history: ClipboardHistory?) {
        showSettings(""); candidateRow.visibility=View.GONE; rows.removeAllViews(); utilityWorkspace()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { showSettings("") } })
        val toggle=androidx.appcompat.widget.SwitchCompat(context).apply { tag="ClipboardEnabled"; setText(R.string.clipboard_enabled); textOn=""; textOff=""; showText=false; setTextColor(palette.text); isChecked=options.enabled("clipboard_enabled"); setOnCheckedChangeListener { _,value -> options.set("clipboard_enabled",value); onTool("clipboard:refresh") } }
        rows.addView(toggle,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(46)))
        val list=LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(8),0,context.dp(8),context.dp(8)) }
        if(history==null || !options.enabled("clipboard_enabled")) {
            list.addView(TextView(context).apply { setText(R.string.clipboard_empty); setTextColor(palette.text) })
        } else {
            fun section(name:String,section:ClipSection,columns:Int,other:String?=null) {
                val heading=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
                heading.addView(TextView(context).apply { text=name; textSize=15f; setTextColor(palette.text) },LayoutParams(0,context.dp(40),1f))
                if(other!=null) heading.addView(Button(context).apply {
                    styleTool(this); text=other; textSize=12f; tag="ClipboardSectionSwitch"
                    setOnClickListener { clipboardPinnedOnly=!clipboardPinnedOnly; showClipboard(history) }
                },LayoutParams(context.dp(68),context.dp(38)))
                heading.addView(Button(context).apply {
                    styleTool(this); setText(R.string.clipboard_clear_all); textSize=12f; tag="ClipboardClear:$name"
                    setOnClickListener { onTool("clipboard:clear:${section.name}") }
                },LayoutParams(context.dp(82),context.dp(38)))
                list.addView(heading)
                val clips=history.items(section)
                if(clips.isEmpty()) list.addView(TextView(context).apply { text=context.getString(R.string.clipboard_no_clips,name); textSize=12f; setTextColor(palette.muted) })
                clips.chunked(columns).forEach { items ->
                    val row=LinearLayout(context)
                    items.forEach { clip ->
                        row.addView(Button(context).apply {
                            styleTool(this); text=clip.text.take(90); maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END
                            textSize=13f; gravity=Gravity.START or Gravity.CENTER_VERTICAL; tag="Clip:${clip.id}"
                            setOnClickListener { onTool("clipboard:paste:${clip.id}") }
                            setOnLongClickListener { view ->
                                PopupMenu(context,view).apply {
                                    menu.add("Copy").setOnMenuItemClickListener { onTool("clipboard:copy:${clip.id}"); true }
                                    menu.add(if(clip.pinned) "Unpin" else "Pin").setOnMenuItemClickListener { onTool("clipboard:pin:${clip.id}"); true }
                                    menu.add("Delete").setOnMenuItemClickListener { onTool("clipboard:delete:${clip.id}"); true }
                                    show()
                                }; true
                            }
                        },LayoutParams(0,context.dp(70),1f).apply { setMargins(context.dp(2),context.dp(2),context.dp(2),context.dp(2)) })
                    }
                    repeat(columns-items.size) { row.addView(View(context),LayoutParams(0,context.dp(70),1f)) }
                    list.addView(row)
                }
            }
            section("Recent",ClipSection.RECENT,2)
            if(clipboardPinnedOnly) section("Pinned",ClipSection.PINNED,2,"Old")
            else section("Old copied",ClipSection.OLD,2,"Pinned")
        }
        list.tag="ClipboardList"
        rows.addView(list,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
    }
    fun backFromPanel():Boolean {
        if(settingsPage.isEmpty()) return false
        showSettings(""); return true
    }

    fun matchesCurrentPreferences(): Boolean = preferences == KeyboardPreferences.load(context) && palette == Palette.from(context) && optionFingerprint == options.fingerprint()

    fun setHistoryAvailability(undo:Boolean,redo:Boolean) {
        (panel as? dev.raabta.keyboard.ui.ToolsPanel)?.historyAvailability(undo,redo)
        for((tag,value) in listOf("Undo" to undo,"Redo" to redo)) toolbarButtons[tag]?.apply {
            isEnabled=value; alpha=if(value) 1f else 0.38f
        }
        for((tag,value) in listOf("edit:undo" to undo,"edit:redo" to redo)) findViewWithTag<View>(tag)?.apply { isEnabled=value; alpha=if(value) 1f else 0.38f }
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        content.layoutParams.width=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceIn(0,context.dp(if(options.number("one_handed")==0 && !floating) 840 else 320))
        val defaultKey=keyHeightBase(landscape)
        var count=4
        if(preferences.numberRow || (state.mode!=KeyboardMode.LETTERS && options.enabled("symbols_number_row"))) count++
        if((state.mode==KeyboardMode.LETTERS && options.enabled("emoji_row")) || (state.mode!=KeyboardMode.LETTERS && options.enabled("symbols_emoji"))) count++
        val toolbarHeight=context.dp(44)
        // Suggestions occupy the same fixed-height center strip as the toolbar;
        // they must not add a second row or shrink the key area.
        val normal=count*(defaultKey+context.dp(6))+toolbarHeight
        val system=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else (MeasureSpec.getSize(heightMeasureSpec)-paddingTop-paddingBottom).coerceAtLeast(0)
        val area=minOf(normal,system)
        val workspace=panel!=null || writingPanel!=null || recorder.isVisible || emojiPanel!=null
        content.layoutParams.height=if(workspace) area else LayoutParams.WRAP_CONTENT
        val message=if(voiceMessage.isVisible) context.dp(if(landscape) 48 else 64) else 0
        voiceMessage.layoutParams.height=if(message>0) message else LayoutParams.WRAP_CONTENT
        panel?.layoutParams?.height=if(settingsPage=="Resize") context.dp(88) else (area-message).coerceAtLeast(0)
        recorder.layoutParams.height=(area-message).coerceAtLeast(0)
        val writingEditorHeight=context.dp(if(resources.configuration.fontScale>1.2f) 102 else 94)
        writingPanel?.layoutParams?.height=if(writingKeyboardVisible) writingEditorHeight else (area-message).coerceAtLeast(0)
        emojiPanel?.layoutParams?.height=if(emojiSearching) context.dp(88) else area
        val fixed=if(workspace) (if(settingsPage=="Resize") context.dp(88) else if(writingPanel!=null && writingKeyboardVisible) writingEditorHeight else if(emojiSearching) context.dp(88) else 0)+message else toolbarHeight+(if(title.isVisible) context.dp(28) else 0)+message
        val keyHeight=minOf(defaultKey,((area-fixed)/count-context.dp(6)).coerceAtLeast(context.dp(16)))
        for(i in 0 until rows.childCount) (rows.getChildAt(i) as? LinearLayout)?.let { row ->
            for(j in 0 until row.childCount) if(row.getChildAt(j) is Button) row.getChildAt(j).layoutParams.height=keyHeight
        }
        gravity=when(options.number("one_handed")) { 1 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or if(floating) Gravity.BOTTOM else Gravity.TOP
        title.dragEnabled=floating
        background = if(floating) rounded(android.graphics.Color.TRANSPARENT,0f) else palette.backgroundDrawable()
        content.background = if(floating) rounded(android.graphics.Color.TRANSPARENT,0f) else palette.backgroundDrawable()
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        if(floating) {
            val available=MeasureSpec.getSize(heightMeasureSpec)
            val desired=(resources.displayMetrics.heightPixels*0.65f).toInt().coerceAtLeast(measuredHeight)
            setMeasuredDimension(measuredWidth,if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED) desired else desired.coerceAtMost(available))
        }
    }

    override fun onDetachedFromWindow() {
        cancelRepeats(); voiceUi.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
