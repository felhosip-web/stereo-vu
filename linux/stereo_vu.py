import sys
import math
import numpy as np
import sounddevice as sd
from PyQt6.QtWidgets import (QApplication, QWidget, QMenu, QDialog, 
                             QFormLayout, QSlider, QComboBox, QCheckBox, 
                             QPushButton, QHBoxLayout, QLabel)
from PyQt6.QtCore import Qt, QTimer, QPoint, QSettings
from PyQt6.QtGui import QPainter, QColor, QAction

class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Stereo VU Beállítások")
        self.settings = QSettings("StereoVU", "LinuxApp")
        
        layout = QFormLayout(self)
        
        self.device_combo = QComboBox()
        self.input_devices = []
        try:
            import sounddevice as sd
            devices = sd.query_devices()
            default_device_idx = sd.default.device[0]
            default_device_name = devices[default_device_idx]['name'] if default_device_idx is not None and default_device_idx >= 0 else None

            current_device_name = self.settings.value("input_device", "", type=str)

            hostapis = sd.query_hostapis()
            for i, dev in enumerate(devices):
                if dev['max_input_channels'] > 0:
                    self.input_devices.append(dev)
                    hostapi_name = hostapis[dev['hostapi']]['name']
                    name = f"{dev['name']} [{hostapi_name}]"
                    # Add item with visible text (name + API) and hidden data (raw name)
                    self.device_combo.addItem(name, dev['name'])

            # Try to select the saved device, or fallback to default
            idx = -1
            if current_device_name:
                # Search by the hidden raw device name
                for j in range(self.device_combo.count()):
                    if self.device_combo.itemData(j) == current_device_name:
                        idx = j
                        break

            if idx < 0 and default_device_name: # If not found, use default
                for j in range(self.device_combo.count()):
                    if self.device_combo.itemData(j) == default_device_name:
                        idx = j
                        break

            if idx >= 0:
                self.device_combo.setCurrentIndex(idx)

        except Exception as e:
            print(f"Error querying audio devices: {e}")
            self.device_combo.addItem("Alapértelmezett", -1)

        layout.addRow("Bemeneti eszköz:", self.device_combo)

        hint_label = QLabel("Tipp: A rendszerhangok (kimenet) méréséhez hagyd\nAlapértelmezetten, majd a Hangerőszabályzóban\n(pavucontrol) a Felvétel fülön állítsd át a forrást\n'Monitor of...' eszközre.")
        hint_label.setStyleSheet("color: gray; font-size: 10px;")
        layout.addRow("", hint_label)

        self.detection_combo = QComboBox()
        self.detection_combo.addItems(["Peak (Csúcsérték)", "RMS (Négyzetes középérték)"])
        self.detection_combo.setCurrentIndex(self.settings.value("detection_mode", 0, type=int))
        layout.addRow("Detektálási mód:", self.detection_combo)

        self.layout_check = QCheckBox("Vízszintes elrendezés (L fent, R lent)")
        self.layout_check.setChecked(self.settings.value("horizontal", False, type=bool))
        layout.addRow("Elrendezés:", self.layout_check)

        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["Digitális Peak", "Analóg VU", "PPM", "Egyéni"])
        self.mode_combo.setCurrentIndex(self.settings.value("mode", 0, type=int))
        layout.addRow("Üzemmód (Ballisztika):", self.mode_combo)

        self.led_slider = QSlider(Qt.Orientation.Horizontal)
        self.led_slider.setRange(10, 40)
        self.led_slider.setValue(self.settings.value("led_count", 20, type=int))
        layout.addRow("LED-ek száma (10-40):", self.led_slider)
        
        self.theme_combo = QComboBox()
        self.theme_combo.addItems(["Klasszikus (Zöld-Narancs-Piros)", "Cyberpunk (Neon Kék-Rózsaszín)", "Tűz (Sárga-Narancs-Vörös)", "Jég (Türkiz-Kék-Fehér)", "Naplemente (Arany-Bíbor)", "VFD (Klasszikus Cián)"])
        self.theme_combo.setCurrentIndex(self.settings.value("theme", 0, type=int))
        layout.addRow("Színséma:", self.theme_combo)
        
        self.scale_slider = QSlider(Qt.Orientation.Horizontal)
        self.scale_slider.setRange(50, 200)
        self.scale_slider.setValue(int(self.settings.value("size_scale", 1.0, type=float) * 100))
        layout.addRow("Méret skálázás (50-200%):", self.scale_slider)
        
        self.opacity_slider = QSlider(Qt.Orientation.Horizontal)
        self.opacity_slider.setRange(0, 255)
        self.opacity_slider.setValue(self.settings.value("opacity", 204, type=int))
        layout.addRow("Háttér sötétsége:", self.opacity_slider)
        
        self.peak_combo = QComboBox()
        self.peak_combo.addItems(["Fehér (klasszikus)", "Színes (LED)", "Fix sárga", "Fix cián"])
        
        # Migrate old colored_peak setting
        if self.settings.contains("peak_mode"):
            self.peak_combo.setCurrentIndex(self.settings.value("peak_mode", 0, type=int))
        else:
            old_colored = self.settings.value("colored_peak", False, type=bool)
            self.peak_combo.setCurrentIndex(1 if old_colored else 0)
            
        layout.addRow("Peak LED stílusa:", self.peak_combo)

        # Új ballisztika beállítások
        self.target_decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.target_decay_slider.setRange(880, 970)
        self.target_decay_slider.setValue(int(self.settings.value("target_decay", 0.94, type=float) * 1000))
        layout.addRow("Célérték elengedés (0.88-0.97):", self.target_decay_slider)

        self.peak_hold_slider = QSlider(Qt.Orientation.Horizontal)
        self.peak_hold_slider.setRange(10, 180)
        self.peak_hold_slider.setValue(self.settings.value("peak_hold", 60, type=int))
        layout.addRow("Peak tartási idő (10-180 képkocka):", self.peak_hold_slider)

        self.peak_decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.peak_decay_slider.setRange(900, 995)
        self.peak_decay_slider.setValue(int(self.settings.value("peak_decay", 0.985, type=float) * 1000))
        layout.addRow("Peak lecsengés (0.900-0.995):", self.peak_decay_slider)

        self.spring_slider = QSlider(Qt.Orientation.Horizontal)
        self.spring_slider.setRange(5, 40)
        self.spring_slider.setValue(int(self.settings.value("analog_spring", 0.15, type=float) * 100))
        layout.addRow("Analóg Rugó (0.05-0.40):", self.spring_slider)

        self.damp_slider = QSlider(Qt.Orientation.Horizontal)
        self.damp_slider.setRange(10, 40)
        self.damp_slider.setValue(int(self.settings.value("analog_damp", 0.25, type=float) * 100))
        layout.addRow("Analóg Csillapítás (0.10-0.40):", self.damp_slider)
        
        self.attack_slider = QSlider(Qt.Orientation.Horizontal)
        self.attack_slider.setRange(5, 100)
        self.attack_slider.setValue(int(self.settings.value("attack_speed", 0.35, type=float) * 100))
        layout.addRow("Egyéni Felfutás (5-100%):", self.attack_slider)
        
        self.decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.decay_slider.setRange(5, 100)
        self.decay_slider.setValue(int(self.settings.value("decay_speed", 0.12, type=float) * 100))
        layout.addRow("Egyéni Lecsengés (5-100%):", self.decay_slider)
        
        self.gain_slider = QSlider(Qt.Orientation.Horizontal)
        self.gain_slider.setRange(1, 300)
        self.gain_slider.setValue(int(self.settings.value("gain", 1.0, type=float) * 100))
        layout.addRow("Érzékenység (0.01x - 3.0x):", self.gain_slider)
        
        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Mentés")
        save_btn.clicked.connect(self.save_settings)
        cancel_btn = QPushButton("Mégse")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        
        layout.addRow(btn_layout)
        
    def save_settings(self):
        # Save the hidden raw device name instead of the formatted text
        self.settings.setValue("input_device", self.device_combo.currentData())
        self.settings.setValue("detection_mode", self.detection_combo.currentIndex())
        self.settings.setValue("horizontal", self.layout_check.isChecked())
        self.settings.setValue("mode", self.mode_combo.currentIndex())
        self.settings.setValue("led_count", self.led_slider.value())
        self.settings.setValue("theme", self.theme_combo.currentIndex())
        self.settings.setValue("size_scale", self.scale_slider.value() / 100.0)
        self.settings.setValue("opacity", self.opacity_slider.value())
        self.settings.setValue("peak_mode", self.peak_combo.currentIndex())
        self.settings.setValue("target_decay", self.target_decay_slider.value() / 1000.0)
        self.settings.setValue("peak_hold", self.peak_hold_slider.value())
        self.settings.setValue("peak_decay", self.peak_decay_slider.value() / 1000.0)
        self.settings.setValue("analog_spring", self.spring_slider.value() / 100.0)
        self.settings.setValue("analog_damp", self.damp_slider.value() / 100.0)
        self.settings.setValue("attack_speed", self.attack_slider.value() / 100.0)
        self.settings.setValue("decay_speed", self.decay_slider.value() / 100.0)
        self.settings.setValue("gain", self.gain_slider.value() / 100.0)
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
        self.vel_l = 0.0
        self.vel_r = 0.0
        self.peak_l = 0.0
        self.peak_r = 0.0
        self.peak_hold_l = 0
        self.peak_hold_r = 0
        
        self.drag_pos = QPoint()
        self.timer = QTimer()
        self.timer.timeout.connect(self.update_meter)
        self.timer.start(16)
        
        self.stream = None
        self.start_audio_stream()

    def start_audio_stream(self):
        if self.stream is not None:
            self.stream.stop()
            self.stream.close()
            self.stream = None

        device_name = self.settings.value("input_device", "", type=str)
        device_index = None

        if device_name:
            try:
                devices = sd.query_devices()
                for i, dev in enumerate(devices):
                    if dev['name'] == device_name and dev['max_input_channels'] > 0:
                        device_index = i
                        break
            except Exception as e:
                print(f"Error querying devices: {e}")

        try:
            self.stream = sd.InputStream(device=device_index, callback=self.audio_callback, channels=2, samplerate=44100)
            self.stream.start()
        except Exception as e:
            print(f"Failed to start audio stream with device {device_name or 'default'}: {e}")
            # Fallback to default if named device failed
            if device_index is not None:
                try:
                    print("Falling back to default device...")
                    self.stream = sd.InputStream(callback=self.audio_callback, channels=2, samplerate=44100)
                    self.stream.start()
                except Exception as e2:
                    print(f"Failed to start fallback audio stream: {e2}")

    def load_settings(self):
        self.mode = self.settings.value("mode", 0, type=int)
        self.num_leds = self.settings.value("led_count", 20, type=int)
        self.theme_id = self.settings.value("theme", 0, type=int)
        self.size_scale = self.settings.value("size_scale", 1.0, type=float)
        self.bg_opacity = self.settings.value("opacity", 204, type=int)
        self.detection_mode = self.settings.value("detection_mode", 0, type=int) # 0 = Peak, 1 = RMS
        self.is_horizontal = self.settings.value("horizontal", False, type=bool)
        
        if self.settings.contains("peak_mode"):
            self.peak_mode = self.settings.value("peak_mode", 0, type=int)
        else:
            self.peak_mode = 1 if self.settings.value("colored_peak", False, type=bool) else 0
            
        if self.mode == 0: # Digital Peak: gyorsabb felfutás, lassabb karakteres lecsengés
            self.attack_speed = 0.90
            self.decay_speed = 0.12
        elif self.mode == 1: # Analog
            self.attack_speed = 0.10
            self.decay_speed = 0.05
        elif self.mode == 2: # PPM
            self.attack_speed = 0.40
            self.decay_speed = 0.08
        else:
            self.attack_speed = self.settings.value("attack_speed", 0.35, type=float)
            self.decay_speed = self.settings.value("decay_speed", 0.12, type=float)
            
        self.digital_gain = self.settings.value("gain", 1.0, type=float)
        self.target_decay = self.settings.value("target_decay", 0.94, type=float)
        self.peak_hold_time = self.settings.value("peak_hold", 60, type=int)
        self.peak_decay_rate = self.settings.value("peak_decay", 0.985, type=float)
        self.analog_spring = self.settings.value("analog_spring", 0.15, type=float)
        self.analog_damp = self.settings.value("analog_damp", 0.25, type=float)
        
        # Ablak méretezése az elrendezés (vízszintes vs függőleges) és LED szám szerint
        if self.is_horizontal:
            # L fent, R lent: szélesség a LED-ek szerint nyúlik el
            base_w = 40 + self.num_leds * 13
            base_h = 80
        else:
            # Függőleges tornyok (mint az Android alapértelmezett oszlopos nézete):
            # 2 oszlop egymás mellett + skála
            base_w = 110
            base_h = 30 + self.num_leds * 13

        self.resize(int(base_w * self.size_scale), int(base_h * self.size_scale))
        self.update()

    def audio_callback(self, indata, frames, time, status):
        if self.detection_mode == 1: # RMS
            val_l = np.sqrt(np.mean(indata[:, 0]**2))
            val_r = np.sqrt(np.mean(indata[:, 1]**2))
        else: # Peak (abszolút csúcsérték az adott pufferben)
            val_l = np.max(np.abs(indata[:, 0]))
            val_r = np.max(np.abs(indata[:, 1]))

        def to_level(signal):
            db = 20 * math.log10(max(signal, 0.0001))
            return max(0.0, min(1.0, (db + 50) / 50))
            
        new_target_l = to_level(val_l * 3.0 * self.digital_gain)
        new_target_r = to_level(val_r * 3.0 * self.digital_gain)

        # Csúcs-követő (peak follower): azonnal felugrik, majd a ciklusban fokozatosan halkul
        self.target_l = min(1.0, max(self.target_l, new_target_l))
        self.target_r = min(1.0, max(self.target_r, new_target_r))

    def update_meter(self):
        if self.mode == 1:
            # Analog spring physics finomhangolható rugó és csillapítás paraméterekkel
            self.vel_l += (self.target_l - self.level_l) * self.analog_spring
            self.vel_l *= (1.0 - self.analog_damp)
            self.level_l += self.vel_l
            
            self.vel_r += (self.target_r - self.level_r) * self.analog_spring
            self.vel_r *= (1.0 - self.analog_damp)
            self.level_r += self.vel_r
            
            # Bouncing overshoot korlátozása
            self.level_l = max(0.0, min(1.2, self.level_l))
            self.level_r = max(0.0, min(1.2, self.level_r))
        else:
            if self.target_l > self.level_l:
                self.level_l += (self.target_l - self.level_l) * self.attack_speed
            else:
                self.level_l -= (self.level_l - self.target_l) * self.decay_speed

            if self.target_r > self.level_r:
                self.level_r += (self.target_r - self.level_r) * self.attack_speed
            else:
                self.level_r -= (self.level_r - self.target_r) * self.decay_speed

        display_l = max(0.0, min(1.0, self.level_l))
        display_r = max(0.0, min(1.0, self.level_r))

        if display_l > self.peak_l:
            self.peak_l = display_l
            self.peak_hold_l = self.peak_hold_time
        else:
            if self.peak_hold_l > 0: self.peak_hold_l -= 1
            else: self.peak_l *= self.peak_decay_rate
            
        if display_r > self.peak_r:
            self.peak_r = display_r
            self.peak_hold_r = self.peak_hold_time
        else:
            if self.peak_hold_r > 0: self.peak_hold_r -= 1
            else: self.peak_r *= self.peak_decay_rate

        # Célérték fokozatos elengedése (release decay)
        self.target_l *= self.target_decay
        self.target_r *= self.target_decay
            
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        painter.setBrush(QColor(0, 0, 0, self.bg_opacity))
        painter.setPen(Qt.PenStyle.NoPen)
        painter.drawRoundedRect(self.rect(), int(10 * self.size_scale), int(10 * self.size_scale))
        
        display_l = max(0.0, min(1.0, self.level_l))
        display_r = max(0.0, min(1.0, self.level_r))

        if self.is_horizontal:
            # Vízszintes sávok (L fent, R lent)
            self.draw_channel_horizontal(painter, display_l, self.peak_l, int(12 * self.size_scale), "L")
            self.draw_channel_horizontal(painter, display_r, self.peak_r, int(45 * self.size_scale), "R")

            if self.theme_id == 5:
                painter.setPen(QColor(255, 255, 255, 45))
                y_l = int(12 * self.size_scale)
                y_r = int(45 * self.size_scale)
                led_h = int(18 * self.size_scale)
                start_x = int(35 * self.size_scale)
                end_x = self.width() - int(10 * self.size_scale)

                painter.drawLine(start_x, y_l + int(led_h * 0.25), end_x, y_l + int(led_h * 0.25))
                painter.drawLine(start_x, y_l + int(led_h * 0.75), end_x, y_l + int(led_h * 0.75))
                painter.drawLine(start_x, y_r + int(led_h * 0.25), end_x, y_r + int(led_h * 0.25))
                painter.drawLine(start_x, y_r + int(led_h * 0.75), end_x, y_r + int(led_h * 0.75))
        else:
            # Függőleges tornyok (L bal, R jobb)
            col_w = int(36 * self.size_scale)
            x_l = int(14 * self.size_scale)
            x_r = int(60 * self.size_scale)
            self.draw_channel_vertical(painter, display_l, self.peak_l, x_l, col_w, "L")
            self.draw_channel_vertical(painter, display_r, self.peak_r, x_r, col_w, "R")

            if self.theme_id == 5:
                painter.setPen(QColor(255, 255, 255, 45))
                top_y = int(24 * self.size_scale)
                end_y = top_y + int(self.num_leds * 13 * self.size_scale)
                for x_col in (x_l, x_r):
                    painter.drawLine(x_col + int(col_w * 0.25), top_y, x_col + int(col_w * 0.25), end_y)
                    painter.drawLine(x_col + int(col_w * 0.75), top_y, x_col + int(col_w * 0.75), end_y)

    def draw_channel_horizontal(self, painter, level, peak, y_offset, label):
        painter.setPen(QColor(255, 255, 255))
        font = painter.font()
        font.setBold(True)
        font.setPointSizeF(font.pointSizeF() * self.size_scale)
        painter.setFont(font)
        painter.drawText(int(15 * self.size_scale), y_offset + int(14 * self.size_scale), label)
        
        led_w = 11 * self.size_scale
        led_h = 18 * self.size_scale
        gap = 2 * self.size_scale
        start_x = 35 * self.size_scale

        if self.theme_id == 5:
            total_segments = self.num_leds * 4
            active_segments = int(level * total_segments)
            peak_segment = int(peak * total_segments)
            
            sub_gap = 2 * self.size_scale
            sub_w = (led_w - 3 * sub_gap) / 4.0
            
            base_color = QColor(0, 229, 255)
            dim_color = QColor(0, 34, 40)
            
            for i in range(self.num_leds):
                x = start_x + i * (led_w + gap)
                
                for j in range(4):
                    global_idx = i * 4 + j
                    is_on = global_idx < active_segments
                    
                    sub_x = x + j * (sub_w + sub_gap)
                    
                    if is_on:
                        painter.setBrush(base_color)
                    else:
                        painter.setBrush(dim_color)
                        
                    painter.setPen(Qt.PenStyle.NoPen)
                    painter.drawRect(int(sub_x), int(y_offset), int(sub_w), int(led_h))
                    
                    if is_on:
                        # Inner glare
                        painter.setBrush(QColor(255, 255, 255, 60))
                        painter.drawRect(int(sub_x + 1), int(y_offset + 1), int(sub_w - 2), int(led_h * 0.4))

                    if global_idx == peak_segment and peak_segment > 0:
                        if self.peak_mode == 1 or self.peak_mode == 0:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(255, 255, 255))
                            painter.drawRect(int(sub_x - 1), int(y_offset - 1), int(sub_w + 1), int(led_h + 1))
                        elif self.peak_mode == 2:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(255, 200, 0))
                            painter.drawRect(int(sub_x - 1), int(y_offset - 1), int(sub_w + 1), int(led_h + 1))
                        elif self.peak_mode == 3:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(0, 230, 255))
                            painter.drawRect(int(sub_x - 1), int(y_offset - 1), int(sub_w + 1), int(led_h + 1))
        else:
            active_leds = int(level * self.num_leds)
            peak_led = int(peak * self.num_leds)
            
            red_start = int(self.num_leds * 0.8)
            yellow_start = int(self.num_leds * 0.6)
            
            for i in range(self.num_leds):
                x = start_x + i * (led_w + gap)
                is_red = i >= red_start
                is_yellow = i >= yellow_start and not is_red
                
                if self.theme_id == 1:
                    if is_red: base_color = QColor(255, 50, 200)
                    elif is_yellow: base_color = QColor(100, 150, 255)
                    else: base_color = QColor(0, 200, 255)
                elif self.theme_id == 2:
                    if is_red: base_color = QColor(255, 0, 50)
                    elif is_yellow: base_color = QColor(255, 100, 0)
                    else: base_color = QColor(255, 180, 0)
                elif self.theme_id == 3: # Ice
                    if is_red: base_color = QColor(255, 255, 255)
                    elif is_yellow: base_color = QColor(136, 221, 255)
                    else: base_color = QColor(0, 229, 255)
                elif self.theme_id == 4: # Sunset
                    if is_red: base_color = QColor(233, 30, 99)
                    elif is_yellow: base_color = QColor(255, 107, 53)
                    else: base_color = QColor(255, 193, 7)
                else:
                    if is_red: base_color = QColor(255, 50, 50)
                    elif is_yellow: base_color = QColor(255, 200, 50)
                    else: base_color = QColor(0, 255, 102)
                    
                if i >= active_leds:
                    painter.setBrush(QColor(base_color.red() // 8, base_color.green() // 8, base_color.blue() // 8))
                else:
                    painter.setBrush(base_color)
                    
                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                
                if i == peak_led and peak_led > 0:
                    if self.peak_mode == 1:
                        painter.setBrush(base_color)
                        painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                    elif self.peak_mode == 2:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 200, 0))
                        painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                    elif self.peak_mode == 3:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(0, 230, 255))
                        painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                    else:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 255, 255, 180))
                        painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

    def draw_channel_vertical(self, painter, level, peak, x_offset, col_w, label):
        painter.setPen(QColor(255, 255, 255))
        font = painter.font()
        font.setBold(True)
        font.setPointSizeF(font.pointSizeF() * self.size_scale)
        painter.setFont(font)
        painter.drawText(x_offset + int(col_w / 2 - 4 * self.size_scale), int(16 * self.size_scale), label)

        led_w = col_w
        led_h = 10 * self.size_scale
        gap = 3 * self.size_scale
        start_y = 22 * self.size_scale

        if self.theme_id == 5:
            total_segments = self.num_leds * 4
            active_segments = int(level * total_segments)
            peak_segment = int(peak * total_segments)

            sub_gap = 2 * self.size_scale
            sub_w = (led_w - 3 * sub_gap) / 4.0
            base_color = QColor(0, 229, 255)
            dim_color = QColor(0, 34, 40)

            for idx_from_bottom in range(self.num_leds):
                idx_from_top = self.num_leds - 1 - idx_from_bottom
                y = start_y + idx_from_top * (led_h + gap)

                for j in range(4):
                    global_idx = idx_from_bottom * 4 + j
                    is_on = global_idx < active_segments
                    sub_x = x_offset + j * (sub_w + sub_gap)

                    if is_on:
                        painter.setBrush(base_color)
                    else:
                        painter.setBrush(dim_color)

                    painter.setPen(Qt.PenStyle.NoPen)
                    painter.drawRect(int(sub_x), int(y), int(sub_w), int(led_h))

                    if is_on:
                        painter.setBrush(QColor(255, 255, 255, 60))
                        painter.drawRect(int(sub_x + 1), int(y + 1), int(sub_w - 2), int(led_h * 0.4))

                    if global_idx == peak_segment and peak_segment > 0:
                        if self.peak_mode == 1 or self.peak_mode == 0:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(255, 255, 255))
                            painter.drawRect(int(sub_x - 1), int(y - 1), int(sub_w + 1), int(led_h + 1))
                        elif self.peak_mode == 2:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(255, 200, 0))
                            painter.drawRect(int(sub_x - 1), int(y - 1), int(sub_w + 1), int(led_h + 1))
                        elif self.peak_mode == 3:
                            painter.setBrush(Qt.BrushStyle.NoBrush)
                            painter.setPen(QColor(0, 230, 255))
                            painter.drawRect(int(sub_x - 1), int(y - 1), int(sub_w + 1), int(led_h + 1))
        else:
            active_leds = int(level * self.num_leds)
            peak_led = int(peak * self.num_leds)

            red_start = int(self.num_leds * 0.8)
            yellow_start = int(self.num_leds * 0.6)

            for idx_from_bottom in range(self.num_leds):
                idx_from_top = self.num_leds - 1 - idx_from_bottom
                y = start_y + idx_from_top * (led_h + gap)

                is_red = idx_from_bottom >= red_start
                is_yellow = idx_from_bottom >= yellow_start and not is_red

                if self.theme_id == 1:
                    if is_red: base_color = QColor(255, 50, 200)
                    elif is_yellow: base_color = QColor(100, 150, 255)
                    else: base_color = QColor(0, 200, 255)
                elif self.theme_id == 2:
                    if is_red: base_color = QColor(255, 0, 50)
                    elif is_yellow: base_color = QColor(255, 100, 0)
                    else: base_color = QColor(255, 180, 0)
                elif self.theme_id == 3: # Ice
                    if is_red: base_color = QColor(255, 255, 255)
                    elif is_yellow: base_color = QColor(136, 221, 255)
                    else: base_color = QColor(0, 229, 255)
                elif self.theme_id == 4: # Sunset
                    if is_red: base_color = QColor(233, 30, 99)
                    elif is_yellow: base_color = QColor(255, 107, 53)
                    else: base_color = QColor(255, 193, 7)
                else:
                    if is_red: base_color = QColor(255, 50, 50)
                    elif is_yellow: base_color = QColor(255, 200, 50)
                    else: base_color = QColor(0, 255, 102)

                if idx_from_bottom >= active_leds:
                    painter.setBrush(QColor(base_color.red() // 8, base_color.green() // 8, base_color.blue() // 8))
                else:
                    painter.setBrush(base_color)

                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))

                if idx_from_bottom == peak_led and peak_led > 0:
                    if self.peak_mode == 1:
                        painter.setBrush(base_color)
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    elif self.peak_mode == 2:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 200, 0))
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    elif self.peak_mode == 3:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(0, 230, 255))
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    else:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 255, 255, 180))
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))

    # Backward compatibility helper
    def draw_channel(self, painter, level, peak, y_offset, label):
        self.draw_channel_horizontal(painter, level, peak, y_offset, label)

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
        old_device = self.settings.value("input_device", "", type=str)
        dialog = SettingsDialog(self)
        if dialog.exec():
            self.load_settings()
            new_device = self.settings.value("input_device", "", type=str)
            if old_device != new_device:
                self.start_audio_stream()

    def closeEvent(self, event):
        if self.stream:
            self.stream.stop()
            self.stream.close()
        super().closeEvent(event)

if __name__ == '__main__':
    import signal
    app = QApplication(sys.argv)

    # Kézi megszakítás kezelése (Ctrl+C)
    signal.signal(signal.SIGINT, lambda sig, frame: app.quit())

    # Timer a jelek feldolgozásához
    timer = QTimer()
    timer.timeout.connect(lambda: None)
    timer.start(500)

    meter = VuMeter()
    meter.show()
    sys.exit(app.exec())
