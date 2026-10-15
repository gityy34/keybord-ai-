package dev.raabta.keyboard.ime

import java.util.Locale

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, NUMERIC }
enum class ShiftMode { OFF, ONCE, LOCKED }

/** UI state only. Typed text is owned by the active editor, never stored here. */
class KeyboardState {
    var mode = KeyboardMode.LETTERS
        private set
    var shift = ShiftMode.OFF
        private set

    fun reset(numeric: Boolean, capitalized: Boolean) {
        mode = if (numeric) KeyboardMode.NUMERIC else KeyboardMode.LETTERS
        shift = if (capitalized) ShiftMode.ONCE else ShiftMode.OFF
    }

    fun toggleShift() {
        shift = when (shift) {
            ShiftMode.OFF -> ShiftMode.ONCE
            ShiftMode.ONCE -> ShiftMode.LOCKED
            ShiftMode.LOCKED -> ShiftMode.OFF
        }
    }

    fun setAutomaticShift(enabled:Boolean) { if(shift!=ShiftMode.LOCKED) shift=if(enabled) ShiftMode.ONCE else ShiftMode.OFF }
    fun lockShift() { shift = ShiftMode.LOCKED }
    fun selectMode(mode: KeyboardMode) { this.mode = mode }
    fun afterCharacter() { if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF }
    fun display(text: String): String =
        if (mode == KeyboardMode.LETTERS && shift != ShiftMode.OFF) text.uppercase(Locale.ROOT) else text
}
