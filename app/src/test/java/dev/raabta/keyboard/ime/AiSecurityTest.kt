package dev.raabta.keyboard.ime

import dev.raabta.keyboard.tools.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URL
import java.io.ByteArrayOutputStream
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[28])
class AiSecurityTest {
    private class Transport(private val code:Int=200,private val response:String="{\"choices\":[{\"message\":{\"content\":\"Improved\"}}]}"):HttpsURLConnection(URL("https://example.org/v1/chat/completions")) {
        val sent=ByteArrayOutputStream()
        var disconnected=false
        override fun connect()=Unit
        override fun disconnect() { disconnected=true }
        override fun usingProxy()=false
        override fun getOutputStream()=sent
        override fun getResponseCode()=code
        override fun getInputStream()=response.byteInputStream()
        override fun getCipherSuite()="TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    @Test fun explicitRequestContainsOnlyDraftInstructionAndChosenModelAndAlwaysDisconnects() {
        val transport=Transport()
        val result=AiRequest { transport }.generate("https://example.org/v1/chat/completions","chosen-model","test-token","Rewrite in Urdu.","My draft")
        assertEquals("Improved",result)
        val payload=org.json.JSONObject(transport.sent.toString("UTF-8"))
        assertEquals("My draft",payload.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(2,payload.getJSONArray("messages").length())
        assertEquals("Bearer test-token",transport.getRequestProperty("Authorization"))
        assertFalse(transport.instanceFollowRedirects); assertTrue(transport.disconnected)
    }
    @Test fun redirectsAndOversizedResponsesFailWithoutFollowingOrReturningText() {
        for(transport in listOf(Transport(302),Transport(response="x".repeat(65537)))) {
            try { AiRequest { transport }.generate("https://example.org/v1/chat/completions","model","","Rewrite","Draft"); fail("Must reject") }
            catch (_:Exception) { assertTrue(transport.disconnected); assertFalse(transport.instanceFollowRedirects) }
        }
    }
    @Test fun cancelledRequestDoesNotOpenAnyConnection() {
        var opened=false
        val request=AiRequest { opened=true; Transport() }; request.cancel()
        try { request.generate("https://example.org/v1/chat/completions","model","","Rewrite","Draft"); fail("Must reject") }
        catch (_:IllegalStateException) { assertFalse(opened) }
    }
    @Test fun invalidHeaderTokenIsRejectedBeforeAnyConnection() {
        var opened=false
        try { AiRequest { opened=true; Transport() }.generate("https://example.org/v1/chat/completions","model","token\r\nInjected: header","Rewrite","Draft"); fail("Must reject") }
        catch (_:IllegalArgumentException) { assertFalse(opened) }
    }
    @Test fun changingEndpointDoesNotReuseExistingCredential() {
        val context=RuntimeEnvironment.getApplication()
        val store=context.getSharedPreferences("ai_connection",0)
        store.edit().clear().putString("endpoint","https://first.example/v1/chat/completions").putString("token","old-encrypted-value").commit()
        val settings=AiConnection(context)
        settings.save("https://second.example/v1/chat/completions","model",null)
        assertFalse(store.contains("token")); assertTrue(settings.ready())
        settings.clear(); assertFalse(settings.ready())
    }
}
