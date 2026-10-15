package dev.raabta.keyboard.setup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.raabta.keyboard.ui.ThemeImport
import dev.raabta.keyboard.ui.PendingKeyboardTool
import java.util.concurrent.Executors

/** System document picker gives explicit access to one bounded colour-only JSON file. */
class ThemeImportActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val ui=Handler(Looper.getMainLooper())
    private var importing=false
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(state?.getBoolean("importing")==true) { Toast.makeText(this,"Import interrupted. Please try again.",Toast.LENGTH_SHORT).show(); finish(); return }
        if(state==null) {
            @Suppress("DEPRECATION")
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="*/*"; putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/json","text/plain","application/octet-stream"))
            },REQUEST) } catch (_:android.content.ActivityNotFoundException) { Toast.makeText(this,"No document picker is available.",Toast.LENGTH_LONG).show(); finish() }
        }
    }
    @Deprecated("Activity result bridge for Android 8 compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val uri=data?.data
        if(requestCode!=REQUEST || resultCode!=RESULT_OK || uri==null) { finish(); return }
        if(importing) return
        importing=true
        worker.execute {
            val result=runCatching {
                val bytes=contentResolver.openInputStream(uri)?.use { stream ->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(1024)
                    while(true) {
                        val count=stream.read(buffer); if(count<0) break
                        require(output.size()+count<=ThemeImport.MAX_BYTES) { "Theme file is too large." }; output.write(buffer,0,count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Could not read this theme file.")
                ThemeImport.parse(bytes.toString(Charsets.UTF_8))
            }
            ui.post {
                if(isFinishing || isDestroyed) return@post
                result.fold({ theme ->
                    ThemeImport.save(this,theme)
                    if(intent.getBooleanExtra("from_keyboard",false)) PendingKeyboardTool.request("themes")
                    Toast.makeText(this,"Theme imported and applied.",Toast.LENGTH_SHORT).show()
                },{ failure -> Toast.makeText(this,failure.message?.takeIf { failure is IllegalArgumentException } ?: "Invalid theme file. Use the Raabta JSON format.",Toast.LENGTH_LONG).show() })
                finish()
            }
        }
    }
    override fun onSaveInstanceState(state:Bundle) { state.putBoolean("importing",importing); super.onSaveInstanceState(state) }
    override fun onDestroy() { worker.shutdownNow(); ui.removeCallbacksAndMessages(null); super.onDestroy() }
    companion object { private const val REQUEST=71 }
}
