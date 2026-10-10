package dev.raabta.keyboard.setup

import androidx.core.net.toUri
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import dev.raabta.keyboard.ui.KeyboardSettingsPanel
import dev.raabta.keyboard.ui.KeyboardOptions
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ime.LearnedWords
import dev.raabta.keyboard.tools.*

/** Android's keyboard settings entry uses categories, independently of first-run enable/test setup. */
class KeyboardSettingsActivity:Activity() {
    private var page="Raabta settings"
    override fun onCreate(state:Bundle?) {
        setTheme(if(Palette.isDark(this)) dev.raabta.keyboard.R.style.AppTheme_Dark else dev.raabta.keyboard.R.style.AppTheme)
        super.onCreate(state)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        page=state?.getString("page") ?: "Raabta settings"
        showPage(page)
        if(android.os.Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
    }
    private fun showPage(value:String) {
        if(value.isEmpty()) { finish(); return }
        val scroll=if(value==page) findViewById<android.view.View>(android.R.id.content)?.findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.scrollY ?: 0 else 0
        page=value
        val palette=Palette.from(this)
        @Suppress("DEPRECATION")
        window.statusBarColor=palette.background
        @Suppress("DEPRECATION")
        window.navigationBarColor=palette.background
        setContentView(KeyboardSettingsPanel(this,page,::showPage,{ showPage(page) },::action).apply {
            setOnApplyWindowInsetsListener { view,insets ->
                if(android.os.Build.VERSION.SDK_INT>=30) {
                    val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                }
                insets
            }
        })
        findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.let { body -> body.post { body.scrollTo(0,scroll) } }
    }
    override fun onResume() { super.onResume(); showPage(page) }
    private fun action(value:String) {
        when {
            value in dev.raabta.keyboard.ui.PendingKeyboardTool.tools -> { dev.raabta.keyboard.ui.PendingKeyboardTool.request(value); finish() }
            value.startsWith("permission:") -> startActivity(Intent(this,OptionalPermissionActivity::class.java).putExtra("setting",value.substringAfter(':')))
            value=="permissions" -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:$packageName".toUri()))
            value=="clear" || value=="learned_clear" -> LearnedWords(this).clear()
            value=="ai:connection" -> startActivity(Intent(this,AiConnectionActivity::class.java))
            value=="ai:clear" -> { AiConnection(this).clear(); showPage(page) }
            value=="translation_delete" -> LocalTranslation.deleteModels(this) { ok -> Toast.makeText(this,if(ok) "Translation model removed." else "Could not remove model.",Toast.LENGTH_LONG).show() }
            value=="dictionary_add" -> {
                val field=EditText(this).apply { hint="Personal dictionary word"; isSaveEnabled=false }
                AlertDialog.Builder(this).setTitle("Add a word").setView(field).setPositiveButton("Save") { _,_ ->
                    KeyboardOptions(this).addWord(field.text.toString()); showPage(page)
                }.setNegativeButton("Cancel",null).show()
            }
            value=="share" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"; putExtra(Intent.EXTRA_TEXT,"Raabta Keyboard: https://github.com/gityy34/keybord-ai-/tree/apk-ui-0.5.0")
            },"Share Raabta"))
            else -> AlertDialog.Builder(this).setMessage("Use this tool from the Raabta keyboard toolbar while editing text.").setPositiveButton("OK",null).show()
        }
    }
    private fun back() { if(page=="Raabta settings") finish() else showPage("Raabta settings") }
    @Deprecated("Compatibility with Android 8–12")
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(state:Bundle) { state.putString("page",page); super.onSaveInstanceState(state) }
}
