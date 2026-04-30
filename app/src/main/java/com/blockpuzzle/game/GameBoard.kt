package com.blockpuzzle.game

/**
 * GameBoard manages the 8×8 grid.
 *
 * Grid convention: board[row][col]
 *   - 0           → empty cell
 *   - non-zero    → filled (stores the ARGB color)
 */
class GameBoard(val gridSize: Int = 8) {

    val board: Array<IntArray> = Array(gridSize) { IntArray(gridSize) }

    // ── Placement ──────────────────────────────────────────────────────────

    /** Returns true if [block] can be placed at (anchorRow, anchorCol). */
    fun canPlace(block: Block, anchorRow: Int, anchorCol: Int): Boolean {
        for ((dr, dc) in block.cells) {
            val r = anchorRow + dr
            val c = anchorCol + dc
            if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) return false
            if (board[r][c] != 0) return false
        }
        return true
    }

    /**
     * Places [block] at (anchorRow, anchorCol).
     * Caller must verify [canPlace] first.
     */
    fun place(block: Block, anchorRow: Int, anchorCol: Int) {
        for ((dr, dc) in block.cells) {
            board[anchorRow + dr][anchorCol + dc] = block.color
        }
    }

    // ── Line clearing ──────────────────────────────────────────────────────

    data class ClearResult(
        val clearedRows: List<Int>,
        val clearedCols: List<Int>,
        val linesCleared: Int          // total count for scoring
    )

    /**
     * Finds and clears all full rows and columns.
     * Returns which rows/cols were cleared.
     */
    fun clearFullLines(): ClearResult {
        val fullRows = (0 until gridSize).filter { r -> board[r].all { it != 0 } }
        val fullCols = (0 until gridSize).filter { c -> (0 until gridSize).all { r -> board[r][c] != 0 } }

        for (r in fullRows) board[r].fill(0)
        for (c in fullCols) for (r in 0 until gridSize) board[r][c] = 0

        return ClearResult(fullRows, fullCols, fullRows.size + fullCols.size)
    }

    // ── Availability check ─────────────────────────────────────────────────

    /** Returns true if [block] can be placed anywhere on the board. */
    fun hasRoomFor(block: Block): Boolean {
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (canPlace(block, r, c)) return true
            }
        }
        return false
    }

    /** Returns true if at least one of the given blocks can be placed. */
    fun hasRoomForAny(blocks: List<Block?>): Boolean =
        blocks.filterNotNull().any { hasRoomFor(it) }

    // ── Utility ────────────────────────────────────────────────────────────

    fun reset() {
        for (row in board) row.fill(0)
    }

    /** Snapshot for save/restore */
    fun snapshot(): Array<IntArray> = Array(gridSize) { board[it].copyOf() }

    fun restore(snap: Array<IntArray>) {
        for (r in 0 until gridSize) board[r] = snap[r].copyOf()
    }
}
