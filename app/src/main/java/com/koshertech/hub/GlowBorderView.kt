package com.koshertech.hub

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/**
 * A soft, multi-color "Gemini-style" glow that frames the whole screen.
 * Static (no busy rotation) — just a tasteful sweep of color around the edge
 * with a blurred outer bloom. Draws on top of everything but never eats touches.
 */
class GlowBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val d = resources.displayMetrics.density
    private val inset = 3.5f * d          // distance from the screen edge
    private val radius = 30f * d          // corner roundness
    private val strokeW = 2.6f * d        // crisp line width

    // The color sweep that runs around the frame.
    private val sweepColors = intArrayOf(
        0xFFFFFFFF.toInt(), // white
        0xFF8A8A8A.toInt(), // silver
        0xFFFFFFFF.toInt(),
        0xFF5E5E5E.toInt(), // graphite
        0xFFD9D9D9.toInt(),
        0xFFFFFFFF.toInt()  // back to white (seamless)
    )
    private val sweepStops = floatArrayOf(0f, 0.22f, 0.44f, 0.64f, 0.84f, 1f)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var frame = RectF()

    init {
        // Blur mask needs software rendering on this view.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        frame = RectF(inset, inset, w - inset, h - inset)
        val shader = SweepGradient(w / 2f, h / 2f, sweepColors, sweepStops)

        linePaint.shader = shader
        linePaint.strokeWidth = strokeW

        bloomPaint.shader = shader
        bloomPaint.strokeWidth = strokeW * 2.6f
        bloomPaint.alpha = 70
        bloomPaint.maskFilter = BlurMaskFilter(11f * d, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        if (frame.isEmpty) return
        canvas.drawRoundRect(frame, radius, radius, bloomPaint) // soft outer glow
        canvas.drawRoundRect(frame, radius, radius, linePaint)  // crisp edge
    }
}
