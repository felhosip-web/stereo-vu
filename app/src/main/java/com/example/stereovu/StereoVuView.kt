package com.example.stereovu

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.MotionEvent
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import kotlin.math.*

class StereoVuView(context: Context) : FrameLayout(context) {
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    private val ledCount = 20
    private var levelL = 0f
    private var levelR = 0f
    private var targetL = 0f
    private var targetR = 0f
    private var peakL = 0f
    private var peakR = 0f
    private var peakHoldL = 0L
    private var peakHoldR = 0L

    private val prefs = context.getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        this@StereoVuView.post { loadPrefs() }
    }
    
    private var decay = 0.88f
    private var gain = 1.0f
    private var sizeScale = 1.0f
    private var opacity = 204
    private var themeId = 0
    private var coloredPeak = false

    private val paintOff = Paint().apply { color = Color.parseColor("#1A1A1A") }
    private val paintGreen = Paint().apply { color = Color.parseColor("#00FF66") }
    private val paintRed = Paint().apply { color = Color.parseColor("#FF2222") }
    private val paintPeak = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadPrefs()

        setPadding(12, 12, 12, 12)
        // 60fps frissítés a decay-hez
        postDelayed(object: Runnable {
            override fun run() {
                // Analóg simítás (tehetetlenség)
                val attackSpeed = 0.35f
                val decaySpeed = 1f - decay

                if (targetL > levelL) {
                    levelL += (targetL - levelL) * attackSpeed
                } else {
                    levelL -= (levelL - targetL) * decaySpeed
                }

                if (targetR > levelR) {
                    levelR += (targetR - levelR) * attackSpeed
                } else {
                    levelR -= (levelR - targetR) * decaySpeed
                }

                // Peak tracking
                if (levelL > peakL) { peakL = levelL; peakHoldL = System.currentTimeMillis() }
                if (levelR > peakR) { peakR = levelR; peakHoldR = System.currentTimeMillis() }

                val now = System.currentTimeMillis()
                if (now - peakHoldL > 900) peakL *= 0.95f
                if (now - peakHoldR > 900) peakR *= 0.95f
                
                targetL *= 0.9f
                targetR *= 0.9f
                
                invalidate()
                postDelayed(this, 16)
            }
        }, 16)

        var startTouchX = 0f
        var startTouchY = 0f
        var lastClickTime = 0L

        setOnTouchListener { _, event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        it.x += (event.rawX - lastTouchX).toInt()
                        it.y += (event.rawY - lastTouchY).toInt()
                        windowManager?.updateViewLayout(this, it)
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = event.eventTime - event.downTime
                    val dist = hypot(event.rawX - startTouchX, event.rawY - startTouchY)
                    if (duration < 300 && dist < 15) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) {
                            // Double tap -> Close service cleanly!
                            context.stopService(android.content.Intent(context, FloatingVuService::class.java))
                        } else {
                            lastClickTime = now
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPrefs() {
        decay = prefs.getFloat("decay", 0.88f)
        gain = prefs.getFloat("gain", 1.0f)
        val oldScale = sizeScale
        sizeScale = prefs.getFloat("size_scale", 1.0f)
        opacity = prefs.getInt("opacity", 204)
        themeId = prefs.getInt("theme", 0)
        coloredPeak = prefs.getBoolean("colored_peak", false)

        // Apply background opacity (Color.argb)
        setBackgroundColor(Color.argb(opacity, 0, 0, 0))

        // Update Theme Colors
        updateThemeColors()

        if (oldScale != sizeScale) {
            requestLayout()
            params?.let {
                windowManager?.updateViewLayout(this, it)
            }
        }
        invalidate()
    }

    private fun updateThemeColors() {
        when (themeId) {
            1 -> { // Cyberpunk (Cyan and Pink/Red)
                paintGreen.color = Color.parseColor("#00F0FF")
                paintRed.color = Color.parseColor("#FF0055")
            }
            2 -> { // Fire (Orange/Yellow and Deep Red)
                paintGreen.color = Color.parseColor("#FFAA00")
                paintRed.color = Color.parseColor("#FF3300")
            }
            3 -> { // Ice (Cyan and Ice White)
                paintGreen.color = Color.parseColor("#00E5FF")
                paintRed.color = Color.parseColor("#FFFFFF")
            }
            4 -> { // Sunset (Gold and Pink-Magenta)
                paintGreen.color = Color.parseColor("#FFC107")
                paintRed.color = Color.parseColor("#E91E63")
            }
            else -> { // Classic (Green and Red)
                paintGreen.color = Color.parseColor("#00FF66")
                paintRed.color = Color.parseColor("#FF2222")
            }
        }
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDetachedFromWindow()
    }

    fun setWindowParams(wm: WindowManager, p: WindowManager.LayoutParams) {
        windowManager = wm
        params = p
    }

    fun updateLevels(rmsL: Float, rmsR: Float) {
        // dB scale -> 0..1
        fun toLevel(rms: Float): Float {
            val db = 20f * log10((rms * 3f * gain).coerceAtLeast(0.0001f))
            // -50dB..0dB -> 0..1
            return ((db + 50f)/50f).coerceIn(0f,1f)
        }
        targetL = toLevel(rmsL)
        targetR = toLevel(rmsR)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 2 oszlop, 20 led
        val baseW = 180f
        val baseH = 20f * 14f + 19f * 4f + 40f
        setMeasuredDimension((baseW * sizeScale).toInt(), (baseH * sizeScale).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(sizeScale, sizeScale)

        val ledH = 12f
        val gap = 4f
        val colW = 70f
        val startY = 20f

        // L
        drawTower(canvas, 12f, startY, colW, ledH, gap, levelL, peakL)
        // R
        drawTower(canvas, 12f+colW+16f, startY, colW, ledH, gap, levelR, peakR)

        // L R felirat
        val tp = Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true }
        canvas.drawText("L", 35f, 18f, tp)
        canvas.drawText("R", 35f+colW+16f, 18f, tp)

        canvas.restore()
    }

    private fun drawTower(c: Canvas, x: Float, y: Float, w: Float, h: Float, gap: Float, level: Float, peak: Float) {
        val activeLeds = (level * ledCount).toInt()
        val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount-1)

        for (i in 0 until ledCount) {
            val idxFromBottom = i
            val idxFromTop = ledCount - 1 - i
            val top = y + idxFromTop*(h+gap)
            val rect = RectF(x, top, x+w, top+h)
            val isOn = idxFromBottom < activeLeds
            val isRed = idxFromBottom >= 17
            val paint = when {
                !isOn -> paintOff
                isRed -> paintRed
                else -> paintGreen
            }
            c.drawRoundRect(rect, 3f, 3f, paint)
            if (isOn) {
                // glow
                paint.setShadowLayer(8f, 0f, 0f, paint.color)
                c.drawRoundRect(rect, 3f, 3f, paint)
                paint.clearShadowLayer()
            }
            // peak hold pötty
            if (idxFromBottom == peakLed && peakLed > 0) {
                val peakRect = RectF(x-2, top-1, x+w+2, top+h+1)
                if (coloredPeak) {
                    val peakPaint = if (isRed) paintRed else paintGreen
                    c.drawRoundRect(peakRect, 4f, 4f, peakPaint)
                    
                    peakPaint.setShadowLayer(8f, 0f, 0f, peakPaint.color)
                    c.drawRoundRect(peakRect, 4f, 4f, peakPaint)
                    peakPaint.clearShadowLayer()
                } else {
                    c.drawRoundRect(peakRect, 4f, 4f, paintPeak)
                }
            }
        }
    }
}aintRed.color     = Color.parseColor("#FF3300")
                paintOffGreen.color  = Color.parseColor("#1A1100")
                paintOffYellow.color = Color.parseColor("#1A0900")
                paintOffRed.color    = Color.parseColor("#1A0500")
            }
            3 -> { // Ice – cyan / sky / white
                paintGreen.color   = Color.parseColor("#00E5FF")
                paintYellow.color  = Color.parseColor("#88DDFF")
                paintRed.color     = Color.parseColor("#FFFFFF")
                paintOffGreen.color  = Color.parseColor("#001520")
                paintOffYellow.color = Color.parseColor("#080F18")
                paintOffRed.color    = Color.parseColor("#101518")
            }
            4 -> { // Sunset – gold / coral / magenta
                paintGreen.color   = Color.parseColor("#FFC107")
                paintYellow.color  = Color.parseColor("#FF6B35")
                paintRed.color     = Color.parseColor("#E91E63")
                paintOffGreen.color  = Color.parseColor("#181008")
                paintOffYellow.color = Color.parseColor("#180800")
                paintOffRed.color    = Color.parseColor("#180010")
            }
            else -> { // Classic green / amber / red
                paintGreen.color   = Color.parseColor("#00FF66")
                paintYellow.color  = Color.parseColor("#FFAA00")
                paintRed.color     = Color.parseColor("#FF2222")
                paintOffGreen.color  = Color.parseColor("#082010")
                paintOffYellow.color = Color.parseColor("#1A1200")
                paintOffRed.color    = Color.parseColor("#1A0808")
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDetachedFromWindow()
    }

    fun setWindowParams(wm: WindowManager, p: WindowManager.LayoutParams) {
        windowManager = wm
        params = p
    }

    fun updateLevels(rmsL: Float, rmsR: Float) {
        fun toLevel(rms: Float): Float {
            val db = 20f * log10((rms * 3f * gain).coerceAtLeast(0.0001f))
            return ((db + 50f) / 50f).coerceIn(0f, 1f)
        }
        val l = toLevel(rmsL)
        val r = toLevel(rmsR)
        // Fast attack: immediately jump to louder level
        levelL = max(levelL, l)
        levelR = max(levelR, r)
        if (l > peakL) { peakL = l; peakHoldL = System.currentTimeMillis() }
        if (r > peakR) { peakR = r; peakHoldR = System.currentTimeMillis() }
    }

    // ── Measurement ──────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseW = xScale + 32f           // right-side dB strip
        val baseH = topY + ledCount * (ledH + ledGap) - ledGap + 10f
        setMeasuredDimension(
            (baseW * sizeScale).toInt(),
            (baseH * sizeScale).toInt()
        )
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(sizeScale, sizeScale)

        val totalW = xScale + 32f
        val totalH = topY + ledCount * (ledH + ledGap) - ledGap + 10f

        // Bezel background
        val bezelRect = RectF(0f, 0f, totalW, totalH)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezelBorder)

        // Subtle groove between the two columns
        val grooveX = xL + colW + 2f
        val grooveRect = RectF(grooveX, topY - 4f, grooveX + 10f, totalH - 6f)
        canvas.drawRoundRect(grooveRect, 3f, 3f, paintGroove)

        // Channel labels (centred above each column)
        canvas.drawText("L", xL + colW / 2f, topY - 8f, paintChanLabel)
        canvas.drawText("R", xR + colW / 2f, topY - 8f, paintChanLabel)

        // LED columns
        drawTower(canvas, xL, levelL, peakL, peakAlphaL)
        drawTower(canvas, xR, levelR, peakR, peakAlphaR)

        // dB scale strip
        drawScale(canvas, xScale, topY)

        canvas.restore()
    }

    // dB scale markings on the right of the R column
    private fun drawScale(c: Canvas, x: Float, startY: Float) {
        data class Mark(val ledIdxFromBottom: Int, val label: String)
        val marks = listOf(
            Mark(19, " 0"),
            Mark(17, "-6"),
            Mark(13, "-10"),
            Mark(7,  "-20"),
            Mark(1,  "-\u221E")   // −∞
        )
        for (m in marks) {
            val idxFromTop = ledCount - 1 - m.ledIdxFromBottom
            val centerY = startY + idxFromTop * (ledH + ledGap) + ledH / 2f
            c.drawLine(x, centerY, x + 5f, centerY, paintScaleTick)
            c.drawText(m.label, x + 7f, centerY + paintScaleText.textSize / 3f, paintScaleText)
        }
    }

    // Draw one LED column at horizontal position x
    private fun drawTower(c: Canvas, x: Float, level: Float, peak: Float, peakAlpha: Float) {
        val activeLeds = (level * ledCount).toInt()
        val peakLed    = (peak  * ledCount).toInt().coerceIn(0, ledCount - 1)
        val r = 5f   // LED corner radius

        for (idxFromBottom in 0 until ledCount) {
            val idxFromTop = ledCount - 1 - idxFromBottom
            val top  = topY + idxFromTop * (ledH + ledGap)
            val rect = RectF(x, top, x + colW, top + ledH)

            val isOn     = idxFromBottom < activeLeds
            val isRed    = idxFromBottom >= 17
            val isYellow = idxFromBottom in 13..16

            // Select the correct paint (on or off, by zone)
            val paint = when {
                isRed    -> if (isOn) paintRed    else paintOffRed
                isYellow -> if (isOn) paintYellow else paintOffYellow
                else     -> if (isOn) paintGreen  else paintOffGreen
            }
            c.drawRoundRect(rect, r, r, paint)

            if (isOn) {
                // Glow: red LEDs glow harder
                val glowRadius = if (isRed) 14f else if (isYellow) 10f else 8f
                paintGlow.set(paint)
                paintGlow.setShadowLayer(glowRadius, 0f, 0f, paint.color)
                c.drawRoundRect(rect, r, r, paintGlow)

                // Plastic sheen on top ~38% of lit LED
                val sheenRect = RectF(x + 2f, top + 1.5f, x + colW - 2f, top + ledH * 0.38f)
                c.drawRoundRect(sheenRect, r - 1f, r - 1f, paintSheen)
            }

            // Peak hold: solid border (white ring) that fades out
            if (idxFromBottom == peakLed && peakAlpha > 0f) {
                val alpha = peakAlpha.toInt().coerceIn(0, 255)
                // Outer ring
                paintPeak.alpha = alpha
                val outerRect = RectF(x - 1.5f, top - 1.5f, x + colW + 1.5f, top + ledH + 1.5f)
                c.drawRoundRect(outerRect, r + 1f, r + 1f, paintPeak)
                // Inner cutout to create a hollow border effect
                paintPeakCutout.alpha = alpha
                val innerRect = RectF(x + 1.5f, top + 1.5f, x + colW - 1.5f, top + ledH - 1.5f)
                c.drawRoundRect(innerRect, r - 1f, r - 1f, paintPeakCutout)
            }
        }
    }
}
