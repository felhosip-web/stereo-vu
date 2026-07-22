package com.example.stereovu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val REQ_OVERLAY = 1001
    private val REQ_AUDIO = 1002
    private val REQ_CAPTURE = 1003
    private var mediaProjectionManager: MediaProjectionManager? = null
    private lateinit var prefs: SharedPreferences

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
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Főcím
        val titleText = TextView(this).apply {
            text = "Stereo VU Beállítások"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Alcím
        val subText = TextView(this).apply {
            text = "Szabd személyre a lebegő hangszintmérő működését és megjelenését valós időben!"
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        container.addView(subText)

        val layoutParamsMatch = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 16)
        }

        // 0. Üzemmód / Ballisztika
        val modeLabel = TextView(this).apply {
            text = "Kijelző Üzemmód (Ballisztika)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        container.addView(modeLabel)

        val modes = arrayOf(
            "Digitális Peak (Gyors, pontos)",
            "Analóg VU (Lassú, tehetetlenség)",
            "PPM (Gyors felfutás, lassú lecsengés)",
            "Egyéni (Csúszkák alapján)"
        )
        val modeSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, modes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            setSelection(prefs.getInt("mode", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("mode", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#252525"))
            setPadding(24, 24, 24, 24)
            layoutParams = layoutParamsMatch
        }
        container.addView(modeSpinner)

        // 1. Lecsengési tehetetlenség (Decay)
        addSlider(
            container,
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

        // 1b. Felfutás (Attack)
        addSlider(
            container,
            layoutParamsMatch,
            labelPrefix = "Egyéni Felfutás (Attack)",
            minVal = 0.05f,
            maxVal = 1.00f,
            defaultVal = 0.35f,
            prefKey = "attack"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        // 1c. LED-ek száma
        addIntSlider(
            container,
            layoutParamsMatch,
            labelPrefix = "LED-ek száma",
            minVal = 10,
            maxVal = 40,
            defaultVal = 20,
            prefKey = "led_count"
        )

        // 1d. Amplitude Count (Referencia szint)
        addIntSlider(
            container,
            layoutParamsMatch,
            labelPrefix = "Amplitude Count (Max jelszint)",
            minVal = 2000,
            maxVal = 32767,
            defaultVal = 32767,
            prefKey = "amplitude_count"
        )

        // 1e. Szigorú Aluláteresztő Szűrő (LPF)
        val lpfCheckbox = CheckBox(this).apply {
            text = "Szigorú Digitális Aluláteresztő Szűrő (LPF)"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            isChecked = prefs.getBoolean("use_lpf", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("use_lpf", isChecked).apply()
            }
            layoutParams = layoutParamsMatch
        }
        container.addView(lpfCheckbox)

        // 2. Érzékenység (Gain)
        addSlider(
            container,
            layoutParamsMatch,
            labelPrefix = "Érzékenység / Erősítés",
            minVal = 0.2f,
            maxVal = 3.0f,
            defaultVal = 1.0f,
            prefKey = "gain"
        ) { v ->
            java.lang.String.format(Locale.US, "%.1fx", v)
        }

        // 3. Kijelző mérete (Size)
        addSlider(
            container,
            layoutParamsMatch,
            labelPrefix = "Kijelző mérete",
            minVal = 0.6f,
            maxVal = 1.8f,
            defaultVal = 1.0f,
            prefKey = "size_scale"
        ) { v ->
            "${(v * 100).toInt()}%"
        }

        // 4. Háttér átlátszósága (Opacity)
        val currentOpacity = prefs.getInt("opacity", 204)
        val opacityLabel = TextView(this).apply {
            text = "Háttér sötétsége: ${(currentOpacity.toFloat() / 255f * 100).toInt()}%"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        container.addView(opacityLabel)

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
        container.addView(opacitySeekBar)

        // 5. Színséma (Color Theme)
        val orientLabel = TextView(this).apply {
            text = "Kijelző Iránya"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        container.addView(orientLabel)

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
            setBackgroundColor(Color.parseColor("#252525"))
            setPadding(24, 24, 24, 24)
            layoutParams = layoutParamsMatch
        }
        container.addView(orientSpinner)

        val themeLabel = TextView(this).apply {
            text = "Színséma kiválasztása"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        container.addView(themeLabel)

        val themes = arrayOf(
            "Klasszikus (Zöld-Narancs-Piros)",
            "Cyberpunk (Neon Kék-Rózsaszín)",
            "Tűz (Sárga-Narancs-Vörös)",
            "Jég (Türkiz-Kék-Fehér)",
            "Naplemente (Arany-Bíbor)",
            "VFD (Klasszikus Cián)"
        )
        val spinner = Spinner(this).apply {
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
            setBackgroundColor(Color.parseColor("#252525"))
            setPadding(24, 24, 24, 24)
            layoutParams = layoutParamsMatch
        }
        container.addView(spinner)

        // 6. Peak LED színe - JAVÍTOTT 4 opció
        val peakLabel = TextView(this).apply {
            text = "Peak LED színe"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 32, 0, 8)
        }
        container.addView(peakLabel)

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
            // backward compat: régi bool -> új int
            val oldMode = if (prefs.contains("peak_mode")) prefs.getInt("peak_mode", 0)
                          else if (prefs.getBoolean("colored_peak", false)) 1 else 0
            setSelection(oldMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("peak_mode", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setBackgroundColor(Color.parseColor("#252525"))
            setPadding(24, 24, 24, 24)
            layoutParams = layoutParamsMatch
        }
        container.addView(peakSpinner)

        // Elválasztó tér
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 32)
        }
        container.addView(spacer)

        // Gombok layout paraméterei (teljes szélesség)
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }

        // Indítás gomb
        val btnStart = Button(this).apply {
            text = "Stereo VU indítása"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00C853")) // Anyag zöld
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
            setBackgroundColor(Color.parseColor("#D50000")) // Anyag piros
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
            textSize = 15f
            setPadding(0, 16, 0, 8)
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

    private fun addIntSlider(
        container: LinearLayout,
        layoutParamsMatch: LinearLayout.LayoutParams,
        labelPrefix: String,
        minVal: Int,
        maxVal: Int,
        defaultVal: Int,
        prefKey: String
    ) {
        val currentVal = prefs.getInt(prefKey, defaultVal)
        val label = TextView(this).apply {
            text = "$labelPrefix: $currentVal"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        container.addView(label)
        val seekBar = SeekBar(this).apply {
            max = maxVal - minVal
            progress = currentVal - minVal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = minVal + progress
                    prefs.edit().putInt(prefKey, v).apply()
                    label.text = "$labelPrefix: $v"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = layoutParamsMatch
        }
        container.addView(seekBar)
    }

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        requestCapture()
    }

    private fun requestCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = mediaProjectionManager?.createScreenCaptureIntent()
            if (intent != null) {
                startActivityForResult(intent, REQ_CAPTURE)
            } else {
                startVuService(null)
            }
        } else {
            startVuService(null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) checkPermissions()
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                startVuService(data)
            } else {
                Toast.makeText(this, "Belső hang engedély kell a Bluetooth-hoz!", Toast.LENGTH_LONG).show()
                startVuService(null)
            }
        }
    }

    private fun startVuService(captureIntent: Intent?) {
        val svc = Intent(this, FloatingVuService::class.java).apply {
            putExtra("captureIntent", captureIntent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        Toast.makeText(this, "VU elindítva! Most nyisd meg a YouTube-ot", Toast.LENGTH_LONG).show()
    }
}