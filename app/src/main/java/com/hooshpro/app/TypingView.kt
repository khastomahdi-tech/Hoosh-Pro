package com.hooshpro.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/** نشانگر «در حال نوشتن…» — سه نقطه‌ی متحرک */
class TypingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var anim: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onDraw(canvas: Canvas) {
        val t = anim?.animatedFraction ?: 0f
        val cy = height / 2f
        val r = height / 6.4f
        val gap = r * 3.1f
        val startX = width / 2f - gap
        for (i in 0 until 3) {
            val phase = ((t * 3f - i * 0.35f) % 1f + 1f) % 1f
            val lift = Math.sin(phase * Math.PI).toFloat()
            val y = cy - lift * height * 0.22f
            paint.alpha = (140 + 110 * lift).toInt().coerceAtMost(255)
            canvas.drawCircle(startX + i * gap, y, r * (0.82f + 0.18f * lift), paint)
        }
        paint.alpha = 255
    }
}
