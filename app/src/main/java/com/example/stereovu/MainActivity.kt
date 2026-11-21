package com.example.stereovu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val REQ_OVERLAY = 1001
    private val REQ_AUDIO = 1002
    private val REQ_CAPTURE = 1003
    private var pendingCaptureSource: AudioCaptureSource? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        // Sötét tónusú root konténer (gördíthető)
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Főcím
        val titleText = TextView(this).apply {
            text = "Stereo VU Beállítások"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 6)
        }
        container.addView(titleText)

        // Alcím
        val subText = TextView(this).apply {
            text = "Szabd személyre a lebegő hangszintmérő működését és megjelenését valós időben!"
            setTextColor(Color.parseColor("#999999"))
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        container.addView(subText)

        val layoutParamsMatch = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 16)
        }

        // Helper a szekciók kártyás kialakításához
        fun createSectionCard(title: String, subtitle: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 28, 32, 28)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C1C1E"))
                    cornerRadius = 24f
                    setStroke(2, Color.parseColor("#2C2C2E"))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
            }

            val cardTitle = TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            card.addView(cardTitle)

            if (subtitle.isNotEmpty()) {
                val cardSubtitle = TextView(this).apply {
                    text = subtitle
                    setTextColor(Color.parseColor("#8E8E93"))
                    textSize = 12f
                    setPadding(0, 0, 0, 16)
                }
                card.addView(cardSubtitle)
            }

            return card
        }

        // =========================================================================
        // Szekció 1: Detektálás és Ballisztika (Detection & Ballistics)
        // =========================================================================
        val sec1 = createSectionCard("1. Detektálás és Ballisztika", "Hangszint számítási mód, RMS ablak és fizikai viselkedés")

        val detLabel = TextView(this).apply {
            text = "Detektálási mód (Detection mode)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 4, 0, 6)
        }
        sec1.addView(detLabel)

        val detModes = arrayOf(
            "Peak (Élénk, közvetlen csúcsérték)",
            "RMS (Effektív érték, zenei ablak)"
        )
        val detSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, detModes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            setSelection(prefs.getInt("detection_mode", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("detection_mode", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#2A2A2D"))
            setPadding(20, 20, 20, 20)
            layoutParams = layoutParamsMatch
        }
        sec1.addView(detSpinner)

        // RMS Window (ms) slider
        addIntSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "RMS Időablak (RMS Window)",
            minVal = 50,
            maxVal = 1000,
            defaultVal = 350,
            prefKey = "rms_window_ms",
            unit = "ms"
        )

        // Ballisztika mód
        val modeLabel = TextView(this).apply {
            text = "Kijelző Üzemmód (Ballisztika)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 8, 0, 6)
        }
        sec1.addView(modeLabel)

        val modes = arrayOf(
            "Digitális Peak (Gyors, pontos)",
            "Analóg VU (Rugós tehetetlenség)",
            "PPM (Gyors felfutás, lassú lecsengés)",
            "True Peak (Inter-sample csúcsérték)",
            "Classic VU (300 ms integráció, ±1 dB)",
            "BBC PPM (Lépcsős / több-fázisú lecsengés)",
            "Nordic / DIN (Gyors attack, egyedi decay)",
            "Egyéni (Csúszkák alapján)"
        )
        val initialMode = BallisticsModeMigration.getMigratedMode(prefs)
        val modeSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, modes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            setSelection(initialMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("mode", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#2A2A2D"))
            setPadding(20, 20, 20, 20)
            layoutParams = layoutParamsMatch
        }
        sec1.addView(modeSpinner)

        // Attack & Decay egyéni módhoz
        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Egyéni Felfutás (Attack)",
            minVal = 0.05f,
            maxVal = 1.00f,
            defaultVal = 0.35f,
            prefKey = "attack"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Egyéni Lecsengés (Decay)",
            minVal = 0.70f,
            maxVal = 0.98f,
            defaultVal = 0.88f,
            prefKey = "decay"
        ) { v ->
            when {
                v < 0.80f -> "Gyors / Reagáló (${(v*100).toInt()}%)"
                v < 0.90f -> "Közepes / Ajánlott (${(v*100).toInt()}%)"
                v < 0.95f -> "Lassú / Sima (${(v*100).toInt()}%)"
                else -> "Nagyon sima (${(v*100).toInt()}%)"
            }
        }

        // Célérték elengedés
        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Célérték elengedés (Release rate)",
            minVal = 0.88f,
            maxVal = 0.97f,
            defaultVal = 0.94f,
            prefKey = "target_decay"
        ) { v ->
            java.lang.String.format(Locale.US, "%.3f", v)
        }

        // Peak Hold time
        addLongSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Peak tartási idő (Peak Hold)",
            minVal = 200L,
            maxVal = 3000L,
            defaultVal = 900L,
            prefKey = "peak_hold_ms",
            unit = "ms"
        )

        // Peak decay rate
        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Peak lecsengés (Peak decay)",
            minVal = 0.900f,
            maxVal = 0.995f,
            defaultVal = 0.985f,
            prefKey = "peak_decay"
        ) { v ->
            java.lang.String.format(Locale.US, "%.3f", v)
        }

        val peakNeverBelowCheckbox = CheckBox(this).apply {
            text = "Peak soha nem megy a sáv alá (Peak never below bar)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("peak_never_below_bar", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("peak_never_below_bar", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec1.addView(peakNeverBelowCheckbox)

        // Analóg rugó & csillapítás
        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Analóg Rugó (Spring)",
            minVal = 0.05f,
            maxVal = 0.40f,
            defaultVal = 0.15f,
            prefKey = "analog_spring"
        ) { v ->
            java.lang.String.format(Locale.US, "%.2f", v)
        }

        addSlider(
            sec1,
            layoutParamsMatch,
            labelPrefix = "Analóg Csillapítás (Damp)",
            minVal = 0.10f,
            maxVal = 0.40f,
            defaultVal = 0.25f,
            prefKey = "analog_damp"
        ) { v ->
            java.lang.String.format(Locale.US, "%.2f", v)
        }

        val lpfCheckbox = CheckBox(this).apply {
            text = "Szigorú Digitális Aluláteresztő Szűrő (LPF)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("use_lpf", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("use_lpf", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec1.addView(lpfCheckbox)

        container.addView(sec1)

        // =========================================================================
        // Szekció 2: Látvány effektusok (Visual Effects)
        // =========================================================================
        val sec2 = createSectionCard("2. Látvány effektusok", "Ragyogás, csillanás, ghost LED-ek és skálajelzések")

        val glowCheckbox = CheckBox(this).apply {
            text = "LED Ragyogás (Glow effektek)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("fx_glow", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("fx_glow", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec2.addView(glowCheckbox)

        val sheenCheckbox = CheckBox(this).apply {
            text = "Belső Fény / Csillanás (Sheen highlight)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("fx_sheen", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("fx_sheen", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec2.addView(sheenCheckbox)

        val ghostCheckbox = CheckBox(this).apply {
            text = "Halvány inaktív LED-ek (Ghost LEDs)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("fx_ghost", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("fx_ghost", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec2.addView(ghostCheckbox)

        val dbScaleCheckbox = CheckBox(this).apply {
            text = "dB Skála feliratok megjelenítése"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("fx_db_scale", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("fx_db_scale", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec2.addView(dbScaleCheckbox)

        container.addView(sec2)

        // =========================================================================
        // Szekció 3: Megjelenés és Elrendezés (Appearance & Layout)
        // =========================================================================
        val sec3 = createSectionCard("3. Megjelenés és Elrendezés", "Témák, LED darabszám, tájolás, méret és átlátszóság")

        val themeLabel = TextView(this).apply {
            text = "Színséma kiválasztása"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 4, 0, 6)
        }
        sec3.addView(themeLabel)

        val themes = arrayOf(
            "Klasszikus (Zöld-Narancs-Piros)",
            "Cyberpunk (Neon Kék-Rózsaszín)",
            "Tűz (Sárga-Narancs-Vörös)",
            "Jég (Türkiz-Kék-Fehér)",
            "Naplemente (Arany-Bíbor)",
            "VFD (Klasszikus Cián)",
            "LCD Green (Zöld LCD)",
            "LCD Amber (Borostyán LCD)"
        )
        val themeSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, themes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            setSelection(prefs.getInt("theme", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("theme", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#2A2A2D"))
            setPadding(20, 20, 20, 20)
            layoutParams = layoutParamsMatch
        }
        sec3.addView(themeSpinner)

        val orientLabel = TextView(this).apply {
            text = "Kijelző Iránya (Elrendezés)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 8, 0, 6)
        }
        sec3.addView(orientLabel)

        val orients = arrayOf("Függőleges (Vertical)", "Vízszintes (Horizontal)")
        val orientSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, orients).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            setSelection(if (prefs.getBoolean("horizontal", false)) 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putBoolean("horizontal", position == 1).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#2A2A2D"))
            setPadding(20, 20, 20, 20)
            layoutParams = layoutParamsMatch
        }
        sec3.addView(orientSpinner)

        addIntSlider(
            sec3,
            layoutParamsMatch,
            labelPrefix = "LED-ek száma",
            minVal = 10,
            maxVal = 40,
            defaultVal = 20,
            prefKey = "led_count"
        )

        addSlider(
            sec3,
            layoutParamsMatch,
            labelPrefix = "Kijelző mérete",
            minVal = 0.6f,
            maxVal = 1.8f,
            defaultVal = 1.0f,
            prefKey = "size_scale"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        val currentOpacity = prefs.getInt("opacity", 204)
        val opacityLabel = TextView(this).apply {
            text = "Háttér sötétsége: ${(currentOpacity.toFloat() / 255f * 100).toInt()}%"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        sec3.addView(opacityLabel)

        val opacitySeekBar = SeekBar(this).apply {
            max = 255
            progress = currentOpacity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    prefs.edit().putInt("opacity", progress).apply()
                    opacityLabel.text = "Háttér sötétsége: ${(progress.toFloat() / 255f * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = layoutParamsMatch
        }
        sec3.addView(opacitySeekBar)

        val peakLabel = TextView(this).apply {
            text = "Peak LED stílusa / színe"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 12, 0, 6)
        }
        sec3.addView(peakLabel)

        val peakOptions = arrayOf(
            "Fehér (klasszikus)",
            "Színes (LED színével egyező)",
            "Fix sárga (jól látható)",
            "Fix cián (minden témán látszik)"
        )
        val peakSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, peakOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            val oldMode = if (prefs.contains("peak_mode")) prefs.getInt("peak_mode", 0)
                          else if (prefs.getBoolean("colored_peak", false)) 1 else 0
            setSelection(oldMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("peak_mode", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#2A2A2D"))
            setPadding(20, 20, 20, 20)
            layoutParams = layoutParamsMatch
        }
        sec3.addView(peakSpinner)

        // LCD Megjelenés (LCD Appearance) - Érvényes: LCD Green és LCD Amber témákra
        val lcdTitle = TextView(this).apply {
            text = "LCD megjelenés (LCD Green & Amber témákhoz)"
            setTextColor(Color.parseColor("#33FF66"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 8)
        }
        sec3.addView(lcdTitle)

        addSlider(
            sec3,
            layoutParamsMatch,
            labelPrefix = "Ghost erősség (Ghost strength)",
            minVal = 0.0f,
            maxVal = 1.0f,
            defaultVal = 0.55f,
            prefKey = "lcd_ghost_strength"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        addSlider(
            sec3,
            layoutParamsMatch,
            labelPrefix = "Szegmens rés (Segment gap)",
            minVal = 1.0f,
            maxVal = 6.0f,
            defaultVal = 2.0f,
            prefKey = "lcd_segment_gap"
        ) { v ->
            java.lang.String.format(Locale.US, "%.1f px", v)
        }

        val sevenSegCheckbox = CheckBox(this).apply {
            text = "Hétszegmenses kijelzés (Seven-segment readouts)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            isChecked = prefs.getBoolean("lcd_seven_segment", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("lcd_seven_segment", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        sec3.addView(sevenSegCheckbox)

        addSlider(
            sec3,
            layoutParamsMatch,
            labelPrefix = "LCD Kontraszt (LCD contrast)",
            minVal = 0.20f,
            maxVal = 1.0f,
            defaultVal = 0.80f,
            prefKey = "lcd_contrast"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        container.addView(sec3)

        // =========================================================================
        // Szekció 4: Általános és Audió (General / Audio)
        // =========================================================================
        val sec4 = createSectionCard("4. Általános és Audió", "Érzékenység és jelszint beállítások")

        addSlider(
            sec4,
            layoutParamsMatch,
            labelPrefix = "Érzékenység / Erősítés (Gain)",
            minVal = 0.01f,
            maxVal = 3.0f,
            defaultVal = 1.0f,
            prefKey = "gain"
        ) { v ->
            java.lang.String.format(Locale.US, "%.2fx", v)
        }

        addIntSlider(
            sec4,
            layoutParamsMatch,
            labelPrefix = "Amplitude Count (Referencia jelszint)",
            minVal = 2000,
            maxVal = 32767,
            defaultVal = 32767,
            prefKey = "amplitude_count"
        )

        val sourceLabel = TextView(this).apply {
            text = "Hangforrás"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        sec4.addView(sourceLabel)

        val internalSourceId = View.generateViewId()
        val microphoneSourceId = View.generateViewId()
        val sourceGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply {
                id = internalSourceId
                text = "Belső lejátszás hangja (alapértelmezett)"
                setTextColor(Color.parseColor("#E0E0E0"))
            })
            addView(RadioButton(this@MainActivity).apply {
                id = microphoneSourceId
                text = "Mikrofon (csak kifejezett választásra)"
                setTextColor(Color.parseColor("#E0E0E0"))
            })
            layoutParams = layoutParamsMatch
        }
        val savedSource = AudioCaptureSource.fromPreferenceValue(
            prefs.getString("audio_capture_source", AudioCaptureSource.INTERNAL_PLAYBACK.preferenceValue)
        ) ?: AudioCaptureSource.INTERNAL_PLAYBACK
        sourceGroup.check(
            if (savedSource == AudioCaptureSource.MICROPHONE) microphoneSourceId else internalSourceId
        )
        sourceGroup.setOnCheckedChangeListener { _, checkedId ->
            val source = when (checkedId) {
                internalSourceId -> AudioCaptureSource.INTERNAL_PLAYBACK
                microphoneSourceId -> AudioCaptureSource.MICROPHONE
                else -> return@setOnCheckedChangeListener
            }
            prefs.edit().putString("audio_capture_source", source.preferenceValue).apply()
        }
        sec4.addView(sourceGroup)

        container.addView(sec4)

        // Gombok layout paraméterei
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 12, 0, 12)
        }

        // Indítás gomb
        val btnStart = Button(this).apply {
            text = "Stereo VU indítása"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00C853"))
            setPadding(32, 24, 32, 24)
            setOnClickListener { checkPermissions() }
            layoutParams = buttonParams
        }
        container.addView(btnStart)

        // Leállítás gomb
        val btnStop = Button(this).apply {
            text = "Lebegő ablak leállítása"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D50000"))
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingVuService::class.java))
                Toast.makeText(this@MainActivity, "Kijelző leállítva", Toast.LENGTH_SHORT).show()
            }
            layoutParams = buttonParams
        }
        container.addView(btnStop)

        root.addView(container)
        setContentView(root)
    }

    private fun addSlider(
        container: LinearLayout,
        layoutParamsMatch: LinearLayout.LayoutParams,
        labelPrefix: String,
        minVal: Float,
        maxVal: Float,
        defaultVal: Float,
        prefKey: String,
        valueFormatter: (Float) -> String
    ) {
        val currentVal = prefs.getFloat(prefKey, defaultVal)
        
        val label = TextView(this).apply {
            text = "$labelPrefix: ${valueFormatter(currentVal)}"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        container.addView(label)

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = (((currentVal - minVal) / (maxVal - minVal)) * 100).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = minVal + (progress.toFloat() / 100f) * (maxVal - minVal)
                    prefs.edit().putFloat(prefKey, v).apply()
                    label.text = "$labelPrefix: ${valueFormatter(v)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = layoutParamsMatch
        }
        container.addView(seekBar)
    }

    private fun addLongSlider(
        container: LinearLayout,
        layoutParamsMatch: LinearLayout.LayoutParams,
        labelPrefix: String,
        minVal: Long,
        maxVal: Long,
        defaultVal: Long,
        prefKey: String,
        unit: String
    ) {
        val currentVal = prefs.getLong(prefKey, defaultVal)
        val label = TextView(this).apply {
            text = "$labelPrefix: $currentVal $unit"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        container.addView(label)
        val seekBar = SeekBar(this).apply {
            max = (maxVal - minVal).toInt()
            progress = (currentVal - minVal).toInt().coerceIn(0, (maxVal - minVal).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = minVal + progress.toLong()
                    prefs.edit().putLong(prefKey, v).apply()
                    label.text = "$labelPrefix: $v $unit"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = layoutParamsMatch
        }
        container.addView(seekBar)
    }

    private fun addIntSlider(
        container: LinearLayout,
        layoutParamsMatch: LinearLayout.LayoutParams,
        labelPrefix: String,
        minVal: Int,
        maxVal: Int,
        defaultVal: Int,
        prefKey: String,
        unit: String = ""
    ) {
        val currentVal = prefs.getInt(prefKey, defaultVal)
        val label = TextView(this).apply {
            text = if (unit.isNotEmpty()) "$labelPrefix: $currentVal $unit" else "$labelPrefix: $currentVal"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        container.addView(label)
        val seekBar = SeekBar(this).apply {
            max = maxVal - minVal
            progress = currentVal - minVal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = minVal + progress
                    prefs.edit().putInt(prefKey, v).apply()
                    label.text = if (unit.isNotEmpty()) "$labelPrefix: $v $unit" else "$labelPrefix: $v"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = layoutParamsMatch
        }
        container.addView(seekBar)
    }

    private fun checkPermissions() {
        val source = selectedAudioSource()
        if (source == null) {
            showError(AudioCaptureRequestError.MISSING_SOURCE.userMessage)
            return
        }
        pendingCaptureSource = source

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        requestCapture(source)
    }

    private fun selectedAudioSource(): AudioCaptureSource? =
        AudioCaptureSource.fromPreferenceValue(
            prefs.getString("audio_capture_source", AudioCaptureSource.INTERNAL_PLAYBACK.preferenceValue)
        )

    private fun requestCapture(source: AudioCaptureSource) {
        if (source == AudioCaptureSource.MICROPHONE) {
            startVuService(source)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showError(AudioCaptureRequestError.INTERNAL_CAPTURE_UNSUPPORTED.userMessage)
            return
        }
        val manager = mediaProjectionManager
        if (manager == null) {
            Log.e(TAG, "MediaProjectionManager unavailable for internal audio capture")
            showError("A belső hangrögzítés szolgáltatása nem érhető el ezen az eszközön.")
            return
        }
        val intent = try {
            manager.createScreenCaptureIntent()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to create MediaProjection consent request", e)
            showError("Nem sikerült elindítani a belső hangrögzítés engedélykérését.")
            return
        }
        if (intent == null) {
            Log.e(TAG, "MediaProjection consent intent unavailable")
            showError("Nem sikerült elindítani a belső hangrögzítés engedélykérését.")
            return
        }
        startActivityForResult(intent, REQ_CAPTURE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_AUDIO) return

        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val source = pendingCaptureSource
            if (source == null) {
                showError(AudioCaptureRequestError.MISSING_SOURCE.userMessage)
            } else {
                requestCapture(source)
            }
        } else {
            Log.w(TAG, "RECORD_AUDIO permission denied")
            showError("A hangrögzítési engedély szükséges a kiválasztott hangforráshoz.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            checkPermissions()
            return
        }
        if (requestCode != REQ_CAPTURE) return

        if (resultCode != Activity.RESULT_OK) {
            Log.i(TAG, "MediaProjection consent denied (resultCode=$resultCode)")
            showError(AudioCaptureRequestError.PROJECTION_CONSENT_DENIED.userMessage)
            return
        }
        if (data == null) {
            Log.e(TAG, "MediaProjection returned RESULT_OK without result data")
            showError(AudioCaptureRequestError.PROJECTION_RESULT_MISSING.userMessage)
            return
        }
        Log.i(TAG, "MediaProjection consent granted; result data present")
        startVuService(AudioCaptureSource.INTERNAL_PLAYBACK, resultCode, data)
    }

    private fun startVuService(
        source: AudioCaptureSource,
        resultCode: Int? = null,
        captureIntent: Intent? = null
    ) {
        val svc = Intent(this, FloatingVuService::class.java).apply {
            putExtra(FloatingVuService.EXTRA_AUDIO_SOURCE, source.preferenceValue)
            resultCode?.let { putExtra(FloatingVuService.EXTRA_PROJECTION_RESULT_CODE, it) }
            captureIntent?.let { putExtra(FloatingVuService.EXTRA_CAPTURE_INTENT, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        Toast.makeText(this, "Hangforrás indítása: ${source.displayName}", Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
