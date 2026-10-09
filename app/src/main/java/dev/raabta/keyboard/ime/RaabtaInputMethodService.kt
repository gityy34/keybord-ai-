package dev.raabta.keyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import dev.raabta.keyboard.setup.SetupActivity

class RaabtaInputMethodService : InputMethodService() {
    private val state = KeyboardState()
    private var keyboard: KeyboardView? = null
    private var editor = EditorInfo()

    override fun onCreateInputView(): View = KeyboardView(
        this, state, ::handleKey,
        onCaps = { state.lockShift(); keyboard?.updateShift() },
        onSettings = {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onSwitch = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
    ).also {
        keyboard = it
        it.render(EditorPolicy.enterLabel(editor))
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboard?.cancelRepeats()
        editor = attribute
        val capsMode = currentInputConnection?.getCursorCapsMode(attribute.inputType) ?: 0
        state.reset(EditorPolicy.isNumeric(attribute), capsMode != 0)
        keyboard?.render(EditorPolicy.enterLabel(editor))
    }

    private fun handleKey(key: Key) {
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
        keyboard?.cancelRepeats()
        editor = EditorInfo()
        state.reset(numeric = false, capitalized = false)
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.cancelRepeats()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        keyboard?.cancelRepeats()
        keyboard = null
        super.onDestroy()
    }
}
