package com.example.stereovu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VfdAppearanceTest {

    @Test
    fun testCanonicalPaletteColors() {
        // Assert canonical active and inactive segment colors
        // Active: #00D4E8, Inactive: #001F26
        assertEquals(0xFF00D4E8.toInt(), VfdHelper.COLOR_ACTIVE)
        assertEquals(0xFF001F26.toInt(), VfdHelper.COLOR_INACTIVE)
        assertEquals(0xFF0A1215.toInt(), VfdHelper.COLOR_BACKGROUND)
        assertEquals(0xFF003840.toInt(), VfdHelper.COLOR_BORDER)
        assertEquals(0xFF040B0D.toInt(), VfdHelper.COLOR_GROOVE)
        assertEquals(0xFF66C0CC.toInt(), VfdHelper.COLOR_LABEL)
        assertEquals(0xFF006A75.toInt(), VfdHelper.COLOR_SCALE_TICK)
        assertEquals(0xFF4DAEB8.toInt(), VfdHelper.COLOR_SCALE_TEXT)
    }

    @Test
    fun testSubsegmentCountPerCell() {
        assertEquals(4, VfdHelper.SUBSEGMENTS_PER_CELL)
    }

    @Test
    fun testPeakModeColorMapping() {
        // Mode 0: White
        assertEquals(0xFFFFFFFF.toInt(), VfdHelper.getPeakColor(0))
        // Mode 1: Canonical Cyan (#00D4E8 - same as active segment)
        assertEquals(VfdHelper.COLOR_ACTIVE, VfdHelper.getPeakColor(1))
        // Mode 2: Amber (#FFCC00)
        assertEquals(0xFFFFCC00.toInt(), VfdHelper.getPeakColor(2))
        // Mode 3: Fixed cyan (#00E5FF)
        assertEquals(0xFF00E5FF.toInt(), VfdHelper.getPeakColor(3))
        // Fallback default
        assertEquals(0xFFFFFFFF.toInt(), VfdHelper.getPeakColor(99))
    }

    @Test
    fun testHorizontalSubsegmentGeometryAndProgression() {
        val cellLeft = 40f
        val cellTop = 20f
        val cellWidth = 12f
        val cellHeight = 70f
        val subGap = 2f

        // Available width for 4 subsegments: cellWidth - 3 * subGap = 12 - 6 = 6f
        // subWidth = 6 / 4 = 1.5f
        val rect0 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 0,
            subGap = subGap
        )
        val rect1 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 1,
            subGap = subGap
        )
        val rect2 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 2,
            subGap = subGap
        )
        val rect3 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 3,
            subGap = subGap
        )

        // Verify progression left to right
        assertEquals(40.0f, rect0.left, 0.001f)
        assertEquals(1.5f, rect0.width, 0.001f)
        assertEquals(70.0f, rect0.height, 0.001f)

        assertEquals(43.5f, rect1.left, 0.001f)
        assertEquals(47.0f, rect2.left, 0.001f)
        assertEquals(50.5f, rect3.left, 0.001f)

        // Monotonic progression
        assertTrue(rect0.left < rect1.left)
        assertTrue(rect1.left < rect2.left)
        assertTrue(rect2.left < rect3.left)

        // No negative or zero width/height
        assertTrue(rect0.width > 0f)
        assertTrue(rect0.height > 0f)
    }

    @Test
    fun testVerticalSubsegmentGeometryAndProgression() {
        val cellLeft = 14f
        val cellTop = 22f
        val cellWidth = 34f
        val cellHeight = 10f
        val subGap = 2f

        // Available height for 4 subsegments: cellHeight - 3 * subGap = 10 - 6 = 4f
        // subHeight = 4 / 4 = 1.0f
        // Progression is bottom-to-top as signal increases:
        // subIndex 0 is bottom-most, subIndex 3 is top-most
        val rect0 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = false,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 0,
            subGap = subGap
        )
        val rect1 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = false,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 1,
            subGap = subGap
        )
        val rect2 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = false,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 2,
            subGap = subGap
        )
        val rect3 = VfdHelper.calculateSubsegmentRect(
            isHorizontal = false,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            subIndex = 3,
            subGap = subGap
        )

        // Check positions
        // sub0 (lowest signal): top = cellTop + 3 * (1 + 2) = 22 + 9 = 31
        assertEquals(31.0f, rect0.top, 0.001f)
        assertEquals(1.0f, rect0.height, 0.001f)
        assertEquals(34.0f, rect0.width, 0.001f)

        // sub1: top = 22 + 2 * 3 = 28
        assertEquals(28.0f, rect1.top, 0.001f)
        // sub2: top = 22 + 1 * 3 = 25
        assertEquals(25.0f, rect2.top, 0.001f)
        // sub3 (highest signal): top = 22 + 0 = 22
        assertEquals(22.0f, rect3.top, 0.001f)

        // Bottom-to-top progression: sub0 is lowest (largest top), sub3 is highest (smallest top)
        assertTrue(rect0.top > rect1.top)
        assertTrue(rect1.top > rect2.top)
        assertTrue(rect2.top > rect3.top)
    }

    @Test
    fun testSmallDimensionSafety() {
        // Very small cell dimension where cellWidth < 3 * subGap
        val rect = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = 10f,
            cellTop = 10f,
            cellWidth = 2f,
            cellHeight = 2f,
            subIndex = 0,
            subGap = 2f
        )
        // Must clamp to >= 0
        assertEquals(0f, rect.width, 0.001f)
        assertTrue(rect.height >= 0f)
        assertTrue(rect.right >= rect.left)
        assertTrue(rect.bottom >= rect.top)
    }

    @Test
    fun testBoundaryValues() {
        val ledCount = 20
        val totalSubsegments = ledCount * 4 // 80

        // Zero level
        assertEquals(0, VfdHelper.calculateActiveSubsegments(0.0f, ledCount))
        assertEquals(0, VfdHelper.calculateActiveSubsegments(-0.5f, ledCount))

        // Full level
        assertEquals(totalSubsegments, VfdHelper.calculateActiveSubsegments(1.0f, ledCount))
        assertEquals(totalSubsegments, VfdHelper.calculateActiveSubsegments(1.5f, ledCount))

        // Peak at 0.0
        assertEquals(0, VfdHelper.calculatePeakSubsegment(0.0f, ledCount))
        // Peak at 1.0 -> index 79 (top-most segment)
        assertEquals(totalSubsegments - 1, VfdHelper.calculatePeakSubsegment(1.0f, ledCount))

        // Global subsegment indices
        assertEquals(0, VfdHelper.getGlobalSubsegmentIndex(0, 0))
        assertEquals(3, VfdHelper.getGlobalSubsegmentIndex(0, 3))
        assertEquals(79, VfdHelper.getGlobalSubsegmentIndex(19, 3))
    }
}
