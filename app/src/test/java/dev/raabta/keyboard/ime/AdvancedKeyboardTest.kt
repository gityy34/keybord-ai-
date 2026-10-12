package dev.raabta.keyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.EditText
import android.widget.Switch
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.setup.OptionalPermissionActivity
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
class AdvancedKeyboardTest {
    private lateinit var service:RaabtaInputMethodService
    private lateinit var field:EditText
    private lateinit var connection:BaseInputConnection
    private lateinit var view:KeyboardView
    @Before fun setup() {
        service=Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        service.getSharedPreferences("keyboard_options",0).edit().clear().commit()
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
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE },false)
        view=service.onCreateInputView() as KeyboardView
    }
    @After fun close() { service.onDestroy() }
    private fun click(tag:String) { if(tag=="Settings") { view.findViewWithTag<View>("Tools").performClick(); view.findViewWithTag<View>("settings:Settings").performClick(); return }; val v=view.findViewWithTag<View>(tag); assertNotNull("Missing $tag",v); assertTrue(v.performClick()) }
    private fun type(text:String) { text.forEach { click(if(it==' ') "Space" else "text:$it") } }
    private fun update() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60)) }
    private fun fullSettings():dev.raabta.keyboard.setup.KeyboardSettingsActivity {
        click("Settings")
        val intent=shadowOf(service).nextStartedActivity
        assertEquals(dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java.name,intent.component!!.className)
        assertNull(view.findViewWithTag<View>("SettingsPanel"))
        return Robolectric.buildActivity(dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java,intent).setup().get()
    }
    private fun returnToKeyboard() {
        service.onStartInputView(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE },false)
        view=ReflectionHelpers.getField(service,"keyboard")
    }
    @Test fun gearOpensFullSettingsWithCategoriesAndReturningPreservesEditor() {
        type("hello"); val settings=fullSettings()
        settings.findViewById<View>(android.R.id.content).findViewWithTag<View>("settings:Preferences").performClick()
        assertNotNull(settings.findViewById<View>(android.R.id.content).findViewWithTag<View>("option:cursor_slide"))
        settings.finish(); returnToKeyboard()
        type(" world"); assertEquals("hello world",field.text.toString())
    }
    @Test fun fullSettingsToolActionReturnsToKeyboardAndOpensRequestedTool() {
        val settings=fullSettings()
        val root=settings.findViewById<View>(android.R.id.content)
        root.findViewWithTag<View>("settings:Writing tools").performClick()
        root.findViewWithTag<View>("settings:Open translation").performClick()
        assertTrue(settings.isFinishing)
        returnToKeyboard()
        assertNotNull(view.findViewWithTag<View>("WritingTool"))
        assertNotNull(view.findViewWithTag<View>("tool:writing:download"))
        assertEquals("",field.text.toString())
    }
    @Test fun emojiSearchIsLocalAndOnlyPickedEmojiEntersTheHostEditor() {
        type("message"); click("Emoji"); click("EmojiSearch"); type("love")
        assertEquals("message",field.text.toString())
        click("emoji:❤️"); assertEquals("message ❤️",field.text.toString())
        assertTrue(dev.raabta.keyboard.ui.EmojiPanel.search("محبت").contains("❤️"))
        click("EmojiBack"); type(" hello"); assertEquals("message ❤️ hello",field.text.toString())
    }
    @Test fun emojiSuggestedForUrduEnglishAndRomanUrduWithoutReplacingWord() {
        type("love"); update(); click("suggestion:❤️")
        assertEquals("love ❤️",field.text.toString())
        assertEquals(listOf("❤️"),KeyboardOptions(service).emoji())
        for(word in listOf("pyar","محبت")) assertTrue(LocalSuggestions.candidates(word,emptyList(),false,false,true,false,false).any { it.text=="❤️" })
    }
    @Test fun wordSuggestionReplacesOnlyCurrentPrefix() {
        type("hel"); update(); click("suggestion:hello"); assertEquals("hello",field.text.toString())
    }
    @Test fun staleSuggestionCannotReplaceChangedSelection() {
        type("hel"); update()
        field.setSelection(0)
        click("suggestion:hello")
        assertEquals("hel",field.text.toString())
    }
    @Test fun passwordsHaveNoWordEmojiOrClipboardSuggestions() {
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD },false)
        type("love"); update()
        assertNull(view.findViewWithTag<View>("suggestion:❤️"))
        assertNull(view.findViewWithTag<View>("suggestion:lovely"))
    }
    @Test fun cursorMovementUsesGraphemeBoundariesAndCanReverse() {
        connection.commitText("a👨‍👩‍👧‍👦ب",1)
        CursorNavigation.move(connection,-1)
        assertEquals(field.length()-1,field.selectionStart)
        CursorNavigation.move(connection,-1)
        assertEquals(1,field.selectionStart)
        CursorNavigation.move(connection,1)
        assertEquals(field.length()-1,field.selectionStart)
    }
    @Test fun slidingSpaceMovesCursorMultipleStepsWithoutInsertingSpace() {
        connection.commitText("abcdef",1)
        val space=view.findViewWithTag<View>("Space")
        val density=service.resources.displayMetrics.density
        fun event(action:Int,x:Float)=MotionEvent.obtain(0,0,action,x*density,20f,0)
        space.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN,80f))
        space.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,56f))
        space.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,32f))
        space.dispatchTouchEvent(event(MotionEvent.ACTION_UP,32f))
        assertEquals("abcdef",field.text.toString()); assertEquals(2,field.selectionStart)
    }
    @Test fun regularSpaceTapStillCommitsOneSpace() { click("Space"); assertEquals(" ",field.text.toString()) }
    @Test fun doubleSpaceAndAutoCorrectionAreRealEditsWhenEnabled() {
        val options=KeyboardOptions(service); options.set("double_space",true); options.set("auto_correction",true)
        type("teh "); assertEquals("the ",field.text.toString())
        click("Space"); assertEquals("the. ",field.text.toString())
    }
    @Test fun clipboardPreviewExtractsItemsAndSurvivesBriefKeyboardPause() {
        val clipboard=service.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Test","Email a@example.com or call +12345678901"))
        val preview=ClipboardPreview(service); preview.refresh()
        assertTrue(preview.candidates(true).any { it.text=="a@example.com" })
        assertTrue(preview.candidates(true).any { it.text=="+12345678901" })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(121))
        assertTrue(preview.candidates(true).any { it.text=="a@example.com" })
    }
    @Test fun emojiAndSuggestionControlsPersistFromFullSettings() {
        val settings=fullSettings()
        val root=settings.findViewById<View>(android.R.id.content)
        root.findViewWithTag<View>("settings:Emojis, stickers and GIFs").performClick()
        root.findViewWithTag<Switch>("option:emoji_row").isChecked=true
        assertTrue(KeyboardOptions(service).enabled("emoji_row"))
        settings.finish(); returnToKeyboard()
        assertNotNull(view.findViewWithTag<View>("text:😂"))
    }
    @Test fun deniedOptionalContactPermissionDisablesTheFeature() {
        KeyboardOptions(service).set("contacts",true)
        val activity=Robolectric.buildActivity(OptionalPermissionActivity::class.java,Intent().putExtra("setting","contacts")).create().get()
        activity.onRequestPermissionsResult(101,arrayOf(android.Manifest.permission.READ_CONTACTS),intArrayOf(PackageManager.PERMISSION_DENIED))
        assertFalse(KeyboardOptions(service).enabled("contacts")); assertTrue(activity.isFinishing)
    }
    @Test fun heldSymbolReleasesWithoutAlsoTypingTheLetter() {
        val key=view.findViewWithTag<View>("text:q")
        key.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20f,20f,0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450))
        key.dispatchTouchEvent(MotionEvent.obtain(0,450,MotionEvent.ACTION_UP,20f,20f,0))
        assertEquals("%",field.text.toString())
        view.findViewWithTag<View>("text:m").performLongClick()
        assertEquals("%?",field.text.toString())
    }
    @Test fun dictionaryAdditionOnlyHappensOnExplicitAction() {
        type("hello"); assertTrue(KeyboardOptions(service).dictionary().isEmpty())
        view.showSettings("Dictionary"); click("settings:Add current word")
        assertEquals(listOf("hello"),KeyboardOptions(service).dictionary())
    }
    @Test fun settingsOpeningCancelsHeldSymbols() {
        view.findViewWithTag<View>("text:q").dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,10f,10f,0))
        click("Settings"); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("",field.text.toString())
    }
    @Test fun flickSymbolInsertsOnlyTheSymbol() {
        KeyboardOptions(service).set("flick_symbols",true)
        view=service.onCreateInputView() as KeyboardView
        val key=view.findViewWithTag<View>("text:q")
        val distance=60f*service.resources.displayMetrics.density
        key.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20f,20f,0))
        key.dispatchTouchEvent(MotionEvent.obtain(0,50,MotionEvent.ACTION_MOVE,20f,20f-distance,0))
        key.dispatchTouchEvent(MotionEvent.obtain(0,60,MotionEvent.ACTION_UP,20f,20f-distance,0))
        assertEquals("%",field.text.toString())
    }
    @Test fun emojiAndCommaKeysCanBeHiddenThroughPreferences() {
        val options=KeyboardOptions(service); options.set("emoji_key",false); options.set("comma_key",false)
        view=service.onCreateInputView() as KeyboardView
        assertNull(view.findViewWithTag<View>("Emoji")); assertNull(view.findViewWithTag<View>("text:,"))
        type("hello"); assertEquals("hello",field.text.toString())
    }
    @Test fun urduLayoutTypesNativeCharactersAndHeldVariant() {
        KeyboardOptions(service).setNumber("language",1)
        view=service.onCreateInputView() as KeyboardView
        click("text:ا"); click("text:ذ")
        assertEquals("اذ",field.text.toString())
        assertTrue(view.findViewWithTag<View>("text:ا").performLongClick())
        assertEquals("اذآ",field.text.toString())
    }
    @Test fun toolboxIncludesEditingAndFloatingControls() {
        click("Tools")
        assertNotNull(view.findViewWithTag<View>("settings:Text editing"))
        assertNotNull(view.findViewWithTag<View>("settings:Floating"))
        click("settings:Text editing")
        assertNull(shadowOf(service).nextStartedActivity)
        assertEquals("",field.text.toString())
    }
    @Test fun offlineGlideRecognisesDictionaryPathsAndRejectsUnknownPaths() {
        assertEquals("hello",LocalSuggestions.glide("helo",emptyList()))
        assertNull(LocalSuggestions.glide("qxz",emptyList()))
    }

    @Test fun changingNumberRowInFullSettingsPreservesEditorAndUpdatesKeyboardOnReturn() {
        type("hello"); val settings=fullSettings()
        val root=settings.findViewById<View>(android.R.id.content)
        root.findViewWithTag<View>("settings:Preferences").performClick()
        root.findViewWithTag<Switch>("option:number_row").isChecked=true
        settings.finish(); returnToKeyboard()
        click("text:1"); assertEquals("hello1",field.text.toString())
    }
    @Test @Config(qualifiers="w600dp-h900dp") fun floatingKeyboardRemainsWithinWindowAndCanBeDragged() {
        KeyboardOptions(service).set("floating",true)
        view=service.onCreateInputView() as KeyboardView
        view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.AT_MOST))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
        val original=view.contentBounds()
        val handle=findHandle(view)!!
        handle.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,0f,0f,0))
        handle.dispatchTouchEvent(MotionEvent.obtain(0,20,MotionEvent.ACTION_MOVE,100f,-100f,0))
        handle.dispatchTouchEvent(MotionEvent.obtain(0,30,MotionEvent.ACTION_UP,100f,-100f,0))
        val moved=view.contentBounds()
        assertTrue(view.floating); assertTrue(original!=moved)
        assertTrue(moved.left>=0 && moved.right<=view.width && moved.top>=0 && moved.bottom<=view.height)
    }
    private fun findHandle(root:View):DragHandle? {
        if(root is DragHandle) return root
        if(root is android.view.ViewGroup) for(i in 0 until root.childCount) findHandle(root.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun androidSettingsEntryShowsCategoriesRatherThanEnableAndTestSetup() {
        val activity=Robolectric.buildActivity(dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java).setup().get()
        assertNotNull(activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("settings:Preferences"))
        assertNull(activity.findViewById<View>(dev.raabta.keyboard.R.id.test_message))
        activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("settings:Preferences").performClick()
        assertNotNull(activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("option:popup"))
        activity.finish()
    }
    @Test fun grammarCheckingMarksKnownRuleWithoutChangingText() {
        KeyboardOptions(service).set("grammar_check",true)
        type("i is ")
        assertEquals("i is ",field.text.toString())
        assertTrue(field.text.getSpans(0,field.length(),android.text.style.UnderlineSpan::class.java).isNotEmpty())
    }

    @Test fun emojiSizeChangesRenderedFastAccessKeys() {
        val options=KeyboardOptions(service); options.set("emoji_row",true); options.setNumber("emoji_size",38)
        view=service.onCreateInputView() as KeyboardView
        assertEquals(38f,view.findViewWithTag<android.widget.TextView>("text:😂").textSize/service.resources.displayMetrics.scaledDensity,0.01f)
    }

}
