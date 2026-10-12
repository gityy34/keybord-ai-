package dev.raabta.keyboard.tools

import androidx.core.content.edit
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** No bundled key/provider. Optional user token is encrypted with a device Keystore key. */
class AiConnection(private val context: Context, private val keySource:(()->SecretKey)?=null) {
    private val store = context.getSharedPreferences("ai_connection", Context.MODE_PRIVATE)
    fun endpoint() = store.getString("endpoint", "").orEmpty()
    fun model() = store.getString("model", "").orEmpty()
    fun configured() = validEndpoint(endpoint()) && validModel(model())
    fun active() = store.getBoolean("active",true)
    fun ready() = configured() && active()
    fun provider() = if(endpoint()==ProviderKind.GROQ.endpoint) ProviderKind.GROQ else ProviderKind.CUSTOM
    fun setActive(value:Boolean) { store.edit { putBoolean("active",value) } }
    fun maskedKey() = store.getString("key_suffix",null)?.let { "••••••••$it" } ?: if(store.getString("token","").isNullOrEmpty()) "No key saved" else "Saved encrypted key"
    private fun fingerprint() = java.security.MessageDigest.getInstance("SHA-256").digest((endpoint()+"\u0000"+model()+"\u0000"+store.getString("token","")).toByteArray()).joinToString("") { "%02x".format(it) }
    fun status():String = if(!configured()) "Not configured" else if(!active()) "Deactivated" else if(store.getString("tested",null)==fingerprint()) "Connected · last test succeeded" else "Configured · not verified"
    fun recordTest(success:Boolean) { store.edit { if(success) putString("tested",fingerprint()) else remove("tested") } }
    fun token(): String {
        val data = store.getString("token", "") ?: return ""
        if (data.isEmpty()) return ""
        val parts = data.split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun save(endpoint: String, model: String, token: String?) {
        require(validEndpoint(endpoint) && validModel(model))
        val encrypted = token?.let {
            require(it.length <= 4096 && it.all { c -> c.code in 33..126 })
            if (it.isEmpty()) "" else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(it.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            }
        }
        store.edit {
            putString("endpoint", endpoint).putString("model", model)
            if (encrypted != null) { putString("token", encrypted); if(token.isNullOrEmpty()) remove("key_suffix") else putString("key_suffix",token.takeLast(4)) }
            else if (endpoint != this@AiConnection.endpoint()) { remove("token"); remove("key_suffix") }
            if(endpoint!=this@AiConnection.endpoint() || model!=this@AiConnection.model() || encrypted!=null) remove("tested")
        }
    }
    fun clear() {
        store.edit { clear() }
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) } } catch (_: Exception) { }
    }
    private fun key(): SecretKey {
        keySource?.let { return it() }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        private const val ALIAS = "raabta-ai-token-v1"
        fun validEndpoint(value: String): Boolean = try {
            val uri = URI(value)
            value.length <= 512 && uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null &&
                uri.rawFragment == null && uri.rawQuery == null && uri.host != "localhost" &&
                !uri.host.matches(Regex("[0-9.]+")) && !uri.host.contains(':') && uri.path.endsWith("/chat/completions")
        } catch (_: Exception) { false }
        fun validModel(value: String) = value.length in 1..128 && value.matches(Regex("[A-Za-z0-9._:/-]+"))
    }
}
