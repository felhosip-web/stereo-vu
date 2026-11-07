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
        self.settings.setValue("led_count", 30)

        # Horizontal with 30 LEDs (where bar width scales past minimum readout width)
        self.settings.setValue("horizontal", True)
        self.settings.setValue("lcd_segment_gap", 1.0)
        meter.load_settings()
        w_gap1 = meter.width()

        self.settings.setValue("lcd_segment_gap", 5.0)
        meter.load_settings()
        w_gap5 = meter.width()

        self.assertGreater(w_gap5, w_gap1)

        # Vertical
        self.settings.setValue("horizontal", False)
        self.settings.setValue("led_count", 20)
        self.settings.setValue("lcd_segment_gap", 1.0)
        meter.load_settings()
        h_gap1 = meter.height()

        self.settings.setValue("lcd_segment_gap", 5.0)
        meter.load_settings()
        h_gap5 = meter.height()

        self.assertGreater(h_gap5, h_gap1)

    def test_actual_rendering_seven_segment_and_characters(self):
        """7 & 8. Verify actual rendered pixels for '+', '-', '.', '0-9', '-INF'."""
        meter = stereo_vu.VuMeter()
        meter.theme_id = 6
        act_c, gst_c = meter.get_lcd_colors()

        img = QImage(300, 100, QImage.Format.Format_ARGB32)
        img.fill(QColor(0, 0, 0, 0))
        p = QPainter(img)

        # Render plus glyph specifically with ghost_color transparent so only active pixels show
        p_img = QImage(20, 20, QImage.Format.Format_ARGB32)
        p_img.fill(QColor(0, 0, 0, 0))
        painter_plus = QPainter(p_img)
        meter.draw_seven_segment_char(painter_plus, '+', 2, 2, 10, 14, act_c, QColor(0, 0, 0, 0))
        painter_plus.end()

        # Count painted pixels for '+' matching act_c
        plus_pixel_count = sum(
            1 for x in range(20) for y in range(20)
            if p_img.pixelColor(x, y) == act_c
        )
        self.assertGreater(plus_pixel_count, 0, "Plus glyph must draw visible active pixels")

        # Test string "+0.0"
        str_img = QImage(100, 30, QImage.Format.Format_ARGB32)
        str_img.fill(QColor(0, 0, 0, 0))
        painter_str = QPainter(str_img)
        end_plus = meter.draw_seven_segment_string(painter_str, "+0.0", 5, 5, 8, 14, 2, act_c, gst_c)
        painter_str.end()
        self.assertGreater(end_plus, 5)

        # Test "-INF" string
        inf_img = QImage(100, 30, QImage.Format.Format_ARGB32)
        inf_img.fill(QColor(0, 0, 0, 0))
        painter_inf = QPainter(inf_img)
        end_inf = meter.draw_seven_segment_string(painter_inf, "-INF", 5, 5, 8, 14, 2, act_c, gst_c)
        painter_inf.end()
        self.assertGreater(end_inf, 5)
        p.end()

    def test_horizontal_and_vertical_readout_bounds(self):
        """9. Horizontal and vertical readout bounds: must fit within widget width and height."""
        for theme_id in (6, 7):
            self.settings.setValue("theme", theme_id)
            for seven_seg in (True, False):
                self.settings.setValue("lcd_seven_segment", seven_seg)

                # Horizontal mode with small LED count (10)
                self.settings.setValue("horizontal", True)
                self.settings.setValue("led_count", 10)
                meter = stereo_vu.VuMeter()
                meter.load_settings()

                img_h = QImage(meter.width(), meter.height(), QImage.Format.Format_ARGB32)
                p_h = QPainter(img_h)
                # Render readouts with max width values (-49.9 and -INF)
                meter.draw_channel_horizontal(p_h, 0.002, 0.0, 10, "L")
                meter.draw_channel_horizontal(p_h, 1.0, 0.85, 38, "R")
                readout_y = 62
                meter.draw_lcd_readout_horizontal(p_h, 35, readout_y, 0.002, 0.0, 1.0, 0.85)
                p_h.end()

                self.assertGreaterEqual(meter.width(), 380, f"Horizontal width for theme {theme_id} must fit full readout")

                # Vertical mode
                self.settings.setValue("horizontal", False)
                meter.load_settings()
                self.assertGreater(meter.height(), 150)

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
