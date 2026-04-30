package com.blockpuzzle.game

import android.content.Context
import android.content.SharedPreferences

/**
 * GameManager is the single source of truth for game state.
 *
 * Responsibilities:
 *  - Owns GameBoard, ScoreSystem, current tray (3 pieces)
 *  - Handles placement requests from the UI
 *  - Detects game-over
 *  - Persists high score via SharedPreferences
 */
class GameManager(context: Context) {

    companion object {
        const val GRID_SIZE = 8
        private const val PREFS_NAME  = "block_puzzle_prefs"
        private const val KEY_HIGH    = "high_score"
    }

    val board  = GameBoard(GRID_SIZE)
    val score  = ScoreSystem()

    /** The three pieces shown in the tray; null = already placed this round */
    val tray: Array<Block?> = arrayOfNulls(3)

    var isGameOver = false
        private set

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Listeners for UI updates
    var onScoreChanged: ((score: Int, highScore: Int) -> Unit)? = null
    var onLinesCleared: ((rows: List<Int>, cols: List<Int>, combo: String) -> Unit)? = null
    var onGameOver:     (() -> Unit)? = null
    var onTrayChanged:  (() -> Unit)? = null

    // ── Initialise ─────────────────────────────────────────────────────────

    init {
        score.highScore = prefs.getInt(KEY_HIGH, 0)
        refillTray()
    }

    // ── Tray management ────────────────────────────────────────────────────

    private fun refillTray() {
        val newBlocks = BlockFactory.generateThreeBlocks(GRID_SIZE)
        for (i in 0..2) tray[i] = newBlocks[i]
        onTrayChanged?.invoke()
    }

    /** Called when tray is all-null – refill only if all three are placed. */
    private fun checkRefillTray() {
        if (tray.all { it == null }) refillTray()
    }

    // ── Placement ──────────────────────────────────────────────────────────

    /**
     * Attempt to place tray piece at [trayIndex] at grid position (row, col).
     * @return true if placement succeeded
     */
    fun tryPlace(trayIndex: Int, anchorRow: Int, anchorCol: Int): Boolean {
        val block = tray[trayIndex] ?: return false
        if (!board.canPlace(block, anchorRow, anchorCol)) return false

        // 1. Place the block
        board.place(block, anchorRow, anchorCol)
        tray[trayIndex] = null

        // 2. Clear full lines
        val clearResult = board.clearFullLines()

        // 3. Update score
        val delta = score.recordPlacement(block.cells.size, clearResult.linesCleared)

        // 4. Notify UI
        onScoreChanged?.invoke(score.score, score.highScore)

        if (clearResult.linesCleared > 0) {
            onLinesCleared?.invoke(
                clearResult.clearedRows,
                clearResult.clearedCols,
                score.comboLabel()
            )
        }

        // 5. Refill tray if needed
        checkRefillTray()

        // 6. Check game-over
        if (!board.hasRoomForAny(tray.toList())) {
            isGameOver = true
            saveHighScore()
            onGameOver?.invoke()
        }

        onTrayChanged?.invoke()
        return true
    }

    // ── Restart ────────────────────────────────────────────────────────────

    fun restart() {
        board.reset()
        score.reset()
        score.highScore = prefs.getInt(KEY_HIGH, 0)
        isGameOver = false
        refillTray()
        onScoreChanged?.invoke(score.score, score.highScore)
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private fun saveHighScore() {
        prefs.edit().putInt(KEY_HIGH, score.highScore).apply()
    }

    fun saveState() {
        // Save high score whenever the app is paused
        saveHighScore()
    }
}
