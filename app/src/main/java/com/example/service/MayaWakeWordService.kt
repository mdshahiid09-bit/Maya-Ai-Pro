package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MayaApplication
import com.example.R
import com.example.engine.AudioDestination

class MayaWakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "maya_wake_word_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "ACTION_TOGGLE_PAUSE"
        const val ACTION_TRIGGER_TALK = "ACTION_TRIGGER_TALK"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPaused: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                isPaused = !isPaused
                val app = application as? MayaApplication
                if (isPaused) {
                    app?.audioPipeline?.destination = AudioDestination.MUTED
                } else {
                    app?.audioPipeline?.destination = AudioDestination.WAKE_WORD
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_TRIGGER_TALK -> {
                val app = application as? MayaApplication
                app?.audioPipeline?.playChime()
                app?.geminiLiveClient?.startSession()
                openMainActivity(wakeUp = true)
                return START_STICKY
            }
            else -> {
                isRunning = true
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification)

                val app = application as? MayaApplication
                app?.audioPipeline?.onWakeWordDetected = {
                    if (!isPaused) {
                        openMainActivity(wakeUp = true)
                        app.geminiLiveClient.startSession()
                    }
                }
                app?.audioPipeline?.start()
            }
        }
        return START_STICKY
    }

    private fun openMainActivity(wakeUp: Boolean) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_START_TALK", wakeUp)
        }
        startActivity(launchIntent)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MayaWakeWordService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val talkIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MayaWakeWordService::class.java).apply { action = ACTION_TRIGGER_TALK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseTitle = if (isPaused) "Resume" else "Pause"
        val statusText = if (isPaused) "Listening paused" else "Listening for 'Hey Maya'..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Maya Voice Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseTitle, pauseIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk", talkIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maya Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Maya ready for hands-free voice commands."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
