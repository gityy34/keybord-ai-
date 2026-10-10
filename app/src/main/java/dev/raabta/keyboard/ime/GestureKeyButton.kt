package dev.raabta.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button

/** One gesture owns each touch sequence; release cannot insert an accidental Space/letter. */
class GestureKeyButton(context:Context) : KeyButton(context) {
    var onMoveCursor: ((Int)->Unit)? = null
    var onSymbol: (()->Unit)? = null
    var onGlideMove: ((Float,Float)->Unit)? = null
    var onGlideEnd: (()->Boolean)? = null
    var onPreview: ((Boolean)->Unit)? = null
    var onCancel: (()->Unit)? = null
    var holdDuration=400L
    var flickDistance=24f * resources.displayMetrics.density
    var enableFlick=false
    private val handler=Handler(Looper.getMainLooper())
    private var x=0f; private var y=0f; private var lastX=0f
    private var consumed=false; private var moved=false
    private val held=Runnable { if(isPressed && !moved && onSymbol!=null) { consumed=true; onSymbol?.invoke(); onPreview?.invoke(false) } }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture(); x=event.x; lastX=x; y=event.y; consumed=false; moved=false; isPressed=true
                onPreview?.invoke(true); onGlideMove?.invoke(event.rawX,event.rawY)
                if(onSymbol!=null) handler.postDelayed(held,holdDuration)
            }
            MotionEvent.ACTION_MOVE -> {
                if(kotlin.math.abs(event.x-x)>ViewConfiguration.get(context).scaledTouchSlop || kotlin.math.abs(event.y-y)>ViewConfiguration.get(context).scaledTouchSlop) {
                    moved=true; handler.removeCallbacks(held); onPreview?.invoke(false)
                }
                if(consumed && onMoveCursor==null) return true
                val move=onMoveCursor
                if(move!=null && moved) {
                    val step=12f*resources.displayMetrics.density
                    val count=((event.x-lastX)/step).toInt().coerceIn(-20,20)
                    if(count!=0) { move(count); lastX+=count*step; consumed=true }
                    // Keep subsequent cursor steps active even after consuming the tap.
                } else if(enableFlick && event.y-y < -flickDistance && onSymbol!=null) {
                    consumed=true; onSymbol?.invoke()
                } else if(moved) onGlideMove?.invoke(event.rawX,event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(held); isPressed=false; onPreview?.invoke(false)
                val glide=if(!consumed && moved) onGlideEnd?.invoke()==true else false
                if(!consumed && !glide && !moved) performClick()
                else if(!consumed && !glide && onGlideEnd==null && onMoveCursor==null) performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
            else -> { if(event.pointerCount>1) cancelGesture() }
        }
        return true
    }
    override fun performClick():Boolean = super.performClick()
    fun cancelGesture() { handler.removeCallbacks(held); isPressed=false; consumed=true; onPreview?.invoke(false); onCancel?.invoke() }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}

/** Native accessibility click semantics remain available on the floating drag handle. */
class DragHandle(context:Context):androidx.appcompat.widget.AppCompatTextView(context) {
    var dragEnabled=false
    var onStart:(()->Unit)?=null
    var onDrag:((Float,Float)->Unit)?=null
    private var x=0f; private var y=0f
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!dragEnabled) return super.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x=event.rawX; y=event.rawY; isPressed=true; onStart?.invoke() }
            MotionEvent.ACTION_MOVE -> onDrag?.invoke(event.rawX-x,event.rawY-y)
            MotionEvent.ACTION_UP -> { isPressed=false; performClick() }
            MotionEvent.ACTION_CANCEL -> isPressed=false
        }
        return true
    }
    override fun performClick():Boolean=super.performClick()
}
