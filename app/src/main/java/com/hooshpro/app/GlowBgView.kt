package com.hooshpro.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/** پس‌زمینه‌ی متحرک صفحه‌ی ورود — گرادیان تیره + هاله‌های نورانی شنارور */
class GlowBgView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var t = 0f
    private var anim: ValueAnimator? = null

    private val base = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1f,
            0xFF070B14.toInt(), 0xFF0D1226.toInt(), Shader.TileMode.CLAMP
        )
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (anim == null) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 14000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    t = it.animatedFraction
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (h > 0) {
            base.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                0xFF070B14.toInt(), 0xFF101430.toInt(), Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        drawGlow(canvas, w * (0.28f + 0.10f * kotlin.math.sin(t * 2f * Math.PI).toFloat()),
            h * (0.24f + 0.05f * kotlin.math.cos(t * 2f * Math.PI).toFloat()),
            w * 0.62f, 0x338B5CF6)
        drawGlow(canvas, w * (0.78f - 0.08f * kotlin.math.sin(t * 2f * Math.PI).toFloat()),
            h * (0.62f + 0.06f * kotlin.math.cos(t * 4f * Math.PI).toFloat()),
            w * 0.55f, 0x2B06B6D4)
        drawGlow(canvas, w * (0.5f + 0.16f * kotlin.math.cos(t * 2f * Math.PI).toFloat()),
            h * (0.88f - 0.03f * kotlin.math.sin(t * 2f * Math.PI).toFloat()),
            w * 0.5f, 0x267C3AED)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        glow.shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, glow)
    }
}
