package dev.raabta.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Button

/** Keeps touch repetition and accessibility clicks on the same edit path. */
class RepeatingDeleteButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : androidx.appcompat.widget.AppCompatButton(context, attrs) {
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private var held = false
    private var swiping = false
    private var downX = 0f
    private var downY = 0f
    var onSwipe: ((Int) -> Unit)? = null
    var onSwipeEnd: (() -> Unit)? = null
    var onSwipeCancel: (() -> Unit)? = null
    var holdDelayMillis = 400L
    var repeatMillis = 55L
    private val repeatClick = object : Runnable {
        override fun run() {
            if (repeating) {
                held = true
                performClick()
                handler.postDelayed(this, repeatMillis.coerceIn(40, 150))
            }
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.pointerCount > 1) { cancelRepeat(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelRepeat()
                isPressed = true
                held = false; swiping = false; downX = event.x; downY = event.y
                repeating = true
                handler.postDelayed(repeatClick, holdDelayMillis.coerceIn(200, 800))
            }
            MotionEvent.ACTION_UP -> {
                if (swiping) { swiping = false; onSwipeEnd?.invoke() }
                else if (!held && isPressed) performClick()
                cancelRepeat()
            }
            MotionEvent.ACTION_CANCEL -> cancelRepeat()
            MotionEvent.ACTION_MOVE -> {
                if (!isPressed && !swiping) return true
                val distance = downX - event.x
                val step = 24f * resources.displayMetrics.density
                if (swiping && kotlin.math.abs(event.y - downY) >= step * 2) { cancelRepeat(); return true }
                if (!held && onSwipe != null && (swiping || distance >= step) && kotlin.math.abs(event.y - downY) < step * 2) {
                    repeating = false; handler.removeCallbacks(repeatClick); swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onSwipe?.invoke(if (distance < step / 2) 0 else (distance / step).toInt().coerceAtLeast(1))
                } else if (!swiping && (event.x < 0 || event.y < 0 || event.x > width || event.y > height)) cancelRepeat()
            }
        }
        return true
    }

    fun cancelRepeat() {
        if (swiping) { swiping = false; onSwipeCancel?.invoke() }
        repeating = false
        handler.removeCallbacks(repeatClick)
        isPressed = false
    }

    override fun onDetachedFromWindow() {
        cancelRepeat()
        super.onDetachedFromWindow()
    }
}
