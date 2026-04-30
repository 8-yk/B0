package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * ClearAnimator draws a bright flash over cleared rows and columns,
 * then fades them out before the board is repainted clean.
 *
 * Usage:
 *  1. Call [startClear] with the rows/cols to highlight.
 *  2. In your View.onDraw, call [draw] after drawing the grid.
 *  3. The animator calls [invalidateCallback] every frame automatically.
 */
class ClearAnimator(private val invalidateCallback: () -> Unit) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alpha = 0f

    private var clearedRows: List<Int> = emptyList()
    private var clearedCols: List<Int> = emptyList()

    private var animator: ValueAnimator? = null

    val isRunning get() = animator?.isRunning == true

    // ── Public API ─────────────────────────────────────────────────────────

    fun startClear(rows: List<Int>, cols: List<Int>) {
        clearedRows = rows
        clearedCols = cols
        animator?.cancel()

        // 0 → 1 (flash in) → 0 (fade out)
        animator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 420L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                alpha = it.animatedValue as Float
                invalidateCallback()
            }
            start()
        }
    }

    /**
     * Draw the flash overlay.
     * @param canvas  the view's canvas
     * @param cellPx  size of one grid cell in pixels
     * @param offsetX left edge of the grid in pixels
     * @param offsetY top  edge of the grid in pixels
     */
    fun draw(canvas: Canvas, cellPx: Float, offsetX: Float, offsetY: Float) {
        if (alpha <= 0f) return

        // White flash with current alpha
        paint.color = Color.WHITE
        paint.alpha = (alpha * 220).toInt().coerceIn(0, 255)

        for (r in clearedRows) {
            canvas.drawRect(
                offsetX,
                offsetY + r * cellPx,
                offsetX + cellPx * 8,   // width = gridSize * cellPx; passed in draw call
                offsetY + r * cellPx + cellPx,
                paint
            )
        }
        for (c in clearedCols) {
            canvas.drawRect(
                offsetX + c * cellPx,
                offsetY,
                offsetX + c * cellPx + cellPx,
                offsetY + cellPx * 8,
                paint
            )
        }
    }

    /**
     * Overload that accepts [gridSize] so we don't hard-code 8.
     */
    fun draw(
        canvas: Canvas,
        cellPx: Float,
        offsetX: Float,
        offsetY: Float,
        gridSize: Int
    ) {
        if (alpha <= 0f) return

        paint.color = Color.WHITE
        paint.alpha = (alpha * 220).toInt().coerceIn(0, 255)

        val gridPx = cellPx * gridSize

        for (r in clearedRows) {
            canvas.drawRect(
                offsetX,
                offsetY + r * cellPx,
                offsetX + gridPx,
                offsetY + r * cellPx + cellPx,
                paint
            )
        }
        for (c in clearedCols) {
            canvas.drawRect(
                offsetX + c * cellPx,
                offsetY,
                offsetX + c * cellPx + cellPx,
                offsetY + gridPx,
                paint
            )
        }
    }
}
