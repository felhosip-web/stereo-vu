import re

with open("app/src/main/java/com/example/stereovu/StereoVuView.kt", "r") as f:
    content = f.read()

# Replace the updateThemeColors to include case 5
theme_pattern = r'4 -> \{ // Sunset.*?\}'
replacement_theme = """4 -> { // Sunset
                paintGreen.color = Color.parseColor("#FFC107"); paintYellow.color = Color.parseColor("#FF6B35"); paintRed.color = Color.parseColor("#E91E63")
                paintOffGreen.color = Color.parseColor("#181008"); paintOffYellow.color = Color.parseColor("#180800"); paintOffRed.color = Color.parseColor("#180010")
            }
            5 -> { // VFD (Klasszikus Cián)
                val vfdCyan = Color.parseColor("#00E5FF")
                val vfdDim = Color.parseColor("#002228")
                paintGreen.color = vfdCyan; paintYellow.color = vfdCyan; paintRed.color = vfdCyan
                paintOffGreen.color = vfdDim; paintOffYellow.color = vfdDim; paintOffRed.color = vfdDim
            }"""
content = re.sub(theme_pattern, replacement_theme, content, flags=re.DOTALL)

# Add isHorizontal to loadPrefs and property
if "private var isHorizontal = false" not in content:
    content = content.replace("private var useLpf = false", "private var useLpf = false\n    private var isHorizontal = false")

load_prefs_pattern = r'val oldScale = sizeScale\n        val oldLedCount = ledCount\n        sizeScale = prefs.getFloat\("size_scale", 1.0f\)\n        ledCount = prefs.getInt\("led_count", 20\)'
load_prefs_replacement = """val oldScale = sizeScale
        val oldLedCount = ledCount
        val oldHoriz = isHorizontal
        sizeScale = prefs.getFloat("size_scale", 1.0f)
        ledCount = prefs.getInt("led_count", 20)
        isHorizontal = prefs.getBoolean("horizontal", false)"""
content = content.replace(
    'val oldScale = sizeScale\n        val oldLedCount = ledCount\n        sizeScale = prefs.getFloat("size_scale", 1.0f)\n        ledCount = prefs.getInt("led_count", 20)',
    load_prefs_replacement
)

content = content.replace(
    'if (oldScale != sizeScale || oldLedCount != ledCount) {',
    'if (oldScale != sizeScale || oldLedCount != ledCount || oldHoriz != isHorizontal) {'
)

# Now replace everything from onMeasure to the end
measure_idx = content.find('override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {')
top_part = content[:measure_idx]

new_bottom = """override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isHorizontal) {
            val baseW = 40f + ledCount * (ledH + ledGap) + 20f
            val baseH = 20f + colW + 16f + 20f + colW + 20f
            setMeasuredDimension((baseW * sizeScale).toInt(), (baseH * sizeScale).toInt())
        } else {
            val baseW = xScale + 32f
            val baseH = topY + ledCount * (ledH + ledGap) - ledGap + 10f
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
    
    private fun drawVertical(canvas: Canvas) {
        val totalW = xScale + 32f
        val totalH = topY + ledCount * (ledH + ledGap) - ledGap + 10f
        val bezelRect = RectF(0f, 0f, totalW, totalH)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezelBorder)

        val grooveX = xL + colW + 2f
        canvas.drawRoundRect(RectF(grooveX, topY - 4f, grooveX + 10f, totalH - 6f), 3f, 3f, paintGroove)

        canvas.drawText("L", xL + colW / 2f, topY - 8f, paintChanLabel)
        canvas.drawText("R", xR + colW / 2f, topY - 8f, paintChanLabel)

        drawTower(canvas, xL, levelL, peakL, peakAlphaL)
        drawTower(canvas, xR, levelR, peakR, peakAlphaR)
        drawScale(canvas, xScale, topY)
    }

    private fun drawHorizontal(canvas: Canvas) {
        val startX = 40f
        val yL = 20f
        val yScale = yL + colW + 12f
        val yR = yScale + 16f
        
        val totalW = startX + ledCount * (ledH + ledGap) + 20f
        val totalH = yR + colW + 16f
        
        val bezelRect = RectF(0f, 0f, totalW, totalH)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezel)
        canvas.drawRoundRect(bezelRect, 8f, 8f, paintBezelBorder)

        val grooveY = yL + colW + 2f
        canvas.drawRoundRect(RectF(startX - 4f, grooveY, totalW - 6f, grooveY + 8f), 3f, 3f, paintGroove)

        canvas.drawText("L", 16f, yL + colW / 2f + paintChanLabel.textSize / 3f, paintChanLabel)
        canvas.drawText("R", 16f, yR + colW / 2f + paintChanLabel.textSize / 3f, paintChanLabel)

        drawTowerHorizontal(canvas, startX, yL, levelL, peakL, peakAlphaL)
        drawTowerHorizontal(canvas, startX, yR, levelR, peakR, peakAlphaR)
        drawScaleHorizontal(canvas, startX, yScale)
    }

    private fun drawScale(c: Canvas, x: Float, startY: Float) {
        data class Mark(val ledIdxFromBottom: Int, val label: String)
        val marks = listOf(
            Mark(ledCount - 1, " 0"), 
            Mark((ledCount * 0.85).toInt(), "-6"), 
            Mark((ledCount * 0.65).toInt(), "-10"), 
            Mark((ledCount * 0.35).toInt(), "-20"), 
            Mark(1, "-\u221E")
        )
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
            Mark(1, "-\u221E"),
            Mark((ledCount * 0.35).toInt(), "-20"),
            Mark((ledCount * 0.65).toInt(), "-10"),
            Mark((ledCount * 0.85).toInt(), "-6"),
            Mark(ledCount - 1, " 0")
        )
        
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
        val activeLeds = (level * ledCount).toInt()
        val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount - 1)
        val redThreshold = (ledCount * 0.85).toInt()
        val yellowThreshold = (ledCount * 0.65).toInt()
        val r = 5f
        for (idxFromBottom in 0 until ledCount) {
            val idxFromTop = ledCount - 1 - idxFromBottom
            val top = topY + idxFromTop * (ledH + ledGap)
            val rect = RectF(x, top, x + colW, top + ledH)
            val isOn = idxFromBottom < activeLeds
            val isRed = idxFromBottom >= redThreshold
            val isYellow = idxFromBottom in yellowThreshold until redThreshold
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
                c.drawRoundRect(RectF(x + 2f, top + 1.5f, x + colW - 2f, top + ledH * 0.38f), r - 1f, r - 1f, paintSheen)
            }
            if (idxFromBottom == peakLed && peakAlpha > 0f && peak > 0.05f) {
                val pPaint = getPeakPaint(isRed, isYellow)
                pPaint.alpha = peakAlpha.toInt().coerceIn(0, 255)
                val outer = RectF(x - 2f, top - 2f, x + colW + 2f, top + ledH + 2f)
                c.drawRoundRect(outer, r + 1f, r + 1f, pPaint)
            }
        }
    }
    
    private fun drawTowerHorizontal(c: Canvas, startX: Float, y: Float, level: Float, peak: Float, peakAlpha: Float) {
        val activeLeds = (level * ledCount).toInt()
        val peakLed = (peak * ledCount).toInt().coerceIn(0, ledCount - 1)
        
        val redThreshold = (ledCount * 0.85).toInt()
        val yellowThreshold = (ledCount * 0.65).toInt()
        
        val r = 3f
        for (i in 0 until ledCount) {
            val left = startX + i * (ledH + ledGap)
            val rect = RectF(left, y, left + ledH, y + colW)
            val isOn = i < activeLeds
            
            val isRed = i >= redThreshold
            val isYellow = i in yellowThreshold until redThreshold
            
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
                
                // Sheen
                c.drawRoundRect(RectF(left + 1f, y + 1.5f, left + ledH * 0.38f, y + colW - 1.5f), r - 1f, r - 1f, paintSheen)
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
"""

with open("app/src/main/java/com/example/stereovu/StereoVuView.kt", "w") as f:
    f.write(top_part + new_bottom)
