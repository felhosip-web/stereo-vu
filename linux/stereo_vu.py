import sys
import math
import numpy as np
import sounddevice as sd
from PyQt6.QtWidgets import (QApplication, QWidget, QMenu, QDialog, 
                             QFormLayout, QSlider, QComboBox, QCheckBox, 
                             QPushButton, QHBoxLayout, QVBoxLayout, QLabel,
                             QTabWidget)
from PyQt6.QtCore import Qt, QTimer, QPoint, QSettings
from PyQt6.QtGui import QPainter, QColor, QAction

class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Stereo VU Beállítások")
        self.settings = QSettings("StereoVU", "LinuxApp")
        self.resize(480, 520)
        
        main_layout = QVBoxLayout(self)
        self.tabs = QTabWidget(self)

        # -------------------------------------------------------------
        # Tab 1: Detektálás és Ballisztika (Detection & Ballistics)
        # -------------------------------------------------------------
        tab1 = QWidget()
        layout1 = QFormLayout(tab1)

        self.detection_combo = QComboBox()
        self.detection_combo.addItems(["Peak (Csúcsérték)", "RMS (Négyzetes középérték)"])
        self.detection_combo.setCurrentIndex(self.settings.value("detection_mode", 0, type=int))
        layout1.addRow("Detektálási mód:", self.detection_combo)

        self.rms_window_slider = QSlider(Qt.Orientation.Horizontal)
        self.rms_window_slider.setRange(50, 1000)
        self.rms_window_slider.setSingleStep(10)
        self.rms_window_slider.setValue(self.settings.value("rms_window_ms", 350, type=int))
        self.rms_window_label = QLabel(f"{self.rms_window_slider.value()} ms")
        self.rms_window_slider.valueChanged.connect(lambda v: self.rms_window_label.setText(f"{v} ms"))
        rms_box = QHBoxLayout()
        rms_box.addWidget(self.rms_window_slider)
        rms_box.addWidget(self.rms_window_label)
        layout1.addRow("RMS ablak (50-1000 ms):", rms_box)

        self.mode_combo = QComboBox()
        self.mode_combo.addItems([
            "Digitális Peak",
            "Analóg VU",
            "PPM",
            "True Peak",
            "Classic VU",
            "BBC PPM",
            "Nordic / DIN",
            "Egyéni"
        ])
        self.mode_combo.setCurrentIndex(self.settings.value("mode", 0, type=int))
        layout1.addRow("Üzemmód (Ballisztika):", self.mode_combo)

        self.attack_slider = QSlider(Qt.Orientation.Horizontal)
        self.attack_slider.setRange(5, 100)
        self.attack_slider.setValue(int(self.settings.value("attack_speed", 0.35, type=float) * 100))
        layout1.addRow("Egyéni Felfutás (5-100%):", self.attack_slider)
        
        self.decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.decay_slider.setRange(5, 100)
        self.decay_slider.setValue(int(self.settings.value("decay_speed", 0.12, type=float) * 100))
        layout1.addRow("Egyéni Lecsengés (5-100%):", self.decay_slider)

        self.target_decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.target_decay_slider.setRange(880, 970)
        self.target_decay_slider.setValue(int(self.settings.value("target_decay", 0.94, type=float) * 1000))
        layout1.addRow("Célérték elengedés (0.88-0.97):", self.target_decay_slider)

        self.peak_hold_slider = QSlider(Qt.Orientation.Horizontal)
        self.peak_hold_slider.setRange(200, 3000)
        saved_peak_hold = self.settings.value("peak_hold_ms", self.settings.value("peak_hold", 900, type=int), type=int)
        if saved_peak_hold <= 180:
            saved_peak_hold = int(saved_peak_hold * 16.6)
        self.peak_hold_slider.setValue(saved_peak_hold)
        layout1.addRow("Peak tartási idő (200-3000 ms):", self.peak_hold_slider)

        self.peak_decay_slider = QSlider(Qt.Orientation.Horizontal)
        self.peak_decay_slider.setRange(900, 995)
        self.peak_decay_slider.setValue(int(self.settings.value("peak_decay", 0.985, type=float) * 1000))
        layout1.addRow("Peak lecsengés (0.900-0.995):", self.peak_decay_slider)

        self.peak_never_below_check = QCheckBox("Bekapcsolva")
        self.peak_never_below_check.setChecked(self.settings.value("peak_never_below_bar", True, type=bool))
        layout1.addRow("Peak soha nem megy a sáv alá:", self.peak_never_below_check)

        self.spring_slider = QSlider(Qt.Orientation.Horizontal)
        self.spring_slider.setRange(5, 40)
        self.spring_slider.setValue(int(self.settings.value("analog_spring", 0.15, type=float) * 100))
        layout1.addRow("Analóg Rugó (0.05-0.40):", self.spring_slider)

        self.damp_slider = QSlider(Qt.Orientation.Horizontal)
        self.damp_slider.setRange(10, 40)
        self.damp_slider.setValue(int(self.settings.value("analog_damp", 0.25, type=float) * 100))
        layout1.addRow("Analóg Csillapítás (0.10-0.40):", self.damp_slider)

        self.tabs.addTab(tab1, "Detektálás & Ballisztika")

        # -------------------------------------------------------------
        # Tab 2: Látvány effektusok (Visual Effects)
        # -------------------------------------------------------------
        tab2 = QWidget()
        layout2 = QFormLayout(tab2)

        self.fx_glow_check = QCheckBox("Bekapcsolva")
        self.fx_glow_check.setChecked(self.settings.value("fx_glow", True, type=bool))
        layout2.addRow("LED Ragyogás (Glow):", self.fx_glow_check)

        self.fx_sheen_check = QCheckBox("Bekapcsolva")
        self.fx_sheen_check.setChecked(self.settings.value("fx_sheen", True, type=bool))
        layout2.addRow("Belső Fény / Csillanás (Sheen):", self.fx_sheen_check)

        self.fx_ghost_check = QCheckBox("Bekapcsolva")
        self.fx_ghost_check.setChecked(self.settings.value("fx_ghost", True, type=bool))
        layout2.addRow("Halvány inaktív LED-ek (Ghost):", self.fx_ghost_check)

        self.fx_db_scale_check = QCheckBox("Bekapcsolva")
        self.fx_db_scale_check.setChecked(self.settings.value("fx_db_scale", True, type=bool))
        layout2.addRow("dB Skála feliratok:", self.fx_db_scale_check)

        self.tabs.addTab(tab2, "Látvány effektek")

        # -------------------------------------------------------------
        # Tab 3: Megjelenés és Elrendezés (Appearance & Layout)
        # -------------------------------------------------------------
        tab3 = QWidget()
        layout3 = QFormLayout(tab3)

        self.theme_combo = QComboBox()
        self.theme_combo.addItems(["Klasszikus (Zöld-Narancs-Piros)", "Cyberpunk (Neon Kék-Rózsaszín)", "Tűz (Sárga-Narancs-Vörös)", "Jég (Türkiz-Kék-Fehér)", "Naplemente (Arany-Bíbor)", "VFD (Klasszikus Cián)"])
        self.theme_combo.setCurrentIndex(self.settings.value("theme", 0, type=int))
        layout3.addRow("Színséma:", self.theme_combo)

        self.layout_check = QCheckBox("Vízszintes (L fent, R lent)")
        self.layout_check.setChecked(self.settings.value("horizontal", False, type=bool))
        layout3.addRow("Elrendezés:", self.layout_check)

        self.led_slider = QSlider(Qt.Orientation.Horizontal)
        self.led_slider.setRange(10, 40)
        self.led_slider.setValue(self.settings.value("led_count", 20, type=int))
        layout3.addRow("LED-ek száma (10-40):", self.led_slider)

        self.scale_slider = QSlider(Qt.Orientation.Horizontal)
        self.scale_slider.setRange(50, 200)
        self.scale_slider.setValue(int(self.settings.value("size_scale", 1.0, type=float) * 100))
        layout3.addRow("Méret skálázás (50-200%):", self.scale_slider)
        
        self.opacity_slider = QSlider(Qt.Orientation.Horizontal)
        self.opacity_slider.setRange(0, 255)
        self.opacity_slider.setValue(self.settings.value("opacity", 204, type=int))
        layout3.addRow("Háttér sötétsége:", self.opacity_slider)

        self.peak_combo = QComboBox()
        self.peak_combo.addItems(["Fehér (klasszikus)", "Színes (LED)", "Fix sárga", "Fix cián"])
        if self.settings.contains("peak_mode"):
            self.peak_combo.setCurrentIndex(self.settings.value("peak_mode", 0, type=int))
        else:
            old_colored = self.settings.value("colored_peak", False, type=bool)
            self.peak_combo.setCurrentIndex(1 if old_colored else 0)
        layout3.addRow("Peak LED stílusa:", self.peak_combo)

        self.tabs.addTab(tab3, "Megjelenés & Elrendezés")

        # -------------------------------------------------------------
        # Tab 4: Általános és Audió (General / Audio)
        # -------------------------------------------------------------
        tab4 = QWidget()
        layout4 = QFormLayout(tab4)

        self.device_combo = QComboBox()
        self.input_devices = []
        try:
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
                    self.device_combo.addItem(name, dev['name'])

            idx = -1
            if current_device_name:
                for j in range(self.device_combo.count()):
                    if self.device_combo.itemData(j) == current_device_name:
                        idx = j
                        break

            if idx < 0 and default_device_name:
                for j in range(self.device_combo.count()):
                    if self.device_combo.itemData(j) == default_device_name:
                        idx = j
                        break

            if idx >= 0:
                self.device_combo.setCurrentIndex(idx)

        except Exception as e:
            print(f"Error querying audio devices: {e}")
            self.device_combo.addItem("Alapértelmezett", -1)

        layout4.addRow("Bemeneti eszköz:", self.device_combo)

        hint_label = QLabel("Tipp: A rendszerhangok (kimenet) méréséhez hagyd\nAlapértelmezetten, majd a Hangerőszabályzóban\n(pavucontrol) a Felvétel fülön állítsd át a forrást\n'Monitor of...' eszközre.")
        hint_label.setStyleSheet("color: gray; font-size: 10px;")
        layout4.addRow("", hint_label)

        self.gain_slider = QSlider(Qt.Orientation.Horizontal)
        self.gain_slider.setRange(1, 300)
        self.gain_slider.setValue(int(self.settings.value("gain", 1.0, type=float) * 100))
        layout4.addRow("Érzékenység / Gain (0.01x - 3.0x):", self.gain_slider)

        self.tabs.addTab(tab4, "Általános & Audió")

        main_layout.addWidget(self.tabs)

        # Gombok
        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Mentés")
        save_btn.clicked.connect(self.save_settings)
        cancel_btn = QPushButton("Mégse")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        main_layout.addLayout(btn_layout)

    def save_settings(self):
        self.settings.setValue("input_device", self.device_combo.currentData())
        self.settings.setValue("detection_mode", self.detection_combo.currentIndex())
        self.settings.setValue("rms_window_ms", self.rms_window_slider.value())
        self.settings.setValue("horizontal", self.layout_check.isChecked())
        self.settings.setValue("mode", self.mode_combo.currentIndex())
        self.settings.setValue("led_count", self.led_slider.value())
        self.settings.setValue("theme", self.theme_combo.currentIndex())
        self.settings.setValue("size_scale", self.scale_slider.value() / 100.0)
        self.settings.setValue("opacity", self.opacity_slider.value())
        self.settings.setValue("peak_mode", self.peak_combo.currentIndex())
        self.settings.setValue("target_decay", self.target_decay_slider.value() / 1000.0)
        self.settings.setValue("peak_hold_ms", self.peak_hold_slider.value())
        self.settings.setValue("peak_hold", self.peak_hold_slider.value())
        self.settings.setValue("peak_decay", self.peak_decay_slider.value() / 1000.0)
        self.settings.setValue("peak_never_below_bar", self.peak_never_below_check.isChecked())
        self.settings.setValue("analog_spring", self.spring_slider.value() / 100.0)
        self.settings.setValue("analog_damp", self.damp_slider.value() / 100.0)
        self.settings.setValue("attack_speed", self.attack_slider.value() / 100.0)
        self.settings.setValue("decay_speed", self.decay_slider.value() / 100.0)
        self.settings.setValue("gain", self.gain_slider.value() / 100.0)
        self.settings.setValue("fx_glow", self.fx_glow_check.isChecked())
        self.settings.setValue("fx_sheen", self.fx_sheen_check.isChecked())
        self.settings.setValue("fx_ghost", self.fx_ghost_check.isChecked())
        self.settings.setValue("fx_db_scale", self.fx_db_scale_check.isChecked())
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
        self.sample_rate = 44100
        self.rms_history = None
        self.rms_history_idx = 0
        self.rms_history_count = 0
        self.rms_window_samples = int(self.sample_rate * 0.35)
        self.init_rms_buffer(self.settings.value("rms_window_ms", 350, type=int))

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
        self.bbc_step_timer_l = 0
        self.bbc_step_timer_r = 0
        
        self.drag_pos = QPoint()
        self.timer = QTimer()
        self.timer.timeout.connect(self.update_meter)
        self.timer.start(16)
        
        self.stream = None
        self.start_audio_stream()

    def init_rms_buffer(self, window_ms):
        window_ms = max(50, min(1000, window_ms))
        needed_samples = int(self.sample_rate * (window_ms / 1000.0))
        if self.rms_history is None or self.rms_history.shape[0] != needed_samples:
            self.rms_window_samples = needed_samples
            self.rms_history = np.zeros((needed_samples, 2), dtype=np.float32)
            self.rms_history_idx = 0
            self.rms_history_count = 0

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
            self.stream = sd.InputStream(device=device_index, callback=self.audio_callback, channels=2, samplerate=self.sample_rate)
            self.stream.start()
        except Exception as e:
            print(f"Failed to start audio stream with device {device_name or 'default'}: {e}")
            if device_index is not None:
                try:
                    print("Falling back to default device...")
                    self.stream = sd.InputStream(callback=self.audio_callback, channels=2, samplerate=self.sample_rate)
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
        self.rms_window_ms = self.settings.value("rms_window_ms", 350, type=int)
        self.init_rms_buffer(self.rms_window_ms)

        self.is_horizontal = self.settings.value("horizontal", False, type=bool)
        
        if self.settings.contains("peak_mode"):
            self.peak_mode = self.settings.value("peak_mode", 0, type=int)
        else:
            self.peak_mode = 1 if self.settings.value("colored_peak", False, type=bool) else 0
            
        self.peak_never_below_bar = self.settings.value("peak_never_below_bar", True, type=bool)

        if self.mode == 0: # Digital Peak: fast attack, characterful decay
            self.attack_speed = 0.90
            self.decay_speed = 0.12
        elif self.mode == 1: # Analog VU: slow ~300 ms integration feel
            self.attack_speed = 0.10
            self.decay_speed = 0.05
        elif self.mode == 2: # PPM: fast attack, slow release
            self.attack_speed = 0.40
            self.decay_speed = 0.08
        elif self.mode == 3: # True Peak: inter-sample peak, fast attack, clean decay
            self.attack_speed = 0.95
            self.decay_speed = 0.15
        elif self.mode == 4: # Classic VU: 300 ms integration, smoother ±1 dB style ballistics
            self.attack_speed = 0.08
            self.decay_speed = 0.04
            self.analog_spring = 0.10
            self.analog_damp = 0.32
        elif self.mode == 5: # BBC PPM: characteristic stepped / multi-stage release
            self.attack_speed = 0.45
            self.decay_speed = 0.06
        elif self.mode == 6: # Nordic / DIN: faster attack, distinct (slightly faster) decay
            self.attack_speed = 0.55
            self.decay_speed = 0.14
        else:
            self.attack_speed = self.settings.value("attack_speed", 0.35, type=float)
            self.decay_speed = self.settings.value("decay_speed", 0.12, type=float)
            
        self.digital_gain = self.settings.value("gain", 1.0, type=float)
        self.target_decay = self.settings.value("target_decay", 0.94, type=float)
        saved_peak_hold = self.settings.value("peak_hold_ms", self.settings.value("peak_hold", 900, type=int), type=int)
        if saved_peak_hold <= 180:
            saved_peak_hold = int(saved_peak_hold * 16.6)
        self.peak_hold_ms = saved_peak_hold
        self.peak_hold_time = max(1, int(round(self.peak_hold_ms / 16.0)))
        self.peak_decay_rate = self.settings.value("peak_decay", 0.985, type=float)
        self.analog_spring = self.settings.value("analog_spring", 0.15, type=float)
        self.analog_damp = self.settings.value("analog_damp", 0.25, type=float)

        self.fx_glow = self.settings.value("fx_glow", True, type=bool)
        self.fx_sheen = self.settings.value("fx_sheen", True, type=bool)
        self.fx_ghost = self.settings.value("fx_ghost", True, type=bool)
        self.fx_db_scale = self.settings.value("fx_db_scale", True, type=bool)
        
        if self.is_horizontal:
            base_w = 40 + self.num_leds * 13 + (15 if self.fx_db_scale else 0)
            base_h = 92 if self.fx_db_scale else 78
        else:
            base_w = 125 if self.fx_db_scale else 100
            base_h = 36 + self.num_leds * 13

        self.resize(int(base_w * self.size_scale), int(base_h * self.size_scale))
        self.update()

    def audio_callback(self, indata, frames, time, status):
        # Peak detection: true peak of the current audio buffer (max absolute sample)
        # Fast, direct, responsive
        peak_l = float(np.max(np.abs(indata[:, 0]))) if frames > 0 else 0.0
        peak_r = float(np.max(np.abs(indata[:, 1]))) if frames > 0 else 0.0

        # Inter-sample peak calculation when in True Peak mode (interpolating between samples)
        if self.mode == 3 and frames > 1:
            chan_l = indata[:, 0]
            chan_r = indata[:, 1]
            # Interpolated sub-sample points (linear midpoint & quarter points)
            mid_l = 0.5 * (chan_l[:-1] + chan_l[1:])
            mid_r = 0.5 * (chan_r[:-1] + chan_r[1:])
            q1_l = 0.75 * chan_l[:-1] + 0.25 * chan_l[1:]
            q3_l = 0.25 * chan_l[:-1] + 0.75 * chan_l[1:]
            q1_r = 0.75 * chan_r[:-1] + 0.25 * chan_r[1:]
            q3_r = 0.25 * chan_r[:-1] + 0.75 * chan_r[1:]
            peak_l = max(peak_l, float(np.max(np.abs(mid_l))), float(np.max(np.abs(q1_l))), float(np.max(np.abs(q3_l))))
            peak_r = max(peak_r, float(np.max(np.abs(mid_r))), float(np.max(np.abs(q1_r))), float(np.max(np.abs(q3_r))))

        # True RMS calculation over sliding window (independent from peak)
        if self.detection_mode == 1:
            if self.rms_history is None or self.rms_history.shape[0] != self.rms_window_samples:
                self.init_rms_buffer(self.rms_window_ms)

            buf_len = self.rms_window_samples
            n = len(indata)
            if n >= buf_len:
                self.rms_history[:] = indata[-buf_len:]
                self.rms_history_idx = 0
                self.rms_history_count = buf_len
            else:
                end_idx = self.rms_history_idx + n
                if end_idx <= buf_len:
                    self.rms_history[self.rms_history_idx:end_idx] = indata
                    self.rms_history_idx = end_idx % buf_len
                else:
                    first_part = buf_len - self.rms_history_idx
                    self.rms_history[self.rms_history_idx:] = indata[:first_part]
                    second_part = n - first_part
                    self.rms_history[:second_part] = indata[first_part:]
                    self.rms_history_idx = second_part
                self.rms_history_count = min(buf_len, self.rms_history_count + n)

            count = max(1, self.rms_history_count)
            # Calculate RMS correctly over the window
            active_samples = self.rms_history if self.rms_history_count >= buf_len else self.rms_history[:count]
            val_l = float(np.sqrt(np.mean(active_samples[:, 0] ** 2)))
            val_r = float(np.sqrt(np.mean(active_samples[:, 1] ** 2)))
        else:
            val_l = peak_l
            val_r = peak_r

        def to_level(signal):
            db = 20 * math.log10(max(signal, 0.0001))
            return max(0.0, min(1.0, (db + 50) / 50))
            
        new_target_l = to_level(val_l * 3.0 * self.digital_gain)
        new_target_r = to_level(val_r * 3.0 * self.digital_gain)

        # Responsive fast attack, follower decay
        self.target_l = min(1.0, max(self.target_l, new_target_l))
        self.target_r = min(1.0, max(self.target_r, new_target_r))

    def update_meter(self):
        if self.mode == 1 or self.mode == 4:
            # 1: Analog VU, 4: Classic VU (spring/damp 2nd-order ballistics)
            self.vel_l += (self.target_l - self.level_l) * self.analog_spring
            self.vel_l *= (1.0 - self.analog_damp)
            self.level_l += self.vel_l
            
            self.vel_r += (self.target_r - self.level_r) * self.analog_spring
            self.vel_r *= (1.0 - self.analog_damp)
            self.level_r += self.vel_r
            
            self.level_l = max(0.0, min(1.2, self.level_l))
            self.level_r = max(0.0, min(1.2, self.level_r))
        elif self.mode == 5:
            # BBC PPM: stepped / multi-stage release
            if self.target_l > self.level_l:
                self.level_l += (self.target_l - self.level_l) * self.attack_speed
                self.bbc_step_timer_l = 0
            else:
                self.bbc_step_timer_l += 1
                if self.bbc_step_timer_l >= 3:
                    drop = max(0.008, self.level_l * 0.05)
                    self.level_l = max(self.target_l, self.level_l - drop)
                    self.bbc_step_timer_l = 0

            if self.target_r > self.level_r:
                self.level_r += (self.target_r - self.level_r) * self.attack_speed
                self.bbc_step_timer_r = 0
            else:
                self.bbc_step_timer_r += 1
                if self.bbc_step_timer_r >= 3:
                    drop = max(0.008, self.level_r * 0.05)
                    self.level_r = max(self.target_r, self.level_r - drop)
                    self.bbc_step_timer_r = 0
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

        if display_l >= self.peak_l:
            self.peak_l = display_l
            self.peak_hold_l = self.peak_hold_time
        else:
            if self.peak_hold_l > 0:
                self.peak_hold_l -= 1
            else:
                self.peak_l *= self.peak_decay_rate
            
        if display_r >= self.peak_r:
            self.peak_r = display_r
            self.peak_hold_r = self.peak_hold_time
        else:
            if self.peak_hold_r > 0:
                self.peak_hold_r -= 1
            else:
                self.peak_r *= self.peak_decay_rate

        # Peak never below bar guarantee
        if self.peak_never_below_bar:
            if self.peak_l < display_l:
                self.peak_l = display_l
            if self.peak_r < display_r:
                self.peak_r = display_r

        self.target_l *= self.target_decay
        self.target_r *= self.target_decay
            
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        if self.theme_id == 5:
            painter.setBrush(QColor(10, 18, 21, self.bg_opacity))
            painter.setPen(QColor(0, 56, 64, 180))
            painter.drawRoundedRect(self.rect().adjusted(1, 1, -1, -1), int(10 * self.size_scale), int(10 * self.size_scale))
        else:
            painter.setBrush(QColor(0, 0, 0, self.bg_opacity))
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(self.rect(), int(10 * self.size_scale), int(10 * self.size_scale))
        
        display_l = max(0.0, min(1.0, self.level_l))
        display_r = max(0.0, min(1.0, self.level_r))

        if self.is_horizontal:
            start_x = int(35 * self.size_scale)
            y_l = int(10 * self.size_scale)
            y_scale = int(34 * self.size_scale)
            y_r = int(48 * self.size_scale) if self.fx_db_scale else int(38 * self.size_scale)
            self.draw_channel_horizontal(painter, display_l, self.peak_l, y_l, "L")
            if self.fx_db_scale:
                self.draw_scale_horizontal(painter, start_x, y_scale)
            self.draw_channel_horizontal(painter, display_r, self.peak_r, y_r, "R")

            if self.theme_id == 5:
                painter.setPen(QColor(255, 255, 255, 45))
                led_h = int(18 * self.size_scale)
                end_x = start_x + int(self.num_leds * 13 * self.size_scale)

                painter.drawLine(start_x, y_l + int(led_h * 0.25), end_x, y_l + int(led_h * 0.25))
                painter.drawLine(start_x, y_l + int(led_h * 0.75), end_x, y_l + int(led_h * 0.75))
                painter.drawLine(start_x, y_r + int(led_h * 0.25), end_x, y_r + int(led_h * 0.25))
                painter.drawLine(start_x, y_r + int(led_h * 0.75), end_x, y_r + int(led_h * 0.75))
        else:
            col_w = int(34 * self.size_scale)
            x_l = int(14 * self.size_scale)
            x_r = int(54 * self.size_scale)
            x_scale = int(94 * self.size_scale)
            self.draw_channel_vertical(painter, display_l, self.peak_l, x_l, col_w, "L")
            self.draw_channel_vertical(painter, display_r, self.peak_r, x_r, col_w, "R")
            if self.fx_db_scale:
                self.draw_scale_vertical(painter, x_scale, int(22 * self.size_scale))

            if self.theme_id == 5:
                painter.setPen(QColor(255, 255, 255, 45))
                top_y = int(22 * self.size_scale)
                end_y = top_y + int(self.num_leds * 13 * self.size_scale)
                for x_col in (x_l, x_r):
                    painter.drawLine(x_col + int(col_w * 0.25), top_y, x_col + int(col_w * 0.25), end_y)
                    painter.drawLine(x_col + int(col_w * 0.75), top_y, x_col + int(col_w * 0.75), end_y)

    def draw_scale_horizontal(self, painter, start_x, y):
        if self.theme_id == 5:
            painter.setPen(QColor(77, 174, 184, 200))
        else:
            painter.setPen(QColor(160, 160, 160, 180))
        font = painter.font()
        font.setBold(False)
        font.setPointSizeF(max(6.0, 7.5 * self.size_scale))
        painter.setFont(font)

        marks = [
            (0, "-∞"),
            (int(self.num_leds * 0.35), "-20"),
            (int(self.num_leds * 0.65), "-10"),
            (int(self.num_leds * 0.85), "-6"),
            (int(self.num_leds * 0.91), "-3"),
            (self.num_leds - 1, "0")
        ]
        led_w = 11 * self.size_scale
        gap = 2 * self.size_scale

        for idx, label in marks:
            x = int(start_x + idx * (led_w + gap) + led_w / 2)
            painter.drawLine(x, y, x, y + int(3 * self.size_scale))
            painter.drawText(x - int(8 * self.size_scale), y + int(10 * self.size_scale), label)

    def draw_scale_vertical(self, painter, x, start_y):
        if self.theme_id == 5:
            painter.setPen(QColor(77, 174, 184, 200))
        else:
            painter.setPen(QColor(160, 160, 160, 180))
        font = painter.font()
        font.setBold(False)
        font.setPointSizeF(max(6.0, 7.5 * self.size_scale))
        painter.setFont(font)

        marks = [
            (self.num_leds - 1, " 0"),
            (int(self.num_leds * 0.91), "-3"),
            (int(self.num_leds * 0.85), "-6"),
            (int(self.num_leds * 0.65), "-10"),
            (int(self.num_leds * 0.35), "-20"),
            (0, "-∞")
        ]
        led_h = 10 * self.size_scale
        gap = 3 * self.size_scale

        for idx_from_bottom, label in marks:
            idx_from_top = self.num_leds - 1 - idx_from_bottom
            y = int(start_y + idx_from_top * (led_h + gap) + led_h / 2)
            painter.drawLine(x, y, x + int(4 * self.size_scale), y)
            painter.drawText(x + int(6 * self.size_scale), y + int(4 * self.size_scale), label)

    def draw_channel_horizontal(self, painter, level, peak, y_offset, label):
        if self.theme_id == 5:
            painter.setPen(QColor(102, 192, 204))
        else:
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
                        painter.setBrush(dim_color if self.fx_ghost else QColor(0, 0, 0, 0))
                        
                    painter.setPen(Qt.PenStyle.NoPen)
                    painter.drawRect(int(sub_x), int(y_offset), int(sub_w), int(led_h))
                    
                    if is_on and self.fx_glow:
                        glow_c = QColor(base_color)
                        glow_c.setAlpha(int(40 + level * 60))
                        painter.setBrush(glow_c)
                        painter.drawRect(int(sub_x - 1), int(y_offset - 1), int(sub_w + 2), int(led_h + 2))
                        painter.setBrush(base_color)
                        painter.drawRect(int(sub_x), int(y_offset), int(sub_w), int(led_h))

                    if is_on and self.fx_sheen:
                        painter.setBrush(QColor(255, 255, 255, 75))
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
                    painter.setBrush(QColor(base_color.red() // 8, base_color.green() // 8, base_color.blue() // 8) if self.fx_ghost else QColor(0, 0, 0, 0))
                else:
                    painter.setBrush(base_color)
                    
                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

                if i < active_leds:
                    if self.fx_glow:
                        glow_c = QColor(base_color)
                        glow_c.setAlpha(int(30 + level * 50))
                        painter.setBrush(glow_c)
                        painter.drawRect(int(x - 1), int(y_offset - 1), int(led_w + 2), int(led_h + 2))
                        painter.setBrush(base_color)
                        painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))
                    if self.fx_sheen:
                        painter.setBrush(QColor(255, 255, 255, 70))
                        painter.drawRect(int(x + 1), int(y_offset + 1), int(led_w - 2), int(led_h * 0.4))
                
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
        if self.theme_id == 5:
            painter.setPen(QColor(102, 192, 204))
        else:
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
            base_color = QColor(0, 212, 232)
            dim_color = QColor(0, 31, 38)

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
                        painter.setBrush(dim_color if self.fx_ghost else QColor(0, 0, 0, 0))

                    painter.setPen(Qt.PenStyle.NoPen)
                    painter.drawRect(int(sub_x), int(y), int(sub_w), int(led_h))

                    if is_on and self.fx_glow:
                        glow_c = QColor(base_color)
                        glow_c.setAlpha(int(40 + level * 60))
                        painter.setBrush(glow_c)
                        painter.drawRect(int(sub_x - 1), int(y - 1), int(sub_w + 2), int(led_h + 2))
                        painter.setBrush(base_color)
                        painter.drawRect(int(sub_x), int(y), int(sub_w), int(led_h))

                    if is_on and self.fx_sheen:
                        painter.setBrush(QColor(255, 255, 255, 75))
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
                    painter.setBrush(QColor(base_color.red() // 8, base_color.green() // 8, base_color.blue() // 8) if self.fx_ghost else QColor(0, 0, 0, 0))
                else:
                    painter.setBrush(base_color)

                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))

                if idx_from_bottom < active_leds:
                    if self.fx_glow:
                        glow_c = QColor(base_color)
                        glow_c.setAlpha(int(30 + level * 50))
                        painter.setBrush(glow_c)
                        painter.drawRect(int(x_offset - 1), int(y - 1), int(led_w + 2), int(led_h + 2))
                        painter.setBrush(base_color)
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    if self.fx_sheen:
                        painter.setBrush(QColor(255, 255, 255, 70))
                        painter.drawRect(int(x_offset + 1), int(y + 1), int(led_w - 2), int(led_h * 0.4))

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

    signal.signal(signal.SIGINT, lambda sig, frame: app.quit())

    timer = QTimer()
    timer.timeout.connect(lambda: None)
    timer.start(500)

    meter = VuMeter()
    meter.show()
    sys.exit(app.exec())
