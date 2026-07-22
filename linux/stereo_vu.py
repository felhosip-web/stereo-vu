import sys
import math
import numpy as np
import sounddevice as sd
from PyQt6.QtWidgets import (
    QApplication, QWidget, QMenu, QDialog, QVBoxLayout, QHBoxLayout, 
    QLabel, QSlider, QComboBox, QCheckBox, QPushButton, QFormLayout
)
from PyQt6.QtGui import QPainter, QColor, QAction
from PyQt6.QtCore import Qt, QTimer, QPoint, QSettings

class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Stereo VU Settings")
        self.setFixedSize(350, 400)
        
        self.settings = QSettings("StereoVU", "LinuxApp")
        
        layout = QFormLayout(self)
        
        # Theme
        self.theme_combo = QComboBox()
        self.theme_combo.addItems(["Klasszikus (Zöld)", "Kék (Cyan)", "Piros (Red)"])
        self.theme_combo.setCurrentIndex(self.settings.value("theme", 0, type=int))
        layout.addRow("Téma (Színvilág):", self.theme_combo)
        
        # Scale
        self.scale_slider = QSlider(Qt.Orientation.Horizontal)
        self.scale_slider.setRange(50, 200)
        self.scale_slider.setValue(int(self.settings.value("size_scale", 1.0, type=float) * 100))
        layout.addRow("Méret (Scale):", self.scale_slider)
        
        # Opacity
        self.opacity_slider = QSlider(Qt.Orientation.Horizontal)
        self.opacity_slider.setRange(50, 255)
        self.opacity_slider.setValue(self.settings.value("opacity", 204, type=int))
        layout.addRow("Átlátszatlanság:", self.opacity_slider)
        
        # Colored Peak
        self.peak_check = QCheckBox("Sáv színével egyező Peak LED")
        self.peak_check.setChecked(self.settings.value("colored_peak", False, type=bool))
        layout.addRow("", self.peak_check)
        
        # Decay Speed
        self.decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.decay_slider.setRange(1, 30)
        self.decay_slider.setValue(int(self.settings.value("decay_speed", 0.12, type=float) * 100))
        layout.addRow("Visszaesés sebessége (Decay):", self.decay_slider)
        
        # Peak Hold
        self.hold_slider = QSlider(Qt.Orientation.Horizontal)
        self.hold_slider.setRange(10, 120)
        self.hold_slider.setValue(self.settings.value("peak_hold_time", 60, type=int))
        layout.addRow("Peak Hold (Képkocka):", self.hold_slider)
        
        # Gain
        self.gain_slider = QSlider(Qt.Orientation.Horizontal)
        self.gain_slider.setRange(10, 60)
        self.gain_slider.setValue(int(self.settings.value("gain", 3.0, type=float) * 10))
        layout.addRow("Digitális Erősítés (Gain):", self.gain_slider)
        
        # Buttons
        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Mentés")
        save_btn.clicked.connect(self.save_settings)
        cancel_btn = QPushButton("Mégse")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        
        layout.addRow(btn_layout)
        
    def save_settings(self):
        self.settings.setValue("theme", self.theme_combo.currentIndex())
        self.settings.setValue("size_scale", self.scale_slider.value() / 100.0)
        self.settings.setValue("opacity", self.opacity_slider.value())
        self.settings.setValue("colored_peak", self.peak_check.isChecked())
        self.settings.setValue("decay_speed", self.decay_slider.value() / 100.0)
        self.settings.setValue("peak_hold_time", self.hold_slider.value())
        self.settings.setValue("gain", self.gain_slider.value() / 10.0)
        self.accept()

class VuMeter(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint | 
            Qt.WindowType.WindowStaysOnTopHint | 
            Qt.WindowType.Tool
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        
        self.settings = QSettings("StereoVU", "LinuxApp")
        self.load_settings()
        
        self.level_l = 0.0
        self.level_r = 0.0
        self.target_l = 0.0
        self.target_r = 0.0
        self.peak_l = 0.0
        self.peak_r = 0.0
        self.peak_hold_l = 0
        self.peak_hold_r = 0

        self.drag_pos = QPoint()

        self.timer = QTimer()
        self.timer.timeout.connect(self.update_meter)
        self.timer.start(16)

        try:
            self.stream = sd.InputStream(callback=self.audio_callback, channels=2, samplerate=44100)
            self.stream.start()
        except Exception as e:
            print(f"Failed to start audio stream: {e}")
            self.stream = None
            
    def load_settings(self):
        self.theme_id = self.settings.value("theme", 0, type=int)
        self.size_scale = self.settings.value("size_scale", 1.0, type=float)
        self.bg_opacity = self.settings.value("opacity", 204, type=int)
        self.colored_peak = self.settings.value("colored_peak", False, type=bool)
        self.decay_speed = self.settings.value("decay_speed", 0.12, type=float)
        self.peak_hold_max = self.settings.value("peak_hold_time", 60, type=int)
        self.digital_gain = self.settings.value("gain", 3.0, type=float)
        
        # Apply scale
        base_w, base_h = 320, 80
        self.resize(int(base_w * self.size_scale), int(base_h * self.size_scale))
        self.update()

    def audio_callback(self, indata, frames, time, status):
        rms_l = np.sqrt(np.mean(indata[:, 0]**2))
        rms_r = np.sqrt(np.mean(indata[:, 1]**2))

        def to_level(rms):
            db = 20 * math.log10(rms) if rms > 0 else -50
            return max(0.0, min(1.0, (db + 50) / 50))

        self.target_l = min(1.0, to_level(rms_l) * self.digital_gain)
        self.target_r = min(1.0, to_level(rms_r) * self.digital_gain)

    def update_meter(self):
        attack = 0.35
        decay = self.decay_speed

        if self.target_l > self.level_l:
            self.level_l += (self.target_l - self.level_l) * attack
        else:
            self.level_l -= (self.level_l - self.target_l) * decay

        if self.target_r > self.level_r:
            self.level_r += (self.target_r - self.level_r) * attack
        else:
            self.level_r -= (self.level_r - self.target_r) * decay

        # Peak tracking
        if self.level_l > self.peak_l:
            self.peak_l = self.level_l
            self.peak_hold_l = self.peak_hold_max
        else:
            if self.peak_hold_l > 0:
                self.peak_hold_l -= 1
            else:
                self.peak_l *= 0.95

        if self.level_r > self.peak_r:
            self.peak_r = self.level_r
            self.peak_hold_r = self.peak_hold_max
        else:
            if self.peak_hold_r > 0:
                self.peak_hold_r -= 1
            else:
                self.peak_r *= 0.95
        
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        painter.setBrush(QColor(0, 0, 0, self.bg_opacity))
        painter.setPen(Qt.PenStyle.NoPen)
        painter.drawRoundedRect(self.rect(), int(10 * self.size_scale), int(10 * self.size_scale))

        self.draw_channel(painter, self.level_l, self.peak_l, int(12 * self.size_scale), "L")
        self.draw_channel(painter, self.level_r, self.peak_r, int(45 * self.size_scale), "R")

    def draw_channel(self, painter, level, peak, y_offset, label):
        painter.setPen(QColor(255, 255, 255))
        font = painter.font()
        font.setBold(True)
        font.setPointSizeF(font.pointSizeF() * self.size_scale)
        painter.setFont(font)
        painter.drawText(int(15 * self.size_scale), y_offset + int(14 * self.size_scale), label)

        num_leds = 20
        led_w = 11 * self.size_scale
        led_h = 18 * self.size_scale
        gap = 2 * self.size_scale
        start_x = 35 * self.size_scale

        active_leds = int(level * num_leds)
        peak_led = int(peak * num_leds)

        for i in range(num_leds):
            x = start_x + i * (led_w + gap)
            is_red = i >= 16
            is_yellow = i >= 12 and not is_red

            # Themes
            if self.theme_id == 1: # Blue
                if is_red: base_color = QColor(255, 50, 200)
                elif is_yellow: base_color = QColor(100, 150, 255)
                else: base_color = QColor(0, 200, 255)
            elif self.theme_id == 2: # Red
                if is_red: base_color = QColor(255, 0, 50)
                elif is_yellow: base_color = QColor(255, 100, 0)
                else: base_color = QColor(255, 180, 0)
            else: # Classic
                if is_red: base_color = QColor(255, 50, 50)
                elif is_yellow: base_color = QColor(255, 200, 50)
                else: base_color = QColor(0, 255, 102)

            # Dim color
            if i >= active_leds:
                painter.setBrush(QColor(base_color.red() // 8, base_color.green() // 8, base_color.blue() // 8))
            else:
                painter.setBrush(base_color)

            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

            # Peak hold dot
            if i == peak_led and peak_led > 0:
                if self.colored_peak:
                    painter.setBrush(base_color)
                    painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                else:
                    painter.setBrush(Qt.BrushStyle.NoBrush)
                    
                painter.setPen(QColor(255, 255, 255, 180))
                painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
        elif event.button() == Qt.MouseButton.RightButton:
            self.show_context_menu(event.globalPosition().toPoint())

    def mouseMoveEvent(self, event):
        if event.buttons() & Qt.MouseButton.LeftButton:
            self.move(event.globalPosition().toPoint() - self.drag_pos)

    def show_context_menu(self, pos):
        menu = QMenu(self)
        
        settings_action = QAction("Beállítások...", self)
        settings_action.triggered.connect(self.open_settings)
        menu.addAction(settings_action)
        
        menu.addSeparator()
        
        exit_action = QAction("Bezárás", self)
        exit_action.triggered.connect(self.close)
        menu.addAction(exit_action)
        
        menu.exec(pos)

    def open_settings(self):
        dialog = SettingsDialog(self)
        if dialog.exec():
            self.load_settings()

    def closeEvent(self, event):
        if self.stream:
            self.stream.stop()
            self.stream.close()
        super().closeEvent(event)

if __name__ == '__main__':
    app = QApplication(sys.argv)
    meter = VuMeter()
    meter.show()
    sys.exit(app.exec())
