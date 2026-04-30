package com.blockpuzzle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.blockpuzzle.game.Block
import com.blockpuzzle.game.GameBoard
import com.blockpuzzle.game.GameManager
import kotlin.math.roundToInt

/**
 * GameBoardView renders the 8×8 grid using Canvas.
 *
 * Features:
 *  - Draws filled cells with rounded-rect style and a subtle 3D highlight
 *  - Shows a ghost/preview of where the dragged piece will land
 *  - Highlights full rows/cols that would be cleared on drop
 *  - Drives ClearAnimator for flash effects
 *
 * All drag logic lives here; it calls GameManager.tryPlace on drop.
 */
class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── External references (set by Activity) ─────────────────────────────
    var gameManager: GameManager? = null
        set(value) { field = value; invalidate() }

    /** Called when the user starts dragging a tray block (index 0-2). */
    var onBlockPickedUp: ((trayIndex: Int) -> Unit)? = null

    /** Called after a successful drop so the tray view can refresh. */
    var onBlockDropped: (() -> Unit)? = null

    // ── Layout ────────────────────────────────────────────────────────────
    private var cellPx   = 0f
    private var boardOX  = 0f   // board origin X
    private var boardOY  = 0f   // board origin Y
    private val radius   get() = cellPx * 0.14f
    private val padding  get() = cellPx * 0.055f

    // ── Paints ────────────────────────────────────────────────────────────
    private val cellPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A2A4A")
    }
    private val gridPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D1B35")
        style = Paint.Style.FILL
    }
    private val ghostPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 90
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        alpha = 40
    }
    private val shinePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
    }

    // ── Drag state ────────────────────────────────────────────────────────
    private var dragBlock:     Block? = null
    private var dragTrayIdx:   Int    = -1
    private var dragX:         Float  = 0f
    private var dragY:         Float  = 0f
    private var ghostAnchorR:  Int    = -1
    private var ghostAnchorC:  Int    = -1
    private var ghostValid:    Boolean = false

    // Ghost rows/cols for "will clear" preview
    private var wouldClearRows: List<Int> = emptyList()
    private var wouldClearCols: List<Int> = emptyList()

    // ── Animations ────────────────────────────────────────────────────────
    private val clearAnimator = ClearAnimator { invalidate() }

    // Drop scale-bounce animator
    private var dropScale = 1f
    private var dropAnchorR = -1
    private var dropAnchorC = -1
    private var dropCells: List<Pair<Int,Int>> = emptyList()

    // ── Rect reuse ────────────────────────────────────────────────────────
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
        val gm = gameManager ?: return
        val board = gm.board

        // 1. Board background
        tmpRect.set(boardOX, boardOY,
            boardOX + cellPx * board.gridSize,
            boardOY + cellPx * board.gridSize)
        canvas.drawRoundRect(tmpRect, radius * 2, radius * 2, gridPaint)

        // 2. Empty cells & filled cells
        for (r in 0 until board.gridSize) {
            for (c in 0 until board.gridSize) {
                val color = board.board[r][c]
                if (color == 0) {
                    drawEmptyCell(canvas, r, c)
                } else {
                    drawFilledCell(canvas, r, c, color)
                }
            }
        }

        // 3. "Would clear" row/col highlights (while dragging)
        if (dragBlock != null && ghostValid) {
            drawClearPreview(canvas)
        }

        // 4. Ghost preview
        if (dragBlock != null && ghostValid) {
            drawGhost(canvas)
        }

        // 5. Drop bounce cells
        if (dropScale != 1f && dropCells.isNotEmpty()) {
            drawDropBounce(canvas)
        }

        // 6. Clear flash animation
        clearAnimator.draw(canvas, cellPx, boardOX, boardOY, board.gridSize)
    }

    private fun drawEmptyCell(canvas: Canvas, r: Int, c: Int) {
        val l = boardOX + c * cellPx + padding
        val t = boardOY + r * cellPx + padding
        val rr = l + cellPx - padding * 2
        val rb = t + cellPx - padding * 2
        tmpRect.set(l, t, rr, rb)
        canvas.drawRoundRect(tmpRect, radius, radius, emptyPaint)
    }

    private fun drawFilledCell(canvas: Canvas, r: Int, c: Int, color: Int, scl: Float = 1f) {
        val cx = boardOX + c * cellPx + cellPx / 2f
        val cy = boardOY + r * cellPx + cellPx / 2f
        val half = (cellPx / 2f - padding) * scl

        val l = cx - half
        val t = cy - half
        val rr = cx + half
        val rb = cy + half

        // Shadow
        tmpRect.set(l + padding * 0.5f, t + padding * 0.5f, rr + padding * 0.5f, rb + padding * 0.5f)
        canvas.drawRoundRect(tmpRect, radius * scl, radius * scl, shadowPaint)

        // Main cell
        cellPaint.color = color
        tmpRect.set(l, t, rr, rb)
        canvas.drawRoundRect(tmpRect, radius * scl, radius * scl, cellPaint)

        // Top shine
        shinePaint.color = Color.argb(60, 255, 255, 255)
        tmpRect.set(l + padding, t + padding, rr - padding, t + half * 0.55f)
        canvas.drawRoundRect(tmpRect, radius * scl, radius * scl, shinePaint)
    }

    private fun drawGhost(canvas: Canvas) {
        val block = dragBlock ?: return
        ghostPaint.color = block.color
        for ((dr, dc) in block.cells) {
            val r = ghostAnchorR + dr
            val c = ghostAnchorC + dc
            if (r < 0 || r >= (gameManager?.board?.gridSize ?: 8)) continue
            if (c < 0 || c >= (gameManager?.board?.gridSize ?: 8)) continue
            val l = boardOX + c * cellPx + padding * 2
            val t = boardOY + r * cellPx + padding * 2
            tmpRect.set(l, t, l + cellPx - padding * 4, t + cellPx - padding * 4)
            canvas.drawRoundRect(tmpRect, radius, radius, ghostPaint)
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
    // Touch / Drag
    // ══════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only the tray view initiates drags; this board only receives
        // ACTION_MOVE and ACTION_UP forwarded from TrayView via the Activity.
        // But we can also handle direct board drags here.
        return false   // Drag is coordinated by MainActivity
    }

    // ── Called by MainActivity's drag coordinator ──────────────────────────

    fun onDragUpdate(block: Block, trayIdx: Int, touchX: Float, touchY: Float) {
        dragBlock   = block
        dragTrayIdx = trayIdx
        dragX       = touchX
        dragY       = touchY
        updateGhost(touchX, touchY, block)
        invalidate()
    }

    fun onDragEnd(touchX: Float, touchY: Float): Boolean {
        val gm    = gameManager ?: return false
        val block = dragBlock   ?: return false

        val placed = if (ghostValid) {
            gm.tryPlace(dragTrayIdx, ghostAnchorR, ghostAnchorC)
        } else false

        if (placed) {
            animateDrop(block, ghostAnchorR, ghostAnchorC)
        }

        dragBlock   = null
        dragTrayIdx = -1
        ghostValid  = false
        wouldClearRows = emptyList()
        wouldClearCols = emptyList()
        invalidate()
        return placed
    }

    fun onDragCancel() {
        dragBlock   = null
        dragTrayIdx = -1
        ghostValid  = false
        wouldClearRows = emptyList()
        wouldClearCols = emptyList()
        invalidate()
    }

    // ── Ghost helpers ──────────────────────────────────────────────────────

    /**
     * The finger offset: we want the block to appear *above* the finger
     * so it's visible while dragging.
     */
    private val FINGER_OFFSET_Y get() = cellPx * 1.8f

    private fun updateGhost(touchX: Float, touchY: Float, block: Block) {
        val gm = gameManager ?: return

        // Map finger → grid cell (with upward offset so block is above thumb)
        val adjustedY = touchY - FINGER_OFFSET_Y
        val col = ((touchX   - boardOX) / cellPx).roundToInt() - block.colSpan / 2
        val row = ((adjustedY - boardOY) / cellPx).roundToInt()

        ghostAnchorR = row
        ghostAnchorC = col
        ghostValid   = gm.board.canPlace(block, row, col)

        if (ghostValid) {
            computeWouldClear(block, row, col)
        } else {
            wouldClearRows = emptyList()
            wouldClearCols = emptyList()
        }
    }

    private fun computeWouldClear(block: Block, anchorR: Int, anchorC: Int) {
        val gm = gameManager ?: return
        val gs = gm.board.gridSize

        // Simulate placement
        val tempBoard = gm.board.snapshot()
        for ((dr, dc) in block.cells) {
            val r = anchorR + dr; val c = anchorC + dc
            if (r in 0 until gs && c in 0 until gs) tempBoard[r][c] = block.color
        }

        wouldClearRows = (0 until gs).filter { r -> tempBoard[r].all { it != 0 } }
        wouldClearCols = (0 until gs).filter { c -> (0 until gs).all { r -> tempBoard[r][c] != 0 } }
    }

    // ── Drop bounce animation ──────────────────────────────────────────────

    private fun animateDrop(block: Block, anchorR: Int, anchorC: Int) {
        dropCells   = block.cells.map { (dr, dc) -> Pair(anchorR + dr, anchorC + dc) }
        dropAnchorR = anchorR
        dropAnchorC = anchorC

        ValueAnimator.ofFloat(1.18f, 0.9f, 1f).apply {
            duration = 260L
            addUpdateListener {
                dropScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ── Called by GameManager callback ─────────────────────────────────────

    fun triggerClearAnimation(rows: List<Int>, cols: List<Int>) {
        clearAnimator.startClear(rows, cols)
    }
}
