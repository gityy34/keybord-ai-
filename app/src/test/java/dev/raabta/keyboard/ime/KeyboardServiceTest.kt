package dev.raabta.keyboard.ime

import android.os.Looper
import android.Manifest
import android.text.Editable
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.RuntimeEnvironment
import dev.raabta.keyboard.voice.*
import java.time.Duration

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class KeyboardServiceTest {
    private lateinit var service: RaabtaInputMethodService
    private lateinit var editor: EditText
    private lateinit var connection: TestConnection
    private lateinit var view: KeyboardView

    private class TestConnection(private val field: EditText) : BaseInputConnection(field, true) {
        val actions = mutableListOf<Int>()
        val events = mutableListOf<KeyEvent>()
        var acceptActions = true
        var acceptCodePointDelete = true
        override fun getEditable(): Editable = field.editableText
        override fun performEditorAction(editorAction: Int): Boolean {
            actions.add(editorAction)
            return acceptActions
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            if (acceptCodePointDelete) super.deleteSurroundingTextInCodePoints(beforeLength, afterLength) else false
        override fun sendKeyEvent(event: KeyEvent): Boolean { events.add(event); return true }
    }

    @Before fun start() {
        service = Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        editor = EditText(service)
        connection = TestConnection(editor)
        ReflectionHelpers.setField(service, "mInputStarted", true)
        ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
        startEditor()
        view = service.onCreateInputView() as KeyboardView
    }

    private fun startEditor(type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                            action: Int = EditorInfo.IME_ACTION_NONE) {
        service.onStartInput(EditorInfo().apply { inputType = type; imeOptions = action }, false)
    }

    private fun press(tag: String) {
        val button = view.findViewWithTag<View>(tag)
        assertNotNull("Missing key: $tag", button)
        assertTrue(button.performClick())
    }

    private fun type(text: String) = text.forEach {
        if (it == ' ') press("Space") else press("text:$it")
    }

    @After fun stop() { service.onDestroy() }

    @Test fun realServiceCommitsRapidTypingSpacesNewlinesAndBackspace() {
        repeat(40) { type("hello world") }
        assertEquals("hello world".repeat(40), editor.text.toString())
        press("Enter")
        assertTrue(editor.text.endsWith("\n"))
        press("Delete")
        press("Delete")
        assertEquals("hello world".repeat(40).dropLast(1), editor.text.toString())
    }

    @Test fun shiftCapsAndSymbolsWorkThroughTheService() {
        press("Shift"); type("ab")
        press("Shift"); press("Shift"); type("cd")
        press("Shift"); type("e")
        press("mode:NUMBERS"); type("123")
        press("mode:SYMBOLS"); press("text:€"); press("text:[")
        press("mode:LETTERS"); type("f")
        assertEquals("AbCDe123€[f", editor.text.toString())
    }

    @Test fun heldShiftLocksCaps() {
        assertTrue(view.findViewWithTag<View>("Shift").performLongClick())
        type("ab")
        assertEquals("AB", editor.text.toString())
    }

    @Test fun deleteRemovesSelectionAndWholeSupplementaryCodePoint() {
        connection.commitText("a😀b", 1)
        press("Delete"); press("Delete")
        assertEquals("a", editor.text.toString())
        connection.commitText("selected", 1)
        editor.setSelection(1, editor.length())
        press("Delete")
        assertEquals("a", editor.text.toString())
        press("text:z")
        assertEquals("az", editor.text.toString())
        repeat(5) { press("Delete") }
        assertEquals("", editor.text.toString())
    }

    @Test fun incompatibleEditorGetsDeleteKeyEvents() {
        connection.acceptCodePointDelete = false
        press("Delete")
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), connection.events.map { it.action })
        assertTrue(connection.events.all { it.keyCode == KeyEvent.KEYCODE_DEL })
    }

    @Test fun searchPerformsActionAndMultilineInsertsNewline() {
        startEditor(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH)
        type("hello"); press("Enter")
        assertEquals(listOf(EditorInfo.IME_ACTION_SEARCH), connection.actions)
        assertEquals("hello", editor.text.toString())
        startEditor(action = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        press("Enter")
        assertEquals("hello\n", editor.text.toString())
    }

    @Test fun refusedEditorActionFallsBackToNewline() {
        connection.acceptActions = false
        startEditor(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE)
        press("Enter")
        assertEquals("\n", editor.text.toString())
    }

    @Test fun numericAndPasswordEditorsStartInTheCorrectMode() {
        press("Shift"); press("Shift")
        startEditor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        type("1230")
        assertEquals("1230", editor.text.toString())
        assertNull(view.findViewWithTag<View>("Shift"))
        startEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        type("secret")
        assertEquals("1230secret", editor.text.toString())
    }

    @Test fun heldBackspaceRepeatsAndStopsWhenInputFinishes() {
        type("abcdef")
        val delete = view.findViewWithTag<View>("Delete")
        delete.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(510))
        assertTrue(editor.length() < 5)
        val remaining = editor.text.toString()
        service.onFinishInputView(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(remaining, editor.text.toString())
    }

    @Test fun noConnectionDoesNotCrashAnyTypingCommand() {
        ReflectionHelpers.setField(service, "mStartedInputConnection", null)
        type("abc "); press("Enter"); press("Delete")
        assertEquals("", editor.text.toString())
    }

    private class Speech(val listener: SpeechEngine.Listener) : SpeechEngine {
        var cancelled = false
        var destroyed = false
        var stopped = false
        override fun start(language: String) = Unit
        override fun stop() { stopped = true }
        override fun cancel() { cancelled = true }
        override fun destroy() { destroyed = true }
    }

    private fun startVoice(): Speech {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        lateinit var engine: Speech
        service.speechEngineFactory = SpeechEngineFactory { Speech(it).also { speech -> engine = speech } }
        press("Voice")
        return engine
    }

    @Test fun voiceFinalResultReplacesSelectionOnce() {
        connection.commitText("hello old", 1)
        editor.setSelection(6, 9)
        val speech = startVoice()
        speech.listener.result("world")
        speech.listener.result("duplicate")
        assertEquals("hello world", editor.text.toString())
        assertTrue(speech.destroyed)
    }

    @Test fun ordinaryTypingCancelsVoiceAndRejectsLateText() {
        val speech = startVoice()
        type("a")
        speech.listener.result("late voice")
        assertEquals("a", editor.text.toString())
        assertTrue(speech.cancelled)
    }

    @Test fun switchingEditorCancelsVoiceBeforeResult() {
        val speech = startVoice()
        startEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        speech.listener.result("private result")
        assertEquals("", editor.text.toString())
        assertFalse(view.findViewWithTag<View>("Voice").isEnabled)
        type("secret")
        assertEquals("secret", editor.text.toString())
    }

    @Test fun movingCursorCancelsVoiceAndRejectsLateResult() {
        val speech = startVoice()
        service.onUpdateSelection(0, 0, 1, 1, -1, -1)
        speech.listener.result("late")
        assertEquals("", editor.text.toString())
        assertTrue(speech.destroyed)
    }

    @Test fun cancelAndHideStopRecordingWithoutInsertion() {
        val first = startVoice()
        press("CancelVoice")
        first.listener.result("cancelled")
        val second = startVoice()
        service.onFinishInputView(true)
        second.listener.result("hidden")
        assertEquals("", editor.text.toString())
        assertTrue(second.destroyed)
    }

    @Test fun microphoneStopsListeningAndInsertsFinalResult() {
        val speech = startVoice()
        press("Voice")
        assertTrue(speech.stopped)
        speech.listener.result("dictated text")
        assertEquals("dictated text", editor.text.toString())
    }

    @Test fun missingPermissionOpensPermissionScreenWithoutStartingRecording() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO)
        service.speechEngineFactory = SpeechEngineFactory { fail("Recording must not start"); throw AssertionError() }
        press("Voice")
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(VoicePermissionActivity::class.java.name, intent.component!!.className)
        assertEquals("", editor.text.toString())
    }
}
