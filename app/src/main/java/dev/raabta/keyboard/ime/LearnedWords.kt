package dev.raabta.keyboard.ime

import androidx.core.content.edit
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bounded word frequencies only. Callers exclude private fields and untyped editor text. */
class LearnedWords(context: Context) {
    private val store = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
    private val entries = linkedMapOf<String, Entry>()
    data class Entry(val word: String, val language: Int, val count: Int)
    private var snapshot = ""
    init { reload() }
    private fun reload() {
        val value = store.getString("words", "[]").orEmpty()
        if (value == snapshot) return
        snapshot = value; entries.clear()
        try {
            val data = JSONArray(value)
            for (i in 0 until minOf(data.length(), 500)) {
                val item = data.getJSONObject(i)
                val entry = Entry(item.getString("word"), item.getInt("language"), item.getInt("count").coerceIn(1, 100))
                if (valid(entry.word) && entry.language in 0..2) entries[key(entry.word, entry.language)] = entry
            }
        } catch (_: org.json.JSONException) { entries.clear() }
    }
    fun record(word: String, language: Int) {
        reload()
        if (!valid(word) || language !in 0..2) return
        val key = key(word, language)
        val old = entries.remove(key)
        entries[key] = Entry(old?.word ?: word, language, ((old?.count ?: 0) + 1).coerceAtMost(100))
        while (entries.size > 500) entries.remove(entries.keys.first())
        save()
    }
    fun matches(prefix: String, language: Int): List<String> {
        reload()
        return entries.values.asSequence()
        .filter { it.language == language && it.count >= 3 && it.word.startsWith(prefix, true) }
        .sortedByDescending { it.count }.map { it.word }.take(8).toList()
    }
    fun learned(): List<Entry> { reload(); return entries.values.filter { it.count >= 3 }.sortedByDescending { it.count } }
    fun remove(word: String, language: Int) { entries.remove(key(word, language)); save() }
    fun clear() { entries.clear(); store.edit { clear() } }
    private fun save() {
        val data = JSONArray()
        entries.values.forEach { data.put(JSONObject().put("word", it.word).put("language", it.language).put("count", it.count)) }
        snapshot = data.toString(); store.edit { putString("words", snapshot) }
    }
    private fun key(word: String, language: Int) = "$language:${word.lowercase(Locale.ROOT)}"
    companion object {
        fun valid(word: String) = word.length in 2..40 && word.all {
            it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt()
        }
    }
}
