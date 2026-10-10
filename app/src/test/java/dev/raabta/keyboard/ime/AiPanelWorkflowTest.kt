package dev.raabta.keyboard.ime

import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import dev.raabta.keyboard.tools.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[28])
class AiPanelWorkflowTest {
    private lateinit var service:RaabtaInputMethodService
    private lateinit var field:EditText
    private lateinit var view:KeyboardView
    private val transport=Transport()
    private class Transport:HttpsURLConnection(URL("https://example.org/v1/chat/completions")) {
        val sent=ByteArrayOutputStream(); var opened=0
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getOutputStream():ByteArrayOutputStream { opened++; sent.reset(); return sent }
        override fun getResponseCode()=200
        override fun getInputStream()=org.json.JSONObject().put("choices",org.json.JSONArray().put(org.json.JSONObject().put("message",org.json.JSONObject().put("content","{\"variants\":[\"Hello there\",\"Hi there\",\"Good morning\"]}")))).toString().byteInputStream()
        override fun getCipherSuite()="TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    @Before fun setup() {
        service=Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        listOf("keyboard_options","keyboard_preferences","ai_connection").forEach { service.getSharedPreferences(it,0).edit().clear().commit() }
        field=EditText(service)
        val connection=object:BaseInputConnection(field,true) { override fun getEditable():Editable=field.editableText }
        ReflectionHelpers.setField(service,"mInputStarted",true)
        ReflectionHelpers.setField(service,"mStartedInputConnection",connection)
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT },false)
        AiConnection(service).save("https://example.org/v1/chat/completions","test-model",null)
        service.aiRequestFactory={ AiRequest { transport } }
        view=service.onCreateInputView() as KeyboardView
    }
    @After fun close() { service.onDestroy() }
    private fun click(tag:String) { val item=view.findViewWithTag<View>(tag); assertNotNull(tag,item); assertTrue(item.performClick()) }
    private fun type(value:String) { value.forEach { click(if(it==' ') "Space" else "text:$it") } }
    private fun complete() {
        ReflectionHelpers.getField<ThreadPoolExecutor>(service,"mediaWorker").submit {}.get(5,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun threeRealParsedPreviewsDoNotEditAppUntilChosenReplacement() {
        field.setText("old message"); field.selectAll()
        click("AI"); click("tool:writing:selection"); click("tool:writing:run"); complete()
        for(index in 0..2) assertNotNull(view.findViewWithTag<View>("ResultCard:$index"))
        assertEquals("old message",field.text.toString()); assertEquals(1,transport.opened)
        click("tool:writing:insert:2"); assertEquals("Good morning",field.text.toString())
        assertNull(view.findViewWithTag<View>("WritingTool"))
    }
    @Test fun toolAndLanguageSelectionDoNotSendUntilGenerateAndUseTheirOwnInstructions() {
        click("AI"); type("hello")
        view.findViewWithTag<View>("tool:writing:style:correct").performClick()
        assertEquals(0,transport.opened)
        ReflectionHelpers.callInstanceMethod<Unit>(service,"writingAction",ReflectionHelpers.ClassParameter.from(String::class.java,"target:ur"))
        click("tool:writing:run"); complete()
        val payload=org.json.JSONObject(transport.sent.toString("UTF-8"))
        val instruction=payload.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(instruction.contains("grammar")); assertTrue(instruction.contains("Urdu")); assertTrue(instruction.contains("variants"))
        assertEquals("hello",payload.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals("",field.text.toString())
    }
    @Test fun customInstructionPreservesDraftAndDoesNotSendOnUseTool() {
        click("AI"); type("hello"); click("tool:writing:custom"); type("be warm")
        click("tool:writing:run"); assertEquals(0,transport.opened)
        assertEquals("hello",view.findViewWithTag<TextView>("ToolPreview").text.toString())
        click("tool:writing:run"); complete()
        val messages=org.json.JSONObject(transport.sent.toString("UTF-8")).getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("be warm"))
        assertEquals("hello",messages.getJSONObject(1).getString("content"))
    }
    @Test fun changesPreviewHighlightsOriginalAndProposedWithoutTouchingEditor() {
        click("AI"); type("hello"); click("tool:writing:run"); complete()
        click("tool:writing:changes:0")
        val changes=view.findViewWithTag<TextView>("Changes:0").text
        assertTrue(changes.contains("Original:\nhello")); assertTrue(changes.contains("Proposed:\nHello there"))
        assertEquals("",field.text.toString())
        click("tool:writing:changes:0"); assertEquals("Hello there",view.findViewWithTag<TextView>("ResultText:0").text.toString())
    }
    @Test fun resultCardsCannotReplaceAfterCaretOrHostContentChanges() {
        field.setText("old message"); field.selectAll()
        click("AI"); click("tool:writing:selection"); click("tool:writing:run"); complete()
        field.setSelection(0); click("tool:writing:insert:0")
        assertEquals("old message",field.text.toString())
        assertTrue(view.findViewWithTag<TextView>("ToolStatus").text.contains("cursor or text changed"))
    }
    @Test fun allToolInstructionsStayBoundedAndVariantsAreRealDeduplicatedProviderText() {
        for(tool in AiTools.all) for(language in listOf("ur","en")) {
            val instruction=AiTools.instruction(tool.id,language,"x".repeat(500))
            assertTrue(instruction.length<=160); assertTrue(instruction.contains(if(language=="ur") "Urdu" else "English"))
        }
        assertEquals(listOf("one","two"),AiTools.variants("```json\n{\"variants\":[\"one\",\"one\",\"two\",\"four\"]}\n```"))
        assertEquals(listOf("Plain response"),AiTools.variants("Plain response"))
    }
}
