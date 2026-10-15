package dev.raabta.keyboard.ime

import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.text.Bidi

object CursorNavigation {
    /** Host owns selection. Grapheme boundaries protect combining marks and joined emoji. */
    fun move(connection: InputConnection?, steps: Int) {
        connection ?: return
        try {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4096 }, 0)
            if (extracted?.text == null || extracted.partialStartOffset >= 0) {
                repeat(kotlin.math.abs(steps).coerceAtMost(20)) {
                    val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }
                return
            }
            val text = extracted.text.toString()
            val selection=extracted.selectionStart.coerceIn(0,text.length)
            val start=text.lastIndexOf('\n',(selection-1).coerceAtLeast(0))+1
            val end=text.indexOf('\n',selection).let { if(it<0) text.length else it }
            val paragraph=text.substring(start.coerceAtMost(end),end)
            val rtl = paragraph.isNotEmpty() && Bidi(paragraph, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseLevel == 1
            val delta = if (rtl) -steps else steps
            var cursor = (if (delta < 0) extracted.selectionStart else extracted.selectionEnd).coerceIn(0, text.length)
            val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
            repeat(kotlin.math.abs(delta).coerceAtMost(20)) {
                val target = if (delta < 0) breaks.preceding(cursor) else breaks.following(cursor)
                if (target != BreakIterator.DONE) cursor = target
            }
            connection.setSelection(extracted.startOffset + cursor, extracted.startOffset + cursor)
        } catch (_: RuntimeException) { /* Disconnected or incompatible editor: leave selection unchanged. */ }
    }
}
