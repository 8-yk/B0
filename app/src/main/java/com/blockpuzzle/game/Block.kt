package com.blockpuzzle.game

import android.graphics.Color
import kotlin.math.max

/**
 * Represents a single block piece.
 */
data class Block(
    val cells: List<Pair<Int, Int>>,
    val color: Int,
    val id: Int = 0
) {
    val rowSpan: Int get() = (cells.maxOfOrNull { it.first }  ?: 0) + 1
    val colSpan: Int get() = (cells.maxOfOrNull { it.second } ?: 0) + 1
}

// ══════════════════════════════════════════════════════════════════════════
// BlockFactory — Smart piece generator (v2)
// ══════════════════════════════════════════════════════════════════════════

/**
 * نظام توليد ذكي يأخذ حالة اللوحة بعين الاعتبار:
 *
 * 1. يحسب لكل شكل "درجة ذكاء" بناءً على:
 *    - عدد المواضع الصالحة على اللوحة الحالية (كلما أكثر = أسهل وضعاً)
 *    - إمكانية تفعيل حذف صفوف/أعمدة (Combo potential)
 *    - مدى ملء الثغرات بجانب بلوكات موجودة (Adjacency bonus)
 *
 * 2. يختار القطع الثلاث بمنطق متدرّج:
 *    - واحدة على الأقل "سهلة الوضع" (score عالي) لتبقي اللاعب قادراً
 *    - البقية مزيج بين ذكي وعشوائي (70 / 30)
 *    - لا تتكرر ثلاث قطع ضخمة في نفس الوقت
 *
 * 3. عند اقتراب اللوحة من الامتلاء → يتحول تلقائياً لقطع أصغر
 */
object BlockFactory {

    val COLORS = intArrayOf(
        Color.parseColor("#FF5252"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#FFD600"),
        Color.parseColor("#69F0AE"),
        Color.parseColor("#40C4FF"),
        Color.parseColor("#448AFF"),
        Color.parseColor("#B388FF"),
        Color.parseColor("#FF80AB")
    )

    // ── Shapes ────────────────────────────────────────────────────────────
    // Index مهم: 0-2 أحادية/ثنائية، 3-8 ثلاثية، 9-18 رباعية، 19-25 خماسية، 26 مربع 3×3
    val SHAPES: List<List<Pair<Int, Int>>> = listOf(
        // 0-2: singles & doubles
        listOf(Pair(0,0)),
        listOf(Pair(0,0), Pair(0,1)),
        listOf(Pair(0,0), Pair(1,0)),
        // 3-8: trominoes
        listOf(Pair(0,0), Pair(0,1), Pair(0,2)),
        listOf(Pair(0,0), Pair(1,0), Pair(2,0)),
        listOf(Pair(0,0), Pair(1,0), Pair(1,1)),
        listOf(Pair(0,1), Pair(1,0), Pair(1,1)),
        listOf(Pair(0,0), Pair(0,1), Pair(1,0)),
        listOf(Pair(0,0), Pair(0,1), Pair(1,1)),
        // 9-18: tetrominoes
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(0,3)),
        listOf(Pair(0,0), Pair(1,0), Pair(2,0), Pair(3,0)),
        listOf(Pair(0,0), Pair(0,1), Pair(1,0), Pair(1,1)),
        listOf(Pair(0,0), Pair(1,0), Pair(2,0), Pair(2,1)),
        listOf(Pair(0,1), Pair(1,1), Pair(2,0), Pair(2,1)),
        listOf(Pair(0,0), Pair(0,1), Pair(1,1), Pair(1,2)),
        listOf(Pair(0,1), Pair(0,2), Pair(1,0), Pair(1,1)),
        listOf(Pair(0,1), Pair(1,0), Pair(1,1), Pair(1,2)),
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(1,0)),
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(1,2)),
        // 19-25: pentominoes
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(0,3), Pair(0,4)),
        listOf(Pair(0,0), Pair(1,0), Pair(2,0), Pair(3,0), Pair(4,0)),
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(1,0), Pair(1,1)),
        listOf(Pair(0,0), Pair(1,0), Pair(1,1), Pair(2,1), Pair(2,2)),
        listOf(Pair(0,0), Pair(0,1), Pair(0,2), Pair(1,1), Pair(2,1)),
        listOf(Pair(0,0), Pair(1,0), Pair(2,0), Pair(2,1), Pair(2,2)),
        listOf(Pair(0,2), Pair(1,2), Pair(2,0), Pair(2,1), Pair(2,2)),
        // 26: 3×3 square
        listOf(Pair(0,0),Pair(0,1),Pair(0,2),Pair(1,0),Pair(1,1),Pair(1,2),Pair(2,0),Pair(2,1),Pair(2,2))
    )

    // ── Category helpers ───────────────────────────────────────────────────
    private val SMALL_INDICES  = (0..8).toList()    // 1-3 خلايا
    private val MEDIUM_INDICES = (9..18).toList()   // 4 خلايا
    private val LARGE_INDICES  = (19..26).toList()  // 5-9 خلايا

    private var colorIndex = 0

    // ══════════════════════════════════════════════════════════════════════
    // الدالة الرئيسية: توليد 3 قطع ذكي
    // ══════════════════════════════════════════════════════════════════════

    /**
     * يولّد 3 قطع بناءً على حالة اللوحة الحالية.
     * إذا كانت اللوحة فارغة تماماً → يستخدم التوليد العشوائي المتوازن.
     */
    fun smartGenerate(board: GameBoard): List<Block> {
        val fillRatio = boardFillRatio(board)

        // لوحة فارغة أو شبه فارغة → عشوائي متوازن
        if (fillRatio < 0.15f) return balancedRandom(board.gridSize)

        // احسب درجة كل شكل
        val scores = scoreAllShapes(board)

        val result = mutableListOf<Block>()
        var largeCount = 0

        // ── القطعة الأولى: الأذكى (ضمان وجود قطعة قابلة للوضع دائماً) ──
        val best = pickByScore(scores, board, minPlacements = 3, bias = 1.0f)
        result.add(makeBlock(best))

        // ── القطعتان الباقيتان: مزيج ذكي/عشوائي ──────────────────────
        repeat(2) {
            val useSmartPick = Math.random() < 0.65
            val idx = if (useSmartPick) {
                pickByScore(scores, board, minPlacements = 1, bias = 0.6f)
            } else {
                fallbackRandom(board.gridSize)
            }

            val shape    = SHAPES[idx]
            val isLarge  = shape.size >= 5
            val tooBig   = isLarge && (largeCount >= 1 || fillRatio > 0.6f)

            if (tooBig) {
                result.add(makeBlock(SMALL_INDICES.random()))
            } else {
                if (isLarge) largeCount++
                result.add(makeBlock(idx))
            }
        }

        return result
    }

    /** Fallback: توليد متوازن بدون تحليل اللوحة */
    fun generateThreeBlocks(gridSize: Int = 8): List<Block> = balancedRandom(gridSize)

    // ══════════════════════════════════════════════════════════════════════
    // خوارزمية التقييم
    // ══════════════════════════════════════════════════════════════════════

    /**
     * درجة كل شكل = validPlacements × w1 + comboPotential × w2 + adjacency × w3
     *
     * validPlacements:  عدد مواضع صالحة على اللوحة
     * comboPotential:   عدد الصفوف/الأعمدة التي يمكن إكمالها بوضع هذه القطعة
     * adjacency:        عدد الخلايا المجاورة لبلوكات موجودة (يشجع ملء الثغرات)
     */
    private fun scoreAllShapes(board: GameBoard): IntArray {
        val gs     = board.gridSize
        val scores = IntArray(SHAPES.size)

        for ((idx, shape) in SHAPES.withIndex()) {
            if (shape.maxOf { it.first } >= gs || shape.maxOf { it.second } >= gs) continue

            val block = Block(shape, 0)
            var validPlacements = 0
            var comboPotential  = 0
            var adjacencyBonus  = 0

            for (r in 0 until gs) {
                for (c in 0 until gs) {
                    if (!board.canPlace(block, r, c)) continue
                    validPlacements++

                    // محاكاة الوضع
                    val temp = board.snapshot()
                    for ((dr, dc) in shape) {
                        val nr = r + dr; val nc = c + dc
                        if (nr in 0 until gs && nc in 0 until gs) temp[nr][nc] = 1
                    }

                    // Combo potential
                    val clearRows = (0 until gs).count { row -> temp[row].all { it != 0 } }
                    val clearCols = (0 until gs).count { col -> (0 until gs).all { rr -> temp[rr][col] != 0 } }
                    comboPotential += (clearRows + clearCols)

                    // Adjacency: كم خلية مجاورة للبلوكات الموجودة
                    for ((dr, dc) in shape) {
                        val nr = r + dr; val nc = c + dc
                        adjacencyBonus += countFilledNeighbors(board, nr, nc)
                    }
                }
            }

            scores[idx] = validPlacements * 2 + comboPotential * 15 + adjacencyBonus
        }
        return scores
    }

    /**
     * اختيار شكل بناءً على الدرجات مع عامل عشوائية.
     * [bias] = 1.0 → يختار من أفضل 20%  |  0.5 → من أفضل 50%
     * [minPlacements] = حدّ أدنى من المواضع الصالحة لضمان الصلاحية.
     */
    private fun pickByScore(
        scores: IntArray,
        board: GameBoard,
        minPlacements: Int,
        bias: Float
    ): Int {
        val gs = board.gridSize
        val ranked = scores.indices
            .filter { idx ->
                val shape = SHAPES[idx]
                shape.maxOf { it.first } < gs && shape.maxOf { it.second } < gs &&
                Block(shape, 0).let { board.hasRoomFor(it) }
            }
            .sortedByDescending { scores[it] }

        if (ranked.isEmpty()) return SMALL_INDICES.random()

        val poolSize = max(1, (ranked.size * (1f - bias * 0.7f)).toInt())
        return ranked.take(poolSize).random()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private fun boardFillRatio(board: GameBoard): Float {
        val gs    = board.gridSize
        val total = gs * gs
        val filled = board.board.sumOf { row -> row.count { it != 0 } }
        return filled.toFloat() / total
    }

    private fun countFilledNeighbors(board: GameBoard, r: Int, c: Int): Int {
        val gs = board.gridSize
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        return dirs.count { (dr, dc) ->
            val nr = r + dr; val nc = c + dc
            nr in 0 until gs && nc in 0 until gs && board.board[nr][nc] != 0
        }
    }

    private fun balancedRandom(gridSize: Int): List<Block> {
        val result = mutableListOf<Block>()
        var largeCount = 0
        repeat(3) {
            val idx = fallbackRandom(gridSize)
            val shape   = SHAPES[idx]
            val isLarge = shape.size >= 5
            if (isLarge && largeCount >= 1) {
                result.add(makeBlock(SMALL_INDICES.random()))
            } else {
                if (isLarge) largeCount++
                result.add(makeBlock(idx))
            }
        }
        return result
    }

    private val WEIGHTED_POOL: List<Int> = buildList {
        repeat(6) { addAll(SMALL_INDICES) }
        repeat(4) { addAll(MEDIUM_INDICES) }
        repeat(2) { addAll(LARGE_INDICES.dropLast(1)) }  // no 3×3 in random pool
    }

    private fun fallbackRandom(gridSize: Int): Int {
        val idx   = WEIGHTED_POOL.random()
        val shape = SHAPES[idx]
        return if (shape.maxOf { it.first } >= gridSize ||
                   shape.maxOf { it.second } >= gridSize) SMALL_INDICES[3]
        else idx
    }

    private fun makeBlock(shapeIdx: Int): Block {
        val color = COLORS[colorIndex % COLORS.size]
        colorIndex++
        return Block(SHAPES[shapeIdx], color)
    }
}
