package dev.raabta.keyboard.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Highlights words while sliding, deletes on release, restores the caret on cancel. */
class DeleteSelection {
    private var connection: InputConnection? = null
    private var text = ""
    private var offset = 0
    private var anchor = 0
    private var start = 0
    fun begin(input: InputConnection?): Boolean {
        cancel()
        input ?: return false
        return try {
            val extracted = input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4096 }, 0) ?: return false
            val value = extracted.text?.toString() ?: return false
            if (value.length > 4096 || extracted.partialStartOffset >= 0 || extracted.selectionStart != extracted.selectionEnd || extracted.selectionStart !in 1..value.length) return false
            connection = input; text = value; offset = extracted.startOffset; anchor = extracted.selectionStart; start = anchor
            true
        } catch (_: RuntimeException) { false }
    }
    fun move(words: Int) {
        val input = connection ?: return
        try {
            var index = anchor
            repeat(words.coerceIn(0, 100)) {
                while (index > 0 && text[index - 1].isWhitespace()) index--
                while (index > 0 && !text[index - 1].isWhitespace()) index--
            }
            if (unchanged()) { start = index; input.setSelection(offset + start, offset + anchor) }
            else abandon()
        } catch (_: RuntimeException) { abandon() }
    }
    fun finish() {
        try {
            if (start < anchor && unchanged() && connection?.getSelectedText(0)?.toString() == text.substring(start, anchor))
                connection?.commitText("", 1)
        } catch (_: RuntimeException) { }
        abandon()
    }
    fun cancel() {
        try { if (unchanged()) connection?.setSelection(offset + anchor, offset + anchor) } catch (_: RuntimeException) { }
        abandon()
    }
    fun abandon() { connection = null; text = ""; start = 0; anchor = 0 }
    private fun unchanged(): Boolean {
        val extracted = connection?.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4096 }, 0) ?: return false
        return extracted.startOffset == offset && extracted.text?.toString() == text &&
            extracted.selectionEnd == anchor && extracted.selectionStart in listOf(anchor, start)
    }
}
