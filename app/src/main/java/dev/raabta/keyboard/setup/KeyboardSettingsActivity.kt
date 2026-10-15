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
import dev.raabta.keyboard.ui.appVersionName
import dev.raabta.keyboard.ime.LearnedWords
import dev.raabta.keyboard.tools.*

/** Android's keyboard settings entry uses categories, independently of first-run enable/test setup. */
class KeyboardSettingsActivity:Activity() {
    private var page="Raabta settings"
    override fun onCreate(state:Bundle?) {
        setTheme(if(Palette.isDark(this)) dev.raabta.keyboard.R.style.AppTheme_Dark else dev.raabta.keyboard.R.style.AppTheme)
        super.onCreate(state)
        page=state?.getString("page") ?: intent.getStringExtra("page") ?: "Raabta settings"
        window.setSoftInputMode(if (page == "Search") {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        showPage(page)
        if(android.os.Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
    }
    private fun showPage(value:String) {
        if(value.isEmpty()) { finish(); return }
        if(value=="Connections") { startActivity(Intent(this,AiConnectionActivity::class.java)); return }
        val scroll=if(value==page) findViewById<android.view.View>(android.R.id.content)?.findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.scrollY ?: 0 else 0
        page=value
        window.setSoftInputMode(if (page == "Search") {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (page != "Search") {
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        val palette=Palette.from(this); palette.applySystemBars(window)
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
        findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.ScrollView>("SettingsScroll")?.let { body -> body.addOnLayoutChangeListener(object:android.view.View.OnLayoutChangeListener { override fun onLayoutChange(v:android.view.View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int) { if(b>t) { body.removeOnLayoutChangeListener(this); body.scrollTo(0,scroll) } } }) }
    }
    override fun onResume() { super.onResume(); showPage(page) }
    private fun action(value:String) {
        when {
            value in dev.raabta.keyboard.ui.PendingKeyboardTool.tools -> { dev.raabta.keyboard.ui.PendingKeyboardTool.request(value); finish() }
            value.startsWith("permission:") -> startActivity(Intent(this,OptionalPermissionActivity::class.java).putExtra("setting",value.substringAfter(':')))
            value=="permissions" -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:$packageName".toUri()))
            value=="clear" || value=="learned_clear" -> LearnedWords(this).clear()
            value=="theme:import" -> startActivity(Intent(this,ThemeImportActivity::class.java))
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
                type="text/plain"; putExtra(Intent.EXTRA_TEXT,"Raabta Keyboard v${appVersionName()}: https://github.com/gityy34/keybord-ai-/releases/tag/v${appVersionName()}")
            },"Share Raabta"))
            else -> AlertDialog.Builder(this).setMessage("Use this tool from the Raabta keyboard toolbar while editing text.").setPositiveButton("OK",null).show()
        }
    }
    private fun back() { if(page=="Raabta settings") finish() else showPage("Raabta settings") }
    @Deprecated("Compatibility with Android 8–12")
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(state:Bundle) { state.putString("page",page); super.onSaveInstanceState(state) }
}
