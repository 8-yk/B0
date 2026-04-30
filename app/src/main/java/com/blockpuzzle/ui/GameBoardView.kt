package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.blockpuzzle.game.Block
import com.blockpuzzle.game.GameManager
import kotlin.math.roundToInt

/**
 * GameBoardView v2 — Ghost always visible:
 *   valid   → block color semi-transparent + white border
 *   invalid → red tint + red border + ✕ on conflicting cells
 */
class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gameManager: GameManager? = null
        set(value) { field = value; invalidate() }

    var onBlockPickedUp: ((Int) -> Unit)? = null
    var onBlockDropped:  (() -> Unit)?   = null

    // ── Layout ────────────────────────────────────────────────────────────
    private var cellPx  = 0f
    private var boardOX = 0f
    private var boardOY = 0f
    private val radius  get() = cellPx * 0.14f
    private val padding get() = cellPx * 0.055f

    // ── Paints ────────────────────────────────────────────────────────────
    private val cellPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A2A4A") }
    private val gridPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D1B35") }
    private val shinePaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 40 }

    // Ghost valid
    private val ghostFillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 140 }
    private val ghostBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.parseColor("#AAFFFFFF")
    }

    // Ghost invalid
    private val ghostRedPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") }
    private val ghostRedBorder  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.parseColor("#FF1744")
    }
    private val xMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; alpha = 210
    }

    // ── Drag state ────────────────────────────────────────────────────────
    private var dragBlock:    Block?  = null
    private var dragTrayIdx:  Int     = -1
    private var ghostAnchorR: Int     = -1
    private var ghostAnchorC: Int     = -1
    private var ghostValid:   Boolean = false
    private var conflictCells: Set<Pair<Int,Int>> = emptySet()
    private var wouldClearRows: List<Int> = emptyList()
    private var wouldClearCols: List<Int> = emptyList()

    // ── Animations ────────────────────────────────────────────────────────
    private val clearAnimator = ClearAnimator { invalidate() }
    private var dropScale  = 1f
    private var dropCells: List<Pair<Int,Int>> = emptyList()

    private val tmpRect = RectF()

    // ══════════════════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════════════════

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gm = gameManager ?: return
        cellPx  = minOf(w, h).toFloat() / gm.board.gridSize
        boardOX = (w - cellPx * gm.board.gridSize) / 2f
        boardOY = (h - cellPx * gm.board.gridSize) / 2f
    }

    // ══════════════════════════════════════════════════════════════════════
    // Drawing
    // ══════════════════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        val gm    = gameManager ?: return
        val board = gm.board

        // 1. Background
        tmpRect.set(boardOX, boardOY,
            boardOX + cellPx * board.gridSize, boardOY + cellPx * board.gridSize)
        canvas.drawRoundRect(tmpRect, radius * 2, radius * 2, gridPaint)

        // 2. Cells
        for (r in 0 until board.gridSize)
            for (c in 0 until board.gridSize) {
                val color = board.board[r][c]
                if (color == 0) drawEmptyCell(canvas, r, c) else drawFilledCell(canvas, r, c, color)
            }

        // 3. Would-clear highlight (valid only)
        if (dragBlock != null && ghostValid) drawClearPreview(canvas)

        // 4. Ghost — ALWAYS when dragging
        if (dragBlock != null) drawGhost(canvas)

        // 5. Drop bounce
        if (dropScale != 1f && dropCells.isNotEmpty()) drawDropBounce(canvas)

        // 6. Clear flash
        clearAnimator.draw(canvas, cellPx, boardOX, boardOY, board.gridSize)
    }

    private fun drawEmptyCell(canvas: Canvas, r: Int, c: Int) {
        val l = boardOX + c * cellPx + padding
        val t = boardOY + r * cellPx + padding
        tmpRect.set(l, t, l + cellPx - padding * 2, t + cellPx - padding * 2)
        canvas.drawRoundRect(tmpRect, radius, radius, emptyPaint)
    }

    private fun drawFilledCell(canvas: Canvas, r: Int, c: Int, color: Int, scl: Float = 1f) {
        val cx = boardOX + c * cellPx + cellPx / 2f
        val cy = boardOY + r * cellPx + cellPx / 2f
        val half = (cellPx / 2f - padding) * scl
        val l = cx - half; val t = cy - half
        val rr = cx + half; val rb = cy + half
        val rad = radius * scl

        tmpRect.set(l + padding * .5f, t + padding * .5f, rr + padding * .5f, rb + padding * .5f)
        canvas.drawRoundRect(tmpRect, rad, rad, shadowPaint)

        cellPaint.color = color
        tmpRect.set(l, t, rr, rb)
        canvas.drawRoundRect(tmpRect, rad, rad, cellPaint)

        shinePaint.color = Color.argb(60, 255, 255, 255)
        tmpRect.set(l + padding, t + padding, rr - padding, t + half * .55f)
        canvas.drawRoundRect(tmpRect, rad, rad, shinePaint)
    }

    private fun drawGhost(canvas: Canvas) {
        val block = dragBlock ?: return
        val gs    = gameManager?.board?.gridSize ?: 8
        val pad   = padding * 2.2f
        val inner = cellPx - pad * 2

        for ((dr, dc) in block.cells) {
            val r = ghostAnchorR + dr
            val c = ghostAnchorC + dc
            if (r < 0 || r >= gs || c < 0 || c >= gs) continue   // off-grid → skip

            val l = boardOX + c * cellPx + pad
            val t = boardOY + r * cellPx + pad
            tmpRect.set(l, t, l + inner, t + inner)

            if (ghostValid) {
                // ── صالح: لون القطعة + حدود بيضاء شفافة ──────────────
                ghostFillPaint.color = block.color
                canvas.drawRoundRect(tmpRect, radius, radius, ghostFillPaint)
                canvas.drawRoundRect(tmpRect, radius, radius, ghostBorderPaint)
            } else {
                // ── غير صالح: أحمر ──────────────────────────────────────
                val isConflict = Pair(r, c) in conflictCells
                ghostRedPaint.alpha  = if (isConflict) 200 else 120
                ghostRedBorder.alpha = if (isConflict) 255 else 170
                canvas.drawRoundRect(tmpRect, radius, radius, ghostRedPaint)
                canvas.drawRoundRect(tmpRect, radius, radius, ghostRedBorder)

                // ✕ على الخلايا المتعارضة فقط
                if (isConflict) {
                    xMarkPaint.strokeWidth = cellPx * 0.07f
                    val arm = inner * 0.26f
                    val cx  = l + inner / 2f
                    val cy  = t + inner / 2f
                    canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, xMarkPaint)
                    canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, xMarkPaint)
                }
            }
        }
    }

    private fun drawClearPreview(canvas: Canvas) {
        val gridPx = cellPx * (gameManager?.board?.gridSize ?: 8)
        for (r in wouldClearRows) {
            tmpRect.set(boardOX, boardOY + r * cellPx, boardOX + gridPx, boardOY + r * cellPx + cellPx)
            canvas.drawRect(tmpRect, highlightPaint)
        }
        for (c in wouldClearCols) {
            tmpRect.set(boardOX + c * cellPx, boardOY, boardOX + c * cellPx + cellPx, boardOY + gridPx)
            canvas.drawRect(tmpRect, highlightPaint)
        }
    }

    private fun drawDropBounce(canvas: Canvas) {
        for ((r, c) in dropCells) {
            val color = gameManager?.board?.board?.get(r)?.get(c) ?: continue
            if (color == 0) continue
            drawFilledCell(canvas, r, c, color, dropScale)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Public drag API
    // ══════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent) = false

    fun onDragUpdate(block: Block, trayIdx: Int, touchX: Float, touchY: Float) {
        dragBlock   = block
        dragTrayIdx = trayIdx
        updateGhost(touchX, touchY, block)
        invalidate()
    }

    fun onDragEnd(touchX: Float, touchY: Float): Boolean {
        val gm    = gameManager ?: return false
        val block = dragBlock   ?: return false
        val placed = if (ghostValid) gm.tryPlace(dragTrayIdx, ghostAnchorR, ghostAnchorC) else false
        if (placed) animateDrop(block, ghostAnchorR, ghostAnchorC)
        clearDragState(); invalidate()
        return placed
    }

    fun onDragCancel() { clearDragState(); invalidate() }

    private fun clearDragState() {
        dragBlock = null; dragTrayIdx = -1; ghostValid = false
        conflictCells = emptySet()
        wouldClearRows = emptyList(); wouldClearCols = emptyList()
    }

    // ── Ghost position ─────────────────────────────────────────────────────

    private val FINGER_OFFSET_Y get() = cellPx * 1.8f

    private fun updateGhost(touchX: Float, touchY: Float, block: Block) {
        val gm = gameManager ?: return
        val gs = gm.board.gridSize

        val adjustedY = touchY - FINGER_OFFSET_Y
        val col = ((touchX    - boardOX) / cellPx).roundToInt() - block.colSpan / 2
        val row = ((adjustedY - boardOY) / cellPx).roundToInt()

        ghostAnchorR = row
        ghostAnchorC = col
        ghostValid   = gm.board.canPlace(block, row, col)

        // Cells that collide with existing blocks
        conflictCells = if (!ghostValid) {
            block.cells.mapNotNull { (dr, dc) ->
                val r = row + dr; val c = col + dc
                if (r in 0 until gs && c in 0 until gs && gm.board.board[r][c] != 0)
                    Pair(r, c) else null
            }.toSet()
        } else emptySet()

        if (ghostValid) computeWouldClear(block, row, col)
        else { wouldClearRows = emptyList(); wouldClearCols = emptyList() }
    }

    private fun computeWouldClear(block: Block, anchorR: Int, anchorC: Int) {
        val gm   = gameManager ?: return
        val gs   = gm.board.gridSize
        val temp = gm.board.snapshot()
        for ((dr, dc) in block.cells) {
            val r = anchorR + dr; val c = anchorC + dc
            if (r in 0 until gs && c in 0 until gs) temp[r][c] = block.color
        }
        wouldClearRows = (0 until gs).filter { r -> temp[r].all { it != 0 } }
        wouldClearCols = (0 until gs).filter { c -> (0 until gs).all { r -> temp[r][c] != 0 } }
    }

    private fun animateDrop(block: Block, anchorR: Int, anchorC: Int) {
        dropCells = block.cells.map { (dr, dc) -> Pair(anchorR + dr, anchorC + dc) }
        ValueAnimator.ofFloat(1.18f, 0.9f, 1f).apply {
            duration = 260L
            addUpdateListener { dropScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun triggerClearAnimation(rows: List<Int>, cols: List<Int>) =
        clearAnimator.startClear(rows, cols)
}
