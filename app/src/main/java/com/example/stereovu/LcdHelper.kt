package com.example.stereovu

import java.util.Locale

object LcdHelper {

    fun getLcdColors(themeId: Int, contrast: Float, ghostStrength: Float): Pair<Int, Int> {
        val baseActive = if (themeId == 6) arrayOf(0x33, 0xFF, 0x66) else arrayOf(0xFF, 0xB0, 0x00)
        val baseGhost = if (themeId == 6) arrayOf(0x0D, 0x2E, 0x14) else arrayOf(0x2E, 0x18, 0x00)
        val c = contrast.coerceIn(0.20f, 1.0f)
        val g = ghostStrength.coerceIn(0.0f, 1.0f)
        val rAct = (baseActive[0] * c).toInt().coerceIn(0, 255)
        val gAct = (baseActive[1] * c).toInt().coerceIn(0, 255)
        val bAct = (baseActive[2] * c).toInt().coerceIn(0, 255)
        val act = (0xFF shl 24) or (rAct shl 16) or (gAct shl 8) or bAct

        val rGst = (baseGhost[0] * g).toInt().coerceIn(0, 255)
        val gGst = (baseGhost[1] * g).toInt().coerceIn(0, 255)
        val bGst = (baseGhost[2] * g).toInt().coerceIn(0, 255)
        val gst = (0xFF shl 24) or (rGst shl 16) or (gGst shl 8) or bGst
        return Pair(act, gst)
    }

    fun getSevenSegmentMap(ch: Char): BooleanArray {
        return when (ch) {
            '0' -> booleanArrayOf(true, true, true, true, true, true, false)
            '1' -> booleanArrayOf(false, true, true, false, false, false, false)
            '2' -> booleanArrayOf(true, true, false, true, true, false, true)
            '3' -> booleanArrayOf(true, true, true, true, false, false, true)
            '4' -> booleanArrayOf(false, true, true, false, false, true, true)
            '5' -> booleanArrayOf(true, false, true, true, false, true, true)
            '6' -> booleanArrayOf(true, false, true, true, true, true, true)
            '7' -> booleanArrayOf(true, true, true, false, false, false, false)
            '8' -> booleanArrayOf(true, true, true, true, true, true, true)
            '9' -> booleanArrayOf(true, true, true, true, false, true, true)
            '-' -> booleanArrayOf(false, false, false, false, false, false, true)
            'I' -> booleanArrayOf(false, true, true, false, false, false, false)
            'N' -> booleanArrayOf(true, true, true, false, true, true, false)
            'F' -> booleanArrayOf(true, false, false, false, true, true, true)
            else -> booleanArrayOf(false, false, false, false, false, false, false)
        }
    }

    fun formatDb(level: Float): String {
        if (level <= 0.001f) return "-INF"
        val db = (level * 50f) - 50f
        return if (db > -49.95f) java.lang.String.format(Locale.US, "%+.1f", db) else "-INF"
    }

    fun calculateHorizontalReadoutWidth(
        sevenSegment: Boolean,
        startX: Float = 40f,
        charW: Float = 11f,
        charGap: Float = 3f
    ): Float {
        // Starts drawing at startX (40f).
        // In seven-segment mode:
        // startX + L:(20) + 5 chars (+0.0 or -49.9 or -INF) + PK:(42) + 5 chars + gap(30) + R:(20) + 5 chars + PK:(42) + 5 chars + rightMargin(20)
        return if (sevenSegment) {
            val fiveCharsW = 5 * charW + 4 * charGap
            startX + 20f + fiveCharsW + 10f + 32f + fiveCharsW + 30f + 20f + fiveCharsW + 10f + 32f + fiveCharsW + 20f
        } else {
            // Starts at startX: text length ~380px + 20px padding
            startX + 400f
        }
    }
}
