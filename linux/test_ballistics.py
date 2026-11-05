import unittest
import numpy as np

class TestBallisticsAndPeak(unittest.TestCase):
    def test_true_peak_intersample_interpolation(self):
        s_arr = np.array([[0.2, 0.2], [0.9, 0.9], [0.3, 0.3]], dtype=np.float32)
        chan_l = s_arr[:, 0]
        mid_l = 0.5 * (chan_l[:-1] + chan_l[1:])
        q1_l = 0.75 * chan_l[:-1] + 0.25 * chan_l[1:]
        q3_l = 0.25 * chan_l[:-1] + 0.75 * chan_l[1:]
        peak_tp = max(float(np.max(np.abs(chan_l))), float(np.max(np.abs(mid_l))), float(np.max(np.abs(q1_l))), float(np.max(np.abs(q3_l))))
        self.assertAlmostEqual(peak_tp, 0.9, places=5)

    def test_bbc_ppm_stepped_release(self):
        level = 1.0
        target = 0.0
        timer = 0
        levels = []
        for _ in range(12):
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

    def test_peak_never_below_bar(self):
        peak = 0.5
        display_level = 0.8
        peak_never_below_bar = True
        peak_hold_time = 30
        peak_hold = 0

        if display_level >= peak:
            peak = display_level
            peak_hold = peak_hold_time

        if peak_never_below_bar and peak < display_level:
            peak = display_level

        self.assertEqual(peak, 0.8)
        self.assertEqual(peak_hold, 30)

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

    def test_db_formatting(self):
        def format_db(level):
            db = (level * 50.0) - 50.0
            return "-INF" if db <= -49.5 else f"{db:+.1f}"

        self.assertEqual(format_db(1.0), "+0.0")
        self.assertEqual(format_db(0.0), "-INF")
        self.assertEqual(format_db(0.5), "-25.0")
        self.assertEqual(format_db(0.8), "-10.0")

if __name__ == '__main__':
    unittest.main()
