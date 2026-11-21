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
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class FloatingVuService : Service() {
    companion object {
        private const val TAG = "FloatingVuService"
        private const val WORKER_JOIN_TIMEOUT_MS = 1000L
        internal const val EXTRA_AUDIO_SOURCE = "audioCaptureSource"
        internal const val EXTRA_PROJECTION_RESULT_CODE = "projectionResultCode"
        internal const val EXTRA_CAPTURE_INTENT = "captureIntent"

        internal fun cleanupUninitializedRecord(
            releaseRecord: () -> Unit,
            stopProjection: () -> Unit
        ) {
            try {
                releaseRecord()
            } catch (t: Throwable) {
                try {
                    Log.w(TAG, "Error releasing uninitialized AudioRecord", t)
                } catch (logErr: Throwable) {
                    System.err.println("[$TAG] WARN: Error releasing uninitialized AudioRecord ${t.message}")
                }
            } finally {
                try {
                    stopProjection()
                } catch (t: Throwable) {
                    try {
                        Log.w(TAG, "Error stopping session MediaProjection after initialization failure", t)
                    } catch (logErr: Throwable) {
                        System.err.println("[$TAG] WARN: Error stopping session MediaProjection after initialization failure ${t.message}")
                    }
                }
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var vuView: StereoVuView? = null
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    @Volatile private var amplitudeCount = 32767.0
    @Volatile private var detectionMode = 0 // 0 = Peak, 1 = RMS
    @Volatile private var ballisticsMode = 0 // 0..7 (3 = True Peak)
    @Volatile private var rmsWindowMs = 350 // Default 350 ms (50 - 1000 ms)
    @Volatile private var activeProjection: MediaProjection? = null
    private val captureFailureReported = AtomicBoolean(false)
    private val truePeakDetector = TruePeakDetector()

    internal val coordinator = CaptureSessionCoordinator(
        mainThreadPoster = { runnable -> mainHandler.post(runnable) },
        resetLevelsAction = { vuView?.updateLevels(0f, 0f) },
        stopServiceAction = { stopSelf() }
    )

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
        
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val source = AudioCaptureSource.fromPreferenceValue(
            intent?.getStringExtra(EXTRA_AUDIO_SOURCE)
        )
        val resultCode = if (intent?.hasExtra(EXTRA_PROJECTION_RESULT_CODE) == true) {
            intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Int.MIN_VALUE)
        } else {
            null
        }
        val captureIntent = getCaptureIntent(intent)
        Log.i(
            TAG,
            "Capture request source=${source?.preferenceValue ?: "missing"}, " +
                "resultCodePresent=${resultCode != null}, captureIntentPresent=${captureIntent != null}"
        )
        val requestError = validateAudioCaptureRequest(
            source = source,
            sdkInt = Build.VERSION.SDK_INT,
            resultCode = resultCode,
            hasCaptureIntent = captureIntent != null
        )
        if (requestError != null) {
            Log.e(TAG, "Rejecting capture request: ${requestError.name}")
            reportCaptureFailure(requestError.userMessage)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val requestedSource = requireNotNull(source)
        startForegroundNotification(requestedSource)
        
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        detectionMode = prefs.getInt("detection_mode", 0)
        ballisticsMode = BallisticsModeMigration.getMigratedMode(prefs)
        rmsWindowMs = prefs.getInt("rms_window_ms", 350).coerceIn(50, 1000)
        
        showOverlay()
        startAudioCapture(requestedSource, resultCode, captureIntent)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getCaptureIntent(intent: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CAPTURE_INTENT, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_CAPTURE_INTENT)
        }

    private fun startForegroundNotification(source: AudioCaptureSource) {
        val channelId = "vu_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "VU Meter", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
        val notif = buildNotification(source.activeStatusMessage())
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val type = if (source == AudioCaptureSource.INTERNAL_PLAYBACK) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(1, notif, type)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                source == AudioCaptureSource.INTERNAL_PLAYBACK -> {
                startForeground(
                    1,
                    notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(1, notif, 0)
            }
            else -> startForeground(1, notif)
        }
    }

    private fun updateCaptureNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, FloatingVuService::class.java).apply {
            action = "ACTION_STOP"
        }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        return Notification.Builder(this, "vu_channel")
            .setContentTitle("Stereo VU aktív")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun reportCaptureFailure(message: String, error: Throwable? = null) {
        if (!captureFailureReported.compareAndSet(false, true)) return
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            stopSelf()
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

    private data class PlatformAudioCaptureResources(
        val record: AudioRecord,
        val sampleRate: Int,
        val projection: MediaProjection?,
        val projectionCallback: MediaProjection.Callback?
    )

    private class CaptureInitializationException(message: String, cause: Throwable) :
        Exception(message, cause)

    private fun createPlatformAudioRecord(
        source: AudioCaptureSource,
        resultCode: Int?,
        captureIntent: Intent?
    ): PlatformAudioCaptureResources {
        val sampleRate = if (source == AudioCaptureSource.INTERNAL_PLAYBACK) 48000 else 44100
        var projection: MediaProjection? = null
        var projectionCallback: MediaProjection.Callback? = null

        try {
            val record = createRecordForSource(
                source = source,
                createInternalPlaybackRecord = {
                    val manager = getSystemService(MediaProjectionManager::class.java)
                        ?: throw IllegalStateException("MediaProjectionManager unavailable")
                    val mediaProjection = manager.getMediaProjection(
                        requireNotNull(resultCode),
                        requireNotNull(captureIntent)
                    ) ?: throw IllegalStateException("MediaProjection was not granted")
                    projection = mediaProjection
                    val callback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            handleProjectionStopped(mediaProjection)
                        }
                    }
                    mediaProjection.registerCallback(callback, mainHandler)
                    projectionCallback = callback
                    val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                    AudioRecord.Builder()
                        .setAudioFormat(format)
                        .setAudioPlaybackCaptureConfig(config)
                        .setBufferSizeInBytes(8192)
                        .build()
                },
                createMicrophoneRecord = {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                    AudioRecord.Builder()
                        .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(8192)
                        .build()
                }
            )
            return PlatformAudioCaptureResources(record, sampleRate, projection, projectionCallback)
        } catch (e: Exception) {
            try {
                projectionCallback?.let { projection?.unregisterCallback(it) }
            } catch (unregisterError: Throwable) {
                Log.w(TAG, "Error unregistering MediaProjection after creation failure", unregisterError)
            }
            try {
                projection?.stop()
            } catch (stopError: Throwable) {
                Log.w(TAG, "Error stopping MediaProjection after creation failure", stopError)
            }
            throw CaptureInitializationException(
                source.failureMessage(AudioCaptureFailureStage.INITIALIZATION),
                e
            )
        }
    }

    private fun startAudioCapture(
        source: AudioCaptureSource,
        resultCode: Int?,
        captureIntent: Intent?
    ) {
        synchronized(coordinator.lock) {
            if (coordinator.currentSession?.isActive == true) {
                return
            }

            val resources = try {
                createPlatformAudioRecord(source, resultCode, captureIntent)
            } catch (e: CaptureInitializationException) {
                reportCaptureFailure(e.message ?: "Hangrögzítési hiba.", e)
                return
            }
            val platformRecord = resources.record
            val sampleRate = resources.sampleRate
            val sessionProjection = resources.projection
            val sessionProjectionCallback = resources.projectionCallback

            if (platformRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize (state != STATE_INITIALIZED)")
                try {
                    sessionProjectionCallback?.let { sessionProjection?.unregisterCallback(it) }
                } catch (unregisterError: Throwable) {
                    Log.w(TAG, "Error unregistering MediaProjection after AudioRecord initialization failure", unregisterError)
                }
                cleanupUninitializedRecord(
                    releaseRecord = { platformRecord.release() },
                    stopProjection = { sessionProjection?.stop() }
                )
                reportCaptureFailure(source.failureMessage(AudioCaptureFailureStage.INITIALIZATION))
                return
            }

            val recordAdapter = object : IAudioRecord {
                private var isReleased = false

                override val state: Int get() = platformRecord.state
                override val recordingState: Int get() = platformRecord.recordingState
                override fun startRecording() = platformRecord.startRecording()
                override fun read(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int =
                    platformRecord.read(audioData, offsetInShorts, sizeInShorts)
                override fun stop() = platformRecord.stop()
                override fun release() {
                    if (isReleased) return
                    isReleased = true
                    try {
                        platformRecord.release()
                    } finally {
                        try {
                            sessionProjectionCallback?.let { sessionProjection?.unregisterCallback(it) }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error unregistering MediaProjection callback", t)
                        }
                        try {
                            sessionProjection?.stop()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error stopping session MediaProjection", t)
                        }
                    }
                }
            }

            var sessionGeneration = 0L
            var session: AudioCaptureSession? = null
            session = AudioCaptureSession(
                record = recordAdapter,
                onError = { t ->
                    reportCaptureFailure(
                        source.failureMessage(AudioCaptureFailureStage.RUNTIME),
                        t
                    )
                },
                onUnexpectedTermination = {
                    session?.let { s ->
                        coordinator.onSessionUnexpectedTermination(s, sessionGeneration)
                    }
                    if (!captureFailureReported.get()) {
                        reportCaptureFailure("A hangrögzítési munkamenet váratlanul leállt.")
                    }
                },
                onStarted = {
                    mainHandler.post {
                        if (coordinator.currentSession === session) {
                            updateCaptureNotification(source.activeStatusMessage())
                            Toast.makeText(
                                applicationContext,
                                "Aktív hangforrás: ${source.displayName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) { record, isRunningPredicate ->
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

                while (isRunningPredicate()) {
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
                        throw IllegalStateException("AudioRecord.read failed with code $read")
                    }
                }
            }

            sessionGeneration = coordinator.attachSession(session)
            activeProjection = sessionProjection
            if (!requireNotNull(session).start("StereoVuAudioCapture")) {
                activeProjection = null
                coordinator.detachForProjectionStop(requireNotNull(session))
                requireNotNull(session).stopAndAwait(WORKER_JOIN_TIMEOUT_MS)
                reportCaptureFailure("A hangrögzítési munkamenet nem indítható el.")
            }
        }
    }

    private fun handleProjectionStopped(projection: MediaProjection) {
        if (activeProjection !== projection) return
        Log.i(TAG, "MediaProjection stopped by the system or user")
        activeProjection = null
        coordinator.currentSession
            ?.let { coordinator.detachForProjectionStop(it) }
            ?.stopAndAwait(WORKER_JOIN_TIMEOUT_MS)
        reportCaptureFailure(
            "A belső hang rögzítési engedélye megszűnt. Indítsd újra a rögzítést."
        )
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)

        activeProjection = null
        val sessionToStop = coordinator.detachForShutdown()

        // Bounded wait for worker thread termination outside lock
        // If join times out, AudioCaptureSession guarantees the recorder and projection are NOT prematurely released
        sessionToStop?.stopAndAwait(WORKER_JOIN_TIMEOUT_MS)

        vuView?.let { windowManager?.removeView(it) }
        vuView = null
        super.onDestroy()
    }
}
