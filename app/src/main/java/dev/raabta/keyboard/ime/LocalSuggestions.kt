package dev.raabta.keyboard.ime

import java.util.Locale

data class Candidate(val label: String, val text: String, val replace: String = "", val emoji: Boolean = false, val imageUri:String? = null, val expiresAt:Long = Long.MAX_VALUE)

/** Small, bounded offline starter dictionary. No model download or text upload. */
object LocalSuggestions {
    val words = ("heart hear heard hearing heat health healthy heaven heavy hello help helpful happy happiness birthday birth love lovely good great morning night thanks thank " +
        "you your yes yesterday no now new next name need nice note please people person friend friends family fine food coffee tea water " +
        "home house how hope have has had want welcome world work working with will would what where when why " +
        "is are am was were we they their there this that the them then today tomorrow time talk take text typing type tool toolbar theme translate translation " +
        "can could come call care clear clipboard copy copied keyboard message meet meeting market make maybe move movable mobile " +
        "link line list like light live language learn learned learning later letter left right read write rewrite response replace " +
        "fast faster fix fixed grammar suggestion suggestions relevant recent old pinned press pressed space enter icon icons settings " +
        "school sorry smile sad sun sunny rain raining congratulations cat dog beautiful beauty because believe receive really " +
        "السلام سلام شکریہ محبت پیار خوش خوشی سالگرہ مبارک صبح رات اچھا اچھی آپ میں ہم گھر پانی چائے دوست " +
        "کیسے ہیں ہے نہیں ہاں کیا کیوں کہاں یہاں وہاں کرنا کرو کام بات زبان اردو انگریزی فون موبائل کیبورڈ مدد سوال جواب " +
        "khushi pyar pyaar mohabbat shukriya salam acha achi aap mein main hum ghar pani paani chai dost subah raat " +
        "kaise hain hai nahi haan kya kyun kahan yahan wahan karna karo kaam baat zaban urdu english madad sawal jawab").split(' ').distinct()
    val corrections = mapOf("teh" to "the", "helo" to "hello", "helllo" to "hello", "recieve" to "receive",
        "becuase" to "because", "thnaks" to "thanks", "freind" to "friend", "woudl" to "would", "adn" to "and")
    val grammar = mapOf("i is" to "I am", "you is" to "you are", "we is" to "we are", "they is" to "they are", "he are" to "he is", "she are" to "she is")
    private val emojiWords = mapOf(
        "love" to "❤️", "heart" to "❤️", "pyar" to "❤️", "pyaar" to "❤️", "محبت" to "❤️", "پیار" to "❤️",
        "happy" to "😊", "smile" to "😊", "khushi" to "😊", "خوشی" to "😊", "خوش" to "😊",
        "birthday" to "🎂", "سالگرہ" to "🎂", "sad" to "😢", "اداس" to "😢", "coffee" to "☕",
        "tea" to "☕", "chai" to "☕", "چائے" to "☕", "fire" to "🔥", "آگ" to "🔥", "cat" to "🐱",
        "dog" to "🐶", "thanks" to "🙏", "shukriya" to "🙏", "شکریہ" to "🙏", "congratulations" to "🎉", "مبارک" to "🎉")
    val popularEmoji = listOf("😂", "😘", "😀", "❤️", "😭", "😎", "🔥", "🎉", "😊", "🙏", "🎂", "☕", "👍", "💐", "🐱", "🐶")
    fun lastWord(text: String) = text.takeLastWhile { Character.isLetter(it) || Character.getType(it) == Character.NON_SPACING_MARK.toInt() || it == '\'' }
    fun candidates(before: String, dictionary: List<String>, wordsEnabled: Boolean, next: Boolean, emoji: Boolean,
                   grammarEnabled: Boolean, smartReplies: Boolean, learned: List<String> = emptyList(),
                   language: Int = 0, roman: Boolean = false): List<Candidate> {
        val word = lastWord(before)
        val lower = word.lowercase(Locale.ROOT)
        val result = mutableListOf<Candidate>()
        val finished = before.trimEnd().takeLastWhile { !it.isWhitespace() }.lowercase(Locale.ROOT)
        if (emoji) emojiWords[if (word.isEmpty()) finished else lower]?.let { result.add(Candidate(it, it, emoji = true)) }
        if (grammarEnabled) grammar.entries.firstOrNull { before.lowercase(Locale.ROOT).endsWith(it.key) }
            ?.let { result.add(Candidate("Grammar: ${it.value}", it.value, before.takeLast(it.key.length))) }
        if (wordsEnabled && word.isNotEmpty()) {
            if (roman && language == 2) RomanUrdu.candidates(word).forEach { result.add(Candidate(it, it, word)) }
            learned.filter { !it.equals(word, true) }.forEach { result.add(Candidate(it, matchCase(word, it), word)) }
            corrections[lower]?.let { result.add(Candidate(it, matchCase(word, it), word)) }
            (dictionary + words).distinct().asSequence().filter { it.startsWith(lower, true) && !it.equals(word, true) }
                .filter { language != 1 || it.any { c -> c in '\u0600'..'\u06ff' } }
                .sortedWith(compareBy<String> { priority(lower, it) }.thenBy { kotlin.math.abs(it.length-word.length) }.thenBy { it.length }.thenBy { it.lowercase(Locale.ROOT) })
                .take(6).forEach { result.add(Candidate(it, matchCase(word, it), word)) }
        }
        if (wordsEnabled && lower.length >= 3 && result.count { !it.emoji } < 2 && language != 1) {
            words.asSequence().filter { it.firstOrNull() == lower.firstOrNull() && kotlin.math.abs(it.length - lower.length) <= 2 }
                .map { it to distance(lower, it) }.filter { it.second <= 1 }.sortedBy { it.second }.take(2)
                .forEach { result.add(Candidate(it.first, matchCase(word, it.first), word)) }
        }
        if (word.isEmpty() && next) {
            val nextWords = when (finished) { "hello", "hi" -> listOf("world", "friend"); "good" -> listOf("morning", "night");
                "thank" -> listOf("you"); "آپ" -> listOf("کا", "کی"); "السلام" -> listOf("علیکم");
                "" -> if(language==1) listOf("میں","آپ","یہ") else if(language==2) listOf("main","aap","hai") else listOf("I","the","you")
                else -> emptyList() }
            nextWords.forEach { result.add(Candidate(it, it)) }
        }
        if (smartReplies && word.isEmpty() && finished in listOf("hello", "hi", "سلام"))
            listOf("Hello!", "How are you?", "وعلیکم السلام").forEach { result.add(Candidate(it, it)) }
        return result.distinctBy { it.text }.sortedBy { if(it.emoji) 0 else 1 }.take(8)
    }
    private fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, c ->
            val next = IntArray(b.length + 1); next[0] = i + 1
            b.forEachIndexed { j, d -> next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (c == d) 0 else 1) }
            row = next
        }
        return row.last()
    }
    fun matchCase(source: String, value: String) = if (source.firstOrNull()?.isUpperCase() == true) value.replaceFirstChar { it.uppercase() } else value
    private fun priority(prefix: String, value: String): Int {
        val common = when (prefix) {
            "h", "he" -> listOf("heart", "hello", "help", "hear")
            "li" -> listOf("link", "line", "like", "list")
            "la" -> listOf("language", "later", "learn")
            "su" -> listOf("suggestion", "suggestions")
            "th" -> listOf("the", "theme", "thanks")
            else -> emptyList()
        }
        val index = common.indexOfFirst { it.equals(value, true) }
        return if (index >= 0) index else 50
    }
    fun glide(path: String, dictionary: List<String>): String? {
        fun compact(s: String) = s.lowercase(Locale.ROOT).fold("") { acc, c -> if (acc.lastOrNull() == c) acc else acc + c }
        val p = compact(path)
        if (p.length < 2) return null
        return (dictionary + words).firstOrNull { compact(it) == p }
    }
}
