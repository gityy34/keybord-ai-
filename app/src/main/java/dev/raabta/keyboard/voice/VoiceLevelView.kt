package dev.raabta.keyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp

/** Smoothed timeline of actual recognizer RMS. Silence decays; no random/synthetic audio levels. */
class VoiceLevelView(context: Context) : View(context) {
    private val palette=Palette.from(context)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=palette.accent; strokeCap=Paint.Cap.ROUND; strokeWidth=context.dp(3).toFloat() }
    private val samples=FloatArray(32)
    private var target=0f
    private var displayed=0f
    private var lastSample=0L
    var animating=false
        private set
    private val frame=object:Runnable {
        override fun run() {
            if(!animating || !isAttachedToWindow || !isShown) return
            if(SystemClock.uptimeMillis()-lastSample>250) target=0f
            displayed+=(target-displayed)*0.35f
            samples.copyInto(samples,0,1,samples.size); samples[samples.lastIndex]=displayed
            invalidate(); postDelayed(this,33)
        }
    }
    init { tag="VoiceLevel"; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setListening(value:Boolean) {
        animating=value; removeCallbacks(frame)
        if(value && isAttachedToWindow && isShown) post(frame)
        if(!value) { target=0f; displayed=0f; samples.fill(0f); invalidate() }
    }
    fun setLevel(rms:Float) {
        target=if(rms.isFinite()) ((rms+2f)/12f).coerceIn(0f,1f) else 0f
        lastSample=SystemClock.uptimeMillis(); samples.copyInto(samples,0,1,samples.size); samples[samples.lastIndex]=target; postInvalidateOnAnimation()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if(animating) post(frame) }
    override fun onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView:View,visibility:Int) {
        super.onVisibilityChanged(changedView,visibility)
        // Visibility callbacks may run during the View superclass constructor.
        if(visibility==VISIBLE) { if(animating && isAttachedToWindow) { removeCallbacks(frame); post(frame) } }
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val gap=width.toFloat()/samples.size
        for(i in samples.indices) {
            paint.color=if(palette.style==dev.raabta.keyboard.ui.SurfaceStyle.GLASS) palette.secondary else if(palette.colorful && i%2==1) palette.secondary else palette.accent
            val half=context.dp(1)+(height*0.42f)*samples[i]
            val x=gap*(i+0.5f); canvas.drawLine(x,height/2f-half,x,height/2f+half,paint)
        }
    }
}
