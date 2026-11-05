package com.example.stereovu

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import kotlin.concurrent.thread
import kotlin.math.sqrt

class FloatingVuService : Service() {
    private var windowManager: WindowManager? = null
    private var vuView: StereoVuView? = null
    private var audioRecord: AudioRecord? = null
    private var running = true
    @Volatile private var amplitudeCount = 32767.0
    @Volatile private var detectionMode = 0 // 0 = Peak, 1 = RMS
    @Volatile private var ballisticsMode = 0 // 0..7 (3 = True Peak)
    @Volatile private var rmsWindowMs = 350 // Default 350 ms (50 - 1000 ms)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "amplitude_count" -> amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
            "detection_mode" -> detectionMode = prefs.getInt("detection_mode", 0)
            "mode" -> ballisticsMode = prefs.getInt("mode", 0)
            "rms_window_ms" -> rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Loads capture preferences, listens for changes, and starts the foreground notification. */
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        detectionMode = prefs.getInt("detection_mode", 0)
        ballisticsMode = prefs.getInt("mode", 0)
        rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        
        startForegroundNotification()
    }

    /**
     * Handles ACTION_STOP with START_NOT_STICKY; otherwise shows the overlay and starts capture.
     *
     * The optional captureIntent extra supplies playback capture consent. Returns START_STICKY
     * for capture requests; a missing captureIntent selects microphone capture.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val captureIntent = intent?.getParcelableExtra<Intent>("captureIntent")
        
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        detectionMode = prefs.getInt("detection_mode", 0)
        ballisticsMode = prefs.getInt("mode", 0)
        rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        
        showOverlay()
        startAudioCapture(captureIntent)
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "vu_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "VU Meter", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
        val stopIntent = Intent(this, FloatingVuService::class.java).apply {
            action = "ACTION_STOP"
        }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Stereo VU aktív")
            .setContentText("Érintsd meg a leállításhoz")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= 34) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(1, notif, type)
        } else {
            startForeground(1, notif)
        }
    }

    private fun showOverlay() {
        if (vuView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vuView = StereoVuView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }
        vuView?.setWindowParams(windowManager!!, params)
        windowManager?.addView(vuView, params)
    }

    /**
     * Starts stereo capture on a worker thread unless a recorder already exists.
     *
     * Uses playback capture at 48 kHz on Android Q or later when [captureIntent] is supplied;
     * otherwise uses the microphone at 44.1 kHz. Posts normalized peak or sliding-window RMS
     * levels to the overlay. True Peak mode also checks linear quarter and midpoint samples
     * within each buffer. RMS uses a window clamped to 50–1000 ms.
     *
     * Exceptions during worker capture or processing end the worker without reaching the caller.
     */
    private fun startAudioCapture(captureIntent: Intent?) {
        if (audioRecord != null) return
        thread {
            try {
                val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && captureIntent != null) 48000 else 44100
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && captureIntent != null) {
                    val mgr = getSystemService(MediaProjectionManager::class.java)
                    val projection = mgr.getMediaProjection(-1, captureIntent)
                    val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                    audioRecord = AudioRecord.Builder()
                        .setAudioFormat(format)
                        .setAudioPlaybackCaptureConfig(config)
                        .setBufferSizeInBytes(8192)
                        .build()
                } else {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                    audioRecord = AudioRecord.Builder()
                        .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(8192)
                        .build()
                }
                audioRecord?.startRecording()
                val buffer = ShortArray(1024)

                // Pre-allocate circular sample-squared buffer for up to 1000 ms (sampleRate frames)
                // 1 frame = 1 stereo pair (1 left sample, 1 right sample)
                val maxBufferFrames = sampleRate // 1 second at sampleRate frames
                val ringSumSqL = DoubleArray(maxBufferFrames)
                val ringSumSqR = DoubleArray(maxBufferFrames)
                var ringHead = 0
                var currentSamples = 0
                var sumSqL = 0.0
                var sumSqR = 0.0
                var lastWindowMs = rmsWindowMs.coerceIn(50, 1000)
                var windowFrames = (sampleRate * lastWindowMs) / 1000
                var iterationCount = 0

                fun recomputeSum() {
                    var sL = 0.0
                    var sR = 0.0
                    val count = currentSamples
                    val start = (ringHead - count + maxBufferFrames) % maxBufferFrames
                    for (k in 0 until count) {
                        val idx = (start + k) % maxBufferFrames
                        sL += ringSumSqL[idx]
                        sR += ringSumSqR[idx]
                    }
                    sumSqL = sL
                    sumSqR = sR
                }

                while (running) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val maxAmp = amplitudeCount
                        val isRms = (detectionMode == 1)
                        val isTruePeak = (ballisticsMode == 3)

                        // Peak detection: standard peak or True Peak (inter-sample peak interpolation)
                        var peakMaxL = 0.0
                        var peakMaxR = 0.0
                        var prevL = 0.0
                        var prevR = 0.0

                        val reqWindowMs = rmsWindowMs.coerceIn(50, 1000)
                        if (reqWindowMs != lastWindowMs) {
                            lastWindowMs = reqWindowMs
                            windowFrames = (sampleRate * lastWindowMs) / 1000
                            if (currentSamples > windowFrames) {
                                currentSamples = windowFrames
                            }
                            recomputeSum()
                        }

                        var i = 0
                        var sampleIdx = 0
                        while (i < read - 1) {
                            val l = buffer[i].toDouble() / maxAmp
                            val r = buffer[i + 1].toDouble() / maxAmp
                            val absL = Math.abs(l)
                            val absR = Math.abs(r)
                            if (absL > peakMaxL) peakMaxL = absL
                            if (absR > peakMaxR) peakMaxR = absR

                            // Inter-sample peak (linear / parabolic interpolation between consecutive samples)
                            if (isTruePeak && sampleIdx > 0) {
                                val midL = Math.abs(0.5 * (l + prevL))
                                val midR = Math.abs(0.5 * (r + prevR))
                                if (midL > peakMaxL) peakMaxL = midL
                                if (midR > peakMaxR) peakMaxR = midR

                                // 4-point linear sub-samples for higher precision
                                val q1L = Math.abs(0.75 * prevL + 0.25 * l)
                                val q3L = Math.abs(0.25 * prevL + 0.75 * l)
                                val q1R = Math.abs(0.75 * prevR + 0.25 * r)
                                val q3R = Math.abs(0.25 * prevR + 0.75 * r)
                                if (q1L > peakMaxL) peakMaxL = q1L
                                if (q3L > peakMaxL) peakMaxL = q3L
                                if (q1R > peakMaxR) peakMaxR = q1R
                                if (q3R > peakMaxR) peakMaxR = q3R
                            }
                            prevL = l
                            prevR = r
                            sampleIdx++

                            val sqL = l * l
                            val sqR = r * r

                            if (currentSamples < windowFrames) {
                                ringSumSqL[ringHead] = sqL
                                ringSumSqR[ringHead] = sqR
                                sumSqL += sqL
                                sumSqR += sqR
                                currentSamples++
                                ringHead = (ringHead + 1) % maxBufferFrames
                            } else {
                                // Overwrite oldest sample at ringHead (which is windowFrames ago)
                                val oldestIdx = (ringHead - windowFrames + maxBufferFrames) % maxBufferFrames
                                sumSqL -= ringSumSqL[oldestIdx]
                                sumSqR -= ringSumSqR[oldestIdx]

                                ringSumSqL[ringHead] = sqL
                                ringSumSqR[ringHead] = sqR
                                sumSqL += sqL
                                sumSqR += sqR
                                ringHead = (ringHead + 1) % maxBufferFrames
                            }

                            i += 2
                        }

                        // Periodic numerical drift correction
                        iterationCount++
                        if (iterationCount >= 100) {
                            iterationCount = 0
                            recomputeSum()
                        }

                        val valL: Float
                        val valR: Float
                        if (isRms) {
                            val count = currentSamples.coerceAtLeast(1)
                            val rmsL = sqrt((sumSqL / count).coerceAtLeast(0.0)).toFloat()
                            val rmsR = sqrt((sumSqR / count).coerceAtLeast(0.0)).toFloat()
                            valL = rmsL
                            valR = rmsR
                        } else {
                            valL = peakMaxL.toFloat()
                            valR = peakMaxR.toFloat()
                        }

                        vuView?.post { vuView?.updateLevels(valL, valR) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        running = false
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        vuView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
