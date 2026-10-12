package dev.raabta.keyboard.ime

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
import android.widget.TextView
import android.widget.ScrollView
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.ui.KeyboardSettingsPanel
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.Palette
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
    private val candidateRow = LinearLayout(context)
    private val candidateButtons = mutableListOf<Button>()
    private var panel: View? = null
    var settingsPage = ""
        private set
    private val gestureButtons = mutableListOf<GestureKeyButton>()
    private var glidePath = ""
    private var header: LinearLayout? = null
    private var toolbarScroller: android.widget.HorizontalScrollView? = null
    private val toolbarButtons=mutableMapOf<String,Button>()
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

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(palette.background)
        setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(5))
        content.orientation = VERTICAL
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setOnApplyWindowInsetsListener { _, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else { @Suppress("DEPRECATION") insets.systemWindowInsetBottom }
            setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(5) + bottom)
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
        fun addTool(label:String,icon:String,tagValue:String,action:()->Unit) {
            val button=headerButton(icon,R.string.keyboard_title,action).apply {
                tag=tagValue; contentDescription=label; caption=label
                layoutParams=LayoutParams(context.dp(48),context.dp(52))
            }
            toolbarButtons[tagValue]=button; header.addView(button)
        }
        addTool("AI","✦","AI") { onTool("ai") }
        addTool("Fix Grammar","✎","Grammar") { onTool("grammar") }
        addTool("Rewrite","↻","Rewrite") { onTool("rewrite") }
        addTool("Tone","☷","Tone") { onTool("tone") }
        addTool("Translate","⇄","Translation") { onTool("translate") }
        microphone.caption="Voice"; microphone.layoutParams=LayoutParams(context.dp(48),context.dp(52)); header.addView(microphone); toolbarButtons["Voice"]=microphone
        addTool("More","▦","Tools") { onTool("tools") }
        toolbarScroller=android.widget.HorizontalScrollView(context).apply { tag="KeyboardToolbar"; isHorizontalScrollBarEnabled=false; addView(header) }
        content.addView(toolbarScroller,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(52)))
        candidateRow.tag="SuggestionStrip"
        candidateRow.isBaselineAligned=false
        candidateRow.gravity=Gravity.CENTER
        candidateRow.visibility=if(options.enabled("suggestion_strip")) View.VISIBLE else View.GONE
        content.addView(candidateRow,0,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(42)))
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
        background = RippleDrawable(ColorStateList.valueOf(palette.ripple), rounded(palette.background, context.dp(8).toFloat()), null)
        layoutParams = LayoutParams(context.dp(40), context.dp(48))
        setOnClickListener { feedback.perform(this); action() }
    }

    fun render(enterLabel: Int) {
        cancelRepeats()
        this.enterLabel = enterLabel
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
            text = if (state.shift == ShiftMode.LOCKED) "⇪" else "⇧"
            contentDescription = context.getString(if (state.shift == ShiftMode.LOCKED) R.string.key_caps else R.string.key_shift)
            setTextColor(if (state.shift == ShiftMode.OFF) palette.text else palette.accent)
            isSelected = state.shift != ShiftMode.OFF
        }
    }

    private fun addRow(keys: List<Key>, inset: Int = 0) {
        val row = LinearLayout(context).apply {
            isBaselineAligned=false
            setPadding(context.dp(inset), 0, context.dp(inset), 0)
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = context.dp(when (preferences.height) {
            KeyboardHeight.COMPACT -> 42
            KeyboardHeight.NORMAL -> if (landscape) 42 else 54
            KeyboardHeight.TALL -> if (landscape) 48 else 64
        })
        for (key in keys) {
            val button = (if (key == Key.Delete) RepeatingDeleteButton(context) else if(key==Key.Space || key is Key.Text) GestureKeyButton(context) else KeyButton(context)).apply {
                isAllCaps = false
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = preferences.vibration
                textSize = if (key is Key.Text && key.text in LocalSuggestions.popularEmoji + options.emoji()) options.number("emoji_size").toFloat().coerceAtMost(if(landscape) 30f else 40f) else if (key is Key.Text) (if (landscape) options.number("font_size").toFloat().coerceAtMost(22f) else options.number("font_size").toFloat()) else 14f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(if (key == Key.Enter) palette.onAccent else palette.text)
                background = RippleDrawable(
                    ColorStateList.valueOf(palette.ripple),
                    palette.shape(if (key == Key.Enter) palette.accent else palette.key, context.dp(9).toFloat()),
                    null,
                )
                stateListAnimator = null
                // Stable identifiers are also used by regression tests.
                tag = when (key) {
                    is Key.Text -> "text:${key.text}"
                    is Key.Mode -> "mode:${key.mode}"
                    else -> key.javaClass.simpleName
                }
                text = when (key) {
                    is Key.Text -> key.text
                    Key.Shift -> "⇧"
                    Key.Delete -> "⌫"
                    Key.Enter -> context.getString(enterLabel)
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
                (this as? KeyButton)?.apply { hintColor=palette.muted; fitLabel(if(key is Key.Text) (if(key.text in LocalSuggestions.popularEmoji + options.emoji()) options.number("emoji_size").toFloat() else options.number("font_size").toFloat()) else 14f) }
                contentDescription = when (key) {
                    Key.Delete -> context.getString(R.string.key_delete)
                    Key.Shift -> context.getString(R.string.key_shift)
                    Key.Language -> context.getString(R.string.keyboard_switch)
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
        rows.visibility=View.VISIBLE; header?.visibility=View.VISIBLE; toolbarScroller?.visibility=View.VISIBLE
        title.visibility=if(options.enabled("floating")) View.VISIBLE else View.GONE
        candidateRow.visibility=if(options.enabled("suggestion_strip")) View.VISIBLE else View.GONE
        setActiveTool("")
    }
    private fun showVoiceWorkspace() {
        recorder.visibility=View.VISIBLE; rows.visibility=View.GONE; candidateRow.visibility=View.GONE
        header?.visibility=View.GONE; toolbarScroller?.visibility=View.GONE; title.visibility=View.GONE
        writingPanel?.visibility=View.GONE; panel?.visibility=View.GONE
    }
    fun voiceReady() { if(voiceState==VoiceState.LISTENING) voiceStatus.setText(R.string.voice_listening) }
    fun dismissVoiceStatus() { recorder.visibility=View.GONE; voiceLevel.setListening(false); voiceUi.removeCallbacks(timerTick); showVoiceMessage(null); if(writingPanel==null) showSettings("") else writingPanel?.visibility=View.VISIBLE }
    fun showVoiceCompleted(text:String="") {
        showVoiceWorkspace(); voiceStatus.setText(R.string.voice_completed); recorder.transcript.text=text
        recorder.findViewWithTag<View>("VoiceStop")?.visibility=View.GONE; recorder.findViewWithTag<View>("VoiceRetry")?.visibility=View.VISIBLE
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
    fun showVoiceLanguages(selected:String=dev.raabta.keyboard.voice.VoiceSettings.language(context)) { recorder.language(selected,onTool) }
    fun hideVoiceLanguages() { languageBar?.let { content.removeView(it) }; languageBar=null }

    fun showWritingTool(mode: String, source: String, target: String, style: String) {
        closeWritingTool(); showSettings(""); hideVoiceLanguages()
        candidateRow.visibility=View.GONE
        val box=WritingToolPanel(context,mode,source,target,style,onTool)
        writingPanel=box; writingKeyboardVisible=false; rows.visibility=View.GONE; header?.visibility=View.GONE; toolbarScroller?.visibility=View.GONE; title.visibility=View.GONE
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
        button.tapSlop=android.view.ViewConfiguration.get(context).scaledTouchSlop*(0.75f+options.number("typing_sensitivity")/100f*1.75f)
        if(key==Key.Space && options.enabled("cursor_slide")) button.onMoveCursor={ onKey(Key.Cursor(it)) }
        if(key is Key.Text && state.mode==KeyboardMode.LETTERS) {
            val symbol=symbols[key.text.firstOrNull()]
            if(symbol!=null && options.enabled("symbols_hold")) {
                button.holdDuration=options.number("hold_duration").toLong()
                button.onSymbol={ onKey(Key.Text(symbol.toString())) }
                button.setOnLongClickListener { onKey(Key.Text(symbol.toString())); true }
                button.enableFlick=options.enabled("flick_symbols")
                button.flickDistance=context.dp(options.number("flick_sensitivity")).toFloat()
            }
            if(options.enabled("popup")) button.onPreview={ shown ->
                keyPopup?.dismiss(); keyPopup=null
                if(shown && voiceState==VoiceState.IDLE && button.isAttachedToWindow) {
                    val preview=TextView(context).apply {
                        text=state.display(key.text); textSize=32f; gravity=Gravity.CENTER
                        setTextColor(palette.text); background=palette.shape(radius=context.dp(10).toFloat())
                    }
                    keyPopup=android.widget.PopupWindow(preview,context.dp(56),context.dp(72),false).apply {
                        isTouchable=false
                        try { showAsDropDown(button,(button.width-context.dp(56))/2,-button.height-context.dp(72)) }
                        catch (_:RuntimeException) { dismiss() }
                    }
                }
            }
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
        val visible=if(ordered.size>=3) listOf(ordered[1],ordered[0])+ordered.drop(2) else ordered
        while(candidateButtons.size<options.number("suggestion_count")) {
            val button=Button(context).apply {
                isAllCaps=false; setTextColor(palette.text); minWidth=0; minimumWidth=0
                minHeight=0; minimumHeight=0; setPadding(context.dp(8),0,context.dp(8),0)
                background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(palette.background,context.dp(6).toFloat()),null)
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
            button.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(if(tag==name) palette.accent else palette.background,context.dp(9).toFloat()),null)
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
            header?.visibility=View.VISIBLE; toolbarScroller?.visibility=View.VISIBLE; title.visibility=if(options.enabled("floating")) View.VISIBLE else View.GONE; rows.visibility=View.VISIBLE
            content.findViewWithTag<View>("SuggestionStrip")?.visibility=if(options.enabled("suggestion_strip")) View.VISIBLE else View.GONE
            render(enterLabel); return
        }
        val settings:View=if(page=="Tools") dev.raabta.keyboard.ui.ToolsPanel(context,{ showSettings("") },onTool) else KeyboardSettingsPanel(context,page,::showSettings,{ onSettingsChanged(page) },onTool,internal=true)
        panel=settings
        setBackgroundColor(palette.background)
        content.translationX=0f; content.translationY=0f
        content.addView(settings,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 220 else 360)))
        toolbarScroller?.visibility=View.GONE; header?.visibility=View.GONE; setActiveTool("Tools")
    }
    private fun styleTool(button:Button) {
        button.setTextColor(palette.text); button.isAllCaps=false; button.isSoundEffectsEnabled=false; button.stateListAnimator=null
        button.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),palette.shape(radius=context.dp(10).toFloat()),null)
    }
    fun showEmojiPicker() {
        showSettings(""); candidateRow.visibility=View.GONE; rows.visibility=View.GONE; toolbarScroller?.visibility=View.GONE; header?.visibility=View.GONE; title.visibility=View.GONE
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
        candidateRow.visibility=View.GONE; toolbarScroller?.visibility=View.GONE; header?.visibility=View.GONE; title.visibility=View.GONE
        settingsPage="Tools"; requestLayout()
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
    fun showClipboard(candidates:List<Candidate>) {
        showSettings(""); candidateRow.visibility=View.GONE; rows.removeAllViews(); utilityWorkspace()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { showSettings("") } })
        val toggle=androidx.appcompat.widget.SwitchCompat(context).apply { tag="ClipboardEnabled"; setText(R.string.clipboard_enabled); setTextColor(palette.text); isChecked=options.enabled("clipboard_enabled"); setOnCheckedChangeListener { _,value -> options.set("clipboard_enabled",value); onTool("clipboard:refresh") } }
        rows.addView(toggle)
        val list=LinearLayout(context).apply { orientation=VERTICAL }
        if(candidates.isEmpty()) list.addView(TextView(context).apply { setText(R.string.clipboard_empty); setTextColor(palette.text) })
        candidates.forEach { candidate -> list.addView(Button(context).apply { styleTool(this); text=candidate.label; isAllCaps=false; setOnClickListener { onCandidate(candidate) } }) }
        rows.addView(ScrollView(context).apply { addView(list) },LayoutParams(LayoutParams.MATCH_PARENT,minOf(context.dp(210),(resources.displayMetrics.heightPixels*0.3f).toInt())))
    }
    fun backFromPanel():Boolean {
        if(settingsPage.isEmpty()) return false
        showSettings(""); return true
    }

    fun matchesCurrentPreferences(): Boolean = preferences == KeyboardPreferences.load(context) && palette == Palette.from(context) && optionFingerprint == options.fingerprint()

    fun setHistoryAvailability(undo:Boolean,redo:Boolean) {
        (panel as? dev.raabta.keyboard.ui.ToolsPanel)?.historyAvailability(undo,redo)
        for((tag,value) in listOf("edit:undo" to undo,"edit:redo" to redo)) findViewWithTag<View>(tag)?.apply { isEnabled=value; alpha=if(value) 1f else 0.38f }
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        content.layoutParams.width=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceIn(0,context.dp(if(options.number("one_handed")==0 && !floating) 840 else 320))
        val defaultKey=context.dp(when(preferences.height) { KeyboardHeight.COMPACT -> 42; KeyboardHeight.NORMAL -> if(landscape) 42 else 54; KeyboardHeight.TALL -> if(landscape) 48 else 64 })
        var count=4
        if(preferences.numberRow || (state.mode!=KeyboardMode.LETTERS && options.enabled("symbols_number_row"))) count++
        if((state.mode==KeyboardMode.LETTERS && options.enabled("emoji_row")) || (state.mode!=KeyboardMode.LETTERS && options.enabled("symbols_emoji"))) count++
        val normal=count*(defaultKey+context.dp(6))+context.dp(52)+(if(options.enabled("suggestion_strip")) context.dp(42) else 0)
        val system=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else (MeasureSpec.getSize(heightMeasureSpec)-paddingTop-paddingBottom).coerceAtLeast(0)
        val area=minOf(normal,system)
        val workspace=panel!=null || writingPanel!=null || recorder.isVisible || emojiPanel!=null
        content.layoutParams.height=if(workspace) area else LayoutParams.WRAP_CONTENT
        val message=if(voiceMessage.isVisible) context.dp(if(landscape) 48 else 64) else 0
        voiceMessage.layoutParams.height=if(message>0) message else LayoutParams.WRAP_CONTENT
        panel?.layoutParams?.height=(area-message).coerceAtLeast(0)
        recorder.layoutParams.height=(area-message).coerceAtLeast(0)
        writingPanel?.layoutParams?.height=if(writingKeyboardVisible) context.dp(if(resources.configuration.fontScale>1.2f) 102 else 94) else (area-message).coerceAtLeast(0)
        emojiPanel?.layoutParams?.height=if(emojiSearching) context.dp(88) else area
        val fixed=if(workspace) (if((writingPanel!=null && writingKeyboardVisible) || emojiSearching) context.dp(88) else 0)+message else context.dp(52)+(if(candidateRow.isVisible) context.dp(42) else 0)+(if(title.isVisible) context.dp(28) else 0)+message
        val keyHeight=minOf(defaultKey,((area-fixed)/count-context.dp(6)).coerceAtLeast(context.dp(16)))
        for(i in 0 until rows.childCount) (rows.getChildAt(i) as? LinearLayout)?.let { row ->
            for(j in 0 until row.childCount) if(row.getChildAt(j) is Button) row.getChildAt(j).layoutParams.height=keyHeight
        }
        gravity=when(options.number("one_handed")) { 1 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or if(floating) Gravity.BOTTOM else Gravity.TOP
        title.dragEnabled=floating; setBackgroundColor(if(floating) android.graphics.Color.TRANSPARENT else palette.background); content.setBackgroundColor(palette.background)
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
