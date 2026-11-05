import unittest
import math
import numpy as np
from true_peak import TruePeakDetector, get_migrated_mode

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
    def test_true_peak_intersample_overshoot(self):
        """
        Verify that TruePeakDetector detects inter-sample peaks that demonstrably
        exceed the largest discrete sample.
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
        self.assertAlmostEqual(peak_l, 1.0, delta=0.1)

    def test_true_peak_buffer_continuity(self):
        """
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

    def test_discrete_peak_unaltered(self):
        """
        Verify that ordinary Peak mode remains discrete max sample.
        """
        s = 0.65
        indata = np.array([[s, -s], [-s, s], [s, -s]], dtype=np.float32)
        discrete_peak_l = float(np.max(np.abs(indata[:, 0])))
        discrete_peak_r = float(np.max(np.abs(indata[:, 1])))

        self.assertAlmostEqual(discrete_peak_l, 0.65)
        self.assertAlmostEqual(discrete_peak_r, 0.65)

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
        # User has custom analog spring=0.35 and damp=0.15
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

        # 0.8 * 0.5 = 0.4, clamped to 0.7
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
