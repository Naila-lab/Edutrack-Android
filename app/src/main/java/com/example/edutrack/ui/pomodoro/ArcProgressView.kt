package com.example.edutrack.ui.pomodoro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 16f
        color       = 0x33FFFFFF   // putih transparan (track)
        strokeCap   = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 16f
        color       = 0xFFFFFFFF.toInt()   // putih solid (progress)
        strokeCap   = Paint.Cap.ROUND
    }

    // 0f – 1f
    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad    = trackPaint.strokeWidth / 2f + 4f
        oval.set(pad, pad, width - pad, height - pad)

        // Track (full circle)
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)

        // Progress arc (clockwise from top)
        val sweep = 360f * progress
        canvas.drawArc(oval, -90f, sweep, false, progressPaint)
    }
}