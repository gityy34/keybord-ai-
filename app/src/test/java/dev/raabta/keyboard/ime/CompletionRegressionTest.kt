package dev.raabta.keyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.*
import android.widget.EditText
import dev.raabta.keyboard.tools.*
import dev.raabta.keyboard.ui.*
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
class CompletionRegressionTest {
    private lateinit var service:RaabtaInputMethodService
    private lateinit var field:EditText
    private lateinit var view:KeyboardView
    private lateinit var connection:BaseInputConnection
    private class Transport(val code:Int=200,val timedOut:Boolean=false):HttpsURLConnection(URL("https://example.org/v1/chat/completions")) {
        val sent=ByteArrayOutputStream(); var opened=0
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getOutputStream():ByteArrayOutputStream { opened++; sent.reset(); return sent }
        override fun getResponseCode()=code
        override fun getInputStream():java.io.InputStream {
            if(timedOut) throw java.net.SocketTimeoutException()
            return "{\"choices\":[{\"message\":{\"content\":\"I am going to the market tomorrow.\"}}]}".byteInputStream()
        }
        override fun getCipherSuite()="TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    @Before fun setup() {
        service=Robolectric.buildService(RaabtaInputMethodService::class.java).create().get()
        listOf("keyboard_options","keyboard_preferences","ai_connection").forEach { service.getSharedPreferences(it,0).edit().clear().commit() }
        PendingKeyboardTool.consume()
        field=EditText(service)
        connection=object:BaseInputConnection(field,true) {
            override fun getEditable():Editable=field.editableText
            override fun getExtractedText(request:ExtractedTextRequest?,flags:Int)=ExtractedText().apply { text=field.text; startOffset=0; partialStartOffset=-1; partialEndOffset=-1; selectionStart=field.selectionStart; selectionEnd=field.selectionEnd }
        }
        ReflectionHelpers.setField(service,"mInputStarted",true); ReflectionHelpers.setField(service,"mStartedInputConnection",connection)
        service.onStartInput(EditorInfo().apply { inputType=InputType.TYPE_CLASS_TEXT },false)
        view=service.onCreateInputView() as KeyboardView
    }
    @After fun close() { service.onDestroy(); PendingKeyboardTool.consume() }
    private fun click(tag:String) { val v=view.findViewWithTag<View>(tag); assertNotNull(tag,v); assertTrue(v.performClick()) }
    private fun awaitSuggestions() {
        val executor=ReflectionHelpers.getField<ThreadPoolExecutor>(service,"suggestionWorker")
        repeat(200) {
            if (executor.activeCount==0 && executor.queue.isEmpty()) return@repeat
            Thread.sleep(2)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun configure(fake:Transport) {
        AiConnection(service).save("https://example.org/v1/chat/completions","test-model",null)
        service.aiRequestFactory={ AiRequest { fake } }
    }
    private fun complete() { ReflectionHelpers.getField<ThreadPoolExecutor>(service,"mediaWorker").submit {}.get(5,TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idle() }
    @Test fun fastTouchesCommitEveryCharacterImmediatelyIncludingSpaceWithSmallMovement() {
        var expected=""
        for(c in "hello world hello world") {
            val key=view.findViewWithTag<View>(if(c==' ') "Space" else "text:$c")
            key.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20f,20f,0))
            if(c==' ') key.dispatchTouchEvent(MotionEvent.obtain(0,2,MotionEvent.ACTION_MOVE,29f,20f,0))
            key.dispatchTouchEvent(MotionEvent.obtain(0,4,MotionEvent.ACTION_UP,if(c==' ') 29f else 20f,20f,0))
            expected+=c; assertEquals(expected,field.text.toString())
        }
    }
    @Test fun slightMovementOnSingleLetterStillCommitsTapInsteadOfDroppingIt() {
        val key=view.findViewWithTag<View>("text:h")
        key.dispatchTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20f,20f,0))
        key.dispatchTouchEvent(MotionEvent.obtain(0,4,MotionEvent.ACTION_MOVE,38f,20f,0))
        key.dispatchTouchEvent(MotionEvent.obtain(0,8,MotionEvent.ACTION_UP,38f,20f,0))
        assertEquals("h",field.text.toString())
    }
    @Test fun sensitivityChangesTapThresholdAndResponseDelayDoesNotDelayTyping() {
        val options=KeyboardOptions(service)
        options.setNumber("typing_sensitivity",0); view=service.onCreateInputView() as KeyboardView
        val small=view.findViewWithTag<GestureKeyButton>("Space").tapSlop
        options.setNumber("typing_sensitivity",100); options.setNumber("suggestion_delay",200); view=service.onCreateInputView() as KeyboardView
        assertTrue(view.findViewWithTag<GestureKeyButton>("Space").tapSlop>small)
        click("text:h"); click("text:e"); assertEquals("he",field.text.toString())
        assertNull(view.findViewWithTag<View>("suggestion:heart"))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(201)); awaitSuggestions(); assertNotNull(view.findViewWithTag<View>("suggestion:heart"))
    }
    @Test fun clipboardTopSwitchClearsAndBlocksPreviewsUntilReenabled() {
        service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test","copied value"))
        click("Tools"); click("settings:Clipboard")
        val toggle=view.findViewWithTag<androidx.appcompat.widget.SwitchCompat>("ClipboardEnabled")
        assertTrue(toggle.isChecked); toggle.performClick()
        assertFalse(KeyboardOptions(service).enabled("clipboard_enabled"))
        val preview=ClipboardPreview(service); preview.refresh(); assertTrue(preview.candidates(true).isEmpty())
        view.findViewWithTag<androidx.appcompat.widget.SwitchCompat>("ClipboardEnabled").performClick(); preview.refresh()
        assertTrue(preview.candidates(false).any { it.text=="copied value" }); assertEquals("",field.text.toString())
    }
    @Test fun grammarUsesRealProviderResultAndReplacesCurrentCompletedSentenceOnly() {
        val fake=Transport(); configure(fake)
        field.setText("Earlier sentence. I am go to market tomorrow."); field.setSelection(field.length())
        click("Grammar"); complete()
        assertEquals(1,fake.opened); assertEquals("Earlier sentence. I am go to market tomorrow.",field.text.toString())
        val payload=org.json.JSONObject(fake.sent.toString("UTF-8"))
        assertEquals("I am go to market tomorrow.",payload.getJSONArray("messages").getJSONObject(1).getString("content"))
        click("tool:writing:replace:0"); assertEquals("Earlier sentence. I am going to the market tomorrow.",field.text.toString())
    }
    @Test fun insertKeepsSelectedOriginalAndCopyDoesNotModifyEditor() {
        configure(Transport()); field.setText("original"); field.selectAll()
        click("Grammar"); complete(); click("tool:writing:copy:0")
        assertEquals("original",field.text.toString()); assertTrue(service.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.contains("going"))
        click("tool:writing:insert:0"); assertEquals("originalI am going to the market tomorrow.",field.text.toString())
    }
    @Test fun currentSentenceCaptureInMiddleRejectsChangedCaretAndKeepsNeighbours() {
        field.setText("One. bad words here. Three."); field.setSelection(9)
        val target=EditorTarget.capture(connection)!!; assertEquals("bad words here.",target.original)
        field.setSelection(8); assertFalse(target.replace(connection,"Better.")); assertEquals("One. bad words here. Three.",field.text.toString())
        field.setSelection(9); assertTrue(target.replace(connection,"Better.")); assertEquals("One. Better. Three.",field.text.toString())
    }
    @Test fun providerErrorsAreMappedAndNormalTypingStillWorks() {
        for((code,problem) in listOf(401 to AiProblem.AUTH,404 to AiProblem.MODEL,429 to AiProblem.RATE,503 to AiProblem.UNAVAILABLE)) {
            val fake=Transport(code)
            try { AiRequest { fake }.generate("https://example.org/v1/chat/completions","model","key","Rewrite","Text"); fail("Should fail") }
            catch(e:AiFailure) { assertEquals(problem,e.problem) }
        }
        try { AiRequest { Transport(timedOut=true) }.generate("https://example.org/v1/chat/completions","model","key","Rewrite","Text"); fail("Should time out") }
        catch(e:AiFailure) { assertEquals(AiProblem.TIMEOUT,e.problem) }
        click("text:h"); click("Space"); assertEquals("h ",field.text.toString())
    }
    @Test fun deactivatedConnectionKeepsConfigurationAndDoesNotSendRequests() {
        val fake=Transport(); configure(fake); val saved=AiConnection(service); saved.setActive(false)
        assertTrue(saved.configured()); assertFalse(saved.ready()); assertEquals("test-model",saved.model())
        field.setText("text"); field.setSelection(4); click("Grammar"); complete(); assertEquals(0,fake.opened)
        saved.setActive(true); assertTrue(saved.ready())
    }
    @Test fun connectionTestUsesProviderAndSuccessfulStatusIsOnlyRecordedAfterResponse() {
        val fake=Transport(); configure(fake); assertTrue(AiConnection(service).status().contains("not verified"))
        click("Tools"); click("settings:Connections"); click("ConnectionTest"); complete()
        assertEquals(1,fake.opened); assertTrue(view.findViewWithTag<android.widget.TextView>("ConnectionSummary").text.contains("last test succeeded"))
        assertEquals("",field.text.toString())
    }
    @Test fun secureKeyEncryptionRoundTripNeverStoresFullPlaintextAndChangingProviderDropsIt() {
        val key=javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val saved=AiConnection(service) { key }
        saved.save("https://example.org/v1/chat/completions","test-model","test-secret-value-93X")
        assertEquals("test-secret-value-93X",saved.token()); assertTrue(saved.maskedKey().endsWith("-93X"))
        assertFalse(service.getSharedPreferences("ai_connection",0).all.values.any { it.toString().contains("test-secret-value-93X") })
        saved.save(ProviderKind.GROQ.endpoint,ProviderKind.GROQ.defaultModel,null)
        assertEquals("",saved.token()); assertEquals(ProviderKind.GROQ,saved.provider())
    }
    @Test fun cancellingQueuedAutomaticLanguageDetectionDoesNotSendTheDraft() {
        val fake=Transport(); configure(fake)
        val executor=ReflectionHelpers.getField<ThreadPoolExecutor>(service,"mediaWorker")
        val started=java.util.concurrent.CountDownLatch(1); val release=java.util.concurrent.CountDownLatch(1)
        executor.execute { started.countDown(); release.await(5,TimeUnit.SECONDS) }
        assertTrue(started.await(5,TimeUnit.SECONDS))
        try {
            field.setText("private draft"); field.setSelection(field.length()); click("Translation")
            ReflectionHelpers.callInstanceMethod<Unit>(service,"writingAction",ReflectionHelpers.ClassParameter.from(String::class.java,"source:auto"))
            click("tool:writing:run"); click("tool:writing:cancel")
        } finally { release.countDown() }
        complete(); assertEquals(0,fake.opened); assertEquals("private draft",field.text.toString())
    }
    @Test fun connectionsScreenPreservesSavedProviderUntilSaveAndReportsMissingKeyWithoutConnecting() {
        configure(Transport())
        val controller=Robolectric.buildActivity(dev.raabta.keyboard.setup.AiConnectionActivity::class.java).setup()
        val activity=controller.get(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
        val root=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        assertFalse(root.findViewWithTag<EditText>("ConnectionKey").isSaveEnabled)
        root.findViewWithTag<android.widget.Spinner>("ProviderChoice").setSelection(ProviderKind.GROQ.ordinal)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("https://example.org/v1/chat/completions",AiConnection(activity).endpoint())
        assertEquals(ProviderKind.GROQ.endpoint,root.findViewWithTag<EditText>("ConnectionEndpoint").text.toString())
        root.findViewWithTag<View>("ConnectionSave").performClick()
        assertEquals(ProviderKind.GROQ.endpoint,AiConnection(activity).endpoint())
        root.findViewWithTag<View>("ConnectionTest").performClick()
        ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(activity,"worker").submit {}.get(5,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(root.findViewWithTag<android.widget.TextView>("ConnectionStatus").text.contains("Authentication failed"))
        assertTrue(AiConnection(activity).status().contains("not verified"))
        root.findViewWithTag<View>("ConnectionDeactivate").performClick(); assertFalse(AiConnection(activity).active())
        root.findViewWithTag<View>("ConnectionActivate").performClick(); assertTrue(AiConnection(activity).active())
        root.findViewWithTag<View>("ConnectionDelete").performClick(); assertFalse(AiConnection(activity).configured())
        controller.pause().stop().destroy()
    }
}
