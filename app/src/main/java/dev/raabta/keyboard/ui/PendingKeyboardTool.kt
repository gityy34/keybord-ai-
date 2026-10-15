package dev.raabta.keyboard.ui

/** One explicit settings navigation request, in memory only and expiring in 30 seconds. */
object PendingKeyboardTool {
    private var value=""
    private var time=0L
    val tools=setOf("translate","ai","roman","emoji","media","editing","clipboard","language","themes")
    fun request(tool:String) { if(tool in tools) { value=tool; time=android.os.SystemClock.uptimeMillis() } }
    fun consume():String {
        val result=if(android.os.SystemClock.uptimeMillis()-time<=30_000) value else ""
        value=""; return result
    }
}
