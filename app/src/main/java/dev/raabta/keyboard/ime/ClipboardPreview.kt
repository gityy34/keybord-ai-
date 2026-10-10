package dev.raabta.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock

/** Current clipboard only, bounded and expiring. Never read URI contents or store a history. */
class ClipboardPreview(private val context:Context) {
    private var until=0L
    private var cached=emptyList<Candidate>()
    private var screenshot:Candidate?=null
    fun refresh() {
        cached=emptyList(); until=SystemClock.uptimeMillis()+120_000
        try {
            val clip=context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
            if(Build.VERSION.SDK_INT>=33 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE")==true) return
            val item=clip.getItemAt(0)
            val text=item.text?.toString()?.take(2048)
            if(!text.isNullOrEmpty()) cached=listOf(Candidate("Paste: ${text.take(32)}",text))
            else if(item.uri!=null && clip.description.hasMimeType("image/*"))
                cached=listOf(Candidate("Paste copied image","",imageUri=item.uri.toString()))
        } catch (_:RuntimeException) { cached=emptyList() }
    }
    fun screenshot(uri:String) { screenshot=Candidate("Paste recent screenshot","",imageUri=uri); until=SystemClock.uptimeMillis()+120_000 }
    fun candidates(items:Boolean):List<Candidate> {
        if(SystemClock.uptimeMillis()>until) { clear(); return emptyList() }
        val result=cached.toMutableList(); screenshot?.let(result::add)
        if(items) cached.firstOrNull { it.imageUri==null }?.text?.let { text ->
            Regex("https?://[^\\s]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|\\+?[0-9][0-9 ()-]{6,}[0-9]").findAll(text).take(3)
                .forEach { result.add(Candidate(it.value.take(32),it.value)) }
        }
        if(items) cached.firstOrNull { it.imageUri==null }?.text?.let { text ->
            Regex("\\b[0-9]{1,5}\\s+[\\p{L}0-9 .]{2,60}\\s(?:Street|Road|Avenue|Lane|Drive|St|Rd)\\b|گلی\\s+نمبر\\s+[۰-۹0-9]+",RegexOption.IGNORE_CASE)
                .find(text)?.value?.let { result.add(Candidate(it.take(40),it)) }
        }
        return result.distinctBy { it.text to it.imageUri }.take(5).map { it.copy(expiresAt=until) }
    }
    fun clear() { cached=emptyList(); screenshot=null; until=0 }
}
