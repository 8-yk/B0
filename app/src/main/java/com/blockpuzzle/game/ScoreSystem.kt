package com.blockpuzzle.game

/**
 * ScoreSystem handles:
 *  - Per-placement points (1 pt per cell placed)
 *  - Line-clear bonus (10 pts per line)
 *  - Combo multiplier (consecutive clears in one turn)
 *  - High-score tracking (in-memory; persistence via SharedPreferences in GameManager)
 */
class ScoreSystem {

    var score: Int = 0
        private set

    var highScore: Int = 0          // loaded & saved externally by GameManager

    var comboCount: Int = 0         // how many lines cleared this turn (for UI flash)
        private set

    private var consecutiveClearTurns = 0   // turns in a row that cleared ≥1 line

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Call after a successful block placement.
     * @param cellsPlaced number of cells in the placed block
     * @param linesCleared rows + cols cleared in this turn
     * @return score delta (how many points were added)
     */
    fun recordPlacement(cellsPlaced: Int, linesCleared: Int): Int {
        var delta = cellsPlaced          // base: 1 pt / cell

        if (linesCleared > 0) {
            consecutiveClearTurns++
            comboCount = linesCleared
            val comboMultiplier = consecutiveClearTurns  // 1× first clear, 2× second, …
            delta += linesCleared * 10 * comboMultiplier // 10 pts/line × multiplier
        } else {
            consecutiveClearTurns = 0
            comboCount = 0
        }

        score += delta
        if (score > highScore) highScore = score
        return delta
    }

    fun reset() {
        score = 0
        comboCount = 0
        consecutiveClearTurns = 0
    }

    // ── Combo label helper ─────────────────────────────────────────────────

    /** Returns a display label like "COMBO ×3" or "" */
    fun comboLabel(): String = when {
        consecutiveClearTurns >= 2 -> "COMBO ×$consecutiveClearTurns"
        else -> ""
    }
}
