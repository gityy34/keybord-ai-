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
) : LinearLayout(context) {
    private val preferences = KeyboardPreferences.load(context)
    private val palette = Palette.from(context)
    private val feedback = KeyFeedback(context, preferences)
    private val content = LinearLayout(context)
    private val rows = LinearLayout(context)
    private val letterButtons = mutableListOf<Pair<Button, String>>()
    private var shiftButton: Button? = null
    private var deleteButton: RepeatingDeleteButton? = null
    private var enterLabel = R.string.key_enter
    private val title = TextView(context)
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
        header.addView(headerButton("🌐", R.string.keyboard_switch, onSwitch))
        header.addView(headerButton("⚙", R.string.keyboard_settings, onSettings))
        content.addView(header)
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
        shiftButton = null
        when (state.mode) {
            KeyboardMode.LETTERS -> {
                if (preferences.numberRow) addRow("1234567890".map { Key.Text(it.toString()) })
                addRow("qwertyuiop".map { Key.Text(it.toString()) })
                addRow("asdfghjkl".map { Key.Text(it.toString()) }, inset = 12)
                addRow(listOf(Key.Shift) + "zxcvbnm".map { Key.Text(it.toString()) } + Key.Delete)
            }
            KeyboardMode.NUMBERS -> {
                addRow("1234567890".map { Key.Text(it.toString()) })
                addRow(listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map { Key.Text(it) })
                addRow(listOf(Key.Mode(KeyboardMode.SYMBOLS)) + listOf("*", "\"", "'", ":", ";", "!", "?").map { Key.Text(it) } + Key.Delete)
            }
            KeyboardMode.SYMBOLS -> {
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
            Key.Text(","), Key.Space, Key.Text("."), Key.Enter,
        ))
        updateShift()
    }

    fun updateShift() {
        for ((button, letter) in letterButtons) button.text = state.display(letter)
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
            val button = (if (key == Key.Delete) RepeatingDeleteButton(context) else Button(context)).apply {
                isAllCaps = false
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = preferences.vibration
                textSize = if (key is Key.Text) (if (landscape) 20f else 22f) else 14f
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
                    Key.Space -> context.getString(R.string.key_space)
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
                if (this is RepeatingDeleteButton) deleteButton = this
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
        })
        microphone.text = if (state == VoiceState.LISTENING) "■" else "🎙"
        microphone.isEnabled = voiceAllowed && state != VoiceState.PROCESSING
        microphone.alpha = if (microphone.isEnabled) 1f else 0.4f
        microphone.contentDescription = context.getString(when {
            !voiceAllowed -> R.string.voice_blocked
            state == VoiceState.LISTENING -> R.string.voice_stop
            else -> R.string.voice_microphone
        })
        cancelVoice.visibility = if (state == VoiceState.IDLE) View.GONE else View.VISIBLE
    }

    fun showVoiceMessage(message: Int?) {
        voiceMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) voiceMessage.setText(message)
    }

    fun matchesCurrentPreferences(): Boolean = preferences == KeyboardPreferences.load(context) && palette == Palette.from(context)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep tablet/landscape keys within reach while the IME window remains full width.
        content.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceIn(0, context.dp(840))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        cancelRepeats()
        super.onDetachedFromWindow()
    }
}
