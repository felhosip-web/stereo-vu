package com.example.stereovu

import kotlin.math.abs

/**
 * 4x oversampling True Peak detector based on polyphase FIR interpolation
 * compliant with ITU-R BS.1770 recommendations.
 *
 * It reconstructs inter-sample peaks between sampled values across consecutive
 * audio frames and preserves filter delay history across buffer boundaries.
 */
class TruePeakDetector {
    companion object {
        // 4x oversampling polyphase FIR reconstruction filter (12 taps per sub-phase, 48 taps total)
        // Designed via windowed sinc interpolation with unity DC gain per sub-phase.
        // Tap k=0 corresponds to the newest sample, and k=11 to the oldest sample in history.
        // Phase 0 is the original sample (delay of 5 samples).
        // Phase 1 reconstructs at offset +0.25 (peak at tap 5).
        // Phase 2 reconstructs at offset +0.50 (symmetric peak between tap 5 and tap 6).
        // Phase 3 reconstructs at offset +0.75 (peak at tap 6).
        val PHASE_0 = doubleArrayOf(
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        )
        val PHASE_1 = doubleArrayOf(
            -0.00040574,  0.00452106, -0.01807915,  0.05255905, -0.14447287,  0.88354769,
             0.29451590, -0.10319490,  0.04300286, -0.01566860,  0.00404516, -0.00037046
        )
        val PHASE_2 = doubleArrayOf(
            -0.00054430,  0.00600078, -0.02359308,  0.06647851, -0.16919841,  0.62085649,
             0.62085649, -0.16919841,  0.06647851, -0.02359308,  0.00600078, -0.00054430
        )
        val PHASE_3 = doubleArrayOf(
            -0.00037046,  0.00404516, -0.01566860,  0.04300286, -0.10319490,  0.29451590,
             0.88354769, -0.14447287,  0.05255905, -0.01807915,  0.00452106, -0.00040574
        )
        const val TAPS = 12
    }

    // Delay lines for left and right channels: hold the last 12 samples
    private val delayL = DoubleArray(TAPS)
    private val delayR = DoubleArray(TAPS)
    private var head = 0

    // Reusable peak output fields to avoid allocation in audio loop
    var lastPeakL: Double = 0.0
        private set
    var lastPeakR: Double = 0.0
        private set

    /**
     * Reset the delay lines.
     */
    fun reset() {
        delayL.fill(0.0)
        delayR.fill(0.0)
        head = 0
        lastPeakL = 0.0
        lastPeakR = 0.0
    }

    /**
     * Process a mono or stereo sample point, updating the history buffer
     * and writing the maximum true peak found across all 4 phases into [lastPeakL] and [lastPeakR].
     */
    fun processSample(sampleL: Double, sampleR: Double) {
        delayL[head] = sampleL
        delayR[head] = sampleR

        var maxL = abs(sampleL)
        var maxR = abs(sampleR)

        // Compute phase 1, phase 2, and phase 3 reconstructed samples
        var p1L = 0.0; var p1R = 0.0
        var p2L = 0.0; var p2R = 0.0
        var p3L = 0.0; var p3R = 0.0

        for (k in 0 until TAPS) {
            val tapIdx = (head - k + TAPS) % TAPS
            val sL = delayL[tapIdx]
            val sR = delayR[tapIdx]

            p1L += sL * PHASE_1[k]
            p1R += sR * PHASE_1[k]
            p2L += sL * PHASE_2[k]
            p2R += sR * PHASE_2[k]
            p3L += sL * PHASE_3[k]
            p3R += sR * PHASE_3[k]
        }

        val absP1L = abs(p1L); val absP1R = abs(p1R)
        val absP2L = abs(p2L); val absP2R = abs(p2R)
        val absP3L = abs(p3L); val absP3R = abs(p3R)

        if (absP1L > maxL) maxL = absP1L
        if (absP2L > maxL) maxL = absP2L
        if (absP3L > maxL) maxL = absP3L

        if (absP1R > maxR) maxR = absP1R
        if (absP2R > maxR) maxR = absP2R
        if (absP3R > maxR) maxR = absP3R

        lastPeakL = maxL
        lastPeakR = maxR
        head = (head + 1) % TAPS
    }

    /**
     * Process an interleaved 16-bit PCM buffer and compute the maximum True Peak
     * across the buffer for both channels, preserving state across buffers.
     */
    fun processBuffer(buffer: ShortArray, readSize: Int, maxAmp: Double): Pair<Double, Double> {
        var peakMaxL = 0.0
        var peakMaxR = 0.0

        var i = 0
        while (i < readSize - 1) {
            val l = buffer[i].toDouble() / maxAmp
            val r = buffer[i + 1].toDouble() / maxAmp

            processSample(l, r)
            if (lastPeakL > peakMaxL) peakMaxL = lastPeakL
            if (lastPeakR > peakMaxR) peakMaxR = lastPeakR

            i += 2
        }

        return Pair(peakMaxL, peakMaxR)
    }
}
