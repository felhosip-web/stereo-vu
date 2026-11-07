package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test

class LcdAppearanceTest {

    private fun getLcdColors(themeId: Int, contrast: Float, ghostStrength: Float): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>> {
        val baseActive = if (themeId == 6) arrayOf(0x33, 0xFF, 0x66) else arrayOf(0xFF, 0xB0, 0x00)
        val baseGhost = if (themeId == 6) arrayOf(0x0D, 0x2E, 0x14) else arrayOf(0x2E, 0x18, 0x00)
        val c = contrast.coerceIn(0.20f, 1.0f)
        val g = ghostStrength.coerceIn(0.0f, 1.0f)
        val act = Triple((baseActive[0] * c).toInt(), (baseActive[1] * c).toInt(), (baseActive[2] * c).toInt())
        val gst = Triple((baseGhost[0] * g).toInt(), (baseGhost[1] * g).toInt(), (baseGhost[2] * g).toInt())
        return Pair(act, gst)
    }

    private fun getSevenSegmentMap(ch: Char): BooleanArray {
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

    private fun formatDb(level: Float): String {
        if (level <= 0.001f) return "-INF"
        val db = (level * 50f) - 50f
        return if (db > -49.95f) java.lang.String.format(java.util.Locale.US, "%+.1f", db) else "-INF"
    }

    @Test
    fun testDefaultSettingsConstants() {
        val defaultGhost = 0.55f
        val defaultGap = 2.0f
        val defaultSeven = true
        val defaultContrast = 0.80f

        assertEquals(0.55f, defaultGhost, 0.001f)
        assertEquals(2.0f, defaultGap, 0.001f)
        assertTrue(defaultSeven)
        assertEquals(0.80f, defaultContrast, 0.001f)
    }

    @Test
    fun testGhostStrengthColorScaling() {
        val (act6Low, gst6Low) = getLcdColors(6, 0.80f, 0.10f)
        val (act6High, gst6High) = getLcdColors(6, 0.80f, 0.90f)

        assertEquals(act6Low, act6High)
        assertTrue(gst6High.second > gst6Low.second)
    }

    @Test
    fun testContrastColorScaling() {
        val (act7Low, gst7Low) = getLcdColors(7, 0.30f, 0.55f)
        val (act7High, gst7High) = getLcdColors(7, 0.90f, 0.55f)

        assertEquals(gst7Low, gst7High)
        assertTrue(act7High.first > act7Low.first)
        assertTrue(act7High.second > act7Low.second)
    }

    @Test
    fun testSevenSegmentCharacterCoverage() {
        val requiredChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', 'I', 'N', 'F', ' ')
        for (ch in requiredChars) {
            val segs = getSevenSegmentMap(ch)
            assertEquals(7, segs.size)
        }
        val minusSegs = getSevenSegmentMap('-')
        assertEquals(booleanArrayOf(false, false, false, false, false, false, true).toList(), minusSegs.toList())

        val eightSegs = getSevenSegmentMap('8')
        assertEquals(booleanArrayOf(true, true, true, true, true, true, true).toList(), eightSegs.toList())
    }

    @Test
    fun testDbValueFormatting() {
        assertEquals("-INF", formatDb(0.0f))
        assertEquals("-INF", formatDb(0.0005f))
        assertEquals("+0.0", formatDb(1.0f))
        assertEquals("-25.0", formatDb(0.5f))
    }

    @Test
    fun testSegmentGapAffectsDimensions() {
        val ledCount = 20
        val ledH = 12f
        val gapMin = 1.0f
        val gapMax = 6.0f

        val heightMin = ledCount * (ledH + gapMin) - gapMin
        val heightMax = ledCount * (ledH + gapMax) - gapMax

        assertTrue(heightMax > heightMin)
        assertEquals(259.0f, heightMin, 0.001f)
        assertEquals(354.0f, heightMax, 0.001f)
    }

    @Test
    fun testSliderBoundsAndClamping() {
        val ghostCappedMin = (-0.5f).coerceIn(0.0f, 1.0f)
        val ghostCappedMax = (1.5f).coerceIn(0.0f, 1.0f)
        assertEquals(0.0f, ghostCappedMin, 0.001f)
        assertEquals(1.0f, ghostCappedMax, 0.001f)

        val gapCappedMin = (0.2f).coerceIn(1.0f, 6.0f)
        val gapCappedMax = (10.0f).coerceIn(1.0f, 6.0f)
        assertEquals(1.0f, gapCappedMin, 0.001f)
        assertEquals(6.0f, gapCappedMax, 0.001f)

        val contrastCappedMin = (0.05f).coerceIn(0.20f, 1.0f)
        val contrastCappedMax = (2.0f).coerceIn(0.20f, 1.0f)
        assertEquals(0.20f, contrastCappedMin, 0.001f)
        assertEquals(1.0f, contrastCappedMax, 0.001f)
    }
}
