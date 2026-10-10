package dev.raabta.keyboard.ime

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
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.ui.KeyboardSettingsPanel
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.rounded
import dev.raabta.keyboard.ui.KeyboardPreferences
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
    private val microphone = headerButton("🎙", R.string.voice_microphone, onVoice).apply { tag = "Voice" }
    private val cancelVoice = headerButton("×", R.string.voice_cancel, onCancelVoice).apply {
        tag = "CancelVoice"
        visibility = View.GONE
    }
    private var voiceAllowed = false
    private var voiceState = VoiceState.IDLE

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
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
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
        header.addView(title.apply {
            setText(R.string.keyboard_title)
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(context.dp(10), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }, LayoutParams(0, context.dp(48), 1f))
        header.addView(cancelVoice)
        header.addView(microphone)
        if (options.enabled("language_key")) header.addView(headerButton("🌐", R.string.keyboard_switch) { onKey(Key.Language) }.apply { setOnLongClickListener { onSwitch(); true } })
        header.addView(headerButton("▦", R.string.keyboard_title) { onTool("tools") }.apply { contentDescription="Keyboard tools"; tag="Tools" })
        header.addView(headerButton("⚙", R.string.keyboard_settings, onSettings).apply { tag="Settings" })
        content.addView(header)
        content.addView(HorizontalScrollView(context).apply { tag="SuggestionStrip"; isHorizontalScrollBarEnabled=false; addView(candidateRow); visibility=if(options.enabled("suggestion_strip")) View.VISIBLE else View.GONE },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(48)))
        content.addView(voiceMessage.apply {
            tag = "VoiceMessage"
            textSize = 13f
            setTextColor(palette.text)
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        })
        rows.orientation = VERTICAL
        content.addView(rows)
        setVoiceState(VoiceState.IDLE)
    }

    private fun headerButton(label: String, description: Int, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = preferences.vibration
        contentDescription = context.getString(description)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(palette.text)
        background = RippleDrawable(ColorStateList.valueOf(palette.ripple), rounded(palette.background, context.dp(8).toFloat()), null)
        layoutParams = LayoutParams(context.dp(48), context.dp(48))
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
            *(if(options.enabled("comma_key")) arrayOf(Key.Text(if(options.number("language")==1) "،" else ",")) else emptyArray()), Key.Space,
            *(if(options.enabled("full_stop_key")) arrayOf(Key.Text(if(options.number("language")==1) "۔" else ".")) else emptyArray()), Key.Enter,
        ))
        updateShift()
    }

    fun updateShift() {
        for ((button, letter) in letterButtons) button.text = letterLabel(letter)
        shiftButton?.apply {
            text = if (state.shift == ShiftMode.LOCKED) "⇪" else "⇧"
            contentDescription = context.getString(if (state.shift == ShiftMode.LOCKED) R.string.key_caps else R.string.key_shift)
            setTextColor(if (state.shift == ShiftMode.OFF) palette.text else palette.accent)
            isSelected = state.shift != ShiftMode.OFF
        }
    }

    private fun addRow(keys: List<Key>, inset: Int = 0) {
        val row = LinearLayout(context).apply {
            setPadding(context.dp(inset), 0, context.dp(inset), 0)
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = context.dp(when (preferences.height) {
            KeyboardHeight.COMPACT -> 42
            KeyboardHeight.NORMAL -> if (landscape) 42 else 54
            KeyboardHeight.TALL -> if (landscape) 48 else 64
        })
        for (key in keys) {
            val button = (if (key == Key.Delete) RepeatingDeleteButton(context) else if(key==Key.Space || key is Key.Text) GestureKeyButton(context) else Button(context)).apply {
                isAllCaps = false
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = preferences.vibration
                textSize = if (key is Key.Text && key.text in LocalSuggestions.popularEmoji + options.emoji()) options.number("emoji_size").toFloat().coerceAtMost(if(landscape) 30f else 40f) else if (key is Key.Text) (if (landscape) options.number("font_size").toFloat().coerceAtMost(22f) else options.number("font_size").toFloat()) else 14f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(if (key == Key.Enter) palette.surface else palette.text)
                background = RippleDrawable(
                    ColorStateList.valueOf(palette.ripple),
                    rounded(if (key == Key.Enter) palette.accent else palette.surface, context.dp(9).toFloat()),
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
                    is Key.Cursor, is Key.Glide -> ""
                    is Key.Mode -> context.getString(when (key.mode) {
                        KeyboardMode.LETTERS -> R.string.key_letters
                        KeyboardMode.NUMBERS -> if (state.mode == KeyboardMode.LETTERS) R.string.key_numbers else R.string.key_digits
                        KeyboardMode.SYMBOLS -> R.string.key_symbols
                        KeyboardMode.NUMERIC -> R.string.key_digits
                    })
                }
                contentDescription = when (key) {
                    Key.Delete -> context.getString(R.string.key_delete)
                    Key.Shift -> context.getString(R.string.key_shift)
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
        setVoiceState(voiceState)
    }

    fun setVoiceState(state: VoiceState) {
        voiceState = state
        if (state == VoiceState.LISTENING) showVoiceMessage(null)
        title.setText(when (state) {
            VoiceState.IDLE -> R.string.keyboard_title
            VoiceState.LISTENING -> R.string.voice_listening
            VoiceState.PROCESSING -> R.string.voice_processing
            VoiceState.PAUSED -> R.string.voice_paused
        })
        microphone.text = if (state == VoiceState.LISTENING || state == VoiceState.PAUSED) "■" else "🎙"
        microphone.isEnabled = voiceAllowed && state != VoiceState.PROCESSING
        microphone.alpha = if (microphone.isEnabled) 1f else 0.4f
        microphone.contentDescription = context.getString(when {
            !voiceAllowed -> R.string.voice_blocked
            state == VoiceState.LISTENING || state == VoiceState.PAUSED -> R.string.voice_stop
            else -> R.string.voice_microphone
        })
        cancelVoice.visibility = if (state == VoiceState.IDLE) View.GONE else View.VISIBLE
    }

    fun showVoiceMessage(message: Int?) {
        voiceMessage.maxLines = Int.MAX_VALUE
        voiceMessage.ellipsize = null
        voiceMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) voiceMessage.setText(message)
    }

    fun showTextMessage(text:String) { voiceMessage.text=text; voiceMessage.maxLines=3; voiceMessage.visibility=View.VISIBLE }

    fun showVoicePreview(text: String) {
        voiceMessage.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        voiceMessage.text = text.takeLast(500)
        voiceMessage.maxLines = 3
        voiceMessage.ellipsize = android.text.TextUtils.TruncateAt.END
    }


    private val symbols = "qwertyuiopasdfghjklzxcvbnm".zip("%\\|=[]<>{}@#$-&-+()*\"':;!?").toMap() + mapOf(
        'ا' to 'آ', 'د' to 'ڈ', 'ر' to 'ڑ', 'ت' to 'ٹ', 'ن' to 'ں', 'ز' to 'ژ', 'س' to 'ص',
        'ح' to 'خ', 'ط' to 'ظ', 'ض' to 'ث', 'ی' to 'ئ', 'و' to 'ؤ')
    private fun letterLabel(letter:String):CharSequence {
        val displayed=state.display(letter)
        val hint=if(options.enabled("symbols_hold")) symbols[letter.firstOrNull()] else null
        if(hint==null) return displayed
        return android.text.SpannableString("$displayed $hint").apply {
            setSpan(android.text.style.RelativeSizeSpan(0.5f),displayed.length,length,0)
            setSpan(android.text.style.ForegroundColorSpan(palette.muted),displayed.length,length,0)
        }
    }
    private fun configureGesture(button:GestureKeyButton,key:Key) {
        gestureButtons.add(button)
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
                        setTextColor(palette.text); background=rounded(palette.surface,context.dp(8).toFloat())
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
        val visible=if(options.enabled("suggestion_strip") && panel==null) candidates.take(6) else emptyList()
        while(candidateButtons.size<visible.size) {
            val button=Button(context).apply {
                isAllCaps=false; setTextColor(palette.text); minWidth=0; minimumWidth=0
                minHeight=0; minimumHeight=0; setPadding(context.dp(8),0,context.dp(8),0)
                background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(palette.background,context.dp(6).toFloat()),null)
                isSoundEffectsEnabled=false
            }
            candidateButtons.add(button); candidateRow.addView(button,LayoutParams(LayoutParams.WRAP_CONTENT,context.dp(48)))
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
    fun showSettings(page:String="Raabta settings") {
        cancelRepeats()
        panel?.let { content.removeView(it) }; panel=null
        settingsPage=page
        for(i in 0 until content.childCount) content.getChildAt(i).visibility=View.GONE
        if(page.isEmpty()) {
            header?.visibility=View.VISIBLE; rows.visibility=View.VISIBLE
            content.findViewWithTag<View>("SuggestionStrip")?.visibility=if(options.enabled("suggestion_strip")) View.VISIBLE else View.GONE
            render(enterLabel); return
        }
        val settings=KeyboardSettingsPanel(context,page,::showSettings,{ onSettingsChanged(page) },onTool)
        panel=settings
        setBackgroundColor(palette.background)
        content.translationX=0f; content.translationY=0f
        content.addView(settings,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 220 else 360)))
    }
    private fun styleTool(button:Button) {
        button.setTextColor(palette.text); button.isAllCaps=false; button.isSoundEffectsEnabled=false
        button.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(palette.surface,context.dp(8).toFloat()),null)
    }
    fun showEmojiPicker() {
        showSettings("")
        rows.removeAllViews()
        val close=Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { render(enterLabel) } }
        rows.addView(close)
        val grid=LinearLayout(context).apply { orientation=VERTICAL }
        (options.emoji()+LocalSuggestions.popularEmoji+listOf("🥰","😍","🤔","😴","😡","🤗","💯","🌹","🌙","⭐","☀️","🌧️","🍎","🍕","⚽","🎁")).distinct().chunked(if(options.number("emoji_size")>30) 4 else 6).forEach { group ->
            val row=LinearLayout(context)
            group.forEach { emoji -> row.addView(Button(context).apply { styleTool(this);
                text=emoji; contentDescription=emoji; textSize=options.number("emoji_size").toFloat(); minWidth=0; minimumWidth=0; setPadding(0,0,0,0)
                setOnClickListener { onCandidate(Candidate(emoji,emoji,emoji=true)) }
            },LayoutParams(0,context.dp(52),1f)) }
            grid.addView(row)
        }
        rows.addView(ScrollView(context).apply { addView(grid) },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(230)))
    }
    fun showEditingTools() {
        showSettings(""); rows.removeAllViews()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { render(enterLabel) } })
        listOf(listOf("left","right","home","end"),listOf("selectAll","copy","cut","paste"),listOf("undo","redo")).forEach { tools ->
            val row=LinearLayout(context)
            tools.forEach { tool -> row.addView(Button(context).apply { styleTool(this);
                text=tool.replaceFirstChar(Char::uppercase); isAllCaps=false; minWidth=0; minimumWidth=0
                setOnClickListener { onTool("edit:$tool") }
            },LayoutParams(0,context.dp(52),1f)) }
            rows.addView(row)
        }
    }
    fun showMediaPicker() {
        showSettings(""); rows.removeAllViews()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { render(enterLabel) } })
        listOf("sticker:smile" to "Smile sticker", "sticker:heart" to "Heart sticker", "anim:smile" to "Smile GIF", "anim:heart" to "Heart GIF").forEach { (item,label) ->
            rows.addView(Button(context).apply { styleTool(this); text=label; setOnClickListener { onTool("media:$item") } })
        }
    }
    fun showClipboard(candidates:List<Candidate>) {
        showSettings(""); rows.removeAllViews()
        rows.addView(Button(context).apply { styleTool(this); setText(R.string.back_to_keyboard); setOnClickListener { render(enterLabel) } })
        val list=LinearLayout(context).apply { orientation=VERTICAL }
        if(candidates.isEmpty()) list.addView(TextView(context).apply { setText(R.string.clipboard_empty); setTextColor(palette.text) })
        candidates.forEach { candidate -> list.addView(Button(context).apply { styleTool(this); text=candidate.label; isAllCaps=false; setOnClickListener { onCandidate(candidate) } }) }
        rows.addView(ScrollView(context).apply { addView(list) },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(210)))
    }
    fun backFromPanel():Boolean {
        if(settingsPage.isEmpty()) return false
        showSettings(if(settingsPage=="Raabta settings") "" else "Raabta settings"); return true
    }

    fun matchesCurrentPreferences(): Boolean = preferences == KeyboardPreferences.load(context) && palette == Palette.from(context) && optionFingerprint == options.fingerprint()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep tablet/landscape keys within reach while the IME window remains full width.
        content.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceIn(0, context.dp(if(options.number("one_handed")==0 && !floating) 840 else 320))
        gravity = when(options.number("one_handed")) { 1 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or (if(floating) Gravity.BOTTOM else Gravity.TOP)
        title.dragEnabled=floating
        setBackgroundColor(if(floating) android.graphics.Color.TRANSPARENT else palette.background)
        content.setBackgroundColor(palette.background)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if(floating) {
            val available=MeasureSpec.getSize(heightMeasureSpec)
            val desired=(resources.displayMetrics.heightPixels*0.65f).toInt().coerceAtLeast(measuredHeight)
            setMeasuredDimension(measuredWidth,if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED) desired else desired.coerceAtMost(available))
        }
    }

    override fun onDetachedFromWindow() {
        cancelRepeats()
        super.onDetachedFromWindow()
    }
}
