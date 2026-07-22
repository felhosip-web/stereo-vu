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

    private val ledCount = 20
    private var levelL = 0f
    private var levelR = 0f
    private var targetL = 0f
    private var targetR = 0f
    private var peakL = 0f
    private var peakR = 0f
    private var peakHoldL = 0L
    private var peakHoldR = 0L
    private var peakAlphaL = 0f
    private var peakAlphaR = 0f

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

    // LEDs
    private val paintOff = Paint().apply { color = Color.parseColor("#1A1A1A") }
    private val paintGreen = Paint().apply { color = Color.parseColor("#00FF66") }
    private val paintYellow = Paint().apply { color = Color.parseColor("#FFAA00") }
    private val paintRed = Paint().apply { color = Color.parseColor("#FF2222") }
    private val paintOffGreen = Paint().apply { color = Color.parseColor("#082010") }
    private val paintOffYellow = Paint().apply { color = Color.parseColor("#1A1200") }
    private val paintOffRed = Paint().apply { color = Color.parseColor("#1A0808") }
    private val paintPeak = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintPeakCutout = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val paintGlow = Paint()
    private val paintSheen = Paint().apply { color = Color.argb(60, 255, 255, 255) }

    // Bezel
    private val paintBezel = Paint().apply { color = Color.parseColor("#1E1E1E") }
    private val paintBezelBorder = Paint().apply { color = Color.parseColor("#333333"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintGroove = Paint().apply { color = Color.parseColor("#0F0F0F") }
    private val paintChanLabel = Paint().apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val paintScaleTick = Paint().apply { color = Color.parseColor("#666666"); strokeWidth = 2f }
    private val paintScaleText = Paint().apply { color = Color.parseColor("#888888"); textSize = 16f }

    // layout constants
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

        setOnTouchListener { _, event ->
            when (event.action) {
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
        setBackgroundColor(Color.argb(opacity, 0, 0, 0))
        updateThemeColors()
        if (oldScale != sizeScale) {
            requestLayout()
            params?.let { windowManager?.updateViewLayout(this, it) }
        }
        invalidate()
    }

    private fun updateThemeColors() {
        when (themeId) {
            1 -> { // Cyberpunk – cyan / magenta
                paintGreen.color = Color.parseColor("#00F0FF")
                paintYellow.color = Color.parseColor("#FF00FF")
                paintRed.color = Color.parseColor("#FF0055")
                paintOffGreen.color = Color.parseColor("#001A1A")
                paintOffYellow.color = Color.parseColor("#1A001A")
                paintOffRed.color = Color.parseColor("#1A0010")
            }
            2 -> { // Fire – orange / red
                paintGreen.color = Color.parseColor("#FFAA00")
                paintYellow.color = Color.parseColor("#FF8800")
                paintRed.color = Color.parseColor("#FF3300")
                paintOffGreen.color = Color.parseColor("#1A1100")
                paintOffYellow.color = Color.parseColor("#1A0900")
                paintOffRed.color = Color.parseColor("#1A0500")
            }
            3 -> { // Ice – cyan / sky / white
                paintGreen.color = Color.parseColor("#00E5FF")
                paintYellow.color = Color.parseColor("#88DDFF")
                paintRed.color = Color.parseColor("#FFFFFF")
                paintOffGreen.color = Color.parseColor("#001520")
                paintOffYellow.color = Color.parseColor("#080F18")
                paintOffRed.color = Color.parseColor("#101518")
            }
            4 -> { // Sunset – gold / coral / magenta
                paintGreen.color = Color.parseColor("#FFC107")
                paintYellow.color = Color.parseColor("#FF6B35")
                paintRed.color = Color.parseColor("#E91E63")
                paintOffGreen.color = Color.parseColor("#181008")
                paintOffYellow.color = Color.parseColor("#180800")
                paintOffRed.color = Color.parseColor("#180010")
            }
            else -> { // Classic green / amber / red
                paintGreen.color = Color.parseColor("#00FF66")
                paintYellow.color = Color.parseColor("#FFAA00")
                paintRed.color = Color.parseColor("#FF2222")
                paintOffGreen.color = Color.parseColor("#082010")
                paintOffYellow.color = Color.parseColor("#1A1200")
                paintOffRed.color = Color.parseColor("#1A0808")
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
        fun toLevel(rms: Float): Float {
            val db = 20f * log10((rms * 3f * gain).coerceAtLeast(0.0001f))
            return ((db + 50f) / 50f).coerceIn(0f, 1f)
        }
        val l = toLevel(rmsL)
        val r = toLevel(rmsR)
        levelL = max(levelL, l)
        levelR = max(levelR, r)
        if (l > peakL) { peakL = l; peakHoldL = System.currentTimeMillis(); peakAlphaL = 255f }
        if (r > peakR) { peakR = r; peakHoldR = System.currentTimeMillis(); peakAlphaR = 255f }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseW = xScale + 32f
        val baseH = topY + ledCount * (ledH + ledGap) - ledGap + 10f
        setMeasuredDimension((baseW * sizeScale).toInt(), (baseH * sizeScale).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(sizeScale, sizeScale)

        val totalW = xScale + 32f
        val totalH = topY + ledCount * (ledH + ledGap) - ledGap + 10f

        val bezelRect = RectF(0f, 0f, totalW, totalH)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezelBorder)

        val grooveX = xL + colW + 2f
        val grooveRect = RectF(grooveX, topY - 4f, grooveX + 10f, totalH - 6f)
        canvas.drawRoundRect(grooveRect, 3f, 3f, paintGroove)

        canvas.drawText("L", xL + colW / 2f, topY - 8f, paintChanLabel)
        canvas.drawText("R", xR + colW / 2f, topY - 8f, paintChanLabel)

        drawTower(canvas, xL, levelL, peakL, peakAlphaL)
        drawTower(canvas, xR, levelR, peakR, peakAlphaR)

        drawScale(canvas, xScale, topY)
        canvas.restore()
    }

    private fun drawScale(c: Canvas, x: Float, startY: Float) {
        data class Mark(val ledIdxFromBottom: Int, val label: String)
        val marks = listOf(Mark(19, " 0"), Mark(17, "-6"), Mark(13, "-10"), Mark(7, "-20"), Mark(1, "-\u221E"))
        for (m in marks) {
            val idxFromTop = ledCount - 1 - m.ledIdxFromBottom
            val centerY = startY + idxFromTop * (ledH + ledGap) + ledH / 2f
            c.drawLine(x, centerY, x + 5f, centerY, paintScaleTick)
            c.drawText(m.label, x + 7f, centerY + paintScaleText.textSize / 3f, paintScaleText)
        }
    }

    private fun drawTower(c: Canvas, x: Float, level: Float, peak: Float, peakAlpha: Float) {
        val activeLeds = (level * ledCount).toInt()
        val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount - 1)
        val r = 5f
        for (idxFromBottom in 0 until ledCount) {
            val idxFromTop = ledCount - 1 - idxFromBottom
            val top = topY + idxFromTop * (ledH + ledGap)
            val rect = RectF(x, top, x + colW, top + ledH)
            val isOn = idxFromBottom < activeLeds
            val isRed = idxFromBottom >= 17
            val isYellow = idxFromBottom in 13..16
            val paint = when {
                isRed -> if (isOn) paintRed else paintOffRed
                isYellow -> if (isOn) paintYellow else paintOffYellow
                else -> if (isOn) paintGreen else paintOffGreen
            }
            c.drawRoundRect(rect, r, r, paint)
            if (isOn) {
                val glowRadius = if (isRed) 14f else if (isYellow) 10f else 8f
                paintGlow.set(paint)
                paintGlow.setShadowLayer(glowRadius, 0f, 0f, paint.color)
                c.drawRoundRect(rect, r, r, paintGlow)
                val sheenRect = RectF(x + 2f, top + 1.5f, x + colW - 2f, top + ledH * 0.38f)
                c.drawRoundRect(sheenRect, r - 1f, r - 1f, paintSheen)
            }
            if (idxFromBottom == peakLed && peakAlpha > 0f) {
                val alpha = peakAlpha.toInt().coerceIn(0, 255)
                paintPeak.alpha = alpha
                val outerRect = RectF(x - 1.5f, top - 1.5f, x + colW + 1.5f, top + ledH + 1.5f)
                c.drawRoundRect(outerRect, r + 1f, r + 1f, paintPeak)
                paintPeakCutout.alpha = alpha
                val innerRect = RectF(x + 1.5f, top + 1.5f, x + colW - 1.5f, top + ledH - 1.5f)
                c.drawRoundRect(innerRect, r - 1f, r - 1f, paintPeakCutout)
            }
        }
    }
}
