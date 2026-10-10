package dev.raabta.keyboard.ime

import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.*
import android.widget.*
import dev.raabta.keyboard.tools.*
import dev.raabta.keyboard.ui.KeyboardOptions
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
class WritingAndLearningTest {
    private lateinit var service:RaabtaInputMethodService
    private lateinit var field:EditText
    private lateinit var connection:BaseInputConnection
    private lateinit var view:KeyboardView
    @Before fun setup() {
        service=Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        listOf("keyboard_options","learned_words","ai_connection","voice_settings").forEach { service.getSharedPreferences(it,0).edit().clear().commit() }
        field=EditText(service)
        connection=object:BaseInputConnection(field,true) {
            override fun getEditable():Editable=field.editableText
            override fun getExtractedText(request:ExtractedTextRequest?,flags:Int)=ExtractedText().apply {
                text=field.text; startOffset=0; partialStartOffset=-1; partialEndOffset=-1
                selectionStart=field.selectionStart; selectionEnd=field.selectionEnd
            }
        }
        ReflectionHelpers.setField(service,"mInputStarted",true)
        ReflectionHelpers.setField(service,"mStartedInputConnection",connection)
        start(InputType.TYPE_CLASS_TEXT)
        view=service.onCreateInputView() as KeyboardView
    }
    @After fun close() { service.onDestroy() }
    private fun start(type:Int,private:Boolean=false) {
        service.onStartInput(EditorInfo().apply { inputType=type; if(private) imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING },false)
    }
    private fun click(tag:String) { val item=view.findViewWithTag<View>(tag); assertNotNull("Missing $tag",item); assertTrue(item.performClick()) }
    private fun type(text:String) { text.forEach { click(if(it==' ') "Space" else "text:$it") } }
    private fun update() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30)) }
    private fun toolbox(name:String) { click("Tools"); click("settings:$name") }
    @Test fun suggestionsHaveEqualWidthsAndBestCompletionInCentre() {
        type("he"); update()
        val strip=view.findViewWithTag<LinearLayout>("SuggestionStrip")
        view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST)); view.layout(0,0,view.measuredWidth,view.measuredHeight)
        val visible=(0 until strip.childCount).map { strip.getChildAt(it) }.filter { it.visibility==View.VISIBLE }
        assertEquals(3,visible.size)
        assertTrue(visible.all { kotlin.math.abs(it.width-visible.first().width)<=1 })
        assertEquals("suggestion:heart",visible[1].tag)
    }
    @Test fun singleEmojiSuggestionUsesFullWidthAndIsCentred() {
        type("love"); update()
        val strip=view.findViewWithTag<LinearLayout>("SuggestionStrip")
        val visible=(0 until strip.childCount).map { strip.getChildAt(it) }.filter { it.visibility==View.VISIBLE }
        // A single candidate is not stuck at the left edge.
        view.showCandidates(listOf(Candidate("❤️","❤️",emoji=true)))
        val button=strip.getChildAt(0) as Button
        assertEquals(0,button.layoutParams.width); assertEquals(1f,(button.layoutParams as LinearLayout.LayoutParams).weight)
        assertTrue(visible.isNotEmpty())
    }
    @Test fun twoThreeFourSuggestionSettingChangesActualStripCapacity() {
        for(count in 2..4) {
            KeyboardOptions(service).setNumber("suggestion_count",count)
            view=service.onCreateInputView() as KeyboardView
            view.showCandidates((1..6).map { Candidate("word$it","word$it") })
            val strip=view.findViewWithTag<LinearLayout>("SuggestionStrip")
            assertEquals(count,(0 until strip.childCount).count { strip.getChildAt(it).visibility==View.VISIBLE })
        }
    }
    @Test fun repeatedWordsBecomePrefixSuggestionsAfterThreeCompletedUsesAndSurviveReload() {
        type("heaaaa heaaaa "); assertTrue(LearnedWords(service).matches("he",0).isEmpty())
        type("heaaaa "); type("he"); update()
        assertNotNull(view.findViewWithTag<View>("suggestion:heaaaa"))
        assertEquals(listOf("heaaaa"),LearnedWords(service).matches("he",0))
        click("suggestion:heaaaa"); assertTrue(field.text.toString().endsWith("heaaaa"))
    }
    @Test fun urduWordsAreLearnedInUrduLanguageAndNotEnglish() {
        KeyboardOptions(service).setNumber("language",1); view=service.onCreateInputView() as KeyboardView
        type("کتاب کتاب کتاب "); assertEquals(listOf("کتاب"),LearnedWords(service).matches("کت",1))
        assertTrue(LearnedWords(service).matches("کت",0).isEmpty())
    }
    @Test fun pastedWordsAreNeverAutomaticallyLearned() {
        repeat(4) { connection.commitText("privateword",1); click("Space") }
        assertTrue(LearnedWords(service).learned().isEmpty())
    }
    @Test fun passwordPrivateEmailUrlAndDisabledLearningNeverPersistWords() {
        for(type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)) {
            start(type); repeat(3) { type("secretword ") }
        }
        start(InputType.TYPE_CLASS_TEXT,true); repeat(3) { type("secretword ") }
        start(InputType.TYPE_CLASS_TEXT); KeyboardOptions(service).set("learn_words",false); repeat(3) { type("secretword ") }
        assertTrue(LearnedWords(service).learned().isEmpty())
    }
    @Test fun clearingLearnedDataInOtherSettingsInstanceUpdatesRunningKeyboard() {
        type("heaaaa heaaaa heaaaa ")
        type("he"); update(); assertNotNull(view.findViewWithTag<View>("suggestion:heaaaa"))
        LearnedWords(service).clear(); click("Shift"); update()
        assertNull(view.findViewWithTag<View>("suggestion:heaaaa"))
    }
    @Test fun romanUrduHaiSuggestionReplacesOnlyRomanPrefixAndCanBeDisabled() {
        KeyboardOptions(service).setNumber("language",2); view=service.onCreateInputView() as KeyboardView
        type("hai"); update(); click("suggestion:ہے"); assertEquals("ہے",field.text.toString())
        assertTrue(LocalSuggestions.candidates("hai",emptyList(),true,false,false,false,false,language=2,roman=false).none { it.text=="ہے" })
    }
    @Test fun romanConversionPreservesNamesPunctuationAndUnknownWords() {
        assertEquals("آپ کیسے ہیں, Alex?",RomanUrdu.convert("aap kaise hain, Alex?"))
    }
    private fun touch(action:Int,x:Float)=MotionEvent.obtain(0,0,action,x*service.resources.displayMetrics.density,20f,0)
    @Test fun swipeDeleteHighlightsWordsThenDeletesOnReleaseWithoutInitialCharacterLoss() {
        connection.commitText("one two three",1)
        val key=view.findViewWithTag<View>("Delete")
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN,100f)); assertEquals("one two three",field.text.toString())
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE,50f)); assertEquals("two three",connection.getSelectedText(0).toString())
        assertEquals("one two three",field.text.toString())
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_UP,50f)); assertEquals("one ",field.text.toString())
    }
    @Test fun cancelSwipeKeepsAllTextAndRestoresOriginalCaret() {
        connection.commitText("one two three",1); val key=view.findViewWithTag<View>("Delete")
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN,100f)); key.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE,50f))
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_CANCEL,50f))
        assertEquals("one two three",field.text.toString()); assertEquals(field.length(),field.selectionStart)
    }
    @Test fun reverseSwipeToStartKeepsTextAndTapDeletesExactlyOneCharacter() {
        connection.commitText("one two",1); val key=view.findViewWithTag<View>("Delete")
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN,100f)); key.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE,50f)); key.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE,100f)); key.dispatchTouchEvent(touch(MotionEvent.ACTION_UP,100f))
        assertEquals("one two",field.text.toString())
        key.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN,20f)); key.dispatchTouchEvent(touch(MotionEvent.ACTION_UP,20f))
        assertEquals("one tw",field.text.toString())
    }
    @Test fun swipeNeverDeletesAfterHostTextChanges() {
        connection.commitText("one two",1); val deletion=DeleteSelection()
        assertTrue(deletion.begin(connection)); deletion.move(1)
        field.setText("new content"); field.setSelection(field.length()); deletion.finish()
        assertEquals("new content",field.text.toString())
    }
    @Test fun romanToolDraftDoesNotEditAppUntilInsert() {
        toolbox("Roman Urdu → Urdu"); type("aap kaise hain")
        assertEquals("",field.text.toString()); click("tool:writing:run")
        assertEquals("آپ کیسے ہیں",view.findViewWithTag<TextView>("ToolPreview").text.toString())
        click("tool:writing:insert"); assertEquals("آپ کیسے ہیں",field.text.toString())
    }
    private class Translation:TranslationEngine {
        var closed=false; var downloadCalls=0; var callback:((String?)->Unit)?=null
        override fun download(done:(Boolean)->Unit) { downloadCalls++; done(true) }
        override fun translate(text:String,done:(String?)->Unit) { callback=done }
        override fun close() { closed=true }
    }
    @Test fun translationUsesExplicitDownloadAndPreviewBeforeInsert() {
        val fake=Translation(); service.translationFactory={ _,_ -> fake }
        click("Translation"); assertEquals(0,fake.downloadCalls)
        click("tool:writing:download"); assertEquals(1,fake.downloadCalls)
        type("salam"); click("tool:writing:run"); fake.callback!!("Hello")
        assertEquals("",field.text.toString()); click("tool:writing:insert"); assertEquals("Hello",field.text.toString())
    }
    @Test fun lateTranslationCannotInsertAfterEditorChanges() {
        val fake=Translation(); service.translationFactory={ _,_ -> fake }
        click("Translation"); type("salam"); click("tool:writing:run")
        start(InputType.TYPE_CLASS_TEXT); fake.callback!!("late")
        assertEquals("",field.text.toString()); assertTrue(fake.closed)
    }
    @Test fun voiceTranslationWaitsForStopThenInsertsEnglishFromUrduWithoutLearningIt() {
        shadowOf(service).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val translation=Translation(); service.translationFactory={ _,_ -> translation }
        lateinit var listener:SpeechEngine.Listener
        var requested=""
        service.speechEngineFactory=SpeechEngineFactory { events ->
            listener=events
            object:SpeechEngine {
                override fun start(language:String) { requested=language }
                override fun stop()=Unit
                override fun cancel()=Unit
                override fun destroy()=Unit
            }
        }
        click("Translation"); click("tool:writing:voice")
        assertEquals(VoiceSettings.URDU,requested)
        listener.result("آپ کیسے ہیں")
        assertEquals("",field.text.toString())
        click("Voice")
        translation.callback!!("How are you?")
        assertEquals("How are you?",field.text.toString())
        assertTrue(LearnedWords(service).learned().isEmpty())
    }
    @Test fun editingDraftInvalidatesOldTranslationAndKeepsAppText() {
        val fake=Translation(); service.translationFactory={ _,_ -> fake }
        click("Translation"); type("salam"); click("tool:writing:run")
        type("a"); fake.callback!!("stale")
        assertEquals("salama",view.findViewWithTag<TextView>("ToolPreview").text.toString())
        assertEquals("",field.text.toString())
    }
    @Test fun writingResultRejectsChangedCaretAndLargeSelections() {
        connection.commitText("existing",1); toolbox("Roman Urdu → Urdu"); type("hai"); click("tool:writing:run")
        field.setSelection(0); click("tool:writing:insert"); assertEquals("existing",field.text.toString())
        field.setText("a".repeat(2001)); field.selectAll(); click("tool:writing:selection")
        assertTrue(view.findViewWithTag<TextView>("ToolStatus").text.contains("2,000"))
    }
    @Test fun aiWithoutProviderShowsConnectionRequirementAndDoesNotChangeEditor() {
        toolbox("AI Write"); type("hello"); click("tool:writing:run")
        assertTrue(view.findViewWithTag<TextView>("ToolStatus").text.contains("Connection")); assertEquals("",field.text.toString())
    }
    @Test fun passwordCannotOpenWritingTool() {
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD); click("Translation")
        assertNull(view.findViewWithTag<View>("WritingTool"))
    }
    @Test fun quickVoiceLanguageBarHasUrduEnglishAutoWithoutMostlyLabels() {
        click("Voice")
        assertNotNull(view.findViewWithTag<View>("VoiceLanguages"))
        click("voice-language:ur-PK"); assertEquals(VoiceSettings.URDU,VoiceSettings.language(service))
        click("voice-language:auto-en-ur"); assertEquals(VoiceSettings.MIXED,VoiceSettings.language(service))
    }
    @Test fun aiEndpointAndResponseValidationRejectUnsafeFormsAndEmptyContent() {
        assertTrue(AiConnection.validEndpoint("https://example.org/v1/chat/completions"))
        listOf("http://example.org/v1/chat/completions","https://user:pass@example.org/v1/chat/completions","https://127.0.0.1/chat/completions","https://example.org/chat/completions#x","https://example.org/chat/completions?token=secret").forEach { assertFalse(it,AiConnection.validEndpoint(it)) }
        assertEquals("Hello",AiRequest.parse("{\"choices\":[{\"message\":{\"content\":\"Hello\"}}]}"))
        try { AiRequest.parse("{\"choices\":[]}"); fail("Must reject invalid response") } catch (_:org.json.JSONException) { }
    }
}
