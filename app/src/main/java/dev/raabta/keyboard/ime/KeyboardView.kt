package dev.raabta.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.rounded

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
) : LinearLayout(context) {
    private val palette = Palette.from(context)
    private val rows = LinearLayout(context)
    private val letterButtons = mutableListOf<Pair<Button, String>>()
    private var shiftButton: Button? = null
    private var deleteButton: RepeatingDeleteButton? = null
    private var enterLabel = R.string.key_enter

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(palette.background)
        setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(5))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            setText(R.string.keyboard_title)
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(context.dp(10), 0, 0, 0)
        }, LayoutParams(0, context.dp(38), 1f))
        header.addView(headerButton("🌐", R.string.keyboard_switch, onSwitch))
        header.addView(headerButton("⚙", R.string.keyboard_settings, onSettings))
        addView(header)
        rows.orientation = VERTICAL
        addView(rows)
    }

    private fun headerButton(label: String, description: Int, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        contentDescription = context.getString(description)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(palette.text)
        background = rounded(palette.background, context.dp(8).toFloat())
        layoutParams = LayoutParams(context.dp(48), context.dp(38))
        setOnClickListener { action() }
    }

    fun render(enterLabel: Int) {
        cancelRepeats()
        this.enterLabel = enterLabel
        rows.removeAllViews()
        letterButtons.clear()
        shiftButton = null
        when (state.mode) {
            KeyboardMode.LETTERS -> {
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
        val height = context.dp(if (landscape) 44 else 54)
        for (key in keys) {
            val button = (if (key == Key.Delete) RepeatingDeleteButton(context) else Button(context)).apply {
                isAllCaps = false
                textSize = if (key is Key.Text) 21f else 14f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(if (key == Key.Enter) palette.surface else palette.text)
                background = RippleDrawable(
                    ColorStateList.valueOf(0x33176B60),
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
                setOnClickListener { onKey(key) }
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

    override fun onDetachedFromWindow() {
        cancelRepeats()
        super.onDetachedFromWindow()
    }
}
