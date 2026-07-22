package com.example.stereovu

import android.app.*
import android.content.Context
import android.content.Intent
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

class FloatingVuService : Service() {
    private var windowManager: WindowManager? = null
    private var vuView: StereoVuView? = null
    private var audioRecord: AudioRecord? = null
    private var running = true
    private var amplitudeCount = 32767.0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val captureIntent = intent?.getParcelableExtra<Intent>("captureIntent")
        
        // Frissítjük a beállításokat induláskor
        val prefs = getSharedPreferences("StereoVuPrefs", Context.MODE_PRIVATE)
        amplitudeCount = prefs.getInt("amplitude_count", 32767).toDouble()
        
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
        if (audioRecord != null) return
        thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && captureIntent != null) {
                    val mgr = getSystemService(MediaProjectionManager::class.java)
                    val projection = mgr.getMediaProjection(-1, captureIntent)
                    val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
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
                        .setSampleRate(44100)
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
                while (running) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sumL = 0.0
                        var sumR = 0.0
                        var count = 0
                        var i = 0
                        
                        // Dinamikus Amplitude Count beállítás alkalmazása
                        val maxAmp = amplitudeCount
                        
                        while (i < read - 1) {
                            val l = buffer[i].toDouble() / maxAmp
                            val r = buffer[i+1].toDouble() / maxAmp
                            sumL += l*l
                            sumR += r*r
                            count++
                            i+=2
                        }
                        val rmsL = Math.sqrt(sumL / count).toFloat()
                        val rmsR = Math.sqrt(sumR / count).toFloat()
                        vuView?.post { vuView?.updateLevels(rmsL, rmsR) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        running = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        vuView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
