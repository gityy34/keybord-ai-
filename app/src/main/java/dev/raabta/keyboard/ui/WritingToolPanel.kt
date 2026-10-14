package dev.raabta.keyboard.ui

import androidx.core.view.isNotEmpty
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.raabta.keyboard.ime.KeyButton
import dev.raabta.keyboard.ime.ToolbarButton
import dev.raabta.keyboard.tools.AiTools

/** A single keyboard-sized workspace: explicit source → provider/model → review → editor action. */
class WritingToolPanel @JvmOverloads constructor(
    context: Context, private val mode: String="ai", source: String="ur", target: String="en",
    private val selectedStyle: String="rewrite", private val command: (String)->Unit={},
) : LinearLayout(context) {
    private val palette=Palette.from(context)
    private val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; isBaselineAligned=false }
    private lateinit var heading:TextView
    private var selector:LinearLayout?=null
    private val sections=mutableListOf<View>()
    private val preview=TextView(context).apply {
        tag="ToolPreview"; textSize=15f; typeface=Typeface.DEFAULT; setTextColor(palette.text)
        setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(8)); textDirection=TEXT_DIRECTION_FIRST_STRONG
        setText(dev.raabta.keyboard.R.string.writing_draft_hint)
    }
    private val status=TextView(context).apply {
        tag="ToolStatus"; textSize=12f; setTextColor(palette.muted); maxLines=2
        setPadding(context.dp(6),context.dp(3),context.dp(6),context.dp(3)); accessibilityLiveRegion=ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val cards=LinearLayout(context).apply { orientation=VERTICAL }
    private val draft=ScrollView(context).apply { tag="ToolDraftScroll"; addView(preview); background=palette.shape(radius=context.dp(12).toFloat()) }
    private val results=ScrollView(context).apply { tag="ToolResults"; addView(cards); visibility=GONE }
    private val primary:Button
    private val done:Button
    private var compact=false
    private var editing=false
    private var custom=false
    private var hasResults=false
    private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate=true; visibility=GONE; tag="ToolProgress"; indeterminateTintList=ColorStateList.valueOf(palette.accent) }
    init {
        orientation=VERTICAL; tag="WritingTool"; setPadding(context.dp(7),0,context.dp(7),context.dp(6)); background = palette.backgroundDrawable()
        header.addView(ToolbarButton(context).apply {
            text="‹"; tag="tool:writing:close"; contentDescription="Back to keyboard"; style(this); setOnClickListener { command("writing:close") }
        },LayoutParams(context.dp(48),context.dp(48)))
        header.addView(TextView(context).apply {
            text=if(mode=="ai") AiTools.get(selectedStyle).group.replace("AI assistance","AI Assistant") else if(mode=="translate") "Translate" else "Roman Urdu"
            textSize=17f; setTextColor(palette.text); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
        }.also { heading=it },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        done=button(header,"Done","writing:keyboard",false).apply {
            tag="tool:writing:done"; visibility=GONE; setOnClickListener { command(if(custom) "writing:run" else "writing:keyboard") }
        }
        header.addView(ToolbarButton(context).apply { text="↻"; tag="tool:writing:retry"; contentDescription="Regenerate or retry"; style(this); setOnClickListener { command("writing:retry") } },LayoutParams(context.dp(44),context.dp(48)))
        addView(header)
        val selection=LinearLayout(context).apply { isBaselineAligned=false }
        if(mode=="ai") {
            val groups=button(selection,"Tools ▾","writing:groups")
            groups.setOnClickListener {
                val choices=if(compact) AiTools.all.map { it.label }+listOf("Connections","Custom tool") else AiTools.groups+listOf("Connections","Custom tool")
                ThemedChoices.show(groups,choices) { index ->
                    val count=if(compact) AiTools.all.size else AiTools.groups.size
                    when(index) {
                        count -> command("ai:connection")
                        count+1 -> command("writing:custom")
                        else -> command("writing:style:"+if(compact) AiTools.all[index].id else AiTools.all.first { it.group==AiTools.groups[index] }.id)
                    }
                }
            }
            language(selection,target,"writing:target:")
        } else if(mode=="translate") {
            language(selection,source,"writing:source:")
            button(selection,"⇄","writing:swap",false).contentDescription="Swap source and target languages"
            language(selection,target,"writing:target:")
        }
        if(selection.isNotEmpty()) { selector=selection; section(selection) }
        if(mode=="ai") {
            val row=LinearLayout(context).apply { isBaselineAligned=false }
            val scroll=HorizontalScrollView(context).apply { tag="AiToolChoices"; isHorizontalScrollBarEnabled=false; addView(row) }
            AiTools.all.filter { it.group==AiTools.get(selectedStyle).group }.forEach { tool ->
                val choice=button(row,tool.label,"writing:style:${tool.id}",false).apply {
                    isSelected=tool.id==selectedStyle; setTextColor(if(isSelected) palette.onAccent else palette.text)
                    background=palette.pressed(if(isSelected) palette.accent else palette.surface,context.dp(10).toFloat())
                }
                if(choice.isSelected) scroll.addOnLayoutChangeListener(object:OnLayoutChangeListener {
                    override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int) { if(r>l) { scroll.removeOnLayoutChangeListener(this); scroll.scrollTo((choice.right-scroll.width).coerceAtLeast(0),0) } }
                })
            }
            section(scroll)
        } else if(mode=="translate") {
            val models=LinearLayout(context).apply { tag="TranslationModels" }; button(models,"Download model","writing:download"); button(models,"Speak → insert","writing:voice"); section(models)
        }
        section(progress,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(3)))
        section(status)
        addView(draft,LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        addView(results,LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        val actions=LinearLayout(context).apply { isBaselineAligned=false }
        button(actions,"Text","writing:nearby").contentDescription="Use current sentence"
        button(actions,"Selection","writing:selection").contentDescription="Use selected text"
        button(actions,"Edit","writing:keyboard").contentDescription="Edit the local tool draft"
        primary=button(actions,if(mode=="ai") "Generate" else "Convert","writing:run").apply { setTextColor(palette.onAccent); background=palette.pressed(palette.accent,context.dp(10).toFloat()) }
        section(actions)
        val extra=LinearLayout(context).apply { tag="WritingExtra" }
        if(mode=="ai") { button(extra,"Connection","ai:connection"); button(extra,"Custom tool","writing:custom") }
        button(extra,"Cancel","writing:cancel")
        section(extra)
    }
    private fun section(view:View,params:LayoutParams=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)) { sections.add(view); addView(view,params) }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        if(!editing) {
            compact=MeasureSpec.getMode(heightMeasureSpec)!=MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(heightMeasureSpec)<context.dp(280)
            findViewWithTag<View>("AiToolChoices")?.visibility=if(compact) GONE else VISIBLE
            findViewWithTag<View>("WritingExtra")?.visibility=if(compact || hasResults) GONE else VISIBLE
            status.maxLines=if(compact) 1 else 2
            adaptHeader()
            findViewWithTag<Button>("tool:writing:groups")?.apply {
                text=if(compact) AiTools.get(selectedStyle).label else context.getString(dev.raabta.keyboard.R.string.tools_menu_title)
                isSelected=compact; setTextColor(if(compact) palette.onAccent else palette.text)
                background=palette.pressed(if(compact) palette.accent else palette.surface,context.dp(10).toFloat())
            }
            findViewWithTag<View>("TranslationModels")?.visibility=if(hasResults) GONE else VISIBLE
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    private fun adaptHeader() {
        val row=selector ?: return
        val folded=compact && !editing
        val owner=if(folded) header else this
        if(row.parent!==owner) {
            (row.parent as? android.view.ViewGroup)?.removeView(row)
            owner.addView(row,1,if(folded) LayoutParams(0,context.dp(48),1f) else LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        }
        heading.visibility=if(folded) GONE else VISIBLE
    }
    private fun style(button:Button) { button.setTextColor(palette.text); button.isSoundEffectsEnabled=false; button.background=palette.pressed(radius=context.dp(10).toFloat()) }
    private fun button(row:LinearLayout,label:String,value:String,weighted:Boolean=true):Button=KeyButton(context).apply {
        style(this); text=label; tag="tool:$value"; textSize=12f; fitLabel(12f)
        if(!weighted) setPadding(context.dp(12),0,context.dp(12),0)
        setOnClickListener { command(value) }
        row.addView(this,LayoutParams(if(weighted) 0 else LayoutParams.WRAP_CONTENT,context.dp(42),if(weighted) 1f else 0f).apply { setMargins(context.dp(2),context.dp(2),context.dp(2),context.dp(2)) })
    }
    private fun language(row:LinearLayout,selected:String,prefix:String) {
        val title=if(selected=="auto") "Detect" else java.util.Locale(selected).getDisplayLanguage(java.util.Locale.ENGLISH)
        val button=button(row,"$title ▾",prefix.removeSuffix(":"))
        button.contentDescription=(if(prefix.contains("source")) "Source language: " else "Target language: ")+title
        button.setOnClickListener {
            val values=if(mode=="translate") (if(prefix.contains("source")) listOf("auto") else emptyList())+listOf("ur","en")+com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages().filter { it !in listOf("ur","en") } else listOf("ur","en")
            val labels=values.map { if(it=="auto") "Detect Language (AI)" else java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH) }
            ThemedChoices.show(button,labels,values.indexOf(selected)) { command(prefix+values[it]) }
        }
    }
    fun setEditing(value:Boolean) {
        editing=value; adaptHeader(); done.visibility=if(value) VISIBLE else GONE
        sections.forEach { it.visibility=if(value || (it.tag=="WritingExtra" && (hasResults || compact))) GONE else VISIBLE }
        progress.visibility=GONE
        draft.visibility=if(value || !hasResults) VISIBLE else GONE
        draft.layoutParams=LayoutParams(LayoutParams.MATCH_PARENT,if(value) context.dp(if(resources.configuration.fontScale>1.2f) 48 else 40) else 0,if(value) 0f else 1f)
        results.visibility=if(!value && hasResults) VISIBLE else GONE
        preview.maxLines=if(value) 1 else Int.MAX_VALUE
        requestLayout()
    }
    fun update(input:String,result:String,message:String,working:Boolean,variants:List<String>) {
        custom=false; preview.text=input.ifEmpty { "Use text/selection, or Edit a local draft." }; status.text=message
        progress.visibility=if(working && !editing) VISIBLE else GONE
        primary.isEnabled=input.isNotBlank() && !working
        val values=variants.ifEmpty { if(result.isEmpty()) emptyList() else listOf(result) }.take(3)
        hasResults=values.isNotEmpty(); findViewWithTag<View>("WritingExtra").visibility=if(hasResults || editing) GONE else VISIBLE; primary.text=if(hasResults) "Regenerate" else if(mode=="ai") "Generate" else "Convert"
        cards.removeAllViews()
        values.forEachIndexed { index,value ->
            val card=LinearLayout(context).apply { orientation=VERTICAL; tag="ResultCard:$index"; setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(8)); background=palette.shape(radius=context.dp(12).toFloat()) }
            val content=TextView(context).apply {
                text=value; textSize=16f; typeface=Typeface.DEFAULT; tag="ResultText:$index"; setTextColor(palette.text)
                textDirection=TEXT_DIRECTION_FIRST_STRONG; setTextIsSelectable(true)
            }
            card.addView(content)
            val actions=LinearLayout(context)
            button(actions,"Replace","writing:replace:$index").apply { isEnabled=!working; setTextColor(palette.onAccent); background=palette.pressed(palette.accent,context.dp(9).toFloat()) }
            button(actions,"Insert","writing:insert:$index").isEnabled=!working
            button(actions,"Copy","writing:copy:$index")
            card.addView(actions)
            button(card,"Show changes","writing:changes:$index",false).apply {
                setOnClickListener {
                    val showing=content.tag=="Changes:$index"; content.tag=if(showing) "ResultText:$index" else "Changes:$index"
                    content.text=if(showing) value else changes(input,value); text=if(showing) "Show changes" else "Hide changes"
                }
            }
            cards.addView(card,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { bottomMargin=context.dp(8) })
        }
        draft.visibility=if(editing || !hasResults) VISIBLE else GONE; results.visibility=if(!editing && hasResults) VISIBLE else GONE
    }
    fun customInstruction(value:String) {
        custom=true; preview.text=value.ifEmpty { "Type your custom instruction." }; status.setText(dev.raabta.keyboard.R.string.custom_instruction_notice)
        primary.setText(dev.raabta.keyboard.R.string.writing_use_tool); primary.isEnabled=value.isNotBlank(); done.isEnabled=value.isNotBlank()
        setEditing(true)
    }
    private fun changes(before:String,after:String):CharSequence {
        var start=0; while(start<minOf(before.length,after.length) && before[start]==after[start]) start++
        var end=0; while(end<minOf(before.length-start,after.length-start) && before[before.lastIndex-end]==after[after.lastIndex-end]) end++
        val prefix="Original:\n"; val middle="\n\nProposed:\n"
        return SpannableString(prefix+before+middle+after).apply {
            if(before.length-start-end>0) setSpan(BackgroundColorSpan(0x55EB6A6A),prefix.length+start,prefix.length+before.length-end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(after.length-start-end>0) setSpan(BackgroundColorSpan(0x5555BB88),prefix.length+before.length+middle.length+start,length-end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
