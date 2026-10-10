package dev.raabta.keyboard.tools

data class AiTool(val id: String, val label: String, val group: String, val instruction: String)

/** Original, short provider instructions. No background message or accessibility collection. */
object AiTools {
    val all = listOf(
        AiTool("ask", "Ask AI", "AI assistance", "Answer the question helpfully"),
        AiTool("humanise", "Humanise", "AI assistance", "Rewrite naturally, removing stiff wording; preserve meaning"),
        AiTool("reply", "Reply", "AI assistance", "Draft a concise, appropriate reply to the supplied message"),
        AiTool("summary", "Summary", "AI assistance", "Summarize the main points accurately and concisely"),
        AiTool("caption", "Caption", "AI assistance", "Write a concise social caption based on the text; do not invent facts"),
        AiTool("email", "Email", "AI assistance", "Draft a clear email from these notes; do not invent facts"),
        AiTool("phrase_emoji", "Phrase → emoji", "AI assistance", "Express the text using relevant emoji"),
        AiTool("continue", "Continue", "AI assistance", "Continue the text naturally"),
        AiTool("correct", "Grammar Fix", "Fix grammar", "Correct spelling and grammar without changing meaning"),
        AiTool("rewrite", "Rephrase", "Fix grammar", "Rewrite clearly and naturally, preserving meaning"),
        AiTool("add_emoji", "Add emojis", "Fix grammar", "Keep the meaning and add a few relevant emoji"),
        AiTool("realistic", "Realistic", "Fix grammar", "Rewrite in a straightforward, believable tone; preserve facts"),
        AiTool("formal", "Professional", "Word tone", "Rewrite professionally and formally"),
        AiTool("friendly", "Casual", "Word tone", "Rewrite in a friendly, casual tone"),
        AiTool("polite", "Polite", "Word tone", "Rewrite politely and respectfully"),
        AiTool("romantic", "Romantic", "Word tone", "Rewrite warmly and romantically, preserving intent"),
        AiTool("empathetic", "Empathetic", "Word tone", "Rewrite with empathy and understanding"),
        AiTool("funny", "Funny", "Word tone", "Rewrite with light humour, preserving meaning"),
        AiTool("poetic", "Poetic", "Word tone", "Rewrite poetically, preserving meaning"),
        AiTool("shorten", "Shorten", "Word tone", "Shorten while preserving the main meaning"),
        AiTool("sarcastic", "Sarcastic", "Word tone", "Rewrite with mild sarcasm, preserving meaning"),
        AiTool("angry", "Angry", "Word tone", "Rewrite firmly and indignantly, without threats"),
        AiTool("flirty", "Flirty", "Word tone", "Rewrite playfully and affectionately, preserving intent"),
        AiTool("gen_z", "Gen-Z", "Word tone", "Rewrite casually using light modern slang"),
        AiTool("witty", "Witty", "Word tone", "Rewrite cleverly and concisely, preserving meaning"),
        AiTool("custom", "Custom tool", "AI assistance", "Apply the user's chosen writing style"),
    )
    val groups = listOf("AI assistance", "Word tone", "Fix grammar")
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first { it.id == "rewrite" }
    fun instruction(id: String, language: String, custom: String = ""): String {
        val task = if (id == "custom" && custom.isNotBlank()) custom.trim().take(90) else get(id).instruction
        return "$task. Output in ${if(language == "ur") "Urdu" else "English"}."
    }
    /** One request may return up to three previews; plain-text providers still return one. */
    fun variants(content: String): List<String> {
        val value = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val array = org.json.JSONObject(value).getJSONArray("variants")
            (0 until minOf(array.length(), 3)).map { array.getString(it).trim() }
                .filter { it.isNotEmpty() && it.length <= 8000 }.distinct().ifEmpty { listOf(content) }
        } catch (_:org.json.JSONException) { listOf(content) }
    }
}
