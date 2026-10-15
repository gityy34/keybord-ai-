package dev.raabta.keyboard.setup

import androidx.core.net.toUri
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*
import dev.raabta.keyboard.R
import dev.raabta.keyboard.tools.*
import dev.raabta.keyboard.ui.*
import java.util.concurrent.Executors

/** Full settings for credentials. No intent-supplied secrets, backups, autofill or screenshot capture. */
class AiConnectionActivity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var request:AIProvider?=null
    @Volatile private var generation=0
    private var testButton:Button?=null
    private lateinit var keyField:EditText
    private lateinit var summary:TextView
    private lateinit var status:TextView
    override fun onCreate(state:Bundle?) {
        setTheme(if(Palette.isDark(this)) R.style.AppTheme_Dark else R.style.AppTheme)
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val connection=AiConnection(this); val palette=Palette.from(this)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); setBackgroundColor(palette.background) }
        fun label(value:String)=TextView(this).apply { text=value; textSize=16f; setTextColor(palette.text); setPadding(0,dp(9),0,dp(9)); body.addView(this) }
        fun button(label:String,tagValue:String,action:()->Unit)=Button(this).apply {
            text=label; tag=tagValue; isAllCaps=false; stateListAnimator=null
            setTextColor(palette.text); backgroundTintList=android.content.res.ColorStateList.valueOf(palette.surface)
            setOnClickListener { action() }; body.addView(this)
        }
        fun field(title:String,value:String,secret:Boolean=false):EditText {
            label(title)
            return EditText(this).apply {
                isSaveEnabled=false; importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                inputType=InputType.TYPE_CLASS_TEXT or if(secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setText(value); setTextColor(palette.text); setSingleLine(true)
                backgroundTintList=android.content.res.ColorStateList.valueOf(palette.accent)
                filters=arrayOf(android.text.InputFilter.LengthFilter(if(secret) 4096 else 512)); body.addView(this)
            }
        }
        label("Connections")
        summary=label("").apply { tag="ConnectionSummary" }
        fun refresh() { summary.text=getString(R.string.connection_summary,if(connection.active() && connection.configured()) connection.provider().label else "None",connection.provider().label,connection.model().ifEmpty { "None" },connection.status(),connection.maskedKey()) }
        refresh()
        label("Only text submitted to an AI tool is sent to this provider. Normal typing stays local. Save changes explicitly; provider fees and privacy policies apply.")
        val providers=Spinner(this).apply { tag="ProviderChoice"; adapter=ArrayAdapter(this@AiConnectionActivity,android.R.layout.simple_spinner_dropdown_item,ProviderKind.entries.map { it.label }); setSelection(connection.provider().ordinal) }
        body.addView(providers)
        val endpoint=field("HTTPS chat/completions URL",connection.endpoint()).apply { tag="ConnectionEndpoint" }
        val model=field("Model",connection.model()).apply { tag="ConnectionModel" }
        keyField=field("Use My Own API Key · blank preserves the key only for the same endpoint","",true).apply { tag="ConnectionKey" }
        var first=true
        providers.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                if(first && position==connection.provider().ordinal) { first=false; return }; first=false
                val chosen=ProviderKind.entries[position]
                endpoint.setText(chosen.endpoint); model.setText(chosen.defaultModel); keyField.text.clear()
            }
        }
        status=label("").apply { tag="ConnectionStatus"; accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        button(getString(R.string.ai_connection_save),"ConnectionSave") {
            try { request?.cancel(); ++generation; testButton?.isEnabled=true; connection.save(endpoint.text.toString().trim(),model.text.toString().trim(),keyField.text.toString().takeIf { it.isNotEmpty() }); keyField.text.clear(); status.setText(R.string.connection_saved); refresh() }
            catch (_:Exception) { status.setText(R.string.ai_connection_invalid) }
        }
        val test=button("Test Connection","ConnectionTest") {}; testButton=test
        test.setOnClickListener {
            if(!connection.configured()) { status.setText(R.string.connection_save_first); return@setOnClickListener }
            request?.cancel(); val run=++generation; val checkedEndpoint=connection.endpoint(); val checkedModel=connection.model()
            status.setText(R.string.connection_testing); test.isEnabled=false
            worker.execute {
                if(run!=generation) return@execute
                val problem=try {
                    val provider=AIService(connection).provider(forTest=true); request=provider
                    if(run!=generation) { provider.cancel(); return@execute }
                    provider.testConnection(); null
                }
                    catch(e:AiFailure) { e.problem } catch (_:Exception) { AiProblem.RESPONSE }
                ui.post {
                    if(run!=generation || isFinishing || isDestroyed) return@post
                    test.isEnabled=true
                    if(connection.endpoint()!=checkedEndpoint || connection.model()!=checkedModel) { status.setText(R.string.connection_changed); return@post }
                    connection.recordTest(problem==null)
                    status.text=problem?.userMessage ?: getString(R.string.connection_success); refresh()
                }
            }
        }
        button("Activate","ConnectionActivate") { if(connection.configured()) { connection.setActive(true); refresh() } else status.setText(R.string.connection_save_first) }
        button("Deactivate","ConnectionDeactivate") { request?.cancel(); ++generation; test.isEnabled=true; connection.setActive(false); refresh() }
        button("Change Provider / Use My Own API Key","ConnectionChange") { providers.performClick(); keyField.requestFocus() }
        button(getString(R.string.ai_connection_remove),"ConnectionDelete") { request?.cancel(); ++generation; test.isEnabled=true; connection.clear(); keyField.text.clear(); endpoint.text.clear(); model.text.clear(); refresh() }
        label("Groq preset uses its official OpenAI-compatible API. A developer account/key is required. Check current account limits at console.groq.com; no unlimited/free quota is promised by this app.")
        button("Get Groq developer key","ConnectionGetKey") {
            try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,"https://console.groq.com/keys".toUri())) }
            catch (_:android.content.ActivityNotFoundException) { status.setText(R.string.connection_no_browser) }
        }
        button("Back","ConnectionBack") { finish() }
        setContentView(ScrollView(this).apply { addView(body); setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) { val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom) }
            else { @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom) }; insets
        } })
    }
    override fun onStop() { request?.cancel(); ++generation; testButton?.isEnabled=true; if(::keyField.isInitialized) keyField.text.clear(); super.onStop() }
    override fun onDestroy() { request?.cancel(); worker.shutdownNow(); ui.removeCallbacksAndMessages(null); super.onDestroy() }
}
