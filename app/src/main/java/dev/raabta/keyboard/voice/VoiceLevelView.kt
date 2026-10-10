package dev.raabta.keyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp

/** Real recognizer RMS only; no audio buffering or synthetic recording animation. */
class VoiceLevelView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Palette.from(context).accent; strokeCap=Paint.Cap.ROUND; strokeWidth=context.dp(3).toFloat() }
    private var level=0f
    init { tag="VoiceLevel"; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setLevel(rms:Float) { level=if(rms.isFinite()) ((rms+2f)/12f).coerceIn(0f,1f) else 0f; postInvalidateOnAnimation() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val count=15; val gap=width.toFloat()/count
        for(i in 0 until count) {
            val taper=1f-kotlin.math.abs(i-count/2f)/(count/2f)
            val half=context.dp(2)+(height*0.38f)*level*(0.35f+taper*0.65f)
            val x=gap*(i+0.5f)
            canvas.drawLine(x,height/2f-half,x,height/2f+half,paint)
        }
    }
}
