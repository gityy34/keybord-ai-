package dev.raabta.keyboard.tools

data class AiTool(val id: String, val label: String, val group: String, val instruction: String)

/** Original, short provider instructions. No background message or accessibility collection. */
object AiTools {
    val all = listOf(
        AiTool("ask", "Ask AI", "AI assistance", "Answer the question helpfully"),
        AiTool("humanise", "Humanise", "AI assistance", "Rewrite naturally, removing stiff wording; preserve meaning"),
        AiTool("reply", "Reply", "AI assistance", "Draft a concise, appropriate reply to the supplied message"),
        AiTool("explain", "Explain", "AI assistance", "Explain the text clearly without inventing facts"),
        AiTool("summary", "Summary", "AI assistance", "Summarize the main points accurately and concisely"),
        AiTool("caption", "Caption", "AI assistance", "Write a concise social caption based on the text; do not invent facts"),
        AiTool("email", "Email", "AI assistance", "Draft a clear email from these notes; do not invent facts"),
        AiTool("phrase_emoji", "Phrase → emoji", "AI assistance", "Express the text using relevant emoji"),
        AiTool("continue", "Continue", "AI assistance", "Continue the text naturally"),
        AiTool("correct", "Grammar Fix", "Fix grammar", "Correct spelling and grammar without changing meaning"),
        AiTool("rewrite", "Paraphrase", "Rewrite", "Rewrite clearly and naturally, preserving meaning"),
        AiTool("add_emoji", "Add emojis", "Fix grammar", "Keep the meaning and add a few relevant emoji"),
        AiTool("realistic", "Realistic", "Fix grammar", "Rewrite in a straightforward, believable tone; preserve facts"),
        AiTool("expand", "Expand", "Rewrite", "Expand clearly while preserving the facts"),
        AiTool("improve", "Improve", "Rewrite", "Improve clarity and flow without changing the meaning"),
        AiTool("clearer", "Clearer", "Rewrite", "Make the wording clear and easy to understand"),
        AiTool("creative", "Creative", "Rewrite", "Rewrite creatively while preserving the meaning"),
        AiTool("professional", "Professional", "Rewrite", "Rewrite professionally"),
        AiTool("casual", "Casual", "Rewrite", "Rewrite casually"),
        AiTool("formal", "Professional", "Tone", "Rewrite professionally and formally"),
        AiTool("friendly", "Casual", "Tone", "Rewrite in a friendly, casual tone"),
        AiTool("polite", "Polite", "Tone", "Rewrite politely and respectfully"),
        AiTool("tone_friendly", "Friendly", "Tone", "Rewrite warmly and conversationally"),
        AiTool("tone_formal", "Formal", "Tone", "Rewrite in a formal tone, preserving meaning"),
        AiTool("empathetic", "Empathetic", "Tone", "Rewrite with empathy and understanding"),
        AiTool("funny", "Funny", "Tone", "Rewrite with light humour, preserving meaning"),
        AiTool("tone_creative", "Creative", "Tone", "Rewrite imaginatively while preserving facts and meaning"),
        AiTool("tone_short", "Short", "Tone", "Rewrite concisely while preserving the main meaning"),
        AiTool("shorten", "Shorten", "Rewrite", "Shorten while preserving the main meaning"),
        AiTool("custom", "Custom", "Tone", "Apply the user's chosen writing style"),
    )
    val groups = listOf("AI assistance", "Fix grammar", "Rewrite", "Tone")
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
