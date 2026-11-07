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
from true_peak import TruePeakDetector, get_migrated_mode

class SettingsDialog(QDialog):
    """
    Settings dialog window for Stereo VU Linux application.
    Organized into 4 tabs:
    - Tab 1: Detection & Ballistics
    - Tab 2: Visual Effects
    - Tab 3: Appearance & Layout
    - Tab 4: General / Audio Device
    """
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
        initial_mode = get_migrated_mode(self.settings)
        self.mode_combo.setCurrentIndex(initial_mode)
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
        self.theme_combo.addItems([
            "Klasszikus (Zöld-Narancs-Piros)",
            "Cyberpunk (Neon Kék-Rózsaszín)",
            "Tűz (Sárga-Narancs-Vörös)",
            "Jég (Türkiz-Kék-Fehér)",
            "Naplemente (Arany-Bíbor)",
            "VFD (Klasszikus Cián)",
            "LCD Green (Zöld LCD)",
            "LCD Amber (Borostyán LCD)"
        ])
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

                # LCD Megjelenés (LCD Appearance) - Érvényes: LCD Green és LCD Amber témákra
        lcd_header = QLabel("<b>LCD Megjelenés (LCD Green & Amber)</b>")
        lcd_header.setStyleSheet("color: #33FF66; margin-top: 10px; margin-bottom: 2px;")
        layout3.addRow(lcd_header)

        self.lcd_ghost_slider = QSlider(Qt.Orientation.Horizontal)
        self.lcd_ghost_slider.setRange(0, 100)
        self.lcd_ghost_slider.setValue(int(round(self.settings.value("lcd_ghost_strength", 0.55, type=float) * 100)))
        layout3.addRow("LCD Ghost erősség (0-100%):", self.lcd_ghost_slider)

        self.lcd_gap_slider = QSlider(Qt.Orientation.Horizontal)
        self.lcd_gap_slider.setRange(10, 60)
        self.lcd_gap_slider.setValue(int(round(self.settings.value("lcd_segment_gap", 2.0, type=float) * 10)))
        layout3.addRow("LCD Szegmens rés (1-6 px):", self.lcd_gap_slider)

        self.lcd_seven_check = QCheckBox("Hétszegmenses dB kijelzés")
        self.lcd_seven_check.setChecked(self.settings.value("lcd_seven_segment", True, type=bool))
        layout3.addRow("Számérték stílus:", self.lcd_seven_check)

        self.lcd_contrast_slider = QSlider(Qt.Orientation.Horizontal)
        self.lcd_contrast_slider.setRange(20, 100)
        self.lcd_contrast_slider.setValue(int(round(self.settings.value("lcd_contrast", 0.80, type=float) * 100)))
        layout3.addRow("LCD Kontraszt (20-100%):", self.lcd_contrast_slider)

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
        """Persist current dialog selections and parameters to QSettings."""
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
        self.settings.setValue("lcd_ghost_strength", self.lcd_ghost_slider.value() / 100.0)
        self.settings.setValue("lcd_segment_gap", self.lcd_gap_slider.value() / 10.0)
        self.settings.setValue("lcd_seven_segment", self.lcd_seven_check.isChecked())
        self.settings.setValue("lcd_contrast", self.lcd_contrast_slider.value() / 100.0)
        self.accept()

class VuMeter(QWidget):
    """
    Real-time frameless floating Stereo VU Meter widget.
    Features 8 professional ballistics modes, True Peak FIR oversampling,
    true RMS sliding window calculation, and customizable visual styles.
    """
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
        self.true_peak_detector = TruePeakDetector()
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
        """Initialize or resize sliding window buffer for true RMS calculation."""
        window_ms = max(50, min(1000, window_ms))
        needed_samples = int(self.sample_rate * (window_ms / 1000.0))
        if self.rms_history is None or self.rms_history.shape[0] != needed_samples:
            self.rms_window_samples = needed_samples
            self.rms_history = np.zeros((needed_samples, 2), dtype=np.float32)
            self.rms_history_idx = 0
            self.rms_history_count = 0

    def start_audio_stream(self):
        """Start sounddevice input audio stream from selected or default audio device."""
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
        """Load and apply persisted preferences and ballistics parameters from QSettings."""
        self.mode = get_migrated_mode(self.settings)
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
            
        # 1. Load general user-configured values FIRST
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

        # 2. Configure ballistics parameters AFTER general preferences
        if self.mode == 0: # Digital Peak
            self.attack_speed = 0.90
            self.decay_speed = 0.12
        elif self.mode == 1: # Analog VU (uses user-defined analog_spring / analog_damp)
            self.attack_speed = 0.10
            self.decay_speed = 0.05
        elif self.mode == 2: # PPM
            self.attack_speed = 0.40
            self.decay_speed = 0.08
        elif self.mode == 3: # True Peak
            self.attack_speed = 0.95
            self.decay_speed = 0.15
        elif self.mode == 4: # Classic VU (dedicated spring/damp, not overwritten by Analog VU settings)
            self.attack_speed = 0.08
            self.decay_speed = 0.04
            self.analog_spring = 0.10
            self.analog_damp = 0.32
        elif self.mode == 5: # BBC PPM
            self.attack_speed = 0.45
            self.decay_speed = 0.06
        elif self.mode == 6: # Nordic / DIN
            self.attack_speed = 0.55
            self.decay_speed = 0.14
        else: # Custom
            self.attack_speed = self.settings.value("attack_speed", 0.35, type=float)
            self.decay_speed = self.settings.value("decay_speed", 0.12, type=float)

        self.peak_never_below_bar = self.settings.value("peak_never_below_bar", True, type=bool)
        self.fx_glow = self.settings.value("fx_glow", True, type=bool)
        self.fx_sheen = self.settings.value("fx_sheen", True, type=bool)
        self.fx_ghost = self.settings.value("fx_ghost", True, type=bool)
        self.fx_db_scale = self.settings.value("fx_db_scale", True, type=bool)
        self.lcd_ghost_strength = max(0.0, min(1.0, self.settings.value("lcd_ghost_strength", 0.55, type=float)))
        self.lcd_segment_gap = max(1.0, min(6.0, self.settings.value("lcd_segment_gap", 2.0, type=float)))
        self.lcd_seven_segment = self.settings.value("lcd_seven_segment", True, type=bool)
        self.lcd_contrast = max(0.20, min(1.0, self.settings.value("lcd_contrast", 0.80, type=float)))
        
        if self.is_horizontal:
            pitch = (11 + self.lcd_segment_gap) if self.theme_id in (6, 7) else 13
            base_w = 40 + int(self.num_leds * pitch) + (15 if self.fx_db_scale else 0)
            readout_extra = 22 if self.theme_id in (6, 7) else 0
            base_h = (92 if self.fx_db_scale else 78) + readout_extra
        else:
            pitch = (10 + self.lcd_segment_gap) if self.theme_id in (6, 7) else 13
            base_w = 125 if self.fx_db_scale else 100
            readout_extra = 24 if self.theme_id in (6, 7) else 0
            base_h = 36 + int(self.num_leds * pitch) + readout_extra

        self.resize(int(base_w * self.size_scale), int(base_h * self.size_scale))
        self.update()

    def audio_callback(self, indata, frames, time, status):
        """Audio stream callback: compute peak/True Peak and RMS values from incoming PCM buffer."""
        # True Peak detection if mode == 3, otherwise standard sample peak
        if self.mode == 3 and frames > 0:
            peak_l, peak_r = self.true_peak_detector.process_buffer(indata[:, 0], indata[:, 1])
        else:
            peak_l = float(np.max(np.abs(indata[:, 0]))) if frames > 0 else 0.0
            peak_r = float(np.max(np.abs(indata[:, 1]))) if frames > 0 else 0.0

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
        """60 FPS physics update loop for ballistics decay, spring simulation, and peak hold."""
        if self.mode == 1 or self.mode == 4:
            # 1: Analog VU, 4: Classic VU
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
        """Render VU meter towers, LEDs, scales, and frame chrome."""
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        if self.theme_id == 6:
            painter.setBrush(QColor(10, 26, 10, self.bg_opacity))
            painter.setPen(QColor(20, 60, 20, 180))
            painter.drawRoundedRect(self.rect().adjusted(1, 1, -1, -1), int(10 * self.size_scale), int(10 * self.size_scale))
        elif self.theme_id == 7:
            painter.setBrush(QColor(18, 10, 0, self.bg_opacity))
            painter.setPen(QColor(60, 35, 0, 180))
            painter.drawRoundedRect(self.rect().adjusted(1, 1, -1, -1), int(10 * self.size_scale), int(10 * self.size_scale))
        elif self.theme_id == 5:
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
            elif self.theme_id in (6, 7):
                readout_y = (y_r + int(24 * self.size_scale))
                self.draw_lcd_readout_horizontal(painter, start_x, readout_y, display_l, self.peak_l, display_r, self.peak_r)
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
            elif self.theme_id in (6, 7):
                top_y = int(22 * self.size_scale)
                led_h = 10 * self.size_scale
                gap = self.lcd_segment_gap * self.size_scale
                end_y = top_y + int(self.num_leds * (led_h + gap) - gap)
                readout_y = end_y + int(8 * self.size_scale)
                self.draw_lcd_readout_vertical(painter, x_l, readout_y, col_w, display_l, self.peak_l, display_r, self.peak_r)

    def get_lcd_colors(self):
        """Calculate modulated active and ghost colors for LCD themes."""
        if self.theme_id == 6:
            base_active = (0x33, 0xFF, 0x66)
            base_ghost = (0x0D, 0x2E, 0x14)
        else: # Theme 7: Amber
            base_active = (0xFF, 0xB0, 0x00)
            base_ghost = (0x2E, 0x18, 0x00)

        c = self.lcd_contrast
        g = self.lcd_ghost_strength

        active_color = QColor(int(base_active[0] * c), int(base_active[1] * c), int(base_active[2] * c))
        ghost_color = QColor(int(base_ghost[0] * g), int(base_ghost[1] * g), int(base_ghost[2] * g))
        return active_color, ghost_color

    def draw_seven_segment_char(self, painter, ch, x, y, w, h, active_color, ghost_color):
        """Draw a single seven-segment character glyph with active and ghost segment states."""
        seven_seg_map = {
            '0': (True, True, True, True, True, True, False),
            '1': (False, True, True, False, False, False, False),
            '2': (True, True, False, True, True, False, True),
            '3': (True, True, True, True, False, False, True),
            '4': (False, True, True, False, False, True, True),
            '5': (True, False, True, True, False, True, True),
            '6': (True, False, True, True, True, True, True),
            '7': (True, True, True, False, False, False, False),
            '8': (True, True, True, True, True, True, True),
            '9': (True, True, True, True, False, True, True),
            '-': (False, False, False, False, False, False, True),
            'I': (False, True, True, False, False, False, False),
            'N': (True, True, True, False, True, True, False),
            'F': (True, False, False, False, True, True, True),
            ' ': (False, False, False, False, False, False, False),
        }
        if ch == '.':
            dot_sz = max(2, int(w * 0.35))
            painter.fillRect(int(x), int(y + h - dot_sz), dot_sz, dot_sz, active_color)
            return int(w * 0.45)

        segs = seven_seg_map.get(ch, seven_seg_map[' '])
        t = max(1, int(min(w, h) * 0.18))
        half_h = h // 2
        defs = [
            (x + t, y, w - 2 * t, t, segs[0]),
            (x + w - t, y + t, t, half_h - t, segs[1]),
            (x + w - t, y + half_h, t, h - half_h - t, segs[2]),
            (x + t, y + h - t, w - 2 * t, t, segs[3]),
            (x, y + half_h, t, h - half_h - t, segs[4]),
            (x, y + t, t, half_h - t, segs[5]),
            (x + t, y + half_h - t // 2, w - 2 * t, t, segs[6]),
        ]
        for sx, sy, sw, sh, on in defs:
            c = active_color if on else ghost_color
            if c is not None and c.alpha() > 0:
                painter.fillRect(int(sx), int(sy), int(sw), int(sh), c)
        return w

    def draw_seven_segment_string(self, painter, text, x, y, char_w, char_h, char_gap, active_color, ghost_color):
        """Draw a text string using seven-segment glyphs."""
        curr_x = x
        for ch in text:
            w_used = self.draw_seven_segment_char(painter, ch, curr_x, y, char_w, char_h, active_color, ghost_color)
            curr_x += w_used + char_gap
        return curr_x

    def draw_lcd_readout_horizontal(self, painter, start_x, y, level_l, peak_l, level_r, peak_r):
        """Render live and peak dB readouts in horizontal mode."""
        active_color, ghost_color = self.get_lcd_colors()
        def format_db(level):
            if level <= 0.001:
                return "-INF"
            db = (level * 50.0) - 50.0
            return f"{db:+.1f}" if db > -49.95 else "-INF"

        str_l = f"L {format_db(level_l)}  PK {format_db(peak_l)}"
        str_r = f"R {format_db(level_r)}  PK {format_db(peak_r)}"

        if self.lcd_seven_segment:
            cw = int(7 * self.size_scale)
            ch = int(12 * self.size_scale)
            cgap = int(2 * self.size_scale)

            painter.setPen(active_color)
            font = painter.font()
            font.setBold(True)
            font.setPointSizeF(max(6.0, 7.5 * self.size_scale))
            painter.setFont(font)

            painter.drawText(start_x, y + ch, "L:")
            x_l = start_x + int(16 * self.size_scale)
            x_l = self.draw_seven_segment_string(painter, format_db(level_l), x_l, y, cw, ch, cgap, active_color, ghost_color)
            painter.drawText(x_l + int(6 * self.size_scale), y + ch, "PK:")
            x_pk_l = x_l + int(28 * self.size_scale)
            self.draw_seven_segment_string(painter, format_db(peak_l), x_pk_l, y, cw, ch, cgap, active_color, ghost_color)

            x_r_start = x_pk_l + int(48 * self.size_scale)
            painter.drawText(x_r_start, y + ch, "R:")
            x_r = x_r_start + int(16 * self.size_scale)
            x_r = self.draw_seven_segment_string(painter, format_db(level_r), x_r, y, cw, ch, cgap, active_color, ghost_color)
            painter.drawText(x_r + int(6 * self.size_scale), y + ch, "PK:")
            x_pk_r = x_r + int(28 * self.size_scale)
            self.draw_seven_segment_string(painter, format_db(peak_r), x_pk_r, y, cw, ch, cgap, active_color, ghost_color)
        else:
            painter.setPen(active_color)
            font = painter.font()
            font.setBold(True)
            font.setPointSizeF(max(6.5, 8.0 * self.size_scale))
            painter.setFont(font)
            full_text = f"{str_l}     {str_r}"
            painter.drawText(start_x, y + int(11 * self.size_scale), full_text)

    def draw_lcd_readout_vertical(self, painter, x_start, y, col_w, level_l, peak_l, level_r, peak_r):
        """Render live and peak dB readouts in vertical mode."""
        active_color, ghost_color = self.get_lcd_colors()
        def format_db(level):
            if level <= 0.001:
                return "-INF"
            db = (level * 50.0) - 50.0
            return f"{db:+.1f}" if db > -49.95 else "-INF"

        if self.lcd_seven_segment:
            cw = int(6 * self.size_scale)
            ch = int(10 * self.size_scale)
            cgap = int(1.5 * self.size_scale)

            self.draw_seven_segment_string(painter, format_db(level_l), x_start, y, cw, ch, cgap, active_color, ghost_color)
            x_r = x_start + int(40 * self.size_scale)
            self.draw_seven_segment_string(painter, format_db(level_r), x_r, y, cw, ch, cgap, active_color, ghost_color)

            y_pk = y + ch + int(3 * self.size_scale)
            self.draw_seven_segment_string(painter, format_db(peak_l), x_start, y_pk, cw, ch, cgap, active_color, ghost_color)
            self.draw_seven_segment_string(painter, format_db(peak_r), x_r, y_pk, cw, ch, cgap, active_color, ghost_color)
        else:
            painter.setPen(active_color)
            font = painter.font()
            font.setBold(True)
            font.setPointSizeF(max(6.0, 7.5 * self.size_scale))
            painter.setFont(font)
            painter.drawText(x_start, y + int(8 * self.size_scale), f"L:{format_db(level_l)}")
            painter.drawText(x_start + int(40 * self.size_scale), y + int(8 * self.size_scale), f"R:{format_db(level_r)}")
            painter.drawText(x_start, y + int(18 * self.size_scale), f"P:{format_db(peak_l)}")
            painter.drawText(x_start + int(40 * self.size_scale), y + int(18 * self.size_scale), f"P:{format_db(peak_r)}")

    def draw_scale_horizontal(self, painter, start_x, y):
        """Draw dB tick marks and numerical labels for horizontal meter layout."""
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
        """Draw dB tick marks and numerical labels for vertical tower layout."""
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
        """Draw a single horizontal LED bar with active LEDs, peak indicator, and styling."""
        if self.theme_id == 5:
            painter.setPen(QColor(102, 192, 204))
        elif self.theme_id == 6:
            painter.setPen(QColor(51, 255, 102))
        elif self.theme_id == 7:
            painter.setPen(QColor(255, 176, 0))
        else:
            painter.setPen(QColor(255, 255, 255))
        font = painter.font()
        font.setBold(True)
        font.setPointSizeF(font.pointSizeF() * self.size_scale)
        painter.setFont(font)
        painter.drawText(int(15 * self.size_scale), y_offset + int(14 * self.size_scale), label)
        
        if self.theme_id in (6, 7):
            gap = self.lcd_segment_gap * self.size_scale
        else:
            gap = 2 * self.size_scale

        led_w = 11 * self.size_scale
        led_h = 18 * self.size_scale
        start_x = 35 * self.size_scale

        if self.theme_id in (6, 7):
            active_color, ghost_color = self.get_lcd_colors()
            active_leds = int(level * self.num_leds)
            peak_led = int(peak * self.num_leds)

            for i in range(self.num_leds):
                x = start_x + i * (led_w + gap)
                is_on = i < active_leds

                if is_on:
                    painter.setBrush(active_color)
                else:
                    painter.setBrush(ghost_color if self.fx_ghost else QColor(0, 0, 0, 0))

                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

                if is_on and self.fx_glow:
                    glow_c = QColor(active_color)
                    glow_c.setAlpha(int(20 + level * 40))
                    painter.setBrush(glow_c)
                    painter.drawRect(int(x - 1), int(y_offset - 1), int(led_w + 2), int(led_h + 2))
                    painter.setBrush(active_color)
                    painter.drawRect(int(x), int(y_offset), int(led_w), int(led_h))

                if i == peak_led and peak_led > 0:
                    if self.peak_mode == 1 or self.peak_mode == 0:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(active_color)
                        painter.drawRect(int(x - 1), int(y_offset - 1), int(led_w + 1), int(led_h + 1))
                    elif self.peak_mode == 2:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 200, 0))
                        painter.drawRect(int(x - 1), int(y_offset - 1), int(led_w + 1), int(led_h + 1))
                    elif self.peak_mode == 3:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(0, 230, 255))
                        painter.drawRect(int(x - 1), int(y_offset - 1), int(led_w + 1), int(led_h + 1))
        elif self.theme_id == 5:
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
        """Draw a single vertical tower of LEDs with active LEDs, peak indicator, and styling."""
        if self.theme_id == 5:
            painter.setPen(QColor(102, 192, 204))
        elif self.theme_id == 6:
            painter.setPen(QColor(51, 255, 102))
        elif self.theme_id == 7:
            painter.setPen(QColor(255, 176, 0))
        else:
            painter.setPen(QColor(255, 255, 255))
        font = painter.font()
        font.setBold(True)
        font.setPointSizeF(font.pointSizeF() * self.size_scale)
        painter.setFont(font)
        painter.drawText(x_offset + int(col_w / 2 - 4 * self.size_scale), int(16 * self.size_scale), label)

        if self.theme_id in (6, 7):
            gap = self.lcd_segment_gap * self.size_scale
        else:
            gap = 3 * self.size_scale

        led_w = col_w
        led_h = 10 * self.size_scale
        start_y = 22 * self.size_scale

        if self.theme_id in (6, 7):
            active_color, ghost_color = self.get_lcd_colors()
            active_leds = int(level * self.num_leds)
            peak_led = int(peak * self.num_leds)

            for idx_from_bottom in range(self.num_leds):
                idx_from_top = self.num_leds - 1 - idx_from_bottom
                y = start_y + idx_from_top * (led_h + gap)
                is_on = idx_from_bottom < active_leds

                if is_on:
                    painter.setBrush(active_color)
                else:
                    painter.setBrush(ghost_color if self.fx_ghost else QColor(0, 0, 0, 0))

                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))

                if is_on and self.fx_glow:
                    glow_c = QColor(active_color)
                    glow_c.setAlpha(int(20 + level * 40))
                    painter.setBrush(glow_c)
                    painter.drawRect(int(x_offset - 1), int(y - 1), int(led_w + 2), int(led_h + 2))
                    painter.setBrush(active_color)
                    painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))

                if idx_from_bottom == peak_led and peak_led > 0:
                    if self.peak_mode == 1 or self.peak_mode == 0:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(active_color)
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    elif self.peak_mode == 2:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(255, 200, 0))
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
                    elif self.peak_mode == 3:
                        painter.setBrush(Qt.BrushStyle.NoBrush)
                        painter.setPen(QColor(0, 230, 255))
                        painter.drawRect(int(x_offset), int(y), int(led_w), int(led_h))
        elif self.theme_id == 5:
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
