package com.example.stereovu

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.*

class StereoVuView(context: Context) : FrameLayout(context) {
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    private var ledCount = 20
    private var levelL = 0f
    private var levelR = 0f
    private var targetL = 0f
    private var targetR = 0f
    private var velL = 0f
    private var velR = 0f
    private var peakL = 0f
    private var peakR = 0f
    private var peakHoldL = 0L
    private var peakHoldR = 0L
    private var peakAlphaL = 255f
    private var peakAlphaR = 255f

    private val prefs = context.getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        this@StereoVuView.post { loadPrefs() }
    }

    private var attackSpeed = 0.90f
    private var decaySpeed = 0.12f
    private var targetDecay = 0.94f
    private var peakHoldMs = 900L
    private var peakDecay = 0.985f
    private var analogSpring = 0.15f
    private var analogDamp = 0.25f
    private var mode = 0
    private var useLpf = false
    private var peakNeverBelowBar = true
    private var bbcStepTimerL = 0
    private var bbcStepTimerR = 0
    private var isHorizontal = false
    private var gain = 1.0f
    private var sizeScale = 1.0f
    private var opacity = 204
    private var themeId = 0
    private var peakMode = 0 // 0=white, 1=colored same as led, 2=amber fixed, 3=cyan fixed

    // Visual Effects & Readouts
    private var fxGlow = true
    private var fxSheen = true
    private var fxGhost = true
    private var fxDbScale = true
    private var showLiveDb = true
    private var showPeakDb = true
    private var lcdGhostStrength = 0.55f
    private var lcdContrast = 0.80f
    private var lcdSegmentGap = 2f
    private var lcdSevenSegment = true

    // LEDs
    private val paintGreen = Paint().apply { color = Color.parseColor("#00FF66") }
    private val paintYellow = Paint().apply { color = Color.parseColor("#FFAA00") }
    private val paintRed = Paint().apply { color = Color.parseColor("#FF2222") }
    private val paintOffGreen = Paint().apply { color = Color.parseColor("#082010") }
    private val paintOffYellow = Paint().apply { color = Color.parseColor("#1A1200") }
    private val paintOffRed = Paint().apply { color = Color.parseColor("#1A0808") }
    private val paintPeak = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val paintPeakFill = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintPeakCutout = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val paintGlow = Paint()
    private val paintSheen = Paint().apply { color = Color.argb(60, 255, 255, 255) }

    private val paintBezel = Paint().apply { color = Color.parseColor("#1E1E1E") }
    private val paintBezelBorder = Paint().apply { color = Color.parseColor("#333333"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintGroove = Paint().apply { color = Color.parseColor("#0F0F0F") }
    private val paintChanLabel = Paint().apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val paintScaleTick = Paint().apply { color = Color.parseColor("#666666"); strokeWidth = 2f }
    private val paintScaleText = Paint().apply { color = Color.parseColor("#888888"); textSize = 16f }
    private val paintReadout = Paint().apply { color = Color.WHITE; textSize = 14f; typeface = Typeface.MONOSPACE }
    private val paintRackScrew = Paint().apply { color = Color.parseColor("#444444"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val paintRackScrewFill = Paint().apply { color = Color.parseColor("#222222"); style = Paint.Style.FILL }

    private val colW = 70f
    private val ledH = 12f
    private val ledGap = 4f
    private val topY = 36f
    private val xL = 12f
    private val xR = xL + colW + 16f
    private val xScale = xR + colW + 8f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var lastClickTime = 0L

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.argb(opacity, 0, 0, 0))
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadPrefs()

        // 60fps decay + peak fade loop - EZ MOZGATJA A KIJELZÉST
        postDelayed(object : Runnable {
            override fun run() {
                if (mode == 1 || mode == 4) {
                    // 1: Analog VU (~300 ms integration feel, spring/damp)
                    // 4: Classic VU (300 ms integration, smoother ±1 dB style ballistics)
                    velL += (targetL - levelL) * analogSpring
                    velL *= (1f - analogDamp)
                    levelL += velL

                    velR += (targetR - levelR) * analogSpring
                    velR *= (1f - analogDamp)
                    levelR += velR
                } else if (mode == 5) {
                    // BBC PPM: Characteristic stepped / multi-stage release (classic British PPM feel)
                    // Attack is fast (~10ms)
                    if (targetL > levelL) {
                        levelL += (targetL - levelL) * attackSpeed
                        bbcStepTimerL = 0
                    } else {
                        // Multi-stage / stepped release: quantize fall in small discrete stair steps every few frames
                        bbcStepTimerL++
                        if (bbcStepTimerL >= 3) {
                            val drop = (levelL * 0.05f).coerceAtLeast(0.008f)
                            levelL = (levelL - drop).coerceAtLeast(targetL)
                            bbcStepTimerL = 0
                        }
                    }

                    if (targetR > levelR) {
                        levelR += (targetR - levelR) * attackSpeed
                        bbcStepTimerR = 0
                    } else {
                        bbcStepTimerR++
                        if (bbcStepTimerR >= 3) {
                            val drop = (levelR * 0.05f).coerceAtLeast(0.008f)
                            levelR = (levelR - drop).coerceAtLeast(targetR)
                            bbcStepTimerR = 0
                        }
                    }
                } else if (useLpf) {
                    // Szigorú digitális aluláteresztő szűrő (symmetrical 1-pole IIR)
                    levelL += (targetL - levelL) * attackSpeed
                    levelR += (targetR - levelR) * attackSpeed
                } else {
                    // Aszimmetrikus (Digital Peak, PPM, True Peak, Nordic/DIN, Custom)
                    if (targetL > levelL) levelL += (targetL - levelL) * attackSpeed
                    else levelL -= (levelL - targetL) * decaySpeed
    
                    if (targetR > levelR) levelR += (targetR - levelR) * attackSpeed
                    else levelR -= (levelR - targetR) * decaySpeed
                }

                levelL = levelL.coerceIn(0f, 1.2f) // Allow slight overshoot for bouncy effect
                levelR = levelR.coerceIn(0f, 1.2f)

                val displayLevelL = levelL.coerceIn(0f, 1f)
                val displayLevelR = levelR.coerceIn(0f, 1f)

                val now = System.currentTimeMillis()

                // Peak tracking: if level rises above peak, jump with it and reset hold
                if (displayLevelL >= peakL) {
                    peakL = displayLevelL
                    peakHoldL = now
                    peakAlphaL = 255f
                } else {
                    // Decay only starts after hold time expires AND current level is below peak
                    if (now - peakHoldL > peakHoldMs) {
                        peakL *= peakDecay
                        val alphaFadeStep = (255f / (peakHoldMs / 16f).coerceAtLeast(10f)).coerceIn(1f, 10f)
                        peakAlphaL = (peakAlphaL - alphaFadeStep).coerceAtLeast(0f)
                    }
                }

                if (displayLevelR >= peakR) {
                    peakR = displayLevelR
                    peakHoldR = now
                    peakAlphaR = 255f
                } else {
                    if (now - peakHoldR > peakHoldMs) {
                        peakR *= peakDecay
                        val alphaFadeStep = (255f / (peakHoldMs / 16f).coerceAtLeast(10f)).coerceIn(1f, 10f)
                        peakAlphaR = (peakAlphaR - alphaFadeStep).coerceAtLeast(0f)
                    }
                }

                // Peak never below bar guarantee
                if (peakNeverBelowBar) {
                    if (peakL < displayLevelL) {
                        peakL = displayLevelL
                        peakAlphaL = 255f
                    }
                    if (peakR < displayLevelR) {
                        peakR = displayLevelR
                        peakAlphaR = 255f
                    }
                }

                targetL *= targetDecay
                targetR *= targetDecay

                invalidate()
                postDelayed(this, 16)
            }
        }, 16)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX; lastTouchY = event.rawY
                    startTouchX = event.rawX; startTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        it.x += (event.rawX - lastTouchX).toInt()
                        it.y += (event.rawY - lastTouchY).toInt()
                        windowManager?.updateViewLayout(this, it)
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dist = hypot(event.rawX - startTouchX, event.rawY - startTouchY)
                    val duration = event.eventTime - event.downTime
                    if (duration < 300 && dist < 15) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) {
                            context.stopService(android.content.Intent(context, FloatingVuService::class.java))
                        } else lastClickTime = now
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPrefs() {
        val oldScale = sizeScale
        val oldLedCount = ledCount
        val oldHoriz = isHorizontal
        val oldDbScale = fxDbScale
        val oldLiveDb = showLiveDb
        val oldPeakDb = showPeakDb
        val oldGap = lcdSegmentGap
        val oldSevenSeg = lcdSevenSegment
        sizeScale = prefs.getFloat("size_scale", 1.0f)
        ledCount = prefs.getInt("led_count", 20)
        isHorizontal = prefs.getBoolean("horizontal", false)
        useLpf = prefs.getBoolean("use_lpf", false)
        fxGlow = prefs.getBoolean("fx_glow", true)
        fxSheen = prefs.getBoolean("fx_sheen", true)
        fxGhost = prefs.getBoolean("fx_ghost", true)
        fxDbScale = prefs.getBoolean("fx_db_scale", true)
        showLiveDb = prefs.getBoolean("show_live_db", true)
        showPeakDb = prefs.getBoolean("show_peak_db", true)
        lcdGhostStrength = prefs.getFloat("lcd_ghost_strength", 0.55f)
        lcdContrast = prefs.getFloat("lcd_contrast", 0.80f)
        lcdSegmentGap = prefs.getInt("lcd_segment_gap", 2).toFloat()
        lcdSevenSegment = prefs.getBoolean("lcd_seven_segment", true)

        if (fxGlow) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        
        peakNeverBelowBar = prefs.getBoolean("peak_never_below_bar", true)
        mode = prefs.getInt("mode", 0)
        when (mode) {
            0 -> { // Digital Peak: fast attack, characterful decay
                attackSpeed = 0.90f
                decaySpeed = 0.12f
            }
            1 -> { // Analog VU: slow ~300 ms integration feel
                attackSpeed = 0.10f
                decaySpeed = 0.05f
                analogSpring = prefs.getFloat("analog_spring", 0.15f)
                analogDamp = prefs.getFloat("analog_damp", 0.25f)
            }
            2 -> { // PPM: fast attack, slow release
                attackSpeed = 0.40f
                decaySpeed = 0.08f
            }
            3 -> { // True Peak: fast attack, clean decay
                attackSpeed = 0.95f
                decaySpeed = 0.15f
            }
            4 -> { // Classic VU: 300 ms integration, smoother ±1 dB style ballistics
                attackSpeed = 0.08f
                decaySpeed = 0.04f
                analogSpring = 0.10f
                analogDamp = 0.32f
            }
            5 -> { // BBC PPM: stepped / multi-stage release (classic British PPM feel)
                attackSpeed = 0.45f
                decaySpeed = 0.06f
            }
            6 -> { // Nordic / DIN: faster attack, distinct (slightly faster) decay
                attackSpeed = 0.55f
                decaySpeed = 0.14f
            }
            7 -> { // Custom: user-defined attack / decay
                attackSpeed = prefs.getFloat("attack", 0.35f)
                val decay = prefs.getFloat("decay", 0.88f)
                decaySpeed = (1f - decay).coerceIn(0.01f, 1.0f)
            }
            else -> {
                attackSpeed = prefs.getFloat("attack", 0.35f)
                val decay = prefs.getFloat("decay", 0.88f)
                decaySpeed = 1f - decay
            }
        }

        targetDecay = prefs.getFloat("target_decay", 0.94f)
        peakHoldMs = prefs.getLong("peak_hold_ms", 900L)
        peakDecay = prefs.getFloat("peak_decay", 0.985f)
        analogSpring = prefs.getFloat("analog_spring", 0.15f)
        analogDamp = prefs.getFloat("analog_damp", 0.25f)

        gain = prefs.getFloat("gain", 1.0f)
        opacity = prefs.getInt("opacity", 204)
        themeId = prefs.getInt("theme", 0)
        // backward compat: régi boolean -> új int
        peakMode = if (prefs.contains("peak_mode")) prefs.getInt("peak_mode", 0)
                   else if (prefs.getBoolean("colored_peak", false)) 1 else 0
        setBackgroundColor(Color.argb(opacity, 0, 0, 0))
        updateThemeColors()
        if (oldScale != sizeScale || oldLedCount != ledCount || oldHoriz != isHorizontal || oldDbScale != fxDbScale || oldLiveDb != showLiveDb || oldPeakDb != showPeakDb || oldGap != lcdSegmentGap || oldSevenSeg != lcdSevenSegment) {
            requestLayout()
            params?.let { windowManager?.updateViewLayout(this, it) }
        }
        invalidate()
    }

    private fun updateThemeColors() {
        when (themeId) {
            1 -> { // Cyberpunk
                paintGreen.color = Color.parseColor("#00F0FF"); paintYellow.color = Color.parseColor("#FF00FF"); paintRed.color = Color.parseColor("#FF0055")
                paintOffGreen.color = Color.parseColor("#001A1A"); paintOffYellow.color = Color.parseColor("#1A001A"); paintOffRed.color = Color.parseColor("#1A0010")
            }
            2 -> { // Fire
                paintGreen.color = Color.parseColor("#FFAA00"); paintYellow.color = Color.parseColor("#FF8800"); paintRed.color = Color.parseColor("#FF3300")
                paintOffGreen.color = Color.parseColor("#1A1100"); paintOffYellow.color = Color.parseColor("#1A0900"); paintOffRed.color = Color.parseColor("#1A0500")
            }
            3 -> { // Ice
                paintGreen.color = Color.parseColor("#00E5FF"); paintYellow.color = Color.parseColor("#88DDFF"); paintRed.color = Color.parseColor("#FFFFFF")
                paintOffGreen.color = Color.parseColor("#001520"); paintOffYellow.color = Color.parseColor("#080F18"); paintOffRed.color = Color.parseColor("#101518")
            }
            4 -> { // Sunset
                paintGreen.color = Color.parseColor("#FFC107"); paintYellow.color = Color.parseColor("#FF6B35"); paintRed.color = Color.parseColor("#E91E63")
                paintOffGreen.color = Color.parseColor("#181008"); paintOffYellow.color = Color.parseColor("#180800"); paintOffRed.color = Color.parseColor("#180010")
            }
            5 -> { // VFD (Klasszikus Videoton Cián - meleg zöldes-cián és sötét teal)
                val vfdCyan = Color.parseColor("#00D4E8")
                val vfdDim = Color.parseColor("#001F26")
                paintGreen.color = vfdCyan; paintYellow.color = vfdCyan; paintRed.color = vfdCyan
                paintOffGreen.color = vfdDim; paintOffYellow.color = vfdDim; paintOffRed.color = vfdDim
            }
            6 -> { // LCD Zöld (Retro LCD Green: #0A1A0A bg, #33FF66 active, #1A2E1A ghost)
                val activeAlpha = (255 * lcdContrast.coerceIn(0.2f, 1f)).toInt()
                val ghostAlpha = (255 * lcdGhostStrength.coerceIn(0f, 1f)).toInt()
                val lcdGreen = Color.argb(activeAlpha, 0x33, 0xFF, 0x66)
                val lcdGhost = Color.argb(ghostAlpha, 0x2A, 0x5E, 0x2A)
                paintGreen.color = lcdGreen; paintYellow.color = lcdGreen; paintRed.color = lcdGreen
                paintOffGreen.color = lcdGhost; paintOffYellow.color = lcdGhost; paintOffRed.color = lcdGhost
            }
            7 -> { // LCD Borostyán (LCD Amber: #120A00 bg, #FFB000 active, #2E1B00 ghost)
                val activeAlpha = (255 * lcdContrast.coerceIn(0.2f, 1f)).toInt()
                val ghostAlpha = (255 * lcdGhostStrength.coerceIn(0f, 1f)).toInt()
                val lcdAmber = Color.argb(activeAlpha, 0xFF, 0xB0, 0x00)
                val lcdGhost = Color.argb(ghostAlpha, 0x5E, 0x38, 0x00)
                paintGreen.color = lcdAmber; paintYellow.color = lcdAmber; paintRed.color = lcdAmber
                paintOffGreen.color = lcdGhost; paintOffYellow.color = lcdGhost; paintOffRed.color = lcdGhost
            }
            else -> { // Classic
                paintGreen.color = Color.parseColor("#00FF66"); paintYellow.color = Color.parseColor("#FFAA00"); paintRed.color = Color.parseColor("#FF2222")
                paintOffGreen.color = Color.parseColor("#082010"); paintOffYellow.color = Color.parseColor("#1A1200"); paintOffRed.color = Color.parseColor("#1A0808")
            }
        }
    }

    override fun onDetachedFromWindow() { prefs.unregisterOnSharedPreferenceChangeListener(prefListener); super.onDetachedFromWindow() }
    fun setWindowParams(wm: WindowManager, p: WindowManager.LayoutParams) { windowManager = wm; params = p }

    fun updateLevels(rawL: Float, rawR: Float) {
        fun toLevel(signal: Float): Float {
            val db = 20f * log10((signal * 3f * gain).coerceAtLeast(0.0001f))
            return ((db + 50f) / 50f).coerceIn(0f, 1f)
        }
        targetL = max(targetL, toLevel(rawL))
        targetR = max(targetR, toLevel(rawR))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val gap = if (themeId in 6..7) lcdSegmentGap else ledGap
        val extraReadoutW = if (showLiveDb || showPeakDb) 115f else 0f
        val extraReadoutH = if (showLiveDb || showPeakDb) 38f else 0f
        if (isHorizontal) {
            val baseW = 40f + ledCount * (ledH + gap) + 28f + extraReadoutW
            val baseH = if (fxDbScale) (20f + colW + 16f + 20f + colW + 20f) else (20f + colW + 12f + colW + 20f)
            setMeasuredDimension((baseW * sizeScale).toInt(), (baseH * sizeScale).toInt())
        } else {
            val baseW = if (fxDbScale) (xScale + 32f) else (xR + colW + 16f)
            val baseH = topY + ledCount * (ledH + gap) - gap + 10f + extraReadoutH
            setMeasuredDimension((baseW * sizeScale).toInt(), (baseH * sizeScale).toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(sizeScale, sizeScale)
        
        if (isHorizontal) {
            drawHorizontal(canvas)
        } else {
            drawVertical(canvas)
        }
        
        canvas.restore()
    }
    
    private fun formatDb(level: Float): String {
        val db = (level * 50f) - 50f
        return if (db <= -49.5f) "-INF" else String.format(java.util.Locale.US, "%+.1f", db)
    }

    private fun draw7SegmentChar(canvas: Canvas, char: Char, x: Float, y: Float, w: Float, h: Float, paint: Paint) {
        val t = max(1.5f, w * 0.18f)
        val halfH = h / 2f

        // Segments: a (top), b (top-right), c (bottom-right), d (bottom), e (bottom-left), f (top-left), g (middle)
        val segs = when (char) {
            '0' -> booleanArrayOf(true, true, true, true, true, true, false)
            '1' -> booleanArrayOf(false, true, true, false, false, false, false)
            '2' -> booleanArrayOf(true, true, false, true, true, false, true)
            '3' -> booleanArrayOf(true, true, true, true, false, false, true)
            '4' -> booleanArrayOf(false, true, true, false, false, true, true)
            '5' -> booleanArrayOf(true, false, true, true, false, true, true)
            '6' -> booleanArrayOf(true, false, true, true, true, true, true)
            '7' -> booleanArrayOf(true, true, true, false, false, false, false)
            '8' -> booleanArrayOf(true, true, true, true, true, true, true)
            '9' -> booleanArrayOf(true, true, true, true, false, true, true)
            '-' -> booleanArrayOf(false, false, false, false, false, false, true)
            'P', 'p' -> booleanArrayOf(true, true, false, false, true, true, true)
            'k', 'K' -> booleanArrayOf(false, true, true, false, true, true, true)
            'L' -> booleanArrayOf(false, false, false, true, true, true, false)
            'R' -> booleanArrayOf(true, true, false, false, true, true, true)
            else -> booleanArrayOf(false, false, false, false, false, false, false)
        }

        if (char == '.') {
            canvas.drawRect(x + w * 0.2f, y + h - t * 1.5f, x + w * 0.2f + t * 1.5f, y + h, paint)
            return
        }

        val origStyle = paint.style
        paint.style = Paint.Style.FILL

        if (segs[0]) canvas.drawRect(x + t, y, x + w - t, y + t, paint) // a
        if (segs[1]) canvas.drawRect(x + w - t, y + t, x + w, y + halfH, paint) // b
        if (segs[2]) canvas.drawRect(x + w - t, y + halfH, x + w, y + h - t, paint) // c
        if (segs[3]) canvas.drawRect(x + t, y + h - t, x + w - t, y + h, paint) // d
        if (segs[4]) canvas.drawRect(x, y + halfH, x + t, y + h - t, paint) // e
        if (segs[5]) canvas.drawRect(x, y + t, x + t, y + halfH, paint) // f
        if (segs[6]) canvas.drawRect(x + t, y + halfH - t / 2f, x + w - t, y + halfH + t / 2f, paint) // g

        paint.style = origStyle
    }

    private fun draw7SegmentString(canvas: Canvas, text: String, x: Float, y: Float, charW: Float, charH: Float, spacing: Float, paint: Paint) {
        var curX = x
        for (c in text) {
            val cw = if (c == '.' || c == ' ') charW * 0.5f else charW
            if (c != ' ') {
                draw7SegmentChar(canvas, c, curX, y, cw, charH, paint)
            }
            curX += cw + spacing
        }
    }

    private fun drawRackScrews(canvas: Canvas, w: Float, h: Float) {
        val r = 3.5f
        val margin = 6f
        val screwPositions = floatArrayOf(
            margin, margin,
            w - margin, margin,
            margin, h - margin,
            w - margin, h - margin
        )
        for (i in 0 until screwPositions.size step 2) {
            val sx = screwPositions[i]
            val sy = screwPositions[i + 1]
            canvas.drawCircle(sx, sy, r, paintRackScrewFill)
            canvas.drawCircle(sx, sy, r, paintRackScrew)
            canvas.drawLine(sx - 2f, sy, sx + 2f, sy, paintRackScrew)
        }
    }

    private fun drawVertical(canvas: Canvas) {
        val extraReadoutH = if (showLiveDb || showPeakDb) 36f else 0f
        val totalW = xScale + 32f
        val towerBottom = topY + ledCount * (ledH + ledGap) - ledGap
        val totalH = towerBottom + 10f + extraReadoutH
        val bezelRect = RectF(0f, 0f, totalW, totalH)

        val vfdBezel = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#0A1215") }
            6 -> Paint().apply { color = Color.parseColor("#0A1A0A") }
            7 -> Paint().apply { color = Color.parseColor("#120A00") }
            else -> paintBezel
        }
        val vfdBorder = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#003840"); style = Paint.Style.STROKE; strokeWidth = 2f }
            6 -> Paint().apply { color = Color.parseColor("#1A381A"); style = Paint.Style.STROKE; strokeWidth = 2f }
            7 -> Paint().apply { color = Color.parseColor("#382200"); style = Paint.Style.STROKE; strokeWidth = 2f }
            else -> paintBezelBorder
        }
        canvas.drawRoundRect(bezelRect, 8f, 8f, vfdBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, vfdBorder)
        drawRackScrews(canvas, totalW, totalH)

        val grooveX = xL + colW + 2f
        val vfdGroove = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#040B0D") }
            6 -> Paint().apply { color = Color.parseColor("#050F05") }
            7 -> Paint().apply { color = Color.parseColor("#0A0600") }
            else -> paintGroove
        }
        canvas.drawRoundRect(RectF(grooveX, topY - 4f, grooveX + 10f, towerBottom + 4f), 3f, 3f, vfdGroove)

        paintChanLabel.color = when (themeId) {
            5 -> Color.parseColor("#66C0CC")
            6 -> Color.parseColor("#33FF66")
            7 -> Color.parseColor("#FFB000")
            else -> Color.WHITE
        }

        canvas.drawText("L", xL + colW / 2f, topY - 8f, paintChanLabel)
        canvas.drawText("R", xR + colW / 2f, topY - 8f, paintChanLabel)

        drawTower(canvas, xL, levelL.coerceIn(0f, 1f), peakL, peakAlphaL)
        drawTower(canvas, xR, levelR.coerceIn(0f, 1f), peakR, peakAlphaR)
        if (fxDbScale) {
            drawScale(canvas, xScale, topY)
        }

        // Readouts under each channel
        if (showLiveDb || showPeakDb) {
            paintReadout.color = when (themeId) {
                5 -> Color.parseColor("#00D4E8")
                6 -> Color.argb((255 * lcdContrast.coerceIn(0.2f, 1f)).toInt(), 0x33, 0xFF, 0x66)
                7 -> Color.argb((255 * lcdContrast.coerceIn(0.2f, 1f)).toInt(), 0xFF, 0xB0, 0x00)
                else -> Color.WHITE
            }
            paintReadout.textAlign = Paint.Align.CENTER
            var readoutY = towerBottom + 16f

            val use7Seg = (themeId in 6..7) && lcdSevenSegment
            if (use7Seg) {
                val cw = 6.5f
                val ch = 11f
                val sp = 1.8f
                if (showLiveDb) {
                    val sL = formatDb(levelL)
                    val sR = formatDb(levelR)
                    draw7SegmentString(canvas, sL, xL + (colW - sL.length * (cw + sp)) / 2f, readoutY - 9f, cw, ch, sp, paintReadout)
                    draw7SegmentString(canvas, sR, xR + (colW - sR.length * (cw + sp)) / 2f, readoutY - 9f, cw, ch, sp, paintReadout)
                    readoutY += 15f
                }
                if (showPeakDb) {
                    val sL = "Pk " + formatDb(peakL)
                    val sR = "Pk " + formatDb(peakR)
                    draw7SegmentString(canvas, sL, xL + (colW - sL.length * (cw + sp)) / 2f, readoutY - 9f, cw, ch, sp, paintReadout)
                    draw7SegmentString(canvas, sR, xR + (colW - sR.length * (cw + sp)) / 2f, readoutY - 9f, cw, ch, sp, paintReadout)
                }
            } else {
                if (showLiveDb) {
                    canvas.drawText(formatDb(levelL), xL + colW / 2f, readoutY, paintReadout)
                    canvas.drawText(formatDb(levelR), xR + colW / 2f, readoutY, paintReadout)
                    readoutY += 14f
                }
                if (showPeakDb) {
                    canvas.drawText("Pk " + formatDb(peakL), xL + colW / 2f, readoutY, paintReadout)
                    canvas.drawText("Pk " + formatDb(peakR), xR + colW / 2f, readoutY, paintReadout)
                }
            }
        }

        if (themeId == 5) {
            val wirePaint = Paint().apply {
                color = Color.argb(45, 255, 255, 255)
                strokeWidth = 1f
            }
            val wireStart = topY - 4f
            val wireEnd = towerBottom + 4f
            canvas.drawLine(xL + colW * 0.3f, wireStart, xL + colW * 0.3f, wireEnd, wirePaint)
            canvas.drawLine(xL + colW * 0.7f, wireStart, xL + colW * 0.7f, wireEnd, wirePaint)
            canvas.drawLine(xR + colW * 0.3f, wireStart, xR + colW * 0.3f, wireEnd, wirePaint)
            canvas.drawLine(xR + colW * 0.7f, wireStart, xR + colW * 0.7f, wireEnd, wirePaint)
        }
    }

    private fun drawHorizontal(canvas: Canvas) {
        val extraReadoutW = if (showLiveDb || showPeakDb) 85f else 0f
        val startX = 40f
        val yL = 20f
        val yScale = yL + colW + 12f
        val yR = if (fxDbScale) (yScale + 16f) else (yL + colW + 12f)
        
        val barsEnd = startX + ledCount * (ledH + ledGap)
        val totalW = barsEnd + 20f + extraReadoutW
        val totalH = yR + colW + 16f
        
        val bezelRect = RectF(0f, 0f, totalW, totalH)
        val vfdBezel = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#0A1215") }
            6 -> Paint().apply { color = Color.parseColor("#0A1A0A") }
            7 -> Paint().apply { color = Color.parseColor("#120A00") }
            else -> paintBezel
        }
        val vfdBorder = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#003840"); style = Paint.Style.STROKE; strokeWidth = 2f }
            6 -> Paint().apply { color = Color.parseColor("#1A381A"); style = Paint.Style.STROKE; strokeWidth = 2f }
            7 -> Paint().apply { color = Color.parseColor("#382200"); style = Paint.Style.STROKE; strokeWidth = 2f }
            else -> paintBezelBorder
        }
        canvas.drawRoundRect(bezelRect, 8f, 8f, vfdBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, vfdBorder)
        drawRackScrews(canvas, totalW, totalH)

        val grooveY = yL + colW + 2f
        val vfdGroove = when (themeId) {
            5 -> Paint().apply { color = Color.parseColor("#040B0D") }
            6 -> Paint().apply { color = Color.parseColor("#050F05") }
            7 -> Paint().apply { color = Color.parseColor("#0A0600") }
            else -> paintGroove
        }
        canvas.drawRoundRect(RectF(startX - 4f, grooveY, barsEnd + 4f, grooveY + 8f), 3f, 3f, vfdGroove)

        paintChanLabel.color = when (themeId) {
            5 -> Color.parseColor("#66C0CC")
            6 -> Color.parseColor("#33FF66")
            7 -> Color.parseColor("#FFB000")
            else -> Color.WHITE
        }

        canvas.drawText("L", 16f, yL + colW / 2f + paintChanLabel.textSize / 3f, paintChanLabel)
        canvas.drawText("R", 16f, yR + colW / 2f + paintChanLabel.textSize / 3f, paintChanLabel)

        drawTowerHorizontal(canvas, startX, yL, levelL.coerceIn(0f, 1f), peakL, peakAlphaL)
        drawTowerHorizontal(canvas, startX, yR, levelR.coerceIn(0f, 1f), peakR, peakAlphaR)
        if (fxDbScale) {
            drawScaleHorizontal(canvas, startX, yScale)
        }

        // Live dB + Peak text on the right with extra spacing & 7-seg support
        if (showLiveDb || showPeakDb) {
            val textX = barsEnd + 16f
            paintReadout.color = when (themeId) {
                5 -> Color.parseColor("#00D4E8")
                6 -> Color.argb((255 * lcdContrast.coerceIn(0.2f, 1f)).toInt(), 0x33, 0xFF, 0x66)
                7 -> Color.argb((255 * lcdContrast.coerceIn(0.2f, 1f)).toInt(), 0xFF, 0xB0, 0x00)
                else -> Color.WHITE
            }
            paintReadout.textAlign = Paint.Align.LEFT

            val use7Seg = (themeId in 6..7) && lcdSevenSegment
            if (use7Seg) {
                val cw = 6.5f
                val ch = 11f
                val sp = 1.8f
                if (showLiveDb) {
                    draw7SegmentString(canvas, "L " + formatDb(levelL), textX, yL + colW * 0.25f, cw, ch, sp, paintReadout)
                    draw7SegmentString(canvas, "R " + formatDb(levelR), textX, yR + colW * 0.25f, cw, ch, sp, paintReadout)
                }
                if (showPeakDb) {
                    val pkY_L = if (showLiveDb) yL + colW * 0.62f else yL + colW * 0.35f
                    val pkY_R = if (showLiveDb) yR + colW * 0.62f else yR + colW * 0.35f
                    draw7SegmentString(canvas, "Pk " + formatDb(peakL), textX, pkY_L, cw, ch, sp, paintReadout)
                    draw7SegmentString(canvas, "Pk " + formatDb(peakR), textX, pkY_R, cw, ch, sp, paintReadout)
                }
            } else {
                if (showLiveDb) {
                    canvas.drawText("L " + formatDb(levelL), textX, yL + colW * 0.38f, paintReadout)
                    canvas.drawText("R " + formatDb(levelR), textX, yR + colW * 0.38f, paintReadout)
                }
                if (showPeakDb) {
                    val pkYOffset = if (showLiveDb) colW * 0.75f else colW * 0.5f
                    canvas.drawText("Pk " + formatDb(peakL), textX, yL + pkYOffset, paintReadout)
                    canvas.drawText("Pk " + formatDb(peakR), textX, yR + pkYOffset, paintReadout)
                }
            }
        }

        if (themeId == 5) {
            val wirePaint = Paint().apply {
                color = Color.argb(45, 255, 255, 255)
                strokeWidth = 1f
            }
            val wireStart = startX - 4f
            val wireEnd = totalW - 10f
            canvas.drawLine(wireStart, yL + colW * 0.25f, wireEnd, yL + colW * 0.25f, wirePaint)
            canvas.drawLine(wireStart, yL + colW * 0.75f, wireEnd, yL + colW * 0.75f, wirePaint)
            canvas.drawLine(wireStart, yR + colW * 0.25f, wireEnd, yR + colW * 0.25f, wirePaint)
            canvas.drawLine(wireStart, yR + colW * 0.75f, wireEnd, yR + colW * 0.75f, wirePaint)
        }
    }

    private fun drawScale(c: Canvas, x: Float, startY: Float) {
        data class Mark(val ledIdxFromBottom: Int, val label: String)
        val marks = listOf(
            Mark(ledCount - 1, " 0"),
            Mark((ledCount * 0.91).toInt(), "-3"),
            Mark((ledCount * 0.85).toInt(), "-6"),
            Mark((ledCount * 0.65).toInt(), "-10"),
            Mark((ledCount * 0.35).toInt(), "-20"),
            Mark(1, "-∞")
        )

        if (themeId == 5) {
            paintScaleTick.color = Color.parseColor("#006A75")
            paintScaleText.color = Color.parseColor("#4DAEB8")
        } else {
            paintScaleTick.color = Color.parseColor("#666666")
            paintScaleText.color = Color.parseColor("#888888")
        }

        for (m in marks) {
            val idxFromTop = ledCount - 1 - m.ledIdxFromBottom
            val centerY = startY + idxFromTop * (ledH + ledGap) + ledH / 2f
            c.drawLine(x, centerY, x + 5f, centerY, paintScaleTick)
            c.drawText(m.label, x + 7f, centerY + paintScaleText.textSize / 3f, paintScaleText)
        }
    }
    
    private fun drawScaleHorizontal(c: Canvas, startX: Float, y: Float) {
        data class Mark(val ledIdx: Int, val label: String)
        val marks = listOf(
            Mark(1, "-∞"),
            Mark((ledCount * 0.35).toInt(), "-20"),
            Mark((ledCount * 0.65).toInt(), "-10"),
            Mark((ledCount * 0.85).toInt(), "-6"),
            Mark((ledCount * 0.91).toInt(), "-3"),
            Mark(ledCount - 1, " 0")
        )

        if (themeId == 5) {
            paintScaleTick.color = Color.parseColor("#006A75")
            paintScaleText.color = Color.parseColor("#4DAEB8")
        } else {
            paintScaleTick.color = Color.parseColor("#666666")
            paintScaleText.color = Color.parseColor("#888888")
        }
        
        for (m in marks) {
            val centerX = startX + m.ledIdx * (ledH + ledGap) + ledH / 2f
            c.drawLine(centerX, y, centerX, y + 5f, paintScaleTick)
            
            val textWidth = paintScaleText.measureText(m.label)
            c.drawText(m.label, centerX - textWidth / 2f, y + 18f, paintScaleText)
        }
    }

    private fun getPeakPaint(isRed: Boolean, isYellow: Boolean): Paint {
        return when (peakMode) {
            1 -> { // Színes - megegyezik a led színével
                when { isRed -> paintRed; isYellow -> paintYellow; else -> paintGreen }
            }
            2 -> { // Fix sárga/amber
                Paint().apply { color = Color.parseColor("#FFCC00"); style = Paint.Style.STROKE; strokeWidth = 3f }
            }
            3 -> { // Fix cián - minden témán látszik
                Paint().apply { color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE; strokeWidth = 3f }
            }
            else -> paintPeak // Fehér klasszikus
        }
    }

    private fun drawTower(c: Canvas, x: Float, level: Float, peak: Float, peakAlpha: Float) {
        val redThreshold = (ledCount * 0.85).toInt()
        val yellowThreshold = (ledCount * 0.65).toInt()
        val r = 5f

        if (themeId == 5) {
            val totalSegments = ledCount * 4
            val activeSegments = (level * totalSegments).toInt()
            val peakSegment = (peak * totalSegments).toInt().coerceIn(0, totalSegments - 1)
            
            // 4 sub-segments HORIZONTALLY per LED for the real VFD matrix look
            val subGap = 2f
            val subW = (colW - 3 * subGap) / 4f

            for (idxFromBottom in 0 until ledCount) {
                val idxFromTop = ledCount - 1 - idxFromBottom
                val top = topY + idxFromTop * (ledH + ledGap)
                
                for (j in 0 until 4) {
                    val globalIdx = idxFromBottom * 4 + j
                    val isOn = globalIdx < activeSegments
                    
                    val subLeft = x + j * (subW + subGap)
                    val rect = RectF(subLeft, top, subLeft + subW, top + ledH)
                    
                    val paint = if (isOn) paintGreen else (if (fxGhost) paintOffGreen else Paint().apply { color = Color.TRANSPARENT })
                    c.drawRoundRect(rect, r - 2f, r - 2f, paint)
                    
                    if (isOn) {
                        if (fxGlow) {
                            val glowRadius = 10f + level * 10f
                            paintGlow.set(paint)
                            paintGlow.setShadowLayer(glowRadius, 0f, 0f, Color.parseColor("#00D4E8"))
                            c.drawRoundRect(rect, r - 2f, r - 2f, paintGlow)
                        }
                        if (fxSheen) {
                            val glareRect = RectF(subLeft + 1f, top + 1f, subLeft + subW - 1f, top + ledH * 0.35f)
                            c.drawRoundRect(glareRect, r - 2f, r - 2f, paintSheen)
                        }
                    }
                    
                    if (globalIdx == peakSegment && peakAlpha > 0f && peak > 0.05f) {
                        val pPaint = getPeakPaint(false, false)
                        pPaint.alpha = peakAlpha.toInt().coerceIn(0, 255)
                        c.drawRoundRect(rect, r - 1f, r - 1f, pPaint)
                    }
                }
            }
        } else {
            val activeLeds = (level * ledCount).toInt()
            val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount - 1)
            for (idxFromBottom in 0 until ledCount) {
                val idxFromTop = ledCount - 1 - idxFromBottom
                val top = topY + idxFromTop * (ledH + ledGap)
                val rect = RectF(x, top, x + colW, top + ledH)
                val isOn = idxFromBottom < activeLeds
                val isRed = idxFromBottom >= redThreshold
                val isYellow = idxFromBottom in yellowThreshold until redThreshold
                val paint = when {
                    isRed -> if (isOn) paintRed else (if (fxGhost) paintOffRed else Paint().apply { color = Color.TRANSPARENT })
                    isYellow -> if (isOn) paintYellow else (if (fxGhost) paintOffYellow else Paint().apply { color = Color.TRANSPARENT })
                    else -> if (isOn) paintGreen else (if (fxGhost) paintOffGreen else Paint().apply { color = Color.TRANSPARENT })
                }
                c.drawRoundRect(rect, r, r, paint)
                if (isOn) {
                    if (fxGlow) {
                        val baseGlow = if (isRed) 14f else if (isYellow) 10f else 8f
                        val glowRadius = baseGlow + level * 6f
                        paintGlow.set(paint)
                        paintGlow.setShadowLayer(glowRadius, 0f, 0f, paint.color)
                        c.drawRoundRect(rect, r, r, paintGlow)
                    }
                    if (fxSheen) {
                        c.drawRoundRect(RectF(x + 2f, top + 1.5f, x + colW - 2f, top + ledH * 0.38f), r - 1f, r - 1f, paintSheen)
                    }
                }
                if (idxFromBottom == peakLed && peakAlpha > 0f && peak > 0.05f) {
                    val pPaint = getPeakPaint(isRed, isYellow)
                    pPaint.alpha = peakAlpha.toInt().coerceIn(0, 255)
                    val outer = RectF(x - 2f, top - 2f, x + colW + 2f, top + ledH + 2f)
                    c.drawRoundRect(outer, r + 1f, r + 1f, pPaint)
                }
            }
        }
    }
    
    private fun drawTowerHorizontal(c: Canvas, startX: Float, y: Float, level: Float, peak: Float, peakAlpha: Float) {
        val redThreshold = (ledCount * 0.85).toInt()
        val yellowThreshold = (ledCount * 0.65).toInt()
        val r = 3f

        if (themeId == 5) {
            val totalSegments = ledCount * 4
            val activeSegments = (level * totalSegments).toInt()
            val peakSegment = (peak * totalSegments).toInt().coerceIn(0, totalSegments - 1)
            
            // For horizontal mode, sub-segments are stacked VERTICALLY within each horizontal block
            val subGap = 2f
            val subH = (colW - 3 * subGap) / 4f

            for (i in 0 until ledCount) {
                val left = startX + i * (ledH + ledGap)
                
                for (j in 0 until 4) {
                    val globalIdx = i * 4 + j
                    val isOn = globalIdx < activeSegments
                    
                    // The 4 sub-segments are vertical slices
                    val subTop = y + (3 - j) * (subH + subGap) // drawing from bottom to top within the block
                    val rect = RectF(left, subTop, left + ledH, subTop + subH)
                    
                    val paint = if (isOn) paintGreen else (if (fxGhost) paintOffGreen else Paint().apply { color = Color.TRANSPARENT })
                    c.drawRoundRect(rect, r - 1f, r - 1f, paint)
                    
                    if (isOn) {
                        if (fxGlow) {
                            val glowRadius = 10f + level * 10f
                            paintGlow.set(paint)
                            paintGlow.setShadowLayer(glowRadius, 0f, 0f, Color.parseColor("#00D4E8"))
                            c.drawRoundRect(rect, r - 1f, r - 1f, paintGlow)
                        }
                        if (fxSheen) {
                            val glareRect = RectF(left + 1f, subTop + 1f, left + ledH * 0.35f, subTop + subH - 1f)
                            c.drawRoundRect(glareRect, r - 1f, r - 1f, paintSheen)
                        }
                    }
                    
                    if (globalIdx == peakSegment && peakAlpha > 0f && peak > 0.05f) {
                        val pPaint = getPeakPaint(false, false)
                        pPaint.alpha = peakAlpha.toInt().coerceIn(0, 255)
                        c.drawRoundRect(rect, r, r, pPaint)
                    }
                }
            }
        } else {
            val activeLeds = (level * ledCount).toInt()
            val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount - 1)
            for (i in 0 until ledCount) {
                val left = startX + i * (ledH + ledGap)
                val rect = RectF(left, y, left + ledH, y + colW)
                val isOn = i < activeLeds
                
                val isRed = i >= redThreshold
                val isYellow = i in yellowThreshold until redThreshold
                
                val paint = when {
                    isRed -> if (isOn) paintRed else (if (fxGhost) paintOffRed else Paint().apply { color = Color.TRANSPARENT })
                    isYellow -> if (isOn) paintYellow else (if (fxGhost) paintOffYellow else Paint().apply { color = Color.TRANSPARENT })
                    else -> if (isOn) paintGreen else (if (fxGhost) paintOffGreen else Paint().apply { color = Color.TRANSPARENT })
                }
                
                c.drawRoundRect(rect, r, r, paint)
                
                if (isOn) {
                    if (fxGlow) {
                        val baseGlow = if (isRed) 14f else if (isYellow) 10f else 8f
                        val glowRadius = baseGlow + level * 6f
                        paintGlow.set(paint)
                        paintGlow.setShadowLayer(glowRadius, 0f, 0f, paint.color)
                        c.drawRoundRect(rect, r, r, paintGlow)
                    }
                    if (fxSheen) {
                        c.drawRoundRect(RectF(left + 1f, y + 1.5f, left + ledH * 0.38f, y + colW - 1.5f), r - 1f, r - 1f, paintSheen)
                    }
                }
                
                if (i == peakLed && peakAlpha > 0f && peak > 0.05f) {
                    val pPaint = getPeakPaint(isRed, isYellow)
                    pPaint.alpha = peakAlpha.toInt().coerceIn(0, 255)
                    val outer = RectF(left - 2f, y - 2f, left + ledH + 2f, y + colW + 2f)
                    c.drawRoundRect(outer, r + 1f, r + 1f, pPaint)
                }
            }
        }
    }
}
