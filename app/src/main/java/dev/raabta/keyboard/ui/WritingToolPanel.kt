package dev.raabta.keyboard.ui

import androidx.core.view.isNotEmpty
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.raabta.keyboard.ime.KeyButton
import dev.raabta.keyboard.ime.ToolbarButton
import dev.raabta.keyboard.tools.AiTools

/** A bounded native panel: local draft → explicit request → review → explicit replacement. */
class WritingToolPanel @JvmOverloads constructor(context: Context, private val mode: String = "ai", source: String = "ur", target: String = "en",
    private val selectedStyle: String = "rewrite", private val command: (String) -> Unit = {}) : LinearLayout(context) {
    private val palette = Palette.from(context)
    private val preview = TextView(context).apply {
        tag="ToolPreview"; textSize=15f; typeface=Typeface.DEFAULT; setTextColor(palette.text)
        setPadding(context.dp(12),context.dp(10),context.dp(12),context.dp(10)); maxLines=3
        setText(dev.raabta.keyboard.R.string.writing_draft_hint); gravity=Gravity.TOP
    }
    private val status = TextView(context).apply {
        tag="ToolStatus"; textSize=12f; setTextColor(palette.muted); maxLines=2
        setPadding(context.dp(10),context.dp(4),context.dp(10),context.dp(4))
        accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val cards = LinearLayout(context).apply { orientation=VERTICAL }
    private val results = ScrollView(context).apply { tag="ToolResults"; addView(cards); visibility=GONE }
    private val run: Button
    private val insert: Button
    private val busy = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate=true; visibility=GONE; tag="ToolProgress"
        indeterminateTintList=ColorStateList.valueOf(palette.accent)
    }
    private val styleRow = LinearLayout(context).apply { isBaselineAligned=false }
    init {
        orientation=VERTICAL; tag="WritingTool"; setBackgroundColor(palette.background)
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; isBaselineAligned=false }
        val back=ToolbarButton(context).apply { text="‹"; contentDescription="Back to keyboard"; tag="tool:writing:close"; style(this); setOnClickListener { command("writing:close") } }
        header.addView(back,LayoutParams(context.dp(42),context.dp(44)))
        val heading=TextView(context).apply {
            text=if(mode=="ai") AiTools.get(selectedStyle).group else if(mode=="translate") "Translate" else "Roman Urdu"
            textSize=17f; typeface=Typeface.DEFAULT; setTextColor(palette.text)
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
        }
        header.addView(heading,LayoutParams(0,context.dp(44),1f).apply { gravity=Gravity.CENTER_VERTICAL })
        if(mode=="ai") {
            val groups=button(header,"Tools ▾","writing:groups",weighted=false)
            groups.setOnClickListener {
                PopupMenu(context,groups).apply {
                    AiTools.groups.forEachIndexed { index,group -> menu.add(0,index,index,group) }
                    setOnMenuItemClickListener { item -> command("writing:style:"+AiTools.all.first { it.group==AiTools.groups[item.itemId] }.id); true }
                }.show()
            }
            languageButton(header,target,"writing:target:")
        } else if(mode=="translate") { languageButton(header,source,"writing:source:"); languageButton(header,target,"writing:target:") }
        addView(header)
        if(mode=="ai") {
            val choices=AiTools.all.filter { it.group==AiTools.get(selectedStyle).group }
            val tone=AiTools.get(selectedStyle).group=="Tone"
            val grid=LinearLayout(context).apply { orientation=VERTICAL; tag="ToneChoices" }
            choices.forEachIndexed { index,tool ->
                val row=if(tone) {
                    if(index%2==0) grid.addView(LinearLayout(context).apply { isBaselineAligned=false })
                    grid.getChildAt(grid.childCount-1) as LinearLayout
                } else styleRow
                button(row,tool.label,"writing:style:${tool.id}",weighted=tone).apply {
                    isSelected=tool.id==selectedStyle
                    background=rounded(if(isSelected) palette.accent else palette.surface,context.dp(7).toFloat())
                    setTextColor(if(isSelected) 0xFFFFFFFF.toInt() else palette.text)
                }
            }
            if(tone) addView(grid) else addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false; tag="AiToolChoices"; addView(styleRow) })
        } else if(mode=="translate") {
            val direction=LinearLayout(context)
            button(direction,"$source → $target ⇄","writing:swap")
            button(direction,"Download model","writing:download")
            addView(direction)
        }
        addView(preview,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(70)))
        addView(busy,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(3)))
        addView(status)
        val actions=LinearLayout(context).apply { isBaselineAligned=false }
        button(actions,"Use text","writing:nearby")
        button(actions,"Use selection","writing:selection")
        if(mode=="ai") button(actions,"Connection","ai:connection")
        if(mode=="translate") button(actions,"Speak → insert","writing:voice")
        run=button(actions,if(mode=="ai") "Generate" else "Convert","writing:run")
        insert=button(actions,"Replace text","writing:insert").apply { isEnabled=false }
        addView(actions)
        val recovery=LinearLayout(context)
        button(recovery,"Regenerate / Retry","writing:retry")
        button(recovery,"Cancel","writing:cancel")
        addView(recovery)
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        addView(results,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(if(landscape) 110 else 180)))
        val bottom=LinearLayout(context)
        button(bottom,"Back to keyboard","writing:keyboard")
        if(mode=="ai") button(bottom,"Custom tool","writing:custom")
        addView(bottom)
        val body=LinearLayout(context).apply { orientation=VERTICAL }
        while(isNotEmpty()) { val item=getChildAt(0); removeViewAt(0); body.addView(item) }
        addView(ScrollView(context).apply { tag="WritingPanelScroll"; addView(body) },LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val maximum=context.dp(if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 140 else 280)
        val allowed=if(View.MeasureSpec.getMode(heightMeasureSpec)==View.MeasureSpec.UNSPECIFIED) maximum else minOf(maximum,View.MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(allowed,View.MeasureSpec.AT_MOST))
    }
    private fun languageButton(row:LinearLayout,selected:String,prefix:String) {
        val language=button(row,if(selected=="auto") "Detect language ▾" else java.util.Locale(selected).getDisplayLanguage(java.util.Locale.ENGLISH)+" ▾",prefix.removeSuffix(":"),weighted=false)
        language.setOnClickListener {
            PopupMenu(context,language).apply {
                val languages=if(mode=="translate") (if(prefix.contains("source")) listOf("auto") else emptyList())+listOf("ur","en")+com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages().filter { it !in listOf("ur","en") } else listOf("ur","en")
                languages.forEachIndexed { index,value -> menu.add(0,index,index,if(value=="auto") "Detect Language (AI)" else java.util.Locale(value).getDisplayLanguage(java.util.Locale.ENGLISH)) }
                setOnMenuItemClickListener { item -> command(prefix+languages[item.itemId]); true }
            }.show()
        }
    }
    private fun style(button:Button) {
        button.setTextColor(palette.text); button.isSoundEffectsEnabled=false
        button.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(palette.surface,context.dp(7).toFloat()),null)
    }
    private fun button(row:LinearLayout,label:String,value:String,weighted:Boolean=true):Button = KeyButton(context).apply {
        style(this); text=label; tag="tool:$value"; textSize=12f; fitLabel(12f)
        if(!weighted) setPadding(context.dp(10),0,context.dp(10),0)
        setOnClickListener { command(value) }
        row.addView(this,LayoutParams(if(weighted) 0 else LayoutParams.WRAP_CONTENT,context.dp(38),if(weighted) 1f else 0f).apply { setMargins(context.dp(2),context.dp(3),context.dp(2),context.dp(3)) })
    }
    fun update(draft:String,result:String,message:String,working:Boolean,variants:List<String>) {
        preview.text=result.ifEmpty { draft.ifEmpty { "Type a draft, or use selected text." } }
        status.text=message; busy.visibility=if(working) VISIBLE else GONE
        run.isEnabled=draft.isNotBlank() && !working; insert.isEnabled=result.isNotEmpty() && !working
        cards.removeAllViews()
        val values=variants.ifEmpty { if(result.isEmpty()) emptyList() else listOf(result) }
        results.visibility=if(values.isEmpty()) GONE else VISIBLE
        // Keep just the draft at the top when result cards are present.
        if(values.isNotEmpty()) { preview.visibility=GONE } else preview.visibility=VISIBLE
        values.take(3).forEachIndexed { index,value ->
            val card=LinearLayout(context).apply {
                orientation=VERTICAL; setPadding(context.dp(10),context.dp(8),context.dp(10),context.dp(8))
                background=rounded(palette.surface,context.dp(10).toFloat()); tag="ResultCard:$index"
            }
            val text=TextView(context).apply { this.text=value; textSize=15f; setTextColor(palette.text); setTextIsSelectable(true); tag="ResultText:$index" }
            card.addView(text)
            val row=LinearLayout(context)
            button(row,"Replace","writing:replace:$index").apply { isEnabled=!working; setTextColor(0xFFFFFFFF.toInt()); background=rounded(palette.accent,context.dp(6).toFloat()) }
            button(row,"Insert","writing:insert:$index").apply { isEnabled=!working }
            button(row,"Copy","writing:copy:$index")
            val detail=LinearLayout(context)
            button(detail,"Show changes","writing:changes:$index").apply {
                setOnClickListener {
                    val showing=text.tag=="Changes:$index"
                    text.tag=if(showing) "ResultText:$index" else "Changes:$index"
                    text.text=if(showing) value else changes(draft,value)
                    this.text=if(showing) "Show changes" else "Hide changes"
                }
            }
            card.addView(row); card.addView(detail); cards.addView(card,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { bottomMargin=context.dp(8) })
        }
    }
    fun customInstruction(value:String) {
        preview.visibility=VISIBLE; preview.text=value.ifEmpty { "Type your custom instruction (up to 90 characters)." }
        results.visibility=GONE; busy.visibility=GONE
        status.setText(dev.raabta.keyboard.R.string.custom_instruction_notice)
        run.setText(dev.raabta.keyboard.R.string.writing_use_tool); run.isEnabled=value.isNotBlank(); insert.isEnabled=false
    }
    private fun changes(before:String,after:String):CharSequence {
        var start=0
        while(start<minOf(before.length,after.length) && before[start]==after[start]) start++
        var end=0
        while(end<minOf(before.length-start,after.length-start) && before[before.lastIndex-end]==after[after.lastIndex-end]) end++
        val prefix="Original:\n"; val middle="\n\nProposed:\n"
        return SpannableString(prefix+before+middle+after).apply {
            if(before.length-start-end>0) setSpan(BackgroundColorSpan(0x55EB6A6A),prefix.length+start,prefix.length+before.length-end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(after.length-start-end>0) setSpan(BackgroundColorSpan(0x5555BB88),prefix.length+before.length+middle.length+start,length-end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
