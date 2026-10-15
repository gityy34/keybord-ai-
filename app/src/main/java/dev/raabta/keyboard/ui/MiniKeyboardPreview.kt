package dev.raabta.keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.withScale

/** A complete, scaled layout preview; no screenshot assets or brand marks. */
class MiniKeyboardPreview @JvmOverloads constructor(context: Context, private var colors: Palette = Palette.from(context)) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun box(canvas: Canvas,x:Float,y:Float,w:Float,h:Float,color:Int,r:Float=4f) {
        paint.shader=null; paint.color=color; paint.style=Paint.Style.FILL; canvas.drawRoundRect(RectF(x,y,x+w,y+h),r,r,paint)
        if (colors.keyBorders) {
            paint.style=Paint.Style.STROKE; paint.strokeWidth=0.8f; paint.color=colors.border
            canvas.drawRoundRect(RectF(x+0.4f,y+0.4f,x+w-0.4f,y+h-0.4f),r,r,paint); paint.style=Paint.Style.FILL
        }
    }
    private fun label(canvas: Canvas,value:String,x:Float,y:Float,size:Float,color:Int) {
        paint.color=color; paint.textSize=size; paint.textAlign=Paint.Align.CENTER
        paint.typeface=android.graphics.Typeface.DEFAULT
        canvas.drawText(value,x,y,paint)
    }
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx=width/174f; val sy=height/135f
        canvas.withScale(sx,sy) {
        paint.shader = colors.backgroundGradient?.takeIf { it.size > 1 }?.let {
            LinearGradient(0f,0f,174f,135f,it.toIntArray(),null,Shader.TileMode.CLAMP)
        }
        paint.style=Paint.Style.FILL; paint.color=colors.background; canvas.drawRoundRect(RectF(0f,0f,174f,135f),8f,8f,paint); paint.shader=null
        box(canvas,3f,3f,168f,15f,colors.surface,8f)
        listOf("the","I","you").forEachIndexed { i,v -> label(canvas,v,30f+i*57f,14f,8f,colors.text) }
        listOf("✦","✎","↻","☷","⇄","♩","▦").forEachIndexed { i,v ->
            val x=3f+i*24f
            box(canvas,x,21f,22f,17f,colors.surface,4f)
            label(canvas,v,x+11f,33f,9f,colors.icon(i))
        }
        fun row(keys:String,y:Float,left:Float=3f,keyWidth:Float=15f,gap:Float=2f) {
            keys.forEachIndexed { i,c ->
                val x=left+i*(keyWidth+gap)
                box(canvas,x,y,keyWidth,18f,colors.key,3f)
                label(canvas,c.toString(),x+keyWidth/2,y+13f,10f,colors.text)
            }
        }
        row("QWERTYUIOP",41f)
        row("ASDFGHJKL",61f,11f,15f,3f)
        box(canvas,3f,81f,21f,18f,colors.surface,3f); label(canvas,"⇧",13f,94f,11f,colors.text)
        row("ZXCVBNM",81f,27f,15f,3f)
        box(canvas,153f,81f,18f,18f,colors.surface,3f); label(canvas,"⌫",162f,94f,10f,colors.text)
        box(canvas,3f,101f,24f,29f,colors.surface,3f); label(canvas,"123",15f,119f,8f,colors.text)
        box(canvas,29f,101f,20f,29f,colors.surface,3f); label(canvas,"☺",39f,119f,11f,colors.text)
        box(canvas,51f,101f,20f,29f,colors.surface,3f); label(canvas,"🌐",61f,119f,9f,colors.text)
        box(canvas,73f,101f,69f,29f,colors.key,3f); label(canvas,"Space",107f,119f,9f,colors.text)
        box(canvas,144f,101f,27f,29f,colors.accent,3f); label(canvas,"↵",157f,120f,15f,colors.onAccent)
        }
    }
    fun setPalette(value: Palette) { colors=value; invalidate() }
}
