package dev.raabta.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import dev.raabta.keyboard.setup.SetupActivity
import android.widget.Toast
import dev.raabta.keyboard.R
import dev.raabta.keyboard.voice.*

class RaabtaInputMethodService : InputMethodService() {
    private val state = KeyboardState()
    private var keyboard: KeyboardView? = null
    private var editor = EditorInfo()
    private var editorGeneration = 0
    private var awaitingVoicePermission = false
    internal var speechEngineFactory = SpeechEngineFactory { listener -> DeviceSpeechEngine(this, listener) }
    private val voice by lazy {
        VoiceController(SpeechEngineFactory { speechEngineFactory.create(it) }, { keyboard?.setVoiceState(it) }, ::voiceError)
    }

    override fun onCreateInputView(): View = KeyboardView(
        this, state, ::handleKey,
        onCaps = { voice.cancel(); state.lockShift(); keyboard?.updateShift() },
        onSettings = {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onSwitch = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
        onVoice = ::toggleVoice,
        onCancelVoice = { voice.cancel() },
    ).also {
        keyboard = it
        it.render(EditorPolicy.enterLabel(editor))
        it.setVoiceAllowed(VoicePolicy.allows(editor))
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (keyboard?.matchesCurrentPreferences() == false) {
            voice.cancel()
            keyboard?.cancelRepeats()
            setInputView(onCreateInputView())
        }
        if (awaitingVoicePermission) {
            awaitingVoicePermission = false
            keyboard?.showVoiceMessage(if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                R.string.voice_permission_granted else R.string.voice_permission_denied)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        voice.cancel()
        keyboard?.cancelRepeats()
        super.onConfigurationChanged(newConfig)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        voice.cancel()
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = attribute
        val capsMode = currentInputConnection?.getCursorCapsMode(attribute.inputType) ?: 0
        state.reset(EditorPolicy.isNumeric(attribute), capsMode != 0)
        keyboard?.render(EditorPolicy.enterLabel(editor))
        keyboard?.setVoiceAllowed(VoicePolicy.allows(editor))
    }

    private fun handleKey(key: Key) {
        voice.cancel()
        val connection = currentInputConnection
        when (key) {
            is Key.Text -> {
                if (EditorCommands.type(connection, state.display(key.text))) {
                    val previousShift = state.shift
                    state.afterCharacter()
                    if (previousShift != state.shift) keyboard?.updateShift()
                }
            }
            Key.Space -> EditorCommands.type(connection, " ")
            Key.Delete -> EditorCommands.delete(connection)
            Key.Enter -> EditorCommands.enter(connection, editor)
            Key.Shift -> { state.toggleShift(); keyboard?.updateShift() }
            is Key.Mode -> { state.selectMode(key.mode); keyboard?.render(EditorPolicy.enterLabel(editor)) }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInput() {
        voice.cancel()
        ++editorGeneration
        keyboard?.cancelRepeats()
        editor = EditorInfo()
        state.reset(numeric = false, capitalized = false)
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voice.cancel()
        keyboard?.cancelRepeats()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        voice.cancel()
        keyboard?.cancelRepeats()
        keyboard = null
        super.onDestroy()
    }

    override fun onWindowHidden() {
        voice.cancel()
        super.onWindowHidden()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) voice.cancel()
    }

    private fun toggleVoice() {
        if (voice.active) { voice.stop(); return }
        if (!VoicePolicy.allows(editor)) { message(R.string.voice_blocked); return }
        if (VoiceSettings.language(this) == VoiceSettings.MIXED && Build.VERSION.SDK_INT < 34) {
            keyboard?.showVoiceMessage(R.string.voice_mixed_unsupported)
            return
        }
        val connection = currentInputConnection ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingVoicePermission = true
            startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val generation = editorGeneration
        voice.start(VoiceSettings.language(this)) { result ->
            if (generation != editorGeneration || currentInputConnection !== connection || !VoicePolicy.allows(editor)) {
                message(R.string.voice_error_insert)
                return@start
            }
            if (!EditorCommands.type(connection, result)) message(R.string.voice_error_insert)
        }
    }

    private fun voiceError(error: VoiceError) {
        val text = when (error) {
        VoiceError.PERMISSION -> R.string.voice_permission_denied
        VoiceError.UNAVAILABLE -> R.string.voice_error_unavailable
        VoiceError.NETWORK -> R.string.voice_error_network
        VoiceError.NO_SPEECH -> R.string.voice_error_no_speech
        VoiceError.BUSY -> R.string.voice_error_busy
        VoiceError.LANGUAGE -> R.string.voice_error_language
        VoiceError.AUDIO -> R.string.voice_error_audio
        VoiceError.TIMEOUT -> R.string.voice_error_timeout
        VoiceError.INTERNAL -> R.string.voice_error_internal
        }
        keyboard?.showVoiceMessage(text)
        if (keyboard == null) message(text)
    }

    private fun message(text: Int) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
