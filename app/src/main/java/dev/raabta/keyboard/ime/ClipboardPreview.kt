package dev.raabta.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock

/** Observes the system clipboard while the IME is active and supplies safe local previews. */
class ClipboardPreview(private val context: Context, val history: ClipboardHistory = ClipboardHistory(context)) {
    private var screenshot: Candidate? = null
    private var screenshotUntil = 0L
    var lastSaveWorked = true
        private set
    private var currentSensitive = false

    @Synchronized fun refresh(newCopy: Boolean = false) {
        if (!dev.raabta.keyboard.ui.KeyboardOptions(context).enabled("clipboard_enabled")) { clearPreview(); return }
        try {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
            currentSensitive = Build.VERSION.SDK_INT >= 33 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
            if (currentSensitive) { clearPreview(); return }
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank() && (newCopy || history.latest()?.text != text)) lastSaveWorked = history.add(text)
            else if (clip.getItemAt(0).uri != null && clip.description.hasMimeType("image/*")) {
                screenshot = Candidate("Paste copied image", "", imageUri=clip.getItemAt(0).uri.toString())
                screenshotUntil = SystemClock.uptimeMillis() + 120_000
            }
        } catch (_: RuntimeException) { lastSaveWorked = false }
    }
    @Synchronized fun screenshot(uri: String) {
        if (!dev.raabta.keyboard.ui.KeyboardOptions(context).enabled("clipboard_enabled")) return
        screenshot = Candidate("Paste recent screenshot", "", imageUri=uri)
        screenshotUntil = SystemClock.uptimeMillis() + 120_000
    }
    @Synchronized fun candidates(items: Boolean): List<Candidate> {
        val options = dev.raabta.keyboard.ui.KeyboardOptions(context)
        if (!options.enabled("clipboard_enabled")) { clearPreview(); return emptyList() }
        val result = mutableListOf<Candidate>()
        if (!currentSensitive) history.latest()?.let { clip ->
            val remaining=(clip.copiedAt+ClipboardHistory.OLD_MILLIS-System.currentTimeMillis()).coerceAtLeast(0)
            result.add(Candidate("Copied: ${clip.text.take(32)}", clip.text, expiresAt=SystemClock.uptimeMillis()+remaining))
        }
        if (SystemClock.uptimeMillis() < screenshotUntil) screenshot?.let(result::add)
        if (items && !currentSensitive) history.latest()?.text?.let { text ->
            Regex("https?://[^\\s]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|\\+?[0-9][0-9 ()-]{6,}[0-9]").findAll(text).take(3)
                .forEach { result.add(Candidate(it.value.take(32), it.value)) }
            Regex("\\b[0-9]{1,5}\\s+[\\p{L}0-9 .]{2,60}\\s(?:Street|Road|Avenue|Lane|Drive|St|Rd)\\b|گلی\\s+نمبر\\s+[۰-۹0-9]+", RegexOption.IGNORE_CASE)
                .find(text)?.value?.let { result.add(Candidate(it.take(40), it)) }
        }
        return result.distinctBy { it.text to it.imageUri }.take(5)
    }
    @Synchronized fun clearPreview() { screenshot=null; screenshotUntil=0 }
    @Synchronized fun clear() { clearPreview(); history.clearUnpinned() }
}
