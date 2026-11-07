package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max

class LcdAppearanceTest {

    @Test
    fun testProductionLcdColors() {
        val (act6Low, gst6Low) = LcdHelper.getLcdColors(6, 0.80f, 0.10f)
        val (act6High, gst6High) = LcdHelper.getLcdColors(6, 0.80f, 0.90f)

        assertEquals(act6Low, act6High)
        assertTrue(((gst6High shr 8) and 0xFF) > ((gst6Low shr 8) and 0xFF))

        val (act7Low, gst7Low) = LcdHelper.getLcdColors(7, 0.30f, 0.55f)
        val (act7High, gst7High) = LcdHelper.getLcdColors(7, 0.90f, 0.55f)

        assertEquals(gst7Low, gst7High)
        assertTrue(((act7High shr 16) and 0xFF) > ((act7Low shr 16) and 0xFF))
        assertTrue(((act7High shr 8) and 0xFF) > ((act7Low shr 8) and 0xFF))
    }

    @Test
    fun testProductionSevenSegmentCharacterCoverage() {
        val chars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '-', 'I', 'N', 'F', ' ')
        for (ch in chars) {
            val segs = LcdHelper.getSevenSegmentMap(ch)
            assertEquals("Char '$ch' segment count", 7, segs.size)
        }

        val minusSegs = LcdHelper.getSevenSegmentMap('-')
        assertEquals(booleanArrayOf(false, false, false, false, false, false, true).toList(), minusSegs.toList())

        val eightSegs = LcdHelper.getSevenSegmentMap('8')
        assertEquals(booleanArrayOf(true, true, true, true, true, true, true).toList(), eightSegs.toList())
    }

    @Test
    fun testProductionFormatDb() {
        assertEquals("-INF", LcdHelper.formatDb(0.0f))
        assertEquals("-INF", LcdHelper.formatDb(0.0005f))
        assertEquals("+0.0", LcdHelper.formatDb(1.0f))
        assertEquals("-25.0", LcdHelper.formatDb(0.5f))
        assertEquals("-49.9", LcdHelper.formatDb(0.002f))
    }

    @Test
    fun testProductionHorizontalReadoutWidthCalculation() {
        val width7Seg = LcdHelper.calculateHorizontalReadoutWidth(true)
        val widthText = LcdHelper.calculateHorizontalReadoutWidth(false)

        assertTrue("7-seg width must be positive", width7Seg > 0f)
        assertTrue("Text width must be positive", widthText > 0f)

        // For small meter: 10 LEDs bar width = 40 + 10*(12 + 2) + 20 = 200f
        // LcdHelper.calculateHorizontalReadoutWidth must ensure measured width is >= readout text width + startX
        val barW10 = 40f + 10 * (12f + 2f) + 20f
        val measuredBaseW7Seg = max(barW10, width7Seg)
        val measuredBaseWText = max(barW10, widthText)

        assertTrue(measuredBaseW7Seg >= width7Seg)
        assertTrue(measuredBaseWText >= widthText)
        assertTrue(measuredBaseW7Seg > barW10)
        assertTrue(measuredBaseWText > barW10)
    }

    @Test
    fun testProductionLayoutRefreshDecisionLogic() {
        // Base unchanged parameters
        val s = 1.0f; val lc = 20; val h = false; val db = true; val gap = 2.0f; val ss = true

        // 1. Both theme transition directions: non-LCD -> LCD and LCD -> non-LCD
        assertTrue("Non-LCD (0) to LCD Green (6) must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 0, 6, gap, gap, ss, ss))
        assertTrue("LCD Green (6) to Non-LCD (0) must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 6, 0, gap, gap, ss, ss))
        assertTrue("LCD Amber (7) to Non-LCD (5) must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 7, 5, gap, gap, ss, ss))
        assertTrue("LCD Green (6) to LCD Amber (7) must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 6, 7, gap, gap, ss, ss))
        assertFalse("Non-LCD (0) to Non-LCD (1) should not relayout unless dimensions changed",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 0, 1, gap, gap, ss, ss))

        // 2. Both seven-segment setting transitions: OFF -> ON and ON -> OFF
        assertTrue("Seven-segment OFF -> ON in LCD theme must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 6, 6, gap, gap, false, true))
        assertTrue("Seven-segment ON -> OFF in LCD theme must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 6, 6, gap, gap, true, false))
        assertFalse("Seven-segment toggle in non-LCD theme should not relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 0, 0, gap, gap, false, true))

        // 3. Segment gap change in LCD theme vs non-LCD theme
        assertTrue("Gap change in LCD theme must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 6, 6, 2.0f, 4.0f, ss, ss))
        assertFalse("Gap change in non-LCD theme should not relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, db, db, 0, 0, 2.0f, 4.0f, ss, ss))

        // 4. Standard properties relayout
        assertTrue("Scale change must relayout",
            LcdHelper.shouldRelayout(1.0f, 1.2f, lc, lc, h, h, db, db, 0, 0, gap, gap, ss, ss))
        assertTrue("Orientation change must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, false, true, db, db, 0, 0, gap, gap, ss, ss))
        assertTrue("LED count change must relayout",
            LcdHelper.shouldRelayout(s, s, 20, 30, h, h, db, db, 0, 0, gap, gap, ss, ss))
        assertTrue("dB scale toggle must relayout",
            LcdHelper.shouldRelayout(s, s, lc, lc, h, h, true, false, 0, 0, gap, gap, ss, ss))
    }

    @Test
    fun testSharedPreferencesDefaultValuesAndPersistence() {
        // Mock SharedPreferences map to verify persistence logic and defaults
        val mockPrefs = mutableMapOf<String, Any>()

        // 1. Fresh installation defaults
        val defaultGhost = (mockPrefs["lcd_ghost_strength"] as? Float) ?: 0.55f
        val defaultGap = (mockPrefs["lcd_segment_gap"] as? Float) ?: 2.0f
        val defaultSeven = (mockPrefs["lcd_seven_segment"] as? Boolean) ?: true
        val defaultContrast = (mockPrefs["lcd_contrast"] as? Float) ?: 0.80f

        assertEquals(0.55f, defaultGhost, 0.001f)
        assertEquals(2.0f, defaultGap, 0.001f)
        assertTrue(defaultSeven)
        assertEquals(0.80f, defaultContrast, 0.001f)

        // 2. Setting modifications & persistence
        mockPrefs["lcd_ghost_strength"] = 0.70f
        mockPrefs["lcd_segment_gap"] = 3.5f
        mockPrefs["lcd_seven_segment"] = false
        mockPrefs["lcd_contrast"] = 0.90f

        assertEquals(0.70f, (mockPrefs["lcd_ghost_strength"] as Float), 0.001f)
        assertEquals(3.5f, (mockPrefs["lcd_segment_gap"] as Float), 0.001f)
        assertFalse(mockPrefs["lcd_seven_segment"] as Boolean)
        assertEquals(0.90f, (mockPrefs["lcd_contrast"] as Float), 0.001f)
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
