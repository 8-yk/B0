package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * MenuDecoView — خلفية حية للشاشة الرئيسية
 * ترسم بلوكات صغيرة تطفو ببطء مع تأثير توهّج.
 */
class MenuDecoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class DecoBlock(
        var x: Float, var y: Float,
        val size: Float,
        val color: Int,
        var alpha: Float,
        val speed: Float,
        val phase: Float      // offset for sine wave
    )

    private val blocks = mutableListOf<DecoBlock>()
    private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine  = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tick   = 0f

    private val COLORS = intArrayOf(
        Color.parseColor("#1A3A6A"),
        Color.parseColor("#1A4060"),
        Color.parseColor("#0D2E50"),
        Color.parseColor("#162840"),
        Color.parseColor("#1A2F5A"),
        Color.parseColor("#183355")
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        blocks.clear()
        val count = 28
        repeat(count) {
            val size = Random.nextFloat() * 42f + 22f
            blocks.add(DecoBlock(
                x      = Random.nextFloat() * w,
                y      = Random.nextFloat() * h,
                size   = size,
                color  = COLORS[Random.nextInt(COLORS.size)],
                alpha  = Random.nextFloat() * 0.7f + 0.15f,
                speed  = Random.nextFloat() * 0.35f + 0.1f,
                phase  = Random.nextFloat() * Math.PI.toFloat() * 2f
            ))
        }
        startAnimation()
    }

    private fun startAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration    = 16L   // ~60fps
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                tick += 0.008f
                updateBlocks()
                invalidate()
            }
            start()
        }
    }

    private fun updateBlocks() {
        val h = height.toFloat()
        for (b in blocks) {
            b.y -= b.speed
            b.x += sin((tick + b.phase).toDouble()).toFloat() * 0.4f
            if (b.y + b.size < 0) b.y = h + b.size  // re-enter from bottom
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (b in blocks) {
            val rad = b.size * 0.15f

            // Shadow glow
            paint.color = b.color
            paint.alpha = (b.alpha * 60).toInt()
            paint.maskFilter = BlurMaskFilter(b.size * 0.4f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(
                b.x - b.size * 0.1f, b.y - b.size * 0.1f,
                b.x + b.size * 1.1f, b.y + b.size * 1.1f,
                rad * 1.5f, rad * 1.5f, paint
            )

            // Main block
            paint.maskFilter = null
            paint.alpha = (b.alpha * 255).toInt()
            canvas.drawRoundRect(b.x, b.y, b.x + b.size, b.y + b.size, rad, rad, paint)

            // Top shine
            shine.color  = Color.argb((b.alpha * 40).toInt(), 255, 255, 255)
            shine.maskFilter = null
            canvas.drawRoundRect(
                b.x + rad, b.y + rad,
                b.x + b.size - rad, b.y + b.size * 0.42f,
                rad, rad, shine
            )
        }
    }
}
