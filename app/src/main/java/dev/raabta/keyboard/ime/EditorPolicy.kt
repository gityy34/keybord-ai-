package dev.raabta.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.raabta.keyboard.R

object EditorPolicy {
    fun isNumeric(info: EditorInfo): Boolean = when (info.inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
    }

    fun action(info: EditorInfo): Int {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        return when (val action = info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS -> action
            else -> EditorInfo.IME_ACTION_NONE
        }
    }

    fun enterLabel(info: EditorInfo): Int = when (action(info)) {
        EditorInfo.IME_ACTION_GO -> R.string.key_go
        EditorInfo.IME_ACTION_SEARCH -> R.string.key_search
        EditorInfo.IME_ACTION_SEND -> R.string.key_send
        EditorInfo.IME_ACTION_NEXT -> R.string.key_next
        EditorInfo.IME_ACTION_PREVIOUS -> R.string.key_previous
        EditorInfo.IME_ACTION_DONE -> R.string.key_done
        else -> R.string.key_enter
    }
}
