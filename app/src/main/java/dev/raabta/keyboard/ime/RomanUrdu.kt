package dev.raabta.keyboard.ime

import java.util.Locale

/** Original, offline common-word transliteration lexicon. Unknown words remain unchanged. */
object RomanUrdu {
    val words = mapOf(
        "hai" to "ہے", "hain" to "ہیں", "he" to "ہے", "han" to "ہاں", "haan" to "ہاں",
        "main" to "میں", "mein" to "میں", "mai" to "میں", "hum" to "ہم", "ham" to "ہم",
        "aap" to "آپ", "ap" to "آپ", "tum" to "تم", "tu" to "تو", "wo" to "وہ", "woh" to "وہ",
        "ye" to "یہ", "yeh" to "یہ", "yahan" to "یہاں", "wahan" to "وہاں", "kahan" to "کہاں",
        "ka" to "کا", "ki" to "کی", "ke" to "کے", "ko" to "کو", "se" to "سے", "par" to "پر",
        "aur" to "اور", "or" to "اور", "lekin" to "لیکن", "agar" to "اگر", "to" to "تو",
        "nahi" to "نہیں", "nahin" to "نہیں", "na" to "نہ", "ji" to "جی", "jee" to "جی",
        "kya" to "کیا", "kyun" to "کیوں", "kyon" to "کیوں", "kaise" to "کیسے", "kab" to "کب",
        "acha" to "اچھا", "achha" to "اچھا", "accha" to "اچھا", "achi" to "اچھی", "theek" to "ٹھیک",
        "thik" to "ٹھیک", "bohat" to "بہت", "bahut" to "بہت", "buhat" to "بہت", "zyada" to "زیادہ",
        "kam" to "کم", "kaam" to "کام", "kar" to "کر", "karo" to "کرو", "karta" to "کرتا",
        "karti" to "کرتی", "karte" to "کرتے", "karna" to "کرنا", "kiya" to "کیا", "ho" to "ہو",
        "hoga" to "ہوگا", "hogi" to "ہوگی", "hona" to "ہونا", "hua" to "ہوا", "hui" to "ہوئی",
        "tha" to "تھا", "thi" to "تھی", "the" to "تھے", "raha" to "رہا", "rahi" to "رہی",
        "rahe" to "رہے", "gaya" to "گیا", "gayi" to "گئی", "jana" to "جانا", "jao" to "جاؤ",
        "aana" to "آنا", "ao" to "آؤ", "aao" to "آؤ", "chalo" to "چلو", "chahiye" to "چاہیے",
        "mujhe" to "مجھے", "mujhay" to "مجھے", "tumhe" to "تمہیں", "humein" to "ہمیں",
        "mera" to "میرا", "meri" to "میری", "mere" to "میرے", "tera" to "تیرا", "apna" to "اپنا",
        "apni" to "اپنی", "apne" to "اپنے", "us" to "اس", "is" to "اس", "unka" to "ان کا",
        "sab" to "سب", "kuch" to "کچھ", "koi" to "کوئی", "har" to "ہر", "ab" to "اب",
        "abhi" to "ابھی", "phir" to "پھر", "pehle" to "پہلے", "baad" to "بعد", "sath" to "ساتھ",
        "saath" to "ساتھ", "liye" to "لیے", "wala" to "والا", "wali" to "والی", "wale" to "والے",
        "salam" to "سلام", "assalam" to "السلام", "alaikum" to "علیکم", "shukriya" to "شکریہ",
        "shukria" to "شکریہ", "meherbani" to "مہربانی", "maaf" to "معاف", "khuda" to "خدا",
        "allah" to "اللہ", "inshallah" to "ان شاء اللہ", "mashallah" to "ما شاء اللہ",
        "dost" to "دوست", "dosti" to "دوستی", "pyar" to "پیار", "pyaar" to "پیار",
        "mohabbat" to "محبت", "khushi" to "خوشی", "khush" to "خوش", "udas" to "اداس",
        "mubarak" to "مبارک", "salgirah" to "سالگرہ", "ghar" to "گھر", "pani" to "پانی",
        "paani" to "پانی", "chai" to "چائے", "khana" to "کھانا", "roti" to "روٹی",
        "subah" to "صبح", "raat" to "رات", "din" to "دن", "aaj" to "آج", "kal" to "کل",
        "waqt" to "وقت", "samay" to "وقت", "jaldi" to "جلدی", "der" to "دیر", "ruk" to "رک",
        "baat" to "بات", "bol" to "بول", "bolo" to "بولو", "sun" to "سن", "suno" to "سنو",
        "samajh" to "سمجھ", "likho" to "لکھو", "likhna" to "لکھنا", "parho" to "پڑھو",
        "dekho" to "دیکھو", "dekh" to "دیکھ", "do" to "دو", "de" to "دے", "lo" to "لو",
        "achaah" to "اچھا", "sahi" to "صحیح", "galat" to "غلط", "ghalat" to "غلط",
        "naam" to "نام", "naya" to "نیا", "nayi" to "نئی", "purana" to "پرانا",
        "zindagi" to "زندگی", "dil" to "دل", "duniya" to "دنیا", "kitab" to "کتاب",
        "mobile" to "موبائل", "phone" to "فون", "keyboard" to "کی بورڈ", "awaz" to "آواز",
        "awaaz" to "آواز", "urdu" to "اردو", "english" to "انگریزی", "zaban" to "زبان",
        "madad" to "مدد", "sawal" to "سوال", "jawab" to "جواب", "zaroor" to "ضرور",
        "zaruri" to "ضروری", "zaroori" to "ضروری", "masla" to "مسئلہ", "mushkil" to "مشکل"
    )
    fun candidates(prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase(Locale.ROOT)
        return (listOfNotNull(words[lower]) + words.asSequence().filter { it.key.startsWith(lower) }
            .sortedBy { it.key.length }.map { it.value }.take(4).toList()).distinct().take(4)
    }
    fun convert(text: String): String = Regex("[A-Za-z]+").replace(text) { words[it.value.lowercase(Locale.ROOT)] ?: it.value }
}
