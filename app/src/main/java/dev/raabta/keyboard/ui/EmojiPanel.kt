package dev.raabta.keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.*
import dev.raabta.keyboard.ime.Candidate
import dev.raabta.keyboard.ime.KeyButton
import dev.raabta.keyboard.ime.LocalSuggestions

/** Original local catalogue and search keywords; does not contact a media service. */
class EmojiPanel @JvmOverloads constructor(context:Context,private val picked:(Candidate)->Unit = {},private val searchMode:(Boolean)->Unit = {},
    private val close:()->Unit = {},private val media:()->Unit = {}):LinearLayout(context) {
    private val palette=Palette.from(context)
    private val options=KeyboardOptions(context)
    private var category="Recent"
    private var query=""
    private val grid=LinearLayout(context).apply { orientation=VERTICAL }
    private val search=KeyButton(context).apply {
        setText(dev.raabta.keyboard.R.string.emoji_search); contentDescription="Search emoji using keyboard"; tag="EmojiSearch"
        setTextColor(palette.muted); background=rounded(palette.surface,context.dp(16).toFloat())
        fitLabel(14f); setOnClickListener { searchMode(true) }
    }
    init {
        tag="EmojiPanel"; orientation=VERTICAL
        addView(search,LayoutParams(LayoutParams.MATCH_PARENT,context.dp(38)))
        val categories=LinearLayout(context)
        listOf("Recent","Faces","Hearts","Nature","Food","Travel","Activities","Objects").forEach { value ->
            categories.addView(button(value,"emoji-category:$value") { category=value; query=""; search.setText(dev.raabta.keyboard.R.string.emoji_search); render() },LayoutParams(LayoutParams.WRAP_CONTENT,context.dp(40)))
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false; addView(categories) })
        addView(ScrollView(context).apply { addView(grid) },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(185)))
        val bottom=LinearLayout(context)
        bottom.addView(button("ABC","EmojiBack",close),LayoutParams(0,context.dp(42),1f))
        bottom.addView(button("GIF / Stickers","EmojiMedia",media),LayoutParams(0,context.dp(42),1f))
        bottom.addView(button("Clear search","EmojiClear") { setQuery("") },LayoutParams(0,context.dp(42),1f))
        addView(bottom); render()
    }
    fun setQuery(value:String) { query=value.take(40); search.text=query.ifEmpty { "Search emoji" }; render() }
    private fun button(label:String,value:String,click:()->Unit)=KeyButton(context).apply {
        text=label; tag=value; fitLabel(12f); setPadding(context.dp(7),0,context.dp(7),0)
        setTextColor(palette.text); background=RippleDrawable(ColorStateList.valueOf(palette.ripple),rounded(palette.background,context.dp(6).toFloat()),null)
        setOnClickListener { click() }
    }
    private fun render() {
        grid.removeAllViews()
        val values=if(query.isNotBlank()) catalogue.filter { it.second.contains(query,ignoreCase=true) }.map { it.first }
            else if(category=="Recent") (options.emoji()+LocalSuggestions.popularEmoji).distinct() else groups[category].orEmpty()
        if(values.isEmpty()) grid.addView(TextView(context).apply { setText(dev.raabta.keyboard.R.string.emoji_no_match); setTextColor(palette.muted); setPadding(context.dp(12),context.dp(12),0,0) })
        val columns=if(options.number("emoji_size")>30) 5 else 7
        values.chunked(columns).forEach { emojis ->
            val row=LinearLayout(context).apply { isBaselineAligned=false }
            emojis.forEach { emoji -> row.addView(KeyButton(context).apply {
                text=emoji; tag="emoji:$emoji"; contentDescription=catalogue.firstOrNull { it.first==emoji }?.second ?: emoji
                fitLabel(options.number("emoji_size").toFloat()); setTextColor(palette.text); background=rounded(palette.background,0f)
                setOnClickListener { picked(Candidate(emoji,emoji,emoji=true)) }
            },LayoutParams(0,context.dp(48),1f)) }
            repeat(columns-emojis.size) { row.addView(View(context),LayoutParams(0,context.dp(48),1f)) }
            grid.addView(row)
        }
    }
    companion object {
        private val groups=mapOf(
            "Faces" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","🙂","🙃","😉","😍","🥰","😘","😎","🤔","😴","😢","😭","😡","🤗","😇","🤩","🥳","😔","😐","😬"),
            "Hearts" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","💕","💖","💔","💯","🙏","👍"),
            "Nature" to listOf("🐱","🐶","🦁","🐼","🐦","🦋","🌹","💐","🌻","🌳","🌙","⭐","☀️","🌧️","🔥","🌈"),
            "Food" to listOf("☕","🍵","🍎","🍌","🍓","🍕","🍔","🍟","🍚","🍰","🎂","🍪","🍫","🥤"),
            "Travel" to listOf("🚗","🚕","🚌","🚆","✈️","🚲","🏠","🏢","🏖️","🌍","🕌","🕋"),
            "Activities" to listOf("⚽","🏏","🏀","🎾","🎉","🎊","🎁","🎈","🎵","🎮","🏆"),
            "Objects" to listOf("📱","💻","⌨️","📚","✏️","📝","💡","🔑","📅","⏰","💰","📷"))
        private val keywords=mapOf("😊" to "happy smile khushi خوش خوشی", "😂" to "laugh funny hansi ہنسی", "❤️" to "heart love pyar محبت پیار", "💔" to "broken heart sad اداس", "😭" to "cry sad rona رونا", "😢" to "sad اداس", "🎂" to "birthday cake سالگرہ", "☕" to "coffee tea chai چائے", "🔥" to "fire aag آگ", "🐱" to "cat billi بلی", "🐶" to "dog kutta کتا", "🙏" to "thanks prayer shukriya شکریہ دعا", "🌹" to "rose flower gulab گلاب", "🎉" to "party congratulations mubarak مبارک", "📚" to "books kitab کتاب", "✈️" to "plane travel safar سفر")
        private val catalogue=groups.flatMap { (group,values) -> values.map { it to "$group ${keywords[it].orEmpty()}" } }
        fun search(query:String)=catalogue.filter { it.second.contains(query,true) }.map { it.first }
    }
}
