package dev.raabta.keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class KeyboardStateTest {
    @Test fun singleShiftAppliesToOneCharacter() {
        val state = KeyboardState()
        state.toggleShift()
        assertEquals("A", state.display("a"))
        state.afterCharacter()
        assertEquals("b", state.display("b"))
    }

    @Test fun capsLockPersistsUntilShiftIsPressed() {
        val state = KeyboardState()
        state.toggleShift()
        state.toggleShift()
        repeat(100) { assertEquals("Z", state.display("z")); state.afterCharacter() }
        state.toggleShift()
        assertEquals("z", state.display("z"))
    }

    @Test fun newEditorResetsCapsAndMode() {
        val state = KeyboardState()
        state.lockShift()
        state.selectMode(KeyboardMode.SYMBOLS)
        state.reset(numeric = true, capitalized = false)
        assertEquals(KeyboardMode.NUMERIC, state.mode)
        assertEquals(ShiftMode.OFF, state.shift)
        state.reset(numeric = false, capitalized = true)
        assertEquals(KeyboardMode.LETTERS, state.mode)
        assertEquals(ShiftMode.ONCE, state.shift)
    }
}
