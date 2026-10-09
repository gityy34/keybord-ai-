package dev.raabta.keyboard.ime

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** All edits go through Android's connection to the current app. No history or context cache. */
object EditorCommands {
    fun type(connection: InputConnection?, text: String): Boolean = connection?.commitText(text, 1) ?: false

    fun delete(connection: InputConnection?) {
        connection ?: return
        // A selection is replaced as a unit; otherwise delete a Unicode code point.
        if (!connection.getSelectedText(0).isNullOrEmpty()) {
            connection.commitText("", 1)
        } else if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            // Some third-party editors don't implement code-point deletion.
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    fun enter(connection: InputConnection?, info: EditorInfo) {
        connection ?: return
        val action = EditorPolicy.action(info)
        if (action != EditorInfo.IME_ACTION_NONE && connection.performEditorAction(action)) return
        if (info.inputType and android.text.InputType.TYPE_MASK_CLASS == android.text.InputType.TYPE_CLASS_TEXT) {
            connection.commitText("\n", 1)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
