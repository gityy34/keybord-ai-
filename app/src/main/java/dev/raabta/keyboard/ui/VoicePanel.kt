package dev.raabta.keyboard.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.raabta.keyboard.ime.KeyButton
import dev.raabta.keyboard.ime.ToolbarButton
import dev.raabta.keyboard.voice.VoiceLevelView
import dev.raabta.keyboard.voice.VoiceSettings

/** One bounded voice workspace. Audio/transcript lifetime stays in the controller, not this view. */
class VoicePanel @JvmOverloads constructor(context: Context, stop: () -> Unit = {}, cancel: () -> Unit = {}, command: (String) -> Unit = {}) : LinearLayout(context) {
    private val palette=Palette.from(context)
    val status=TextView(context).apply { tag="VoiceStatus"; textSize=14f; setTextColor(palette.text); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val timer=TextView(context).apply { tag="VoiceTimer"; textSize=13f; setTextColor(palette.muted); gravity=Gravity.END }
    val level=VoiceLevelView(context)
    val transcript=TextView(context).apply { tag="VoiceTranscript"; textSize=16f; setTextColor(palette.text); setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(8)); textDirection=View.TEXT_DIRECTION_FIRST_STRONG }
    private val languages=LinearLayout(context).apply { isBaselineAligned=false; tag="VoiceLanguages" }
    init {
        orientation=VERTICAL; tag="VoiceRecorder"; setPadding(context.dp(8),0,context.dp(8),context.dp(6)); setBackgroundColor(palette.background)
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        fun icon(value:String,tagValue:String,description:String,action:()->Unit)=ToolbarButton(context).apply {
            text=value; tag=tagValue; contentDescription=description; setTextColor(palette.text)
            background=palette.pressed(radius=context.dp(10).toFloat()); setOnClickListener { action() }
        }
        header.addView(icon("‹","VoiceDismiss","Cancel and return to keyboard",cancel),LayoutParams(context.dp(48),context.dp(48)))
        header.addView(TextView(context).apply { setText(dev.raabta.keyboard.R.string.voice_title); textSize=18f; setTextColor(palette.text) },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        header.addView(icon("⌫","VoiceDelete","Delete voice draft or unchanged last transcription") { command("voice:delete") },LayoutParams(context.dp(48),context.dp(48)))
        addView(header)
        addView(languages,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(44)))
        val state=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(context.dp(6),0,context.dp(6),0) }
        state.addView(status,LayoutParams(0,context.dp(28),1f)); state.addView(timer,LayoutParams(context.dp(54),context.dp(28))); addView(state)
        addView(level,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(56)))
        addView(ScrollView(context).apply { addView(transcript); background=palette.shape(radius=context.dp(12).toFloat()) },LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        val actions=LinearLayout(context).apply { gravity=Gravity.CENTER; isBaselineAligned=false }
        fun button(label:String,tagValue:String,action:()->Unit)=KeyButton(context).apply {
            text=label; tag=tagValue; textSize=14f; fitLabel(14f); setTextColor(palette.onAccent)
            background=palette.pressed(palette.accent,context.dp(12).toFloat()); setOnClickListener { action() }
        }
        actions.addView(button(context.getString(dev.raabta.keyboard.R.string.voice_stop_short),"VoiceStop",stop).apply { contentDescription=context.getString(dev.raabta.keyboard.R.string.voice_stop) },LayoutParams(0,context.dp(46),1f).apply { topMargin=context.dp(6) })
        actions.addView(button(context.getString(dev.raabta.keyboard.R.string.voice_retry),"VoiceRetry") { command("voice:retry") }.apply { visibility=GONE },LayoutParams(0,context.dp(46),1f).apply { topMargin=context.dp(6) })
        addView(actions)
        language(VoiceSettings.language(context),command)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        if(MeasureSpec.getMode(heightMeasureSpec)!=MeasureSpec.UNSPECIFIED) level.layoutParams.height=minOf(context.dp(56),(MeasureSpec.getSize(heightMeasureSpec)-context.dp(210)).coerceAtLeast(0))
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    fun language(selected:String,command:(String)->Unit) {
        languages.removeAllViews()
        val defaults=listOf(VoiceSettings.URDU to "Urdu",VoiceSettings.ENGLISH to "English",VoiceSettings.MIXED to "Auto")
        val choices=if(defaults.any { it.first==selected }) defaults else defaults+listOf(selected to java.util.Locale(selected).getDisplayLanguage(java.util.Locale.ENGLISH))
        for((value,label) in choices) {
            languages.addView(KeyButton(context).apply {
                text=label; tag="voice-language:$value"; textSize=14f; fitLabel(14f); isSelected=selected==value
                setTextColor(if(isSelected) palette.onAccent else palette.text)
                background=palette.pressed(if(isSelected) palette.accent else palette.surface,context.dp(8).toFloat())
                if(value in defaults.map { it.first }) setOnClickListener { command("voice-language:$value") } else isClickable=false
            },LayoutParams(0,context.dp(40),1f).apply { setMargins(context.dp(2),0,context.dp(2),0) })
        }
    }
}
