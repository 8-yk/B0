package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ParticleView — طبقة شفافة فوق اللوحة ترسم جزيئات متطايرة
 * عند حذف صف أو عمود.
 *
 * كيفية الاستخدام:
 *   particleView.burst(rows, cols, cellPx, boardOX, boardOY)
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // الـ View شفاف تماماً — فقط الجزيئات ترسم
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ── Particle data class ────────────────────────────────────────────────
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,               // سرعة أفقية px/frame
        var vy: Float,               // سرعة رأسية px/frame
        var radius: Float,
        var color: Int,
        var alpha: Float = 1f,
        var life: Float = 1f,        // 1→0
        var shape: Int = 0           // 0=circle  1=square  2=star-dot
    )

    private val particles = mutableListOf<Particle>()
    private val paint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    // ── الجاذبية والاحتكاك ────────────────────────────────────────────────
    private val GRAVITY  = 0.28f
    private val FRICTION = 0.97f

    // ══════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * يفجّر جزيئات من مركز كل خلية في الصفوف/الأعمدة المحذوفة.
     *
     * @param rows      الصفوف المحذوفة (indices)
     * @param cols      الأعمدة المحذوفة (indices)
     * @param cellPx    حجم الخلية بالبكسل
     * @param boardOX   إزاحة اللوحة أفقياً
     * @param boardOY   إزاحة اللوحة رأسياً
     * @param colors    ألوان البلوكات في تلك الخلايا (اختياري)
     */
    fun burst(
        rows: List<Int>,
        cols: List<Int>,
        cellPx: Float,
        boardOX: Float,
        boardOY: Float,
        gridSize: Int = 8,
        boardColors: Array<IntArray>? = null
    ) {
        val newParticles = mutableListOf<Particle>()

        // ─ جزيئات الصفوف ─
        for (r in rows) {
            for (c in 0 until gridSize) {
                val cx = boardOX + c * cellPx + cellPx / 2f
                val cy = boardOY + r * cellPx + cellPx / 2f
                val color = boardColors?.get(r)?.get(c)?.takeIf { it != 0 }
                    ?: randomVibrantColor()
                spawnBurst(newParticles, cx, cy, cellPx, color, count = 6)
            }
        }

        // ─ جزيئات الأعمدة ─
        for (c in cols) {
            for (r in 0 until gridSize) {
                // تجنّب التكرار مع تقاطع الصفوف
                if (r in rows) continue
                val cx = boardOX + c * cellPx + cellPx / 2f
                val cy = boardOY + r * cellPx + cellPx / 2f
                val color = boardColors?.get(r)?.get(c)?.takeIf { it != 0 }
                    ?: randomVibrantColor()
                spawnBurst(newParticles, cx, cy, cellPx, color, count = 6)
            }
        }

        particles.addAll(newParticles)
        startAnimation()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Internal
    // ══════════════════════════════════════════════════════════════════════

    private fun spawnBurst(
        list: MutableList<Particle>,
        cx: Float, cy: Float,
        cellPx: Float,
        color: Int,
        count: Int
    ) {
        repeat(count) {
            val angle  = Random.nextFloat() * 360f
            val speed  = Random.nextFloat() * cellPx * 0.18f + cellPx * 0.06f
            val rad    = Math.toRadians(angle.toDouble())
            val radius = Random.nextFloat() * cellPx * 0.11f + cellPx * 0.04f
            val shape  = listOf(0, 0, 0, 1, 2).random()   // mostly circles

            // Slight jitter from center
            val jx = (Random.nextFloat() - 0.5f) * cellPx * 0.4f
            val jy = (Random.nextFloat() - 0.5f) * cellPx * 0.4f

            list.add(
                Particle(
                    x      = cx + jx,
                    y      = cy + jy,
                    vx     = (cos(rad) * speed).toFloat(),
                    vy     = (sin(rad) * speed - cellPx * 0.12f).toFloat(), // bias upward
                    radius = radius,
                    color  = color,
                    shape  = shape
                )
            )
        }
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = 0
            addUpdateListener { tick() }
            start()
        }
    }

    private fun tick() {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x   += p.vx
            p.y   += p.vy
            p.vy  += GRAVITY
            p.vx  *= FRICTION
            p.vy  *= FRICTION
            p.life -= 0.022f
            p.alpha = p.life.coerceIn(0f, 1f)
            if (p.life <= 0f) iter.remove()
        }
        if (particles.isEmpty()) animator?.cancel()
        invalidate()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Draw
    // ══════════════════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)

            when (p.shape) {
                0 -> canvas.drawCircle(p.x, p.y, p.radius, paint)
                1 -> canvas.drawRect(
                    p.x - p.radius, p.y - p.radius,
                    p.x + p.radius, p.y + p.radius, paint
                )
                else -> {
                    // نجمة صغيرة = دائرتان متداخلتان بحجمين
                    canvas.drawCircle(p.x, p.y, p.radius, paint)
                    paint.alpha = (p.alpha * 130).toInt()
                    canvas.drawCircle(p.x, p.y, p.radius * 1.7f, paint)
                    paint.alpha = (p.alpha * 255).toInt()
                }
            }
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────
    private val vibrantColors = intArrayOf(
        Color.parseColor("#FF5252"), Color.parseColor("#FF9800"),
        Color.parseColor("#FFD600"), Color.parseColor("#69F0AE"),
        Color.parseColor("#40C4FF"), Color.parseColor("#B388FF"),
        Color.parseColor("#FF80AB"), Color.parseColor("#FFFFFF")
    )
    private fun randomVibrantColor() = vibrantColors[Random.nextInt(vibrantColors.size)]
}
