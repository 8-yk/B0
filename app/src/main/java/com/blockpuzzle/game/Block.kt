package com.blockpuzzle.game

import android.graphics.Color

/**
 * Represents a single block piece with a shape (list of cells) and color.
 * Shape cells are relative offsets from the top-left anchor (row, col).
 */
data class Block(
    val cells: List<Pair<Int, Int>>,   // (row, col) offsets
    val color: Int,
    val id: Int = 0
) {
    val rowSpan: Int get() = (cells.maxOfOrNull { it.first } ?: 0) + 1
    val colSpan: Int get() = (cells.maxOfOrNull { it.second } ?: 0) + 1
}

/**
 * All available piece shapes and the color palette.
 */
object BlockFactory {

    // ── Vibrant color palette ──────────────────────────────────────────────
    val COLORS = intArrayOf(
        Color.parseColor("#FF5252"),   // Red
        Color.parseColor("#FF9800"),   // Orange
        Color.parseColor("#FFD600"),   // Yellow
        Color.parseColor("#69F0AE"),   // Green
        Color.parseColor("#40C4FF"),   // Cyan
        Color.parseColor("#448AFF"),   // Blue
        Color.parseColor("#B388FF"),   // Purple
        Color.parseColor("#FF80AB")    // Pink
    )

    // ── Shape definitions ──────────────────────────────────────────────────
    private val SHAPES: List<List<Pair<Int, Int>>> = listOf(

        // Singles & doubles (easy filler pieces)
        listOf(Pair(0, 0)),                                          // 1×1
        listOf(Pair(0, 0), Pair(0, 1)),                             // 1×2 H
        listOf(Pair(0, 0), Pair(1, 0)),                             // 2×1 V

        // Trominoes
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2)),                 // 1×3 H
        listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0)),                 // 3×1 V
        listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1)),                 // L-mini
        listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1)),                 // J-mini
        listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0)),                 // corner TL
        listOf(Pair(0, 0), Pair(0, 1), Pair(1, 1)),                 // corner TR

        // Tetrominoes
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(0, 3)),    // I-H
        listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(3, 0)),    // I-V
        listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0), Pair(1, 1)),    // 2×2 square
        listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(2, 1)),    // L
        listOf(Pair(0, 1), Pair(1, 1), Pair(2, 0), Pair(2, 1)),    // J
        listOf(Pair(0, 0), Pair(0, 1), Pair(1, 1), Pair(1, 2)),    // S
        listOf(Pair(0, 1), Pair(0, 2), Pair(1, 0), Pair(1, 1)),    // Z
        listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, 2)),    // T
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 0)),    // L-flat
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 2)),    // J-flat

        // Pentominoes (larger, higher score)
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(0, 3), Pair(0, 4)),  // I-5H
        listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(3, 0), Pair(4, 0)),  // I-5V
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 0), Pair(1, 1)),  // P
        listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1), Pair(2, 1), Pair(2, 2)),  // Z-5
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 1), Pair(2, 1)),  // Plus-ish
        listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(2, 1), Pair(2, 2)),  // L-5
        listOf(Pair(0, 2), Pair(1, 2), Pair(2, 0), Pair(2, 1), Pair(2, 2)),  // J-5

        // 3×3 square (high value)
        listOf(
            Pair(0, 0), Pair(0, 1), Pair(0, 2),
            Pair(1, 0), Pair(1, 1), Pair(1, 2),
            Pair(2, 0), Pair(2, 1), Pair(2, 2)
        )
    )

    // Weighted indices – smaller pieces appear more often so the game stays fair
    private val WEIGHTED_INDICES: List<Int> = buildList {
        // singles & doubles: weight 6
        repeat(6) { addAll(listOf(0, 1, 2)) }
        // trominoes: weight 5
        repeat(5) { addAll(listOf(3, 4, 5, 6, 7, 8)) }
        // tetrominoes: weight 4
        repeat(4) { addAll(listOf(9, 10, 11, 12, 13, 14, 15, 16, 17, 18)) }
        // pentominoes: weight 2
        repeat(2) { addAll(listOf(19, 20, 21, 22, 23, 24, 25)) }
        // 3×3: weight 1
        add(26)
    }

    private var colorIndex = 0

    /** Returns a random block that is guaranteed to fit on an [gridSize]×[gridSize] board. */
    fun randomBlock(gridSize: Int = 8): Block {
        val idx = WEIGHTED_INDICES.random()
        val shape = SHAPES[idx]
        // Safety: if shape is too big for grid, fall back to a tromino
        val safeShape = if (
            shape.maxOf { it.first } >= gridSize ||
            shape.maxOf { it.second } >= gridSize
        ) SHAPES[3] else shape

        val color = COLORS[colorIndex % COLORS.size]
        colorIndex++
        return Block(safeShape, color)
    }

    /** Generates a balanced set of 3 blocks (avoid three huge pieces at once). */
    fun generateThreeBlocks(gridSize: Int = 8): List<Block> {
        val blocks = mutableListOf<Block>()
        var largeCount = 0
        repeat(3) {
            val candidate = randomBlock(gridSize)
            val isLarge = candidate.cells.size >= 5
            if (isLarge && largeCount >= 1) {
                // Force a smaller piece
                val smallShape = SHAPES[(0..8).random()]
                val color = COLORS[colorIndex % COLORS.size]
                colorIndex++
                blocks.add(Block(smallShape, color))
            } else {
                if (isLarge) largeCount++
                blocks.add(candidate)
            }
        }
        return blocks
    }
}
