package dev.raabta.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Button

/** Keeps touch repetition and accessibility clicks on the same edit path. */
class RepeatingDeleteButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Button(context, attrs) {
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    var holdDelayMillis = 400L
    var repeatMillis = 55L
    private val repeatClick = object : Runnable {
        override fun run() {
            if (repeating) {
                performClick()
                handler.postDelayed(this, repeatMillis.coerceIn(40, 150))
            }
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelRepeat()
                isPressed = true
                performClick()
                repeating = true
                handler.postDelayed(repeatClick, holdDelayMillis.coerceIn(200, 800))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelRepeat()
            MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x > width || event.y > height) cancelRepeat()
        }
        return true
    }

    fun cancelRepeat() {
        repeating = false
        handler.removeCallbacks(repeatClick)
        isPressed = false
    }

    override fun onDetachedFromWindow() {
        cancelRepeat()
        super.onDetachedFromWindow()
    }
}
