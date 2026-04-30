package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.blockpuzzle.game.Block
import com.blockpuzzle.game.GameManager

/**
 * TrayView draws the 3 available pieces at the bottom of the screen.
 *
 * Drag flow:
 *  1. User touches a piece → TrayView detects the slot, notifies Activity via [onDragStarted].
 *  2. Activity forwards MOVE/UP events to GameBoardView.
 *  3. On UP the Activity calls [markPlaced] if placement succeeded.
 */
class TrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── External refs ──────────────────────────────────────────────────────
    var gameManager: GameManager? = null
        set(value) { field = value; invalidate() }

    /** Notified when a drag begins: (trayIndex, block, startX, startY) */
    var onDragStarted: ((Int, Block, Float, Float) -> Unit)? = null

    // ── Layout ────────────────────────────────────────────────────────────
    private val SLOT_COUNT   = 3
    private val MAX_CELL_PX  = 38f          // px cap so tray pieces don't overflow
    private var slotWidth    = 0f
    private var cellPx       = 0f

    // ── Paints ────────────────────────────────────────────────────────────
    private val cellPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
    }
    private val dimPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88000000")
    }

    // ── Drag state ────────────────────────────────────────────────────────
    private var draggingIdx  = -1   // slot currently being dragged (-1 = none)

    // ── Scale-in animation per slot ────────────────────────────────────────
    private val scaleAnim   = FloatArray(3) { 1f }
    private val tmpRect     = RectF()

    // ══════════════════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════════════════

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        slotWidth = w / SLOT_COUNT.toFloat()
        // Choose cell size so even a 4-wide block fits in a slot
        cellPx = minOf(slotWidth / 5f, h / 6f, MAX_CELL_PX)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Drawing
    // ══════════════════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        val gm = gameManager ?: return

        for (i in 0..2) {
            val block = gm.tray[i]
            val slotCx = slotWidth * i + slotWidth / 2f
            val slotCy = height / 2f

            if (block == null) continue
            if (i == draggingIdx) continue     // hidden while dragging

            drawBlock(canvas, block, slotCx, slotCy, scaleAnim[i])
        }
    }

    private fun drawBlock(
        canvas: Canvas,
        block: Block,
        cx: Float,
        cy: Float,
        scale: Float = 1f
    ) {
        val cs   = cellPx * scale
        val pad  = cs * 0.06f
        val rad  = cs * 0.16f
        val totalW = block.colSpan * cs
        val totalH = block.rowSpan * cs
        val startX = cx - totalW / 2f
        val startY = cy - totalH / 2f

        for ((r, c) in block.cells) {
            val l = startX + c * cs + pad
            val t = startY + r * cs + pad
            val rr = l + cs - pad * 2
            val rb = t + cs - pad * 2

            // Shadow
            tmpRect.set(l + pad * 0.4f, t + pad * 0.4f, rr + pad * 0.4f, rb + pad * 0.4f)
            canvas.drawRoundRect(tmpRect, rad, rad, shadowPaint)

            // Fill
            cellPaint.color = block.color
            tmpRect.set(l, t, rr, rb)
            canvas.drawRoundRect(tmpRect, rad, rad, cellPaint)

            // Shine
            shinePaint.color = Color.argb(55, 255, 255, 255)
            tmpRect.set(l + pad, t + pad, rr - pad, t + (cs - pad * 2) * 0.45f)
            canvas.drawRoundRect(tmpRect, rad, rad, shinePaint)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Touch
    // ══════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gm = gameManager ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = slotAt(event.x)
                val block = gm.tray.getOrNull(idx) ?: return false
                draggingIdx = idx
                invalidate()
                onDragStarted?.invoke(idx, block, event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                draggingIdx = -1
                invalidate()
            }
        }
        return true
    }

    private fun slotAt(x: Float): Int = (x / slotWidth).toInt().coerceIn(0, 2)

    // ── Called by Activity ─────────────────────────────────────────────────

    /** Hide the slot while dragging. */
    fun startDrag(idx: Int) {
        draggingIdx = idx
        invalidate()
    }

    /** Show the slot again (failed drop). */
    fun cancelDrag() {
        draggingIdx = -1
        invalidate()
    }

    /** Animate a new block appearing in slot [idx]. */
    fun animateSlotAppear(idx: Int) {
        scaleAnim[idx] = 0f
        draggingIdx = -1
        ValueAnimator.ofFloat(0f, 1.15f, 0.9f, 1f).apply {
            duration = 320L
            addUpdateListener {
                scaleAnim[idx] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun refresh() {
        draggingIdx = -1
        invalidate()
    }
}
