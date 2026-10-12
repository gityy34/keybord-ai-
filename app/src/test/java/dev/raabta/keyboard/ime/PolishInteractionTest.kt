package dev.raabta.keyboard.ime

import android.Manifest
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.View
import android.view.inputmethod.*
import android.widget.EditText
import android.widget.TextView
import dev.raabta.keyboard.tools.*
import dev.raabta.keyboard.ui.*
import dev.raabta.keyboard.voice.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[28])
class PolishInteractionTest {
    private lateinit var service:RaabtaInputMethodService
    private lateinit var field:EditText
    private lateinit var view:KeyboardView
    private lateinit var raw:BaseInputConnection
    @Before fun setup() {
        service=Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        listOf("keyboard_options","keyboard_preferences","ai_connection","voice_settings","keyboard_theme_import").forEach { service.getSharedPreferences(it,0).edit().clear().commit() }
        field=EditText(service)
        raw=object:BaseInputConnection(field,true) {
            override fun getEditable():Editable=field.editableText
            override fun performContextMenuAction(id:Int)=field.onTextContextMenuItem(id)
            override fun getExtractedText(request:ExtractedTextRequest?,flags:Int)=ExtractedText().apply { text=field.text; startOffset=0; partialStartOffset=-1; selectionStart=field.selectionStart; selectionEnd=field.selectionEnd }
        }
        ReflectionHelpers.setField(service,"mInputStarted",true); ReflectionHelpers.setField(service,"mStartedInputConnection",raw)
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT },false)
        view=service.onCreateInputView() as KeyboardView
    }
    @After fun close() { service.onDestroy() }
    private fun click(tag:String) { val child=view.findViewWithTag<View>(tag); assertNotNull(tag,child); assertTrue("Disabled $tag",child.isEnabled); assertTrue(child.performClick()) }
    @Test fun typingUndoRedoUsesBoundedHistoryAndNewEditInvalidatesRedo() {
        for(c in "hello") click("text:$c")
        click("Tools"); assertTrue(view.findViewWithTag<View>("settings:Undo").isEnabled); assertFalse(view.findViewWithTag<View>("settings:Redo").isEnabled)
        click("settings:Undo"); assertEquals("",field.text.toString()); assertEquals(0,field.selectionStart)
        click("settings:Redo"); assertEquals("hello",field.text.toString()); assertEquals(5,field.selectionStart)
        click("settings:Undo"); click("SettingsBack"); click("text:q"); click("Tools")
        assertFalse(view.findViewWithTag<View>("settings:Redo").isEnabled); assertEquals("q",field.text.toString())
    }
    @Test fun batchReplacementHasOneUndoAndExternalEditsAreNeverOverwritten() {
        field.setText("first wrong last"); field.setSelection(6,11)
        val tracked=service.currentInputConnection!!
        tracked.beginBatchEdit(); tracked.commitText("correct",1); tracked.endBatchEdit()
        click("Tools"); click("settings:Undo"); assertEquals("first wrong last",field.text.toString()); assertEquals(6,field.selectionStart); assertEquals(11,field.selectionEnd)
        click("settings:Redo"); assertEquals("first correct last",field.text.toString())
        field.setText("external change"); field.setSelection(3); click("settings:Undo")
        assertEquals("external change",field.text.toString()); assertEquals(3,field.selectionStart)
        assertFalse(view.findViewWithTag<View>("settings:Undo").isEnabled)
    }
    @Test fun historiesClearForPrivateFieldsAndLargeUnsupportedEditors() {
        click("text:a"); service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD },false)
        click("Tools"); assertFalse(view.findViewWithTag<View>("settings:Undo").isEnabled)
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT },false); click("SettingsBack")
        field.setText("x".repeat(5000)); field.setSelection(5000); click("text:a"); click("Tools")
        assertFalse(view.findViewWithTag<View>("settings:Undo").isEnabled); assertEquals(5001,field.length())
    }
    private fun voice():SpeechEngine.Listener {
        shadowOf(service).grantPermissions(Manifest.permission.RECORD_AUDIO)
        lateinit var events:SpeechEngine.Listener
        service.speechEngineFactory=SpeechEngineFactory { listener ->
            events=listener
            object:SpeechEngine { override fun start(language:String) { listener.ready() }; override fun stop()=Unit; override fun cancel()=Unit; override fun destroy()=Unit }
        }
        click("Voice"); assertEquals("Listening…",view.findViewWithTag<TextView>("VoiceStatus").text.toString()); return events
    }
    @Test fun voiceStartsDirectlyAndDeleteRemovesOnlyItsUnchangedCommittedText() {
        field.setText("before "); field.setSelection(field.length())
        val events=voice(); events.partial("spoken"); assertEquals("before ",field.text.toString())
        assertEquals("spoken",view.findViewWithTag<TextView>("VoiceTranscript").text.toString())
        click("VoiceStop"); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertEquals("before spoken",field.text.toString()); click("VoiceDelete")
        assertEquals("before ",field.text.toString()); assertEquals(7,field.selectionStart)
        assertEquals("",view.findViewWithTag<TextView>("VoiceTranscript").text.toString())
        assertEquals(View.GONE,view.findViewWithTag<View>("VoiceRecorder").visibility)
    }
    @Test fun cancelDropsVoiceDraftButDeleteNeverOverwritesLaterHostChanges() {
        val events=voice(); events.partial("accidental"); click("VoiceDismiss"); events.result("late")
        assertEquals("",field.text.toString())
        val next=voice(); next.partial("spoken"); click("VoiceStop"); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        field.setText("external"); field.setSelection(4); click("VoiceDelete"); assertEquals("external",field.text.toString()); assertEquals(4,field.selectionStart)
    }
    @Test fun importedThemeIsValidatedAndAffectsKeysAndToolPanel() {
        val json="""{"version":1,"name":"Custom blue","dark":true,"colors":{"background":"#080E1D","surface":"#131E34","text":"#EDF8FF","muted":"#AABED4","accent":"#63E5FF"}}"""
        ThemeImport.save(service,ThemeImport.parse(json)); assertEquals(KeyboardTheme.CUSTOM,KeyboardPreferences.load(service).theme)
        view=service.onCreateInputView() as KeyboardView; val colors=Palette.from(service)
        assertEquals(colors.onAccent,view.findViewWithTag<TextView>("Enter").currentTextColor)
        click("Tools"); click("settings:Theme"); assertNotNull(view.findViewWithTag<View>("ThemePicker")); assertNotNull(view.findViewWithTag<View>("ThemeImport"))
        for(value in listOf(json.replace("#EDF8FF","#080E1D"),json.replace("\"dark\":true","\"url\":\"https://evil.test\",\"dark\":true"),json.replace("#63E5FF","#0063E5FF"))) {
            try { ThemeImport.parse(value); fail("Unsafe/unreadable theme accepted") } catch (_:Exception) { }
        }
    }
    @Test fun pasteImmediatelyEnablesUndoWithoutReopeningTheEditingPanel() {
        service.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("test","copied"))
        click("Tools"); click("settings:Text editing"); assertFalse(view.findViewWithTag<View>("edit:undo").isEnabled)
        click("edit:paste"); assertEquals("copied",field.text.toString()); assertTrue(view.findViewWithTag<View>("edit:undo").isEnabled)
        click("edit:undo"); assertEquals("",field.text.toString()); assertTrue(view.findViewWithTag<View>("edit:redo").isEnabled)
    }

}
