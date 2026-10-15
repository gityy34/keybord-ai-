package dev.raabta.keyboard.ime

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedClip(val id: String, val text: String, val copiedAt: Long, val pinned: Boolean = false)
enum class ClipSection { RECENT, OLD, PINNED }

/** Bounded encrypted text history. No URI, password field, or sensitive clipboard is captured. */
class ClipboardHistory @JvmOverloads constructor(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val keySource: (() -> SecretKey)? = null,
) {
    private val store = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    private val clips = read().toMutableList()

    fun items(section: ClipSection): List<SavedClip> {
        expire()
        val ordered = clips.sortedByDescending { it.copiedAt }
        val unpinned = ordered.filterNot(SavedClip::pinned)
        return when (section) {
            ClipSection.RECENT -> unpinned.filter { clock() - it.copiedAt < RECENT_MILLIS }.take(6)
            ClipSection.OLD -> unpinned.filter { it !in itemsRecent(unpinned) }.take(20)
            ClipSection.PINNED -> ordered.filter(SavedClip::pinned)
        }
    }

    private fun itemsRecent(unpinned: List<SavedClip>) = unpinned.filter { clock() - it.copiedAt < RECENT_MILLIS }.take(6)
    fun latest(): SavedClip? = (items(ClipSection.RECENT) + items(ClipSection.OLD)).maxByOrNull(SavedClip::copiedAt)
    fun find(id: String): SavedClip? {
        expire()
        return clips.firstOrNull { it.id == id }
    }

    /** A duplicate copied again becomes the newest item. Pinned content stays pinned. */
    fun add(text: String): Boolean {
        if (text.isBlank()) return false
        expire()
        val value = text.take(MAX_CHARS)
        val already = clips.firstOrNull { it.text == value }
        clips.removeAll { it.text == value }
        clips.add(SavedClip(already?.id ?: id(), value, clock(), already?.pinned ?: false))
        trim()
        // Keystore may be temporarily unavailable. Keep the current IME session usable,
        // but report that the copy could not be saved across process restarts.
        return persist()
    }
    fun pin(id: String, value: Boolean): Boolean = change(id) { it.copy(pinned = value) }
    fun delete(id: String): Boolean {
        val previous = clips.toList()
        if (!clips.removeAll { it.id == id }) return false
        return if (persist()) true else { clips.clear(); clips.addAll(previous); false }
    }
    fun clear(section: ClipSection): Boolean {
        val previous = clips.toList()
        val ids = items(section).map(SavedClip::id).toSet()
        clips.removeAll { it.id in ids }
        return if (persist()) true else { clips.clear(); clips.addAll(previous); false }
    }
    fun clearUnpinned(): Boolean {
        val previous = clips.toList()
        clips.removeAll { !it.pinned }
        return if (persist()) true else { clips.clear(); clips.addAll(previous); false }
    }
    private fun change(id: String, transform: (SavedClip) -> SavedClip): Boolean {
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return false
        val previous = clips.toList()
        clips[index] = transform(clips[index])
        trim()
        return if (persist()) true else { clips.clear(); clips.addAll(previous); false }
    }
    private fun trim() {
        val unpinned = clips.filterNot(SavedClip::pinned).sortedByDescending(SavedClip::copiedAt)
        val keep = unpinned.take(26).map(SavedClip::id).toSet()
        clips.removeAll { !it.pinned && it.id !in keep }
    }
    private fun expire() {
        if (clips.removeAll { !it.pinned && (clock() < it.copiedAt || clock() - it.copiedAt >= OLD_MILLIS) }) persist()
    }
    private fun id() = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private fun persist(): Boolean = try {
        val rows = JSONArray()
        clips.forEach { rows.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.copiedAt).put("pin", it.pinned)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(rows.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        store.edit(commit = true) { putString("entries", value) }
        true
    } catch (_: Exception) { false }
    private fun read(): List<SavedClip> = try {
        val value = store.getString("entries", null) ?: return emptyList()
        val parts = value.split(':'); require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val rows = JSONArray(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            SavedClip(row.getString("id"), row.getString("text"), row.getLong("time"), row.getBoolean("pin"))
        }.filter { it.text.isNotBlank() && it.text.length <= MAX_CHARS && (it.pinned || clock() in it.copiedAt until it.copiedAt + OLD_MILLIS) }
    } catch (_: Exception) { emptyList() }
    private fun key(): SecretKey {
        keySource?.let { return it() }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        const val RECENT_MILLIS = 8 * 60 * 60 * 1000L
        const val OLD_MILLIS = 24 * 60 * 60 * 1000L
        private const val MAX_CHARS = 8192
        private const val KEY_ALIAS = "raabta-clipboard-history-v1"
    }
}
