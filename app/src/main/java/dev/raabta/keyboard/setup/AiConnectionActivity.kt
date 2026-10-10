package dev.raabta.keyboard.setup

import dev.raabta.keyboard.R
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import dev.raabta.keyboard.tools.AiConnection
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp

/** User-requested connection setup only; never accepts credentials from intents. */
class AiConnectionActivity: Activity() {
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val settings = AiConnection(this)
        val palette = Palette.from(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(24),dp(18),dp(18)); setBackgroundColor(palette.background) }
        fun label(text: String) { body.addView(TextView(this).apply { this.text=text; setTextColor(palette.text); textSize=16f; setPadding(0,dp(10),0,dp(10)) }) }
        fun field(title: String, value: String, secret: Boolean=false): EditText {
            label(title)
            return EditText(this).apply {
                isSaveEnabled=false; importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setText(value); setTextColor(palette.text); maxLines=2
                filters=arrayOf(android.text.InputFilter.LengthFilter(if(secret) 4096 else 512))
                body.addView(this)
            }
        }
        label("AI connection")
        label("Use a trusted OpenAI-compatible backend. Only your tool draft is sent when you press Generate. Provider charges and data policies apply. No provider or key is bundled.")
        val endpoint=field("HTTPS URL ending /chat/completions",settings.endpoint())
        val model=field("Model name",settings.model())
        val token=field("Optional token · blank keeps the existing token","",true)
        val status=TextView(this).apply { setTextColor(palette.text); body.addView(this) }
        body.addView(Button(this).apply { setText(R.string.ai_connection_save); setOnClickListener {
            try {
                settings.save(endpoint.text.toString().trim(),model.text.toString().trim(),token.text.toString().takeIf { it.isNotEmpty() })
                token.text.clear(); finish()
            } catch (_: Exception) { status.setText(R.string.ai_connection_invalid) }
        } })
        body.addView(Button(this).apply { setText(R.string.ai_connection_remove); setOnClickListener { settings.clear(); token.text.clear(); finish() } })
        body.addView(Button(this).apply { setText(R.string.back_to_keyboard); setOnClickListener { token.text.clear(); finish() } })
        setContentView(ScrollView(this).apply {
            addView(body)
            setOnApplyWindowInsetsListener { view,insets ->
                if(android.os.Build.VERSION.SDK_INT>=30) { val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom) }
                else { @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom) }
                insets
            }
        })
    }
}
