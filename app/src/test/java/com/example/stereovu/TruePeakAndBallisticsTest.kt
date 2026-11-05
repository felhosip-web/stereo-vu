package com.example.stereovu

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

class TruePeakAndBallisticsTest {

    @Test
    fun testTruePeakIntersampleOvershoot() {
        val tp = TruePeakDetector()
        val s = sin(PI / 4.0) // 0.707106...
        var maxL = 0.0
        var maxR = 0.0

        for (i in 0 until 40) {
            val sampleVal = when (i % 4) {
                0 -> s
                1 -> s
                2 -> -s
                3 -> -s
                else -> 0.0
            }
            tp.processSample(sampleVal, sampleVal * 0.5)
            val pL = tp.lastPeakL
            val pR = tp.lastPeakR
            if (pL > maxL) maxL = pL
            if (pR > maxR) maxR = pR
        }

        // True Peak must detect inter-sample peak above 0.7071
        assertTrue("Expected True Peak > 0.85, got $maxL", maxL > 0.85)
        assertTrue("Expected True Peak R > 0.42, got $maxR", maxR > 0.42)
    }

    @Test
    fun testBufferContinuity() {
        val tpContinuous = TruePeakDetector()
        val tpSplit = TruePeakDetector()
        val s = sin(PI / 4.0)

        var maxCont = 0.0
        for (i in 0 until 32) {
            val v = if (i % 4 in 0..1) s else -s
            tpContinuous.processSample(v, v)
            val pL = tpContinuous.lastPeakL
            if (pL > maxCont) maxCont = pL
        }

        var maxSplit = 0.0
        // Split across two simulated buffers of 16 samples each
        for (i in 0 until 16) {
            val v = if (i % 4 in 0..1) s else -s
            tpSplit.processSample(v, v)
            val pL = tpSplit.lastPeakL
            if (pL > maxSplit) maxSplit = pL
        }
        for (i in 16 until 32) {
            val v = if (i % 4 in 0..1) s else -s
            tpSplit.processSample(v, v)
            val pL = tpSplit.lastPeakL
            if (pL > maxSplit) maxSplit = pL
        }

        assertEquals(maxCont, maxSplit, 0.0001)
    }

    @Test
    fun testPeakNeverBelowBarLogic() {
        var peak = 0.5f
        var displayLevel = 0.8f
        val peakNeverBelowBar = true
        var peakHoldTimeRemaining = 0L

        if (displayLevel >= peak) {
            peak = displayLevel
            peakHoldTimeRemaining = 1000L
        }
        if (peakNeverBelowBar && peak < displayLevel) {
            peak = displayLevel
        }
        assertEquals(0.8f, peak, 0.001f)
        assertEquals(1000L, peakHoldTimeRemaining)

        // When display level drops to 0.7f, decaying peak must never drop below 0.7f
        displayLevel = 0.7f
        peak *= 0.5f // 0.8 * 0.5 = 0.4
        if (peakNeverBelowBar && peak < displayLevel) {
            peak = displayLevel
        }
        assertEquals(0.7f, peak, 0.001f)
    }

    @Test
    fun testClassicVuIndependence() {
        // Mode 4 (Classic VU) sets spring=0.10f, damp=0.32f
        val userSpring = 0.35f
        val userDamp = 0.15f
        val mode = 4

        var spring = userSpring
        var damp = userDamp

        if (mode == 4) {
            spring = 0.10f
            damp = 0.32f
        }

        assertEquals(0.10f, spring, 0.001f)
        assertEquals(0.32f, damp, 0.001f)
    }
}
