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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

class FloatingVuService : Service() {
    companion object {
        private const val TAG = "FloatingVuService"
        private const val WORKER_JOIN_TIMEOUT_MS = 1000L
    }

    private var windowManager: WindowManager? = null
    private var vuView: StereoVuView? = null
    private val captureLock = Any()
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val isRunning = AtomicBoolean(false)
    @Volatile private var amplitudeCount = 32767.0
    @Volatile private var detectionMode = 0 // 0 = Peak, 1 = RMS
    @Volatile private var ballisticsMode = 0 // 0..7 (3 = True Peak)
    @Volatile private var rmsWindowMs = 350 // Default 350 ms (50 - 1000 ms)
    private val truePeakDetector = TruePeakDetector()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "amplitude_count" -> amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
            "detection_mode" -> detectionMode = prefs.getInt("detection_mode", 0)
            "mode" -> ballisticsMode = BallisticsModeMigration.getMigratedMode(prefs)
            "rms_window_ms" -> rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        detectionMode = prefs.getInt("detection_mode", 0)
        ballisticsMode = BallisticsModeMigration.getMigratedMode(prefs)
        rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val captureIntent = intent?.getParcelableExtra<Intent>("captureIntent")
        
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        detectionMode = prefs.getInt("detection_mode", 0)
        ballisticsMode = BallisticsModeMigration.getMigratedMode(prefs)
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

    private fun startAudioCapture(captureIntent: Intent?) {
        synchronized(captureLock) {
            if (isRunning.get() || audioRecord != null || captureThread != null) {
                return
            }
            isRunning.set(true)

            val worker = thread(name = "StereoVuAudioCapture", start = false) {
                var record: AudioRecord? = null
                try {
                    val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && captureIntent != null) 48000 else 44100
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && captureIntent != null) {
                        val mgr = getSystemService(MediaProjectionManager::class.java)
                        val projection = mgr?.getMediaProjection(-1, captureIntent)
                        if (projection == null) {
                            Log.e(TAG, "MediaProjection not available")
                            return@thread
                        }
                        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build()
                        val format = AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build()
                        record = AudioRecord.Builder()
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
                        record = AudioRecord.Builder()
                            .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(8192)
                            .build()
                    }

                    synchronized(captureLock) {
                        if (!isRunning.get()) {
                            // Service stopped while AudioRecord was being initialized
                            record?.release()
                            return@thread
                        }
                        audioRecord = record
                    }

                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord failed to initialize (state != STATE_INITIALIZED)")
                        return@thread
                    }

                    record.startRecording()
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

                    while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val maxAmp = amplitudeCount
                            val isRms = (detectionMode == 1)

                            // Peak detection: True peak of current audio buffer (max absolute sample)
                            // Fast, responsive attack
                            var peakMaxL = 0.0
                            var peakMaxR = 0.0
                            val isTruePeak = (ballisticsMode == 3)

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
                            while (i < read - 1) {
                                val l = buffer[i].toDouble() / maxAmp
                                val r = buffer[i + 1].toDouble() / maxAmp

                                if (isTruePeak) {
                                    truePeakDetector.processSample(l, r)
                                    val sPeakL = truePeakDetector.lastPeakL
                                    val sPeakR = truePeakDetector.lastPeakR
                                    if (sPeakL > peakMaxL) peakMaxL = sPeakL
                                    if (sPeakR > peakMaxR) peakMaxR = sPeakR
                                } else {
                                    val absL = Math.abs(l)
                                    val absR = Math.abs(r)
                                    if (absL > peakMaxL) peakMaxL = absL
                                    if (absR > peakMaxR) peakMaxR = absR
                                }

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
                        } else if (read == 0) {
                            // Avoid tight loop on 0 samples read
                            try {
                                Thread.sleep(10)
                            } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        } else {
                            // Non-positive error return values from AudioRecord.read()
                            when (read) {
                                AudioRecord.ERROR_INVALID_OPERATION ->
                                    Log.e(TAG, "AudioRecord.read returned ERROR_INVALID_OPERATION")
                                AudioRecord.ERROR_BAD_VALUE ->
                                    Log.e(TAG, "AudioRecord.read returned ERROR_BAD_VALUE")
                                AudioRecord.ERROR_DEAD_OBJECT ->
                                    Log.e(TAG, "AudioRecord.read returned ERROR_DEAD_OBJECT (HAL service died)")
                                else ->
                                    Log.e(TAG, "AudioRecord.read returned unhandled error: $read")
                            }
                            // Do not enter an uncontrolled retry loop on terminal errors
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during audio capture", e)
                } finally {
                    synchronized(captureLock) {
                        try {
                            if (record != null) {
                                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                    record.stop()
                                }
                                record.release()
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error cleaning up worker AudioRecord", t)
                        }
                        if (audioRecord === record) {
                            audioRecord = null
                        }
                    }
                }
            }

            captureThread = worker
            worker.start()
        }
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)

        val threadToJoin: Thread?
        synchronized(captureLock) {
            isRunning.set(false)
            try {
                audioRecord?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        it.stop()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping AudioRecord on onDestroy", t)
            }
            threadToJoin = captureThread
            captureThread = null
        }

        // Bounded wait for worker thread termination outside lock to avoid deadlocks
        threadToJoin?.let { worker ->
            try {
                worker.interrupt()
                worker.join(WORKER_JOIN_TIMEOUT_MS)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        synchronized(captureLock) {
            try {
                audioRecord?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Error releasing AudioRecord on onDestroy", t)
            }
            audioRecord = null
        }

        vuView?.let { windowManager?.removeView(it) }
        vuView = null
        super.onDestroy()
    }
}
