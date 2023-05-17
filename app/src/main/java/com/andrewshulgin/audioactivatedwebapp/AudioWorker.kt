package com.andrewshulgin.audioactivatedwebapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt


class AudioWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    companion object {
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_ID = 1337
    }

    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
    private val notificationManager = applicationContext.getSystemService<NotificationManager>()!!
    private val recordingInProgress = AtomicBoolean(false)
    private var recorder: AudioRecord? = null

    private var rms = 0
    private var peak = 0
    private var wakeRms = -1
    private var wakePeak = -1
    private var debugRms = false

    override suspend fun doWork(): Result {
        val id = applicationContext.getString(R.string.notification_channel_id)
        val title = applicationContext.getString(R.string.notification_title)

        wakeRms = inputData.getInt("wake_rms", 1000)
        wakePeak = inputData.getInt("wake_peak", -1)
        debugRms = inputData.getBoolean("debug_rms", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        val notification = NotificationCompat.Builder(applicationContext, id).setContentTitle(title)
            .setTicker(title).setContentText(applicationContext.getString(R.string.running))
            .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).build()

        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
        main()

        return Result.success()
    }

    private fun main() {
        try {
            val buffer = ShortArray(bufferSize)
            recorder = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufferSize * 10
            )
            recorder!!.startRecording()
            recordingInProgress.set(true)

            while (recordingInProgress.get()) {
                val result = recorder!!.read(buffer, 0, bufferSize)
                calcRms(buffer, result)
                if (debugRms) {
                    Log.d("RMS", String.format(
                        "rms=%d peak=%d wake_rms=%d wake_peak=%d",
                        rms, peak, wakeRms, wakePeak
                    ))
                }
                if ((wakeRms in 0 until rms) || (wakePeak in 0 until peak)) {
                    val intent = Intent(applicationContext, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    applicationContext.startActivity(intent)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val id = applicationContext.getString(R.string.notification_channel_id)
        val name = applicationContext.getString(R.string.notification_channel_title)
        val descriptionText =
            applicationContext.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(id, name, importance)
        mChannel.description = descriptionText
        notificationManager.createNotificationChannel(mChannel)
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
}
