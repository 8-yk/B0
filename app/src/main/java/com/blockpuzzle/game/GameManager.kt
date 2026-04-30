package com.blockpuzzle.game

import android.content.Context
import android.content.SharedPreferences

/**
 * GameManager v2 — يمرر حالة اللوحة لـ BlockFactory.smartGenerate
 * حتى يكون توليد القطع الثلاث مبنياً على الواقع الحالي للشبكة.
 */
class GameManager(context: Context) {

    companion object {
        const val GRID_SIZE    = 8
        private const val PREFS_NAME = "block_puzzle_prefs"
        private const val KEY_HIGH   = "high_score"
    }

    val board  = GameBoard(GRID_SIZE)
    val score  = ScoreSystem()

    val tray: Array<Block?> = arrayOfNulls(3)

    var isGameOver = false
        private set

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var onScoreChanged: ((score: Int, highScore: Int) -> Unit)? = null
    var onLinesCleared: ((rows: List<Int>, cols: List<Int>, combo: String) -> Unit)? = null
    var onGameOver:     (() -> Unit)? = null
    var onTrayChanged:  (() -> Unit)? = null

    init {
        score.highScore = prefs.getInt(KEY_HIGH, 0)
        refillTray()
    }

    // ── Tray ──────────────────────────────────────────────────────────────

    /**
     * يملأ الصينية بـ 3 قطع ذكية مبنية على حالة اللوحة الحالية.
     */
    private fun refillTray() {
        val newBlocks = BlockFactory.smartGenerate(board)
        for (i in 0..2) tray[i] = newBlocks[i]
        onTrayChanged?.invoke()
    }

    private fun checkRefillTray() {
        if (tray.all { it == null }) refillTray()
    }

    // ── Placement ─────────────────────────────────────────────────────────

    fun tryPlace(trayIndex: Int, anchorRow: Int, anchorCol: Int): Boolean {
        val block = tray[trayIndex] ?: return false
        if (!board.canPlace(block, anchorRow, anchorCol)) return false

        board.place(block, anchorRow, anchorCol)
        tray[trayIndex] = null

        val clearResult = board.clearFullLines()
        val delta       = score.recordPlacement(block.cells.size, clearResult.linesCleared)

        onScoreChanged?.invoke(score.score, score.highScore)

        if (clearResult.linesCleared > 0) {
            onLinesCleared?.invoke(
                clearResult.clearedRows,
                clearResult.clearedCols,
                score.comboLabel()
            )
        }

        checkRefillTray()

        if (!board.hasRoomForAny(tray.toList())) {
            isGameOver = true
            saveHighScore()
            onGameOver?.invoke()
        }

        onTrayChanged?.invoke()
        return true
    }

    // ── Restart ───────────────────────────────────────────────────────────

    fun restart() {
        board.reset()
        score.reset()
        score.highScore = prefs.getInt(KEY_HIGH, 0)
        isGameOver      = false
        refillTray()
        onScoreChanged?.invoke(score.score, score.highScore)
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private fun saveHighScore() {
        prefs.edit().putInt(KEY_HIGH, score.highScore).apply()
    }

    fun saveState() = saveHighScore()
}
