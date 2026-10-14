package dev.raabta.keyboard.ime

import androidx.core.content.edit
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bounded local word frequencies, recency and explicit selections. Callers exclude private fields and untyped editor text. */
class LearnedWords(context: Context) {
    private val store = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
    private val entries = linkedMapOf<String, Entry>()
    data class Entry(val word: String, val language: Int, val count: Int, val lastUsed: Long = 0L, val selected: Int = 0)
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
                val entry = Entry(item.getString("word"), item.getInt("language"), item.getInt("count").coerceIn(1, 100),
                    item.optLong("lastUsed", 0L), item.optInt("selected", 0).coerceIn(0, 100))
                if (valid(entry.word) && entry.language in 0..2) entries[key(entry.word, entry.language)] = entry
            }
        } catch (_: org.json.JSONException) { entries.clear() }
    }
    @Synchronized
    fun record(word: String, language: Int, selected: Boolean = false) {
        reload()
        if (!valid(word) || language !in 0..2) return
        val key = key(word, language)
        val old = entries.remove(key)
        entries[key] = Entry(old?.word ?: word, language, ((old?.count ?: 0) + 1).coerceAtMost(100),
            System.currentTimeMillis(), ((old?.selected ?: 0) + if(selected) 1 else 0).coerceAtMost(100))
        while (entries.size > 500) entries.remove(entries.keys.first())
        save()
    }
    @Synchronized
    fun matches(prefix: String, language: Int): List<String> {
        reload()
        val now=System.currentTimeMillis()
        val normalized=if(language==2) LocalSuggestions.romanKey(prefix) else prefix.lowercase(Locale.ROOT)
        return entries.values.asSequence()
            .filter { it.language == language && (it.count >= 3 || it.selected > 0) }
            .filter { it.word.startsWith(prefix, true) || (language==2 && LocalSuggestions.romanKey(it.word).startsWith(normalized)) }
            .sortedByDescending { score(it, now, prefix, normalized) }.map { it.word }.take(8).toList()
    }
    @Synchronized
    fun learned(): List<Entry> { reload(); val now=System.currentTimeMillis(); return entries.values.filter { it.count >= 3 || it.selected > 0 }.sortedByDescending { score(it, now, "", "") } }
    @Synchronized
    fun remove(word: String, language: Int) { entries.remove(key(word, language)); save() }
    @Synchronized
    fun clear() { entries.clear(); store.edit { clear() } }
    private fun score(entry: Entry, now: Long, prefix: String, normalized: String): Double {
        val recencyDays=((now-entry.lastUsed).coerceAtLeast(0L)/86_400_000.0)
        val recency=1.0/(1.0+recencyDays)
        val exactPrefix=if(entry.word.startsWith(prefix,true)) 2.0 else 0.0
        val roman=if(normalized.isNotEmpty() && LocalSuggestions.romanKey(entry.word).startsWith(normalized)) 1.0 else 0.0
        return entry.count*2.0 + entry.selected*4.0 + recency*3.0 + exactPrefix + roman
    }
    private fun save() {
        val data = JSONArray()
        entries.values.forEach { data.put(JSONObject().put("word", it.word).put("language", it.language).put("count", it.count).put("lastUsed", it.lastUsed).put("selected", it.selected)) }
        snapshot = data.toString(); store.edit { putString("words", snapshot) }
    }
    private fun key(word: String, language: Int) = "$language:${word.lowercase(Locale.ROOT)}"
    companion object {
        fun valid(word: String) = word.length in 2..40 && word.all {
            it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt()
        }
    }
}
