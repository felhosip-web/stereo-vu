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
        // Horizontal meter:
        // Logical cell has width = ledH (12f), height = colW (70f).
        // 4 thin horizontal subsegments stacked vertically (divide cell along Y axis).
        // Each subsegment occupies approximately 1/4 of cell height and full cell width.
        // Progression within cell: bottom to top (subIndex 0 is bottom-most, 3 is top-most).
        val cellLeft = 40f
        val cellTop = 20f
        val cellWidth = 12f
        val cellHeight = 70f
        val subGap = 2f

        // Available height: 70 - 3 * 2 = 64f. subHeight = 64 / 4 = 16f.
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

        // All share the full width
        assertEquals(40.0f, rect0.left, 0.001f)
        assertEquals(12.0f, rect0.width, 0.001f)
        assertEquals(16.0f, rect0.height, 0.001f)
        assertEquals(12.0f, rect1.width, 0.001f)
        assertEquals(12.0f, rect2.width, 0.001f)
        assertEquals(12.0f, rect3.width, 0.001f)

        // Vertical stacking bottom to top:
        // sub0 (lowest): top = cellTop + 3 * (16 + 2) = 20 + 54 = 74f
        assertEquals(74.0f, rect0.top, 0.001f)
        // sub1: top = 20 + 2 * 18 = 56f
        assertEquals(56.0f, rect1.top, 0.001f)
        // sub2: top = 20 + 1 * 18 = 38f
        assertEquals(38.0f, rect2.top, 0.001f)
        // sub3 (highest): top = 20 + 0 = 20f
        assertEquals(20.0f, rect3.top, 0.001f)

        // Progression from bottom to top (y decreases as signal increases)
        assertTrue(rect0.top > rect1.top)
        assertTrue(rect1.top > rect2.top)
        assertTrue(rect2.top > rect3.top)

        assertTrue(rect0.width > 0f)
        assertTrue(rect0.height > 0f)
    }

    @Test
    fun testVerticalSubsegmentGeometryAndProgression() {
        // Vertical meter:
        // Logical cell has width = colW (70f), height = ledH (12f).
        // 4 narrow vertical subsegments side-by-side (divide cell along X axis).
        // Each subsegment occupies approximately 1/4 of cell width and full cell height.
        // Progression within cell: left to right (subIndex 0..3).
        val cellLeft = 14f
        val cellTop = 22f
        val cellWidth = 70f
        val cellHeight = 12f
        val subGap = 2f

        // Available width: 70 - 3 * 2 = 64f. subWidth = 64 / 4 = 16f.
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

        // All share the full cell height
        assertEquals(22.0f, rect0.top, 0.001f)
        assertEquals(12.0f, rect0.height, 0.001f)
        assertEquals(16.0f, rect0.width, 0.001f)
        assertEquals(12.0f, rect1.height, 0.001f)
        assertEquals(12.0f, rect2.height, 0.001f)
        assertEquals(12.0f, rect3.height, 0.001f)

        // Side-by-side progression left to right
        assertEquals(14.0f, rect0.left, 0.001f)
        assertEquals(32.0f, rect1.left, 0.001f)
        assertEquals(50.0f, rect2.left, 0.001f)
        assertEquals(68.0f, rect3.left, 0.001f)

        // Progression left to right
        assertTrue(rect0.left < rect1.left)
        assertTrue(rect1.left < rect2.left)
        assertTrue(rect2.left < rect3.left)

        assertTrue(rect0.width > 0f)
        assertTrue(rect0.height > 0f)
    }

    @Test
    fun testSmallDimensionSafety() {
        // Very small cell dimension where cellWidth < 3 * subGap
        val rectHoriz = VfdHelper.calculateSubsegmentRect(
            isHorizontal = true,
            cellLeft = 10f,
            cellTop = 10f,
            cellWidth = 2f,
            cellHeight = 2f,
            subIndex = 0,
            subGap = 2f
        )
        assertEquals(2f, rectHoriz.width, 0.001f)
        assertEquals(0f, rectHoriz.height, 0.001f)
        assertTrue(rectHoriz.right >= rectHoriz.left)
        assertTrue(rectHoriz.bottom >= rectHoriz.top)

        val rectVert = VfdHelper.calculateSubsegmentRect(
            isHorizontal = false,
            cellLeft = 10f,
            cellTop = 10f,
            cellWidth = 2f,
            cellHeight = 2f,
            subIndex = 0,
            subGap = 2f
        )
        assertEquals(0f, rectVert.width, 0.001f)
        assertEquals(2f, rectVert.height, 0.001f)
        assertTrue(rectVert.right >= rectVert.left)
        assertTrue(rectVert.bottom >= rectVert.top)
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
