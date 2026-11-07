import os
import sys
import unittest
from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import QSettings
from PyQt6.QtGui import QColor, QImage, QPainter

# Ensure offscreen Qt platform
os.environ["QT_QPA_PLATFORM"] = "offscreen"

app = QApplication.instance()
if app is None:
    app = QApplication(sys.argv)

import stereo_vu


class TestLcdAppearanceLinux(unittest.TestCase):
    def setUp(self):
        self.settings = QSettings("StereoVU", "LinuxApp")
        # Snapshot any existing keys
        self.orig_ghost = self.settings.value("lcd_ghost_strength")
        self.orig_gap = self.settings.value("lcd_segment_gap")
        self.orig_seven = self.settings.value("lcd_seven_segment")
        self.orig_contrast = self.settings.value("lcd_contrast")
        self.orig_theme = self.settings.value("theme")
        self.orig_horiz = self.settings.value("horizontal")

    def tearDown(self):
        for key, orig in [
            ("lcd_ghost_strength", self.orig_ghost),
            ("lcd_segment_gap", self.orig_gap),
            ("lcd_seven_segment", self.orig_seven),
            ("lcd_contrast", self.orig_contrast),
            ("theme", self.orig_theme),
            ("horizontal", self.orig_horiz),
        ]:
            if orig is not None:
                self.settings.setValue(key, orig)
            else:
                self.settings.remove(key)

    def test_default_values_fresh_installation(self):
        """1. Default values for all four settings on fresh installations."""
        self.settings.remove("lcd_ghost_strength")
        self.settings.remove("lcd_segment_gap")
        self.settings.remove("lcd_seven_segment")
        self.settings.remove("lcd_contrast")

        dlg = stereo_vu.SettingsDialog()
        # Ghost: 0.55 (55%)
        self.assertEqual(dlg.lcd_ghost_slider.value(), 55)
        # Gap: 2.0 (20)
        self.assertEqual(dlg.lcd_gap_slider.value(), 20)
        # Seven-segment: True
        self.assertTrue(dlg.lcd_seven_check.isChecked())
        # Contrast: 0.80 (80%)
        self.assertEqual(dlg.lcd_contrast_slider.value(), 80)

        meter = stereo_vu.VuMeter()
        self.assertAlmostEqual(meter.lcd_ghost_strength, 0.55, places=2)
        self.assertAlmostEqual(meter.lcd_segment_gap, 2.0, places=1)
        self.assertTrue(meter.lcd_seven_segment)
        self.assertAlmostEqual(meter.lcd_contrast, 0.80, places=2)

    def test_persistence_and_restoration(self):
        """2. Correct persistence and restoration on Linux."""
        dlg = stereo_vu.SettingsDialog()
        dlg.lcd_ghost_slider.setValue(72)
        dlg.lcd_gap_slider.setValue(45)
        dlg.lcd_seven_check.setChecked(False)
        dlg.lcd_contrast_slider.setValue(35)
        dlg.save_settings()

        self.assertAlmostEqual(self.settings.value("lcd_ghost_strength", type=float), 0.72, places=2)
        self.assertAlmostEqual(self.settings.value("lcd_segment_gap", type=float), 4.5, places=1)
        self.assertFalse(self.settings.value("lcd_seven_segment", type=bool))
        self.assertAlmostEqual(self.settings.value("lcd_contrast", type=float), 0.35, places=2)

        meter = stereo_vu.VuMeter()
        meter.load_settings()
        self.assertAlmostEqual(meter.lcd_ghost_strength, 0.72, places=2)
        self.assertAlmostEqual(meter.lcd_segment_gap, 4.5, places=1)
        self.assertFalse(meter.lcd_seven_segment)
        self.assertAlmostEqual(meter.lcd_contrast, 0.35, places=2)

    def test_slider_bounds_and_value_conversion(self):
        """3. Slider bounds and value conversion."""
        dlg = stereo_vu.SettingsDialog()
        # Ghost: 0-100% -> 0.0-1.0
        self.assertEqual(dlg.lcd_ghost_slider.minimum(), 0)
        self.assertEqual(dlg.lcd_ghost_slider.maximum(), 100)
        dlg.lcd_ghost_slider.setValue(0)
        self.assertAlmostEqual(dlg.lcd_ghost_slider.value() / 100.0, 0.0)
        dlg.lcd_ghost_slider.setValue(100)
        self.assertAlmostEqual(dlg.lcd_ghost_slider.value() / 100.0, 1.0)

        # Gap: 1-6 px -> 10-60 in slider (0.1 step)
        self.assertEqual(dlg.lcd_gap_slider.minimum(), 10)
        self.assertEqual(dlg.lcd_gap_slider.maximum(), 60)
        dlg.lcd_gap_slider.setValue(10)
        self.assertAlmostEqual(dlg.lcd_gap_slider.value() / 10.0, 1.0)
        dlg.lcd_gap_slider.setValue(60)
        self.assertAlmostEqual(dlg.lcd_gap_slider.value() / 10.0, 6.0)

        # Contrast: 20-100% -> 0.20-1.0
        self.assertEqual(dlg.lcd_contrast_slider.minimum(), 20)
        self.assertEqual(dlg.lcd_contrast_slider.maximum(), 100)
        dlg.lcd_contrast_slider.setValue(20)
        self.assertAlmostEqual(dlg.lcd_contrast_slider.value() / 100.0, 0.20)
        dlg.lcd_contrast_slider.setValue(100)
        self.assertAlmostEqual(dlg.lcd_contrast_slider.value() / 100.0, 1.0)

    def test_ghost_strength_affects_inactive_segments(self):
        """4. Ghost strength affects inactive segment rendering."""
        meter = stereo_vu.VuMeter()
        meter.theme_id = 6 # LCD Green
        meter.lcd_contrast = 1.0

        meter.lcd_ghost_strength = 0.20
        _, ghost_low = meter.get_lcd_colors()

        meter.lcd_ghost_strength = 0.90
        _, ghost_high = meter.get_lcd_colors()

        self.assertGreater(ghost_high.green(), ghost_low.green())
        self.assertGreater(ghost_high.red(), ghost_low.red())

    def test_contrast_affects_active_segments(self):
        """5. Contrast affects active segment/readout rendering."""
        meter = stereo_vu.VuMeter()
        meter.theme_id = 7 # LCD Amber
        meter.lcd_ghost_strength = 0.55

        meter.lcd_contrast = 0.25
        act_low, _ = meter.get_lcd_colors()

        meter.lcd_contrast = 0.95
        act_high, _ = meter.get_lcd_colors()

        self.assertGreater(act_high.red(), act_low.red())
        self.assertGreater(act_high.green(), act_low.green())

    def test_segment_gap_affects_geometry(self):
        """6. Segment gap affects rendering geometry and layout dimensions."""
        meter = stereo_vu.VuMeter()
        self.settings.setValue("theme", 6)

        self.settings.setValue("lcd_segment_gap", 1.0)
        self.settings.setValue("horizontal", True)
        meter.load_settings()
        w_gap1 = meter.width()

        self.settings.setValue("lcd_segment_gap", 5.0)
        meter.load_settings()
        w_gap5 = meter.width()

        self.assertGreater(w_gap5, w_gap1)

        # Vertical
        self.settings.setValue("horizontal", False)
        self.settings.setValue("lcd_segment_gap", 1.0)
        meter.load_settings()
        h_gap1 = meter.height()

        self.settings.setValue("lcd_segment_gap", 5.0)
        meter.load_settings()
        h_gap5 = meter.height()

        self.assertGreater(h_gap5, h_gap1)

    def test_seven_segment_readout_and_character_coverage(self):
        """7 & 8. Seven-segment mode and character coverage: 0-9, ., -, and -INF."""
        meter = stereo_vu.VuMeter()
        meter.theme_id = 6
        act_c, gst_c = meter.get_lcd_colors()

        img = QImage(300, 100, QImage.Format.Format_ARGB32)
        p = QPainter(img)

        chars_to_test = "0123456789.-INF "
        for ch in chars_to_test:
            w = meter.draw_seven_segment_char(p, ch, 10, 10, 8, 14, act_c, gst_c)
            self.assertGreater(w, 0)

        end_x = meter.draw_seven_segment_string(p, "-12.5", 10, 10, 8, 14, 2, act_c, gst_c)
        self.assertGreater(end_x, 10)

        end_inf = meter.draw_seven_segment_string(p, "-INF", 10, 10, 8, 14, 2, act_c, gst_c)
        self.assertGreater(end_inf, 10)
        p.end()

    def test_horizontal_and_vertical_layouts(self):
        """9. Horizontal and vertical layout behavior."""
        meter = stereo_vu.VuMeter()
        meter.theme_id = 6

        # Horizontal
        self.settings.setValue("horizontal", True)
        meter.load_settings()
        w_h = meter.width()
        h_h = meter.height()

        # Vertical
        self.settings.setValue("horizontal", False)
        meter.load_settings()
        w_v = meter.width()
        h_v = meter.height()

        self.assertNotEqual((w_h, h_h), (w_v, h_v))
        self.assertGreater(w_h, h_h)
        self.assertGreater(h_v, w_v)

    def test_themes_0_to_5_remain_unchanged(self):
        """10. Theme IDs 0-5 remain unchanged."""
        dlg = stereo_vu.SettingsDialog()
        self.assertEqual(dlg.theme_combo.count(), 8)
        self.assertEqual(dlg.theme_combo.itemText(0), "Klasszikus (Zöld-Narancs-Piros)")
        self.assertEqual(dlg.theme_combo.itemText(1), "Cyberpunk (Neon Kék-Rózsaszín)")
        self.assertEqual(dlg.theme_combo.itemText(2), "Tűz (Sárga-Narancs-Vörös)")
        self.assertEqual(dlg.theme_combo.itemText(3), "Jég (Türkiz-Kék-Fehér)")
        self.assertEqual(dlg.theme_combo.itemText(4), "Naplemente (Arany-Bíbor)")
        self.assertEqual(dlg.theme_combo.itemText(5), "VFD (Klasszikus Cián)")
        self.assertEqual(dlg.theme_combo.itemText(6), "LCD Green (Zöld LCD)")
        self.assertEqual(dlg.theme_combo.itemText(7), "LCD Amber (Borostyán LCD)")


if __name__ == "__main__":
    unittest.main()
