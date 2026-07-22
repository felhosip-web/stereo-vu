import re

with open("linux/stereo_vu.py", "r") as f:
    content = f.read()

# Add themes to combo box in SettingsDialog
theme_combo_pattern = r'self\.theme_combo\.addItems\(\["Klasszikus \(Zöld-Narancs-Piros\)", \n                                  "Cyberpunk \(Neon Kék-Rózsaszín\)",\n                                  "Tűz \(Sárga-Narancs-Vörös\)"\]\)'
new_theme_combo = 'self.theme_combo.addItems(["Klasszikus (Zöld-Narancs-Piros)", "Cyberpunk (Neon Kék-Rózsaszín)", "Tűz (Sárga-Narancs-Vörös)", "Jég (Türkiz-Kék-Fehér)", "Naplemente (Arany-Bíbor)", "VFD (Klasszikus Cián)"])'
content = re.sub(theme_combo_pattern, new_theme_combo, content, flags=re.DOTALL)

# Also there's no layout orientation setting in Linux app, but it is already horizontal!
# Wait, the python code actually draws it horizontally. Look:
# start_x + i * (led_w + gap)
# It draws horizontal bars like a stereo rack! So horizontal is already the ONLY layout in python app.

# Let's add the VFD theme to the python app's drawing code.
theme_if_pattern = r'if self\.theme_id == 1:.*?if is_red: base_color = QColor\(255, 50, 50\)\n                elif is_yellow: base_color = QColor\(255, 200, 50\)\n                else: base_color = QColor\(0, 255, 102\)'

new_theme_if = """if self.theme_id == 1:
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
            elif self.theme_id == 5: # VFD
                base_color = QColor(0, 229, 255)
            else:
                if is_red: base_color = QColor(255, 50, 50)
                elif is_yellow: base_color = QColor(255, 200, 50)
                else: base_color = QColor(0, 255, 102)"""

content = re.sub(theme_if_pattern, new_theme_if, content, flags=re.DOTALL)

with open("linux/stereo_vu.py", "w") as f:
    f.write(content)

