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
        # Horizontal meter:
        # Logical cell has width = led_w (12.0), height = led_h (70.0).
        # 4 thin horizontal subsegments stacked vertically (divide cell along Y axis).
        # Each subsegment occupies approximately 1/4 of cell height and full cell width.
        # Progression within cell: bottom to top (sub_index 0 is bottom-most, 3 is top-most).
        cell_left = 40.0
        cell_top = 20.0
        cell_width = 12.0
        cell_height = 70.0
        sub_gap = 2.0

        # Available height: 70 - 3 * 2 = 64.0, sub_h = 64 / 4 = 16.0
        r0 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 0, sub_gap)
        r1 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 1, sub_gap)
        r2 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 2, sub_gap)
        r3 = vfd_helper.calculate_subsegment_rect(True, cell_left, cell_top, cell_width, cell_height, 3, sub_gap)

        # All share the full width
        self.assertAlmostEqual(r0[0], 40.0)
        self.assertAlmostEqual(r0[2], 12.0)
        self.assertAlmostEqual(r0[3], 16.0)
        self.assertAlmostEqual(r1[2], 12.0)
        self.assertAlmostEqual(r2[2], 12.0)
        self.assertAlmostEqual(r3[2], 12.0)

        # Stacking order bottom to top:
        # sub0: top = 20 + 3 * 18 = 74.0
        self.assertAlmostEqual(r0[1], 74.0)
        # sub1: top = 20 + 2 * 18 = 56.0
        self.assertAlmostEqual(r1[1], 56.0)
        # sub2: top = 20 + 1 * 18 = 38.0
        self.assertAlmostEqual(r2[1], 38.0)
        # sub3: top = 20 + 0 = 20.0
        self.assertAlmostEqual(r3[1], 20.0)

        # Progression bottom to top: y decreases as signal increases
        self.assertTrue(r0[1] > r1[1] > r2[1] > r3[1])
        self.assertTrue(r0[2] > 0)
        self.assertTrue(r0[3] > 0)

    def test_vertical_subsegment_geometry_and_progression(self):
        # Vertical meter:
        # Logical cell has width = col_w (70.0), height = led_h (12.0).
        # 4 narrow vertical subsegments side-by-side (divide cell along X axis).
        # Each subsegment occupies approximately 1/4 of cell width and full cell height.
        # Progression within cell: left to right (sub_index 0..3).
        cell_left = 14.0
        cell_top = 22.0
        cell_width = 70.0
        cell_height = 12.0
        sub_gap = 2.0

        # Available width: 70 - 3 * 2 = 64.0, sub_w = 64 / 4 = 16.0
        r0 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 0, sub_gap)
        r1 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 1, sub_gap)
        r2 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 2, sub_gap)
        r3 = vfd_helper.calculate_subsegment_rect(False, cell_left, cell_top, cell_width, cell_height, 3, sub_gap)

        # All share the full height
        self.assertAlmostEqual(r0[1], 22.0)
        self.assertAlmostEqual(r0[3], 12.0)
        self.assertAlmostEqual(r0[2], 16.0)
        self.assertAlmostEqual(r1[3], 12.0)
        self.assertAlmostEqual(r2[3], 12.0)
        self.assertAlmostEqual(r3[3], 12.0)

        # Side by side left to right:
        # sub0: left = 14 + 0 = 14.0
        self.assertAlmostEqual(r0[0], 14.0)
        # sub1: left = 14 + 18 = 32.0
        self.assertAlmostEqual(r1[0], 32.0)
        # sub2: left = 14 + 36 = 50.0
        self.assertAlmostEqual(r2[0], 50.0)
        # sub3: left = 14 + 54 = 68.0
        self.assertAlmostEqual(r3[0], 68.0)

        # Progression left to right: x increases as signal increases
        self.assertTrue(r0[0] < r1[0] < r2[0] < r3[0])
        self.assertTrue(r0[2] > 0)
        self.assertTrue(r0[3] > 0)

    def test_small_dimension_safety(self):
        r_horiz = vfd_helper.calculate_subsegment_rect(True, 10.0, 10.0, 2.0, 2.0, 0, 2.0)
        self.assertEqual(r_horiz[2], 2.0)
        self.assertEqual(r_horiz[3], 0.0)

        r_vert = vfd_helper.calculate_subsegment_rect(False, 10.0, 10.0, 2.0, 2.0, 0, 2.0)
        self.assertEqual(r_vert[2], 0.0)
        self.assertEqual(r_vert[3], 2.0)

    def test_all_supported_scales_produce_non_zero_dimensions_and_no_overlap(self):
        test_scales = [0.50, 0.60, 0.79, 0.80, 0.99, 1.00, 1.50, 2.00]
        for scale in test_scales:
            sub_gap = 2.0 * scale if scale >= 1.0 else max(1.0, 2.0 * scale)

            # Vertical test
            col_w = int(34 * scale)
            led_h = 10.0 * scale
            vert_rects = []
            for j in range(4):
                sx, sy, sw, sh = vfd_helper.calculate_subsegment_rect(
                    False, 14.0 * scale, 22.0 * scale, col_w, led_h, j, sub_gap
                )
                eff_w = max(1, int(sw))
                eff_h = max(1, int(sh))
                self.assertGreater(eff_w, 0, f"Vertical eff_w must be >0 at scale {scale}")
                self.assertGreater(eff_h, 0, f"Vertical eff_h must be >0 at scale {scale}")
                vert_rects.append((int(sx), int(sy), eff_w, eff_h))

            for j in range(3):
                curr = vert_rects[j]
                next_rect = vert_rects[j + 1]
                self.assertLessEqual(curr[0] + curr[2], next_rect[0], f"Vertical subsegments overlap at scale {scale}")

            # Horizontal test
            led_w = 11.0 * scale
            led_h_horiz = 18.0 * scale
            horiz_rects = []
            for j in range(4):
                sx, sy, sw, sh = vfd_helper.calculate_subsegment_rect(
                    True, 35.0 * scale, 10.0 * scale, led_w, led_h_horiz, j, sub_gap
                )
                eff_w = max(1, int(sw))
                eff_h = max(1, int(sh))
                self.assertGreater(eff_w, 0, f"Horizontal eff_w must be >0 at scale {scale}")
                self.assertGreater(eff_h, 0, f"Horizontal eff_h must be >0 at scale {scale}")
                horiz_rects.append((int(sx), int(sy), eff_w, eff_h))

            for j in range(3):
                curr = horiz_rects[j]      # lower signal, larger top
                next_rect = horiz_rects[j + 1]  # higher signal, smaller top
                self.assertLessEqual(next_rect[1] + next_rect[3], curr[1], f"Horizontal subsegments overlap at scale {scale}")

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
