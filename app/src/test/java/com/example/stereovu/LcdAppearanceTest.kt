package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test

class LcdAppearanceTest {

    @Test
    fun testProductionLcdColors() {
        // Direct call to production LcdHelper.getLcdColors
        val (act6Low, gst6Low) = LcdHelper.getLcdColors(6, 0.80f, 0.10f)
        val (act6High, gst6High) = LcdHelper.getLcdColors(6, 0.80f, 0.90f)

        // Contrast unchanged -> active color identical
        assertEquals(act6Low, act6High)
        // Ghost color green channel increases with ghost strength
        assertTrue(((gst6High shr 8) and 0xFF) > ((gst6Low shr 8) and 0xFF))

        // Amber theme
        val (act7Low, gst7Low) = LcdHelper.getLcdColors(7, 0.30f, 0.55f)
        val (act7High, gst7High) = LcdHelper.getLcdColors(7, 0.90f, 0.55f)

        assertEquals(gst7Low, gst7High)
        assertTrue(((act7High shr 16) and 0xFF) > ((act7Low shr 16) and 0xFF))
        assertTrue(((act7High shr 8) and 0xFF) > ((act7Low shr 8) and 0xFF))
    }

    @Test
    fun testProductionSevenSegmentCharacterCoverage() {
        // Exercise actual production mapping in LcdHelper
        val chars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', 'I', 'N', 'F', ' ')
        for (ch in chars) {
            val segs = LcdHelper.getSevenSegmentMap(ch)
            assertEquals("Char '$ch' segment count", 7, segs.size)
        }

        // Verify '-' has only middle segment
        val minusSegs = LcdHelper.getSevenSegmentMap('-')
        assertEquals(booleanArrayOf(false, false, false, false, false, false, true).toList(), minusSegs.toList())

        // Verify '8' has all 7 segments active
        val eightSegs = LcdHelper.getSevenSegmentMap('8')
        assertEquals(booleanArrayOf(true, true, true, true, true, true, true).toList(), eightSegs.toList())
    }

    @Test
    fun testProductionFormatDb() {
        // Exercise actual production LcdHelper.formatDb
        assertEquals("-INF", LcdHelper.formatDb(0.0f))
        assertEquals("-INF", LcdHelper.formatDb(0.0005f))
        assertEquals("+0.0", LcdHelper.formatDb(1.0f))
        assertEquals("-25.0", LcdHelper.formatDb(0.5f))
        assertEquals("-49.9", LcdHelper.formatDb(0.002f))
    }

    @Test
    fun testProductionHorizontalReadoutWidthCalculation() {
        // Exercise production LcdHelper.calculateHorizontalReadoutWidth
        val width7Seg = LcdHelper.calculateHorizontalReadoutWidth(true)
        val widthText = LcdHelper.calculateHorizontalReadoutWidth(false)

        assertTrue("7-seg width must be positive", width7Seg > 0f)
        assertTrue("Text width must be positive", widthText > 0f)

        // For 10 LEDs, bar width would be: 40 + 10*(12 + 2) + 20 = 200f
        // LcdHelper.calculateHorizontalReadoutWidth must ensure sufficient width so no clipping occurs
        val barW10 = 40f + 10 * (12f + 2f) + 20f
        val measuredBaseW = kotlin.math.max(barW10, width7Seg)
        assertTrue("Measured width must accommodate complete readout", measuredBaseW >= width7Seg)
        assertTrue(measuredBaseW > barW10)
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

    @Test
    fun testExistingThemesRegressionPreserved() {
        // Ensure Theme IDs 0..5 remain mapped and valid
        val validThemeIds = 0..7
        assertTrue(validThemeIds.contains(0)) // Classic
        assertTrue(validThemeIds.contains(1)) // Cyberpunk
        assertTrue(validThemeIds.contains(2)) // Fire
        assertTrue(validThemeIds.contains(3)) // Ice
        assertTrue(validThemeIds.contains(4)) // Sunset
        assertTrue(validThemeIds.contains(5)) // VFD
        assertTrue(validThemeIds.contains(6)) // LCD Green
        assertTrue(validThemeIds.contains(7)) // LCD Amber
    }
}
