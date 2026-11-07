import unittest
import vfd_helper


class TestVfdAppearance(unittest.TestCase):
    """
    Regression and consistency test suite for Linux VFD (Videoton) display.
    """

    def test_canonical_palette_colors(self):
        # Assert canonical active and inactive segment colors
        self.assertEqual(vfd_helper.COLOR_ACTIVE_HEX.upper(), "#00D4E8")
        self.assertEqual(vfd_helper.COLOR_INACTIVE_HEX.upper(), "#001F26")
        self.assertEqual(vfd_helper.COLOR_BACKGROUND_HEX.upper(), "#0A1215")
        self.assertEqual(vfd_helper.COLOR_BORDER_HEX.upper(), "#003840")
        self.assertEqual(vfd_helper.COLOR_GROOVE_HEX.upper(), "#040B0D")
        self.assertEqual(vfd_helper.COLOR_LABEL_HEX.upper(), "#66C0CC")
        self.assertEqual(vfd_helper.COLOR_SCALE_TICK_HEX.upper(), "#006A75")
        self.assertEqual(vfd_helper.COLOR_SCALE_TEXT_HEX.upper(), "#4DAEB8")

        self.assertEqual(vfd_helper.COLOR_ACTIVE_RGB, (0x00, 0xD4, 0xE8))
        self.assertEqual(vfd_helper.COLOR_INACTIVE_RGB, (0x00, 0x1F, 0x26))

    def test_subsegments_per_cell(self):
        self.assertEqual(vfd_helper.SUBSEGMENTS_PER_CELL, 4)

    def test_peak_mode_color_mapping(self):
        # Mode 0: White
        self.assertEqual(vfd_helper.get_peak_color_rgb(0), (255, 255, 255))
        self.assertEqual(vfd_helper.get_peak_color_hex(0).upper(), "#FFFFFF")

        # Mode 1: Canonical Cyan (#00D4E8 - active segment cyan)
        self.assertEqual(vfd_helper.get_peak_color_rgb(1), (0x00, 0xD4, 0xE8))
        self.assertEqual(vfd_helper.get_peak_color_hex(1).upper(), "#00D4E8")

        # Mode 2: Amber (#FFCC00)
        self.assertEqual(vfd_helper.get_peak_color_rgb(2), (255, 204, 0))
        self.assertEqual(vfd_helper.get_peak_color_hex(2).upper(), "#FFCC00")

        # Mode 3: Fixed bright cyan (#00E5FF)
        self.assertEqual(vfd_helper.get_peak_color_rgb(3), (0x00, 0xE5, 0xFF))
        self.assertEqual(vfd_helper.get_peak_color_hex(3).upper(), "#00E5FF")

        # Fallback
        self.assertEqual(vfd_helper.get_peak_color_rgb(99), (255, 255, 255))

    def test_horizontal_subsegment_geometry_and_progression(self):
        cell_left = 40.0
        cell_top = 20.0
        cell_width = 12.0
        cell_height = 70.0
        sub_gap = 2.0

        r0 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 0, sub_gap)
        r1 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 1, sub_gap)
        r2 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 2, sub_gap)
        r3 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 3, sub_gap)

        # Available width: 12 - 3 * 2 = 6, sub_w = 6 / 4 = 1.5
        self.assertAlmostEqual(r0[0], 40.0)
        self.assertAlmostEqual(r0[1], 20.0)
        self.assertAlmostEqual(r0[2], 1.5)
        self.assertAlmostEqual(r0[3], 70.0)

        self.assertAlmostEqual(r1[0], 43.5)
        self.assertAlmostEqual(r2[0], 47.0)
        self.assertAlmostEqual(r3[0], 50.5)

        # Monotonic progression left to right
        self.assertTrue(r0[0] < r1[0] < r2[0] < r3[0])
        self.assertTrue(r0[2] > 0)
        self.assertTrue(r0[3] > 0)

    def test_vertical_subsegment_geometry_and_progression(self):
        cell_left = 14.0
        cell_top = 22.0
        cell_width = 34.0
        cell_height = 10.0
        sub_gap = 2.0

        # Available height: 10 - 3 * 2 = 4, sub_h = 4 / 4 = 1.0
        # Bottom-to-top progression:
        # sub0 is lowest signal (at bottom), sub3 is highest signal (at top)
        r0 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 0, sub_gap)
        r1 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 1, sub_gap)
        r2 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 2, sub_gap)
        r3 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 3, sub_gap)

        self.assertAlmostEqual(r0[0], 14.0)
        self.assertAlmostEqual(r0[1], 31.0)
        self.assertAlmostEqual(r0[2], 34.0)
        self.assertAlmostEqual(r0[3], 1.0)

        self.assertAlmostEqual(r1[1], 28.0)
        self.assertAlmostEqual(r2[1], 25.0)
        self.assertAlmostEqual(r3[1], 22.0)

        # Bottom to top progression: y decreases as signal increases
        self.assertTrue(r0[1] > r1[1] > r2[1] > r3[1])

    def test_small_dimension_safety(self):
        r = vfd_helper.calculate_subsegment_rect(True, 10.0, 10.0, 2.0, 2.0, 0, 2.0)
        self.assertGreaterEqual(r[2], 0.0)
        self.assertGreaterEqual(r[3], 0.0)

    def test_boundary_values(self):
        led_count = 20
        total_subsegments = 80

        # Level 0.0
        self.assertEqual(vfd_helper.calculate_active_subsegments(0.0, led_count), 0)
        self.assertEqual(vfd_helper.calculate_active_subsegments(-0.1, led_count), 0)

        # Level 1.0
        self.assertEqual(vfd_helper.calculate_active_subsegments(1.0, led_count), total_subsegments)
        self.assertEqual(vfd_helper.calculate_active_subsegments(1.5, led_count), total_subsegments)

        # Peak 0.0
        self.assertEqual(vfd_helper.calculate_peak_subsegment(0.0, led_count), 0)
        # Peak 1.0
        self.assertEqual(vfd_helper.calculate_peak_subsegment(1.0, led_count), total_subsegments - 1)

        # Global index
        self.assertEqual(vfd_helper.get_global_subsegment_index(0, 0), 0)
        self.assertEqual(vfd_helper.get_global_subsegment_index(0, 3), 3)
        self.assertEqual(vfd_helper.get_global_subsegment_index(19, 3), 79)


if __name__ == '__main__':
    unittest.main()
