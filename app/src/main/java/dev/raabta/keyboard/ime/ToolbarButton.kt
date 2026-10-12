package dev.raabta.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.dp

/** Vector toolbar artwork is independent of the phone's emoji font. */
class ToolbarButton(context: Context) : KeyButton(context) {
    var caption:String?=null
    private val captionPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign=android.graphics.Paint.Align.CENTER; typeface=android.graphics.Typeface.DEFAULT }
    private var lastIcon = 0
    private var artwork: android.graphics.drawable.Drawable? = null
    override fun onDraw(canvas: Canvas) {
        val icon = when (text.toString()) {
            "🎙" -> R.drawable.ic_microphone
            "■" -> R.drawable.ic_stop
            "🌐" -> R.drawable.ic_globe
            "▦" -> R.drawable.ic_grid
            "✦" -> R.drawable.ic_ai
            "▤" -> R.drawable.ic_clipboard
            "⚙" -> R.drawable.ic_settings
            "☺" -> R.drawable.ic_emoji
            "‹" -> R.drawable.ic_back
            "×" -> R.drawable.ic_close
            "✎" -> R.drawable.ic_grammar
            "↻" -> R.drawable.ic_rewrite
            "☷" -> R.drawable.ic_tone
            "⇄" -> R.drawable.ic_translate
            else -> 0
        }
        if (icon == 0) { super.onDraw(canvas); return }
        if (lastIcon != icon) { lastIcon = icon; artwork = androidx.appcompat.content.res.AppCompatResources.getDrawable(context,icon)?.mutate() }
        artwork?.apply {
            setTint(currentTextColor)
            val size = context.dp(22).coerceAtMost(width).coerceAtMost(height)
            val top=if(caption==null) (height-size)/2 else context.dp(5)
            setBounds((width-size)/2, top, (width+size)/2, top+size)
            draw(canvas)
        }
        caption?.let { label -> captionPaint.color=currentTextColor; captionPaint.textSize=context.dp(if(label.length>8) 8 else 9).toFloat(); canvas.drawText(label,width/2f,height-context.dp(8).toFloat(),captionPaint) }
    }
}
