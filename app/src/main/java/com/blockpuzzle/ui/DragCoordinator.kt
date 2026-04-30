package com.blockpuzzle.ui

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.blockpuzzle.game.Block

/**
 * DragCoordinator sits in the Activity and bridges drag events
 * from TrayView → GameBoardView.
 *
 * It intercepts raw touch events on the root layout so finger
 * tracking is continuous even when the pointer moves between views.
 */
class DragCoordinator(
    private val root:      ViewGroup,
    private val tray:      TrayView,
    private val board:     GameBoardView
) {
    private var activeBlock:   Block? = null
    private var activeTrayIdx: Int    = -1
    private val lastPos        = PointF()

    /** Attach to the root layout's dispatchTouchEvent. Returns true if consumed. */
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Let TrayView handle DOWN normally (it identifies the slot)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeBlock == null) return false
                lastPos.set(ev.rawX, ev.rawY)
                val boardPos = rawToBoardLocal(ev.rawX, ev.rawY)
                board.onDragUpdate(activeBlock!!, activeTrayIdx, boardPos.x, boardPos.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activeBlock == null) return false
                val boardPos = rawToBoardLocal(ev.rawX, ev.rawY)
                val placed = board.onDragEnd(boardPos.x, boardPos.y)
                if (!placed) {
                    tray.cancelDrag()
                    board.onDragCancel()
                }
                // Clean up
                activeBlock   = null
                activeTrayIdx = -1
                return placed
            }

            MotionEvent.ACTION_CANCEL -> {
                board.onDragCancel()
                tray.cancelDrag()
                activeBlock   = null
                activeTrayIdx = -1
            }
        }
        return false
    }

    /** TrayView calls this when it detects a drag start. */
    fun beginDrag(trayIdx: Int, block: Block, rawX: Float, rawY: Float) {
        activeBlock   = block
        activeTrayIdx = trayIdx
        lastPos.set(rawX, rawY)
        tray.startDrag(trayIdx)
        val boardPos = rawToBoardLocal(rawX, rawY)
        board.onDragUpdate(block, trayIdx, boardPos.x, boardPos.y)
    }

    val isDragging get() = activeBlock != null

    // ── Coord conversion ───────────────────────────────────────────────────

    private fun rawToBoardLocal(rawX: Float, rawY: Float): PointF {
        val loc = IntArray(2)
        board.getLocationOnScreen(loc)
        return PointF(rawX - loc[0], rawY - loc[1])
    }
}
