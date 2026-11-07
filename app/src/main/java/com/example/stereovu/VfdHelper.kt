package com.example.stereovu

import kotlin.math.max

/**
 * Pure helper for VFD (Klasszikus Cián / Videoton) display theme.
 * Standardizes color palettes, subsegment geometry, progression order,
 * and peak mode color mappings across platforms without requiring Android graphics dependencies in tests.
 */
object VfdHelper {
    // Canonical VFD Palette (Videoton amplifier style)
    const val COLOR_ACTIVE: Int = 0xFF00D4E8.toInt()       // #00D4E8 - Videoton Cyan
    const val COLOR_INACTIVE: Int = 0xFF001F26.toInt()     // #001F26 - Ghost teal
    const val COLOR_BACKGROUND: Int = 0xFF0A1215.toInt()   // #0A1215 - Dark teal-tinted background
    const val COLOR_BORDER: Int = 0xFF003840.toInt()       // #003840 - Bezel border
    const val COLOR_GROOVE: Int = 0xFF040B0D.toInt()       // #040B0D - Groove
    const val COLOR_LABEL: Int = 0xFF66C0CC.toInt()        // #66C0CC - Channel label
    const val COLOR_SCALE_TICK: Int = 0xFF006A75.toInt()   // #006A75 - dB tick marks
    const val COLOR_SCALE_TEXT: Int = 0xFF4DAEB8.toInt()   // #4DAEB8 - dB text
    const val COLOR_WIRE: Int = 0x2DFFFFFF                 // 45/255 white grid wires

    // Peak color mappings
    const val COLOR_PEAK_WHITE: Int = 0xFFFFFFFF.toInt()   // Mode 0: White
    const val COLOR_PEAK_CYAN: Int = 0xFF00D4E8.toInt()    // Mode 1: Canonical Cyan (same as active)
    const val COLOR_PEAK_AMBER: Int = 0xFFFFCC00.toInt()   // Mode 2: Amber
    const val COLOR_PEAK_FIXED_CYAN: Int = 0xFF00E5FF.toInt() // Mode 3: Fixed bright cyan

    const val SUBSEGMENTS_PER_CELL = 4

    data class RectCoords(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = max(0f, right - left)
        val height: Float get() = max(0f, bottom - top)
    }

    /**
     * Resolves the peak color ARGB integer for the given peak mode.
     * Modes:
     * 0: White peak
     * 1: Active cyan (same as VFD active segment)
     * 2: Amber peak
     * 3: Fixed cyan peak
     */
    fun getPeakColor(peakMode: Int): Int {
        return when (peakMode) {
            1 -> COLOR_PEAK_CYAN
            2 -> COLOR_PEAK_AMBER
            3 -> COLOR_PEAK_FIXED_CYAN
            else -> COLOR_PEAK_WHITE
        }
    }

    /**
     * Calculates the bounding rectangle of a subsegment within a logical cell.
     *
     * In both vertical and horizontal meters, the subsegments divide the cell along the progression axis:
     * - Horizontal: logical cell at [cellLeft, cellTop, cellLeft + cellW, cellTop + cellH]
     *   Progression is left-to-right: subsegment j (0..3) occupies a horizontal slice.
     *   subLeft = cellLeft + j * (subW + subGap)
     * - Vertical: logical cell at [cellLeft, cellTop, cellLeft + cellW, cellTop + cellH]
     *   Progression is bottom-to-top: subsegment j (0..3) occupies a vertical slice.
     *   Since cellTop is the top of the cell and higher signal is towards top,
     *   subsegment 0 (lowest) is at the bottom of the cell, and subsegment 3 (highest) is at the top:
     *   subTop = cellTop + (3 - j) * (subH + subGap)
     *
     * To prevent overlapping or negative dimensions when scaled down, subsegment width/height
     * are clamped to at least 0f.
     */
    fun calculateSubsegmentRect(
        isHorizontal: Boolean,
        cellLeft: Float,
        cellTop: Float,
        cellWidth: Float,
        cellHeight: Float,
        subIndex: Int,
        subGap: Float = 2.0f
    ): RectCoords {
        val clampedSubIdx = subIndex.coerceIn(0, SUBSEGMENTS_PER_CELL - 1)
        return if (isHorizontal) {
            // Horizontal meter: cell progresses horizontally (width = cellWidth, height = cellHeight)
            // 4 subsegments along X axis
            val availableW = max(0f, cellWidth - (SUBSEGMENTS_PER_CELL - 1) * subGap)
            val subW = availableW / SUBSEGMENTS_PER_CELL.toFloat()
            val left = cellLeft + clampedSubIdx * (subW + subGap)
            RectCoords(
                left = left,
                top = cellTop,
                right = left + subW,
                bottom = cellTop + max(0f, cellHeight)
            )
        } else {
            // Vertical meter: cell progresses vertically bottom-to-top
            // 4 subsegments along Y axis
            val availableH = max(0f, cellHeight - (SUBSEGMENTS_PER_CELL - 1) * subGap)
            val subH = availableH / SUBSEGMENTS_PER_CELL.toFloat()
            // subIndex 0 is bottom-most, 3 is top-most
            val top = cellTop + (SUBSEGMENTS_PER_CELL - 1 - clampedSubIdx) * (subH + subGap)
            RectCoords(
                left = cellLeft,
                top = top,
                right = cellLeft + max(0f, cellWidth),
                bottom = top + subH
            )
        }
    }

    /**
     * Calculates the global subsegment index (0 until totalSubsegments) for a given cell index
     * and subsegment index.
     */
    fun getGlobalSubsegmentIndex(cellIndex: Int, subIndex: Int): Int {
        return cellIndex * SUBSEGMENTS_PER_CELL + subIndex
    }

    /**
     * Computes the number of active subsegments for a given level (0.0..1.0).
     */
    fun calculateActiveSubsegments(level: Float, totalCells: Int): Int {
        val totalSubsegments = totalCells * SUBSEGMENTS_PER_CELL
        return (level.coerceIn(0f, 1f) * totalSubsegments).toInt()
    }

    /**
     * Computes the peak subsegment index for a given peak value (0.0..1.0).
     * Clamped to [0, totalSubsegments - 1].
     */
    fun calculatePeakSubsegment(peak: Float, totalCells: Int): Int {
        val totalSubsegments = totalCells * SUBSEGMENTS_PER_CELL
        return (peak * totalSubsegments).toInt().coerceIn(0, max(0, totalSubsegments - 1))
    }
}
