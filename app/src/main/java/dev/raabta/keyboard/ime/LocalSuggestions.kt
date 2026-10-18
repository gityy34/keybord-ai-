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
        "kaise hain hai nahi haan kya kia kyaa kyun kahan yahan wahan karna karo kaam baat zaban urdu english madad sawal jawab").split(' ').distinct()
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
            result.add(Candidate(word, word, word))
            if (roman && language == 2) RomanUrdu.candidates(word).forEach { result.add(Candidate(it, it, word)) }
            learned.filter { !it.equals(word, true) }.forEach { result.add(Candidate(it, matchCase(word, it), word)) }
            corrections[lower]?.let { result.add(Candidate(it, matchCase(word, it), word)) }
            val allWords=(dictionary + words).distinct()
            allWords.asSequence().filter {
                val matchesPrefix = if (language == 2 && roman) {
                    it.startsWith(word, true) || romanKey(it).startsWith(romanKey(word))
                } else it.startsWith(lower, true)
                matchesPrefix && !it.equals(word, true)
            }
                .filter { language != 1 || it.any { c -> c in '\u0600'..'\u06ff' } }
                .sortedWith(compareBy<String> { priority(lower, it) }.thenBy { romanPenalty(lower,it,language,roman) }.thenBy { kotlin.math.abs(it.length-word.length) }.thenBy { it.length }.thenBy { it.lowercase(Locale.ROOT) })
                .take(8).forEach { result.add(Candidate(it, matchCase(word, it), word)) }
        }
        if (wordsEnabled && lower.length >= 3 && result.count { !it.emoji } < 3 && language != 1) {
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
        return result.distinctBy { it.text }.take(8)
    }
    fun glideCandidates(path: String, dictionary: List<String>, learned: List<String> = emptyList(), language: Int = 0, roman: Boolean = false): List<Candidate> {
        val p = compact(path)
        if (p.length < 2) return emptyList()
        val pool=(dictionary + learned + words).distinct().asSequence()
            .filter { language != 1 || it.any { c -> c in '\u0600'..'\u06ff' } }
            .mapNotNull { word ->
                val c=compact(word)
                val starts=if(c.firstOrNull()==p.firstOrNull()) 0 else 5
                val ends=if(c.lastOrNull()==p.lastOrNull()) 0 else 2
                val order=orderedPenalty(p,c)
                if(order>=99) null else word to (starts+ends+order+distance(p,c)+romanPenalty(p,word,language,roman))
            }.sortedWith(compareBy<Pair<String,Int>> { it.second }.thenBy { kotlin.math.abs(compact(it.first).length-p.length) }.thenBy { it.first.length })
            .take(6).map { Candidate(it.first, it.first, "") }.toList()
        return pool
    }
    fun glide(path: String, dictionary: List<String>): String? = glideCandidates(path,dictionary).firstOrNull()?.text
    private fun compact(s: String) = s.lowercase(Locale.ROOT).fold(StringBuilder()) { acc, c -> if (acc.lastOrNull() != c) acc.append(c); acc }.toString()
    private fun orderedPenalty(path:String, word:String):Int {
        var index=0; var misses=0
        for(c in path) {
            val found=word.indexOf(c,index)
            if(found>=0) index=found+1 else misses++
        }
        return if(misses>path.length/2) 99 else misses*3 + kotlin.math.abs(word.length-path.length)
    }
    /**
     * Compare Roman Urdu by a tolerant phonetic-ish key.  Users commonly type
     * the same word as kya/kia/kyaa, main/mein, or pyar/pyaar.  The key is only
     * used for the Roman Urdu language, so English spelling is unaffected.
     */
    fun romanKey(value:String): String {
        var key=value.lowercase(Locale.ROOT)
            .replace('’','\'')
            .replace(Regex("[^a-z]"), "")
        key=key.replace("ph","f").replace("aa","a").replace("ee","i").replace("ii","i")
            .replace("oo","u").replace("uu","u").replace("ai","e").replace("ay","e")
            .replace("ya","ia")
        return compact(key)
    }
    private fun romanPenalty(prefix:String,value:String,language:Int,roman:Boolean):Int = if(language==2 && roman && romanKey(value).startsWith(romanKey(prefix))) 0 else 1
    fun distance(a: String, b: String): Int {
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
}
