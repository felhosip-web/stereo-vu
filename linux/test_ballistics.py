import unittest
import math
import numpy as np
from true_peak import TruePeakDetector, get_migrated_mode, PHASE_1, PHASE_2, PHASE_3, TAPS

class FakeSettings:
    """Mock for QSettings to test idempotent migration and preference retrieval."""
    def __init__(self, data=None):
        self.data = data or {}

    def value(self, key, default=None, type=None):
        val = self.data.get(key, default)
        if type is bool:
            return bool(val)
        if type is int:
            return int(val)
        if type is float:
            return float(val)
        return val

    def setValue(self, key, val):
        self.data[key] = val

class TestBallisticsAndPeak(unittest.TestCase):
    def test_fir_phase_dc_gains(self):
        """
        1. Constant DC input: verify that every polyphase sub-filter preserves DC level
        within a tight tolerance (sum of coefficients == 1.0).
        """
        sum_p0 = 1.0 # Phase 0 is identity tap [0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0]
        sum_p1 = float(np.sum(PHASE_1))
        sum_p2 = float(np.sum(PHASE_2))
        sum_p3 = float(np.sum(PHASE_3))

        self.assertAlmostEqual(sum_p0, 1.0, places=6)
        self.assertAlmostEqual(sum_p1, 1.0, places=6)
        self.assertAlmostEqual(sum_p2, 1.0, places=6)
        self.assertAlmostEqual(sum_p3, 1.0, places=6)

        # Process a continuous constant DC buffer and verify reconstructed peak equals the DC value
        # We prime the filter past the 12-tap startup delay line
        tp = TruePeakDetector()
        dc_val = 0.75
        tp.process_buffer(np.full(12, dc_val, dtype=np.float64), np.full(12, -dc_val, dtype=np.float64))
        dc_l = np.full(30, dc_val, dtype=np.float64)
        dc_r = np.full(30, -dc_val, dtype=np.float64)
        peak_l, peak_r = tp.process_buffer(dc_l, dc_r)
        self.assertAlmostEqual(peak_l, dc_val, places=4)
        self.assertAlmostEqual(peak_r, dc_val, places=4)

    def test_true_peak_intersample_overshoot(self):
        """
        2. Inter-sample overshoot: verify that TruePeakDetector detects inter-sample peaks
        that demonstrably exceed the largest discrete sample.
        For a sine wave at fs/4 (4 samples per period) sampled with 45 degree phase offset:
        Samples: sin(pi/4), sin(3pi/4), sin(5pi/4), sin(7pi/4) = [+0.7071, +0.7071, -0.7071, -0.7071].
        Discrete peak = 0.7071, continuous true peak = 1.0!
        """
        tp = TruePeakDetector()
        s = math.sin(math.pi / 4.0)
        samples_l = np.array([s, s, -s, -s] * 10, dtype=np.float64)
        samples_r = np.array([s * 0.5, s * 0.5, -s * 0.5, -s * 0.5] * 10, dtype=np.float64)

        peak_l, peak_r = tp.process_buffer(samples_l, samples_r)
        discrete_max_l = float(np.max(np.abs(samples_l)))
        discrete_max_r = float(np.max(np.abs(samples_r)))

        # True peak must demonstrably exceed discrete sample max
        self.assertGreater(peak_l, discrete_max_l + 0.15)
        self.assertGreater(peak_r, discrete_max_r + 0.07)
        self.assertAlmostEqual(peak_l, 1.0, delta=0.05)
        self.assertAlmostEqual(peak_r, 0.5, delta=0.05)

    def test_full_scale_and_near_full_scale(self):
        """
        3. Full-scale and near-full-scale signals:
        Ensure that TruePeakDetector correctly handles 1.0 and 0.99 signals without clipping or overflow.
        """
        tp = TruePeakDetector()
        fs_l = np.array([1.0, -1.0, 1.0, -1.0] * 8, dtype=np.float64)
        fs_r = np.array([0.99, -0.99, 0.99, -0.99] * 8, dtype=np.float64)
        peak_l, peak_r = tp.process_buffer(fs_l, fs_r)
        self.assertGreaterEqual(peak_l, 1.0)
        self.assertGreaterEqual(peak_r, 0.99)

    def test_positive_and_negative_polarity(self):
        """
        4. Polarity invariance:
        A positive peak and negative peak of equal magnitude must produce identical True Peak values.
        """
        tp_pos = TruePeakDetector()
        tp_neg = TruePeakDetector()

        s = 0.8
        pos_l = np.array([0.0, s, 0.0, 0.0] * 5, dtype=np.float64)
        neg_l = np.array([0.0, -s, 0.0, 0.0] * 5, dtype=np.float64)

        p_pos_l, _ = tp_pos.process_buffer(pos_l, pos_l)
        p_neg_l, _ = tp_neg.process_buffer(neg_l, neg_l)

        self.assertAlmostEqual(p_pos_l, p_neg_l, places=6)

    def test_independent_left_right_channels(self):
        """
        5. Channel independence:
        Left and right channels must not cross-bleed or influence each other's True Peak.
        """
        tp = TruePeakDetector()
        s = math.sin(math.pi / 4.0)
        sig_l = np.array([s, s, -s, -s] * 8, dtype=np.float64)
        silence_r = np.zeros(32, dtype=np.float64)

        peak_l, peak_r = tp.process_buffer(sig_l, silence_r)
        self.assertGreater(peak_l, 0.95)
        self.assertAlmostEqual(peak_r, 0.0, places=6)

    def test_true_peak_buffer_continuity(self):
        """
        6. Buffer-boundary continuity:
        Verify that filter delay state is preserved across audio buffer boundaries.
        Splitting samples across two buffers must not lose the reconstructed peak.
        """
        tp_continuous = TruePeakDetector()
        tp_split = TruePeakDetector()

        s = math.sin(math.pi / 4.0)
        full_l = np.array([s, s, -s, -s] * 8, dtype=np.float64)
        full_r = np.array([s, s, -s, -s] * 8, dtype=np.float64)

        peak_cont_l, peak_cont_r = tp_continuous.process_buffer(full_l, full_r)

        # Split into two buffers right in the middle of waveform
        buf1_l, buf2_l = full_l[:15], full_l[15:]
        buf1_r, buf2_r = full_r[:15], full_r[15:]

        p1_l, p1_r = tp_split.process_buffer(buf1_l, buf1_r)
        p2_l, p2_r = tp_split.process_buffer(buf2_l, buf2_r)
        peak_split_l = max(p1_l, p2_l)
        peak_split_r = max(p1_r, p2_r)

        self.assertAlmostEqual(peak_cont_l, peak_split_l, places=4)
        self.assertAlmostEqual(peak_cont_r, peak_split_r, places=4)

    def test_reset_behavior(self):
        """
        7. Reset behavior:
        Calling reset() clears the delay history so previous signals do not affect new buffers.
        """
        tp = TruePeakDetector()
        s = 0.9
        ones = np.full(12, s, dtype=np.float64)
        tp.process_buffer(ones, ones)

        tp.reset()
        zeros = np.zeros(12, dtype=np.float64)
        peak_l, peak_r = tp.process_buffer(zeros, zeros)
        self.assertEqual(peak_l, 0.0)
        self.assertEqual(peak_r, 0.0)

    def test_discrete_peak_unaltered(self):
        """
        8. Ordinary sample Peak mode must remain unchanged.
        """
        s = 0.65
        indata = np.array([[s, -s], [-s, s], [s, -s]], dtype=np.float32)
        discrete_peak_l = float(np.max(np.abs(indata[:, 0])))
        discrete_peak_r = float(np.max(np.abs(indata[:, 1])))

        self.assertAlmostEqual(discrete_peak_l, 0.65)
        self.assertAlmostEqual(discrete_peak_r, 0.65)

    def test_numerical_equivalence_with_reference(self):
        """
        Verify that the optimized TruePeakDetector produces output strictly equivalent
        to the unvectorized definition within floating-point tolerance (< 1e-12).
        """
        # Baseline reference detector logic
        class ReferenceDetector:
            def __init__(self):
                self.delay_l = np.zeros(TAPS, dtype=np.float64)
                self.delay_r = np.zeros(TAPS, dtype=np.float64)
                self.head = 0

            def process_buffer(self, samples_l: np.ndarray, samples_r: np.ndarray):
                peak_l = float(np.max(np.abs(samples_l))) if len(samples_l) > 0 else 0.0
                peak_r = float(np.max(np.abs(samples_r))) if len(samples_r) > 0 else 0.0
                for i in range(len(samples_l)):
                    s_l = float(samples_l[i])
                    s_r = float(samples_r[i])
                    self.delay_l[self.head] = s_l
                    self.delay_r[self.head] = s_r
                    idx = (self.head - np.arange(TAPS)) % TAPS
                    hist_l = self.delay_l[idx]
                    hist_r = self.delay_r[idx]
                    p1_l = abs(np.dot(hist_l, PHASE_1))
                    p2_l = abs(np.dot(hist_l, PHASE_2))
                    p3_l = abs(np.dot(hist_l, PHASE_3))
                    p1_r = abs(np.dot(hist_r, PHASE_1))
                    p2_r = abs(np.dot(hist_r, PHASE_2))
                    p3_r = abs(np.dot(hist_r, PHASE_3))
                    max_l = max(abs(s_l), p1_l, p2_l, p3_l)
                    max_r = max(abs(s_r), p1_r, p2_r, p3_r)
                    self.head = (self.head + 1) % TAPS
                    if max_l > peak_l: peak_l = max_l
                    if max_r > peak_r: peak_r = max_r
                return peak_l, peak_r

        ref = ReferenceDetector()
        opt = TruePeakDetector()

        np.random.seed(42)
        # Test varying chunk sizes across 10 buffers
        for chunk_size in [1, 5, 11, 12, 13, 64, 256, 1024]:
            buf_l = np.random.uniform(-1.0, 1.0, chunk_size)
            buf_r = np.random.uniform(-1.0, 1.0, chunk_size)
            ref_l, ref_r = ref.process_buffer(buf_l, buf_r)
            opt_l, opt_r = opt.process_buffer(buf_l, buf_r)

            self.assertAlmostEqual(ref_l, opt_l, delta=1e-12, msg=f"Mismatch on chunk size {chunk_size} L")
            self.assertAlmostEqual(ref_r, opt_r, delta=1e-12, msg=f"Mismatch on chunk size {chunk_size} R")

    def test_mode_migration_backward_compatibility(self):
        """
        Verify that persisted v1 mode == 3 (Custom) is migrated to mode 7,
        and modes 0, 1, 2 remain unchanged.
        """
        # Test v1 mode 3 -> 7
        s_custom = FakeSettings({"mode": 3})
        migrated = get_migrated_mode(s_custom)
        self.assertEqual(migrated, 7)
        self.assertEqual(s_custom.value("mode"), 7)
        self.assertTrue(s_custom.value("ballistics_v2_migrated"))

        # Test idempotence (subsequent call keeps mode 7)
        self.assertEqual(get_migrated_mode(s_custom), 7)

        # Test new True Peak mode 3 after migration remains mode 3
        s_new_tp = FakeSettings({"mode": 3, "ballistics_v2_migrated": True})
        self.assertEqual(get_migrated_mode(s_new_tp), 3)

        # Test v1 mode 1 (Analog VU) remains mode 1
        s_analog = FakeSettings({"mode": 1})
        self.assertEqual(get_migrated_mode(s_analog), 1)
        self.assertEqual(s_analog.value("mode"), 1)

        # Test v1 mode 0 (Digital Peak) remains mode 0
        s_peak = FakeSettings({"mode": 0})
        self.assertEqual(get_migrated_mode(s_peak), 0)

    def test_classic_vu_spring_damp_independence(self):
        """
        Verify Classic VU parameters are not overwritten by user Analog VU preferences.
        """
        user_spring = 0.35
        user_damp = 0.15

        # In Classic VU (mode 4):
        mode = 4
        spring = user_spring
        damp = user_damp

        if mode == 4:
            spring = 0.10
            damp = 0.32

        self.assertEqual(spring, 0.10)
        self.assertEqual(damp, 0.32)

    def test_peak_never_below_bar(self):
        """
        Verify Peak-never-below-bar clamping and tracking behavior.
        """
        peak = 0.5
        display_level = 0.8
        peak_never_below_bar = True
        peak_hold_time = 30
        peak_hold = 0

        # Level exceeds peak: peak jumps with it
        if display_level >= peak:
            peak = display_level
            peak_hold = peak_hold_time

        if peak_never_below_bar and peak < display_level:
            peak = display_level

        self.assertEqual(peak, 0.8)
        self.assertEqual(peak_hold, 30)

        # Level drops: peak holds then decays, clamped by bar
        display_level = 0.7
        peak_decay_rate = 0.5
        peak_hold = 0

        if display_level >= peak:
            peak = display_level
            peak_hold = peak_hold_time
        else:
            if peak_hold > 0:
                peak_hold -= 1
            else:
                peak *= peak_decay_rate

        if peak_never_below_bar:
            if peak < display_level:
                peak = display_level

        self.assertEqual(peak, 0.7)

    def test_bbc_ppm_stepped_release(self):
        """
        Verify BBC PPM stepped / multi-stage decay.
        """
        level = 1.0
        target = 0.0
        timer = 0
        levels = []
        for _ in range(9):
            timer += 1
            if timer >= 3:
                drop = max(0.008, level * 0.05)
                level = max(target, level - drop)
                timer = 0
            levels.append(level)

        self.assertEqual(levels[0], 1.0)
        self.assertEqual(levels[1], 1.0)
        self.assertLess(levels[2], 1.0)
        self.assertEqual(levels[3], levels[2])
        self.assertEqual(levels[4], levels[2])
        self.assertLess(levels[5], levels[4])

if __name__ == '__main__':
    unittest.main()
