package com.andrewshulgin.audioactivatedwebapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

class AudioService : Service() {
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, ENCODING
    )
    private val recordingInProgress = AtomicBoolean(false)
    private var recorder: AudioRecord? = null

    private var rms = 0
    private var peak = 0
    private var wakeRms = -1
    private var wakePeak = -1
    private var debugRms = false

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE)
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "wake_rms" -> {
                wakeRms = prefs.getInt(key, 1000)
            }
            "wake_peak" -> {
                wakePeak = prefs.getInt(key, -1)
            }
            "debug_rms" -> {
                debugRms = prefs.getBoolean(key, false)
            }
            "url" -> {
                val url = prefs.getString(key, "about:blank")
                // TODO
            }
            "wake_timeout" -> {
                val wakeTimeout = prefs.getLong(key, 10)
                // TODO
            }
        }
    }

    private fun calcRms(buffer: ShortArray, length: Int) {
        var abs = 0.0
        peak = 0
        for (i in 0 until length) {
            val v: Int = abs(buffer[i].toInt())
            if (peak < v) {
                peak = v
            }
            val `val` = buffer[i].toDouble()
            abs += `val` * `val`
        }
        rms = sqrt(abs / length.toDouble()).toInt()
    }

    override fun onCreate() {
        super.onCreate()
        wakeRms = prefs.getInt("wake_rms", 1000)
        wakePeak = prefs.getInt("wake_peak", -1)
        debugRms = prefs.getBoolean("debug_rms", false)
        startForeground(NOTIFICATION_ID, createNotification())
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!recordingInProgress.get()) {
            Thread {
                try {
                    val buffer = ShortArray(bufferSize)
                    recorder = AudioRecord(
                        AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferSize * 10
                    )
                    recorder!!.startRecording()
                    recordingInProgress.set(true)

                    while (recordingInProgress.get()) {
                        val result = recorder!!.read(buffer, 0, bufferSize)
                        calcRms(buffer, result)
                        if (debugRms) {
                            Log.d(
                                "RMS", String.format(
                                    "rms=%d peak=%d wake_rms=%d wake_peak=%d",
                                    rms,
                                    peak,
                                    wakeRms,
                                    wakePeak
                                )
                            )
                        }
                        if ((wakeRms in 0 until rms) || (wakePeak in 0 until peak)) {
                            val powerManager =
                                applicationContext.getSystemService(POWER_SERVICE) as PowerManager
                            val wakeLock = powerManager.newWakeLock(
                                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "AudioActivatedWebApp::PreLaunchWakeLock"
                            )
                            wakeLock.acquire(1000)
                            val newIntent = Intent(applicationContext, MainActivity::class.java)
                            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            applicationContext.startActivity(newIntent)
                        }
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }.start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val channelId = getString(R.string.notification_channel_id)
        val title = getString(R.string.notification_title)
        val text = getString(R.string.running)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, title, NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId).setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground))
            .build()
    }


    companion object {
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_ID = 1001
    }
}
