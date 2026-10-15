package dev.raabta.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import dev.raabta.keyboard.ui.dp

/** Explicit metrics avoid OEM button styles shifting labels outside the key background. */
open class KeyButton(context: Context, attrs: AttributeSet? = null) : AppCompatButton(context, attrs) {
    var symbolHint: String = ""
        set(value) { if(field!=value) { field = value; invalidate() } }
    var hintColor: Int = 0
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private var preferredSp = 16f
    private val measurementPaint=Paint()
    private val measurementMetrics=Paint.FontMetrics()
    init {
        gravity = Gravity.CENTER
        textAlignment = TEXT_ALIGNMENT_GRAVITY
        textDirection = TEXT_DIRECTION_FIRST_STRONG
        typeface = Typeface.DEFAULT
        includeFontPadding = false
        isAllCaps = false
        setSingleLine(true)
        setHorizontallyScrolling(false)
        ellipsize = null
        setPadding(0, 0, 0, 0)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        stateListAnimator = null
    }
    fun fitLabel(preferredSp: Float) {
        this.preferredSp=preferredSp.coerceIn(8f,40f)
        requestLayout()
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        val available=(measuredWidth-compoundPaddingLeft-compoundPaddingRight-context.dp(2)).coerceAtLeast(1)
        val vertical=(measuredHeight-compoundPaddingTop-compoundPaddingBottom-context.dp(2)).coerceAtLeast(1)
        val preferred=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,preferredSp,resources.displayMetrics)
        measurementPaint.set(paint); measurementPaint.textSize=preferred
        val textWidth=measurementPaint.measureText(text.toString()).coerceAtLeast(1f)
        measurementPaint.getFontMetrics(measurementMetrics)
        val textHeight=(measurementMetrics.bottom-measurementMetrics.top).coerceAtLeast(1f)
        val size=(preferred*minOf(1f,available/textWidth,vertical/textHeight)).coerceAtLeast(1f)
        if(kotlin.math.abs(textSize-size)>0.1f) {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size)
            super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (symbolHint.isEmpty()) return
        hintPaint.color = hintColor
        hintPaint.textSize = context.dp(9).toFloat()
        hintPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(symbolHint, width - context.dp(4).toFloat(), context.dp(3) - hintPaint.fontMetrics.ascent, hintPaint)
    }
}
