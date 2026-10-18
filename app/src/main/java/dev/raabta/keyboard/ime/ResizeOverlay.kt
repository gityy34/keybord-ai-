package dev.raabta.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.Palette
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.rounded

/** Which edge of the live resize frame is being moved. */
internal enum class ResizeEdge { TOP, BOTTOM }

/**
 * A small, keyboard-local resize surface. The keyboard remains visible below the
 * translucent scrim while the two edge grips change its measured height. The
 * old SeekBar is retained as a zero-size compatibility control for settings and
 * accessibility clients that still address the old tag.
 */
@SuppressLint("ViewConstructor")
internal class ResizeOverlay(
    context: Context,
    private val palette: Palette,
    private val minimumScale: Int,
    private val maximumScale: Int,
    initialScale: Int,
    private val onReset: () -> Unit,
    private val onCancel: () -> Unit,
    private val onDone: () -> Unit,
    private val onDragStart: (ResizeEdge, Int) -> Unit,
    private val onScaleChanged: (ResizeEdge, Int, Float, Int, Boolean) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val boundsRect = RectF()
    private val topGrip: View
    private val bottomGrip: View
    private var activeEdge: ResizeEdge? = null
    private var dragDownY = 0f
    private var dragDownScale = initialScale
    private var currentScale = initialScale
    private var limitExceeded = false
    private var syncingSlider = false
    private val scalePerPixel = 1f / (resources.displayMetrics.density * 2f)
    private var compatibilitySlider: SeekBar

    init {
        tag = "ResizePanel"
        setWillNotDraw(false)
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.resize_keyboard) + ". Drag the top or bottom line."

        topGrip = grip(ResizeEdge.TOP, "ResizeTopHandle", "Drag top edge to resize keyboard")
        bottomGrip = grip(ResizeEdge.BOTTOM, "ResizeBottomHandle", "Drag bottom edge to resize keyboard")
        addView(topGrip, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(46)).apply { gravity = Gravity.TOP })
        addView(bottomGrip, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(46)).apply { gravity = Gravity.BOTTOM })

        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(4), context.dp(3), context.dp(4), context.dp(3))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        actions.addView(actionButton("↻", "ResizeReset", "Reset keyboard size") { onReset() },
            LinearLayout.LayoutParams(context.dp(66), context.dp(46)).apply { setMargins(context.dp(3), 0, context.dp(3), 0) })
        actions.addView(actionButton("Cancel", "ResizeCancel", "Cancel keyboard resize") { onCancel() },
            LinearLayout.LayoutParams(context.dp(72), context.dp(46)).apply { setMargins(context.dp(3), 0, context.dp(3), 0) })
        actions.addView(Button(context).apply {
            text = "↕"
            tag = "ResizeMove"
            textSize = 20f
            isAllCaps = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setTextColor(palette.onAccent)
            background = rounded(palette.accent, context.dp(23).toFloat())
            contentDescription = "Resize handles. Drag the top or bottom line."
            setOnClickListener { announceForAccessibility("Drag the top or bottom resize line") }
        }, LinearLayout.LayoutParams(context.dp(48), context.dp(46)).apply { setMargins(context.dp(3), 0, context.dp(3), 0) })
        actions.addView(actionButton("Done", "ResizeDone", "Save keyboard size") { onDone() },
            LinearLayout.LayoutParams(context.dp(66), context.dp(46)).apply { setMargins(context.dp(3), 0, context.dp(3), 0) })
        addView(actions, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(52)).apply { gravity = Gravity.CENTER })

        // Keep the old automation/settings hook without taking visual space.
        compatibilitySlider = SeekBar(context).apply {
            tag = "ResizeSlider"
            max = (maximumScale - minimumScale).coerceAtLeast(1)
            progress = (initialScale - minimumScale).coerceIn(0, max)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (syncingSlider) return
                    val proposed = (minimumScale + progress).toFloat()
                    currentScale = proposed.toInt()
                    onScaleChanged(ResizeEdge.BOTTOM, currentScale, proposed, currentScale, false)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        addView(compatibilitySlider, FrameLayout.LayoutParams(1, 1).apply { gravity = Gravity.TOP or Gravity.START })
    }

    private fun actionButton(label: String, id: String, description: String, action: () -> Unit) = Button(context).apply {
        text = label
        tag = id
        textSize = if (label.length > 5) 12f else 14f
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setTextColor(palette.onAccent)
        background = palette.pressed(palette.accent, context.dp(22).toFloat())
        contentDescription = description
        setOnClickListener { action() }
    }

    private fun grip(edge: ResizeEdge, id: String, description: String) = View(context).apply {
        tag = id
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnTouchListener { view, event ->
            val handled = handleTouch(edge, event)
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            handled
        }
    }

    private fun handleTouch(edge: ResizeEdge, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeEdge = edge
                dragDownY = event.rawY
                dragDownScale = currentScale
                limitExceeded = false
                onDragStart(edge, dragDownScale)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeEdge != edge) return true
                val direction = if (edge == ResizeEdge.BOTTOM) 1f else -1f
                val proposed = dragDownScale + (event.rawY - dragDownY) * direction * scalePerPixel
                val clamped = proposed.toInt().coerceIn(minimumScale, maximumScale)
                limitExceeded = proposed < minimumScale || proposed > maximumScale
                currentScale = clamped
                onScaleChanged(edge, dragDownScale, proposed, clamped, limitExceeded)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeEdge == edge) {
                    activeEdge = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onDragEnd()
                    limitExceeded = false
                    invalidate()
                }
                return true
            }
        }
        return true
    }

    fun setScale(scale: Int) {
        currentScale = scale.coerceIn(minimumScale, maximumScale)
        syncingSlider = true
        compatibilitySlider.progress = currentScale - minimumScale
        syncingSlider = false
        invalidate()
    }

    fun setLimitExceeded(exceeded: Boolean) {
        limitExceeded = exceeded
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val edgeColor = if (limitExceeded) Color.rgb(220, 55, 55) else palette.accent
        val radius = context.dp(26).toFloat()
        scrimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, if (palette.dark) 128 else 72)
        boundsRect.set(1f, 1f, width - 1f, height - 1f)
        canvas.drawRoundRect(boundsRect, radius, radius, scrimPaint)
        borderPaint.color = edgeColor
        borderPaint.strokeWidth = context.dp(if (limitExceeded) 2 else 1).toFloat()
        boundsRect.set(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        canvas.drawRoundRect(boundsRect, radius, radius, borderPaint)
        handlePaint.color = edgeColor
        handlePaint.strokeWidth = context.dp(if (limitExceeded) 4 else 3).toFloat()
        val inset = context.dp(28).toFloat()
        canvas.drawLine(width / 2f - inset, context.dp(15).toFloat(), width / 2f + inset, context.dp(15).toFloat(), handlePaint)
        canvas.drawLine(width / 2f - inset, height - context.dp(15).toFloat(), width / 2f + inset, height - context.dp(15).toFloat(), handlePaint)
    }
}
