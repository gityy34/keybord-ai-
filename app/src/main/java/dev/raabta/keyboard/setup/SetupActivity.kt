package dev.raabta.keyboard.setup

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.RaabtaInputMethodService
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.rounded
import dev.raabta.keyboard.ui.KeyboardPreferences
import dev.raabta.keyboard.ui.KeyboardTheme
import dev.raabta.keyboard.ui.KeyboardHeight
import dev.raabta.keyboard.voice.VoiceSettings
import dev.raabta.keyboard.voice.VoicePermissionActivity

class SetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var picker: Button
    private val fields = mutableListOf<EditText>()
    private lateinit var palette: Palette

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Palette.isDark(this)) R.style.AppTheme_Dark else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        palette = Palette.from(this)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.background)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        content.addView(label("RAABTA", 13f, palette.accent))
        content.addView(label(getString(R.string.setup_title), 32f, palette.text).apply {
            setPadding(0, dp(12), 0, dp(12))
        })
        content.addView(label(getString(R.string.setup_description), 16f, palette.muted))
        status = label("", 14f, palette.accent).apply { setPadding(0, dp(20), 0, dp(12)) }
        content.addView(status)
        content.addView(action(R.string.enable_keyboard) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(label(getString(R.string.enable_help), 13f, palette.muted).apply { setPadding(0, dp(8), 0, dp(16)) })
        picker = action(R.string.choose_keyboard) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
        content.addView(picker)
        content.addView(label(getString(R.string.appearance_title), 22f, palette.text).apply { setPadding(0, dp(24), 0, dp(8)) })
        val preferences = KeyboardPreferences.load(this)
        content.addView(choiceGroup(listOf(
            KeyboardTheme.SYSTEM to R.string.theme_system,
            KeyboardTheme.LIGHT to R.string.theme_light,
            KeyboardTheme.DARK to R.string.theme_dark,
        ), preferences.theme, "Theme") { selected ->
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(theme = selected))
            recreate()
        })
        content.addView(label(getString(R.string.height_title), 17f, palette.text).apply { setPadding(0, dp(12), 0, 0) })
        content.addView(choiceGroup(listOf(
            KeyboardHeight.COMPACT to R.string.height_compact,
            KeyboardHeight.NORMAL to R.string.height_normal,
            KeyboardHeight.TALL to R.string.height_tall,
        ), preferences.height, "Height") { selected ->
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(height = selected))
        })
        content.addView(toggle(R.string.number_row, "NumberRow", preferences.numberRow) {
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(numberRow = it))
        })
        content.addView(toggle(R.string.vibration_toggle, "Vibration", preferences.vibration) {
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(vibration = it))
        })
        content.addView(toggle(R.string.sound_toggle, "Sound", preferences.sound) {
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(sound = it))
        })
        content.addView(label(getString(R.string.feedback_help), 13f, palette.muted).apply { setPadding(0, dp(8), 0, dp(8)) })
        content.addView(label(getString(R.string.hold_delay_title), 17f, palette.text))
        content.addView(choiceGroup(listOf(200L to R.string.hold_fast, 400L to R.string.hold_normal, 600L to R.string.hold_slow), preferences.holdDelayMillis, "HoldDelay") {
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(holdDelayMillis = it))
        })
        content.addView(label(getString(R.string.repeat_title), 17f, palette.text))
        content.addView(choiceGroup(listOf(55L to R.string.repeat_fast, 85L to R.string.repeat_normal, 120L to R.string.repeat_slow), preferences.repeatMillis, "Repeat") {
            KeyboardPreferences.save(this, KeyboardPreferences.load(this).copy(repeatMillis = it))
        })
        content.addView(label(getString(R.string.test_heading), 22f, palette.text).apply { setPadding(0, dp(26), 0, dp(12)) })
        content.addView(testField(R.id.test_message, R.string.test_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_NONE, multiline = true))
        content.addView(testField(R.id.test_search, R.string.search_hint, InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH))
        content.addView(testField(R.id.test_number, R.string.number_hint,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, EditorInfo.IME_ACTION_NEXT))
        content.addView(testField(R.id.test_password, R.string.password_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE))
        content.addView(label(getString(R.string.typing_help), 14f, palette.muted).apply { setPadding(0, dp(14), 0, dp(24)) })
        content.addView(label(getString(R.string.voice_title), 20f, palette.text))
        val languages = RadioGroup(this)
        for ((language, title) in listOf(VoiceSettings.ENGLISH to R.string.voice_language_english, VoiceSettings.URDU to R.string.voice_language_urdu, VoiceSettings.MIXED to R.string.voice_language_mixed)) {
            languages.addView(RadioButton(this).apply {
                id = View.generateViewId()
                setText(title)
                setTextColor(palette.text)
                isChecked = VoiceSettings.language(this@SetupActivity) == language
                setOnCheckedChangeListener { _, checked -> if (checked) VoiceSettings.setLanguage(this@SetupActivity, language) }
            })
        }
        content.addView(languages)
        content.addView(label(getString(R.string.voice_pause_title), 17f, palette.text))
        content.addView(choiceGroup(listOf(0L to R.string.voice_manual_stop, 8_000L to R.string.voice_pause_eight, 12_000L to R.string.voice_pause_twelve), VoiceSettings.pauseMillis(this), "VoicePause") {
            VoiceSettings.setPauseMillis(this, it)
        })
        content.addView(label(getString(R.string.voice_finish_title), 17f, palette.text))
        content.addView(choiceGroup(listOf(800L to R.string.voice_finish_fast, 3_000L to R.string.voice_finish_quality), VoiceSettings.finalWaitMillis(this), "VoiceFinish") {
            VoiceSettings.setFinalWaitMillis(this, it)
        })
        content.addView(label(getString(R.string.voice_mixed_primary_title), 17f, palette.text))
        content.addView(choiceGroup(listOf(VoiceSettings.URDU to R.string.voice_primary_urdu, VoiceSettings.ENGLISH to R.string.voice_primary_english), VoiceSettings.mixedPrimary(this), "VoicePrimary") {
            VoiceSettings.setMixedPrimary(this, it)
        })
        content.addView(label(getString(R.string.voice_accuracy_help), 13f, palette.muted).apply { setPadding(0, dp(8), 0, dp(8)) })
        content.addView(Switch(this).apply {
            tag = "OnDeviceOnly"
            setText(R.string.voice_on_device_only)
            setTextColor(palette.text)
            isChecked = VoiceSettings.onDeviceOnly(this@SetupActivity)
            setOnCheckedChangeListener { _, enabled -> VoiceSettings.setOnDeviceOnly(this@SetupActivity, enabled) }
        })
        content.addView(label(getString(R.string.voice_privacy), 14f, palette.muted).apply { setPadding(0, dp(8), 0, dp(12)) })
        content.addView(action(R.string.voice_allow) { startActivity(Intent(this, VoicePermissionActivity::class.java)) })
        content.addView(label("", 14f, palette.muted).apply { setPadding(0, dp(12), 0, 0) })
        content.addView(label(getString(R.string.privacy_title), 20f, palette.text))
        content.addView(label(getString(R.string.privacy_description), 14f, palette.muted).apply { setPadding(0, dp(8), 0, dp(20)) })
        content.addView(label(getString(R.string.version_label), 12f, palette.muted))
        content.isFocusableInTouchMode = true
        content.requestFocus()
    }

    private fun <T> choiceGroup(choices: List<Pair<T, Int>>, selected: T, prefix: String, onChoice: (T) -> Unit) = RadioGroup(this).apply {
        choices.forEach { (value, title) ->
            addView(RadioButton(this@SetupActivity).apply {
                id = View.generateViewId()
                tag = "$prefix:$value"
                setText(title)
                setTextColor(palette.text)
                isChecked = value == selected
                setOnCheckedChangeListener { _, checked -> if (checked) onChoice(value) }
            })
        }
    }

    private fun toggle(title: Int, viewTag: String, selected: Boolean, onToggle: (Boolean) -> Unit) = Switch(this).apply {
        tag = viewTag
        setText(title)
        setTextColor(palette.text)
        isChecked = selected
        setPadding(0, dp(6), 0, dp(6))
        minHeight = dp(48)
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(InputMethodManager::class.java)
        val component = ComponentName(this, RaabtaInputMethodService::class.java).flattenToShortString()
        val enabled = manager.enabledInputMethodList.any { it.id == component }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        status.setText(when {
            current == component -> R.string.active_status
            enabled -> R.string.enabled_status
            else -> R.string.disabled_status
        })
        picker.isEnabled = enabled
        picker.alpha = if (enabled) 1f else 0.5f
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun action(title: Int, callback: () -> Unit) = Button(this).apply {
        setText(title)
        isAllCaps = false
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(palette.surface)
        background = rounded(palette.accent, dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        setOnClickListener { callback() }
    }

    private fun testField(viewId: Int, hintId: Int, type: Int, action: Int, multiline: Boolean = false) = EditText(this).apply {
        id = viewId
        inputType = type
        // Keep a normal text field for voice testing; password/non-text fields remain blocked by the IME.
        imeOptions = action
        setHint(hintId)
        textSize = 17f
        setTextColor(palette.text)
        setHintTextColor(palette.muted)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(palette.surface, dp(12).toFloat())
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        gravity = Gravity.TOP or Gravity.START
        if (multiline) { minLines = 2; maxLines = 4 } else setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        }
        fields.add(this)
    }

    override fun onDestroy() {
        fields.forEach { it.text.clear() }
        fields.clear()
        super.onDestroy()
    }
}
