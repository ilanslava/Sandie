package com.ilanslava.sandie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoiceService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phrase = intent?.getStringExtra(EXTRA_WAKE_PHRASE) ?: "Hey Sandie"

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sandie is listening")
            .setContentText("Wake phrase: $phrase")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // First prototype: the foreground microphone service is now the
        // foundation for the continuous wake-word engine.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sandie voice assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_WAKE_PHRASE = "wake_phrase"
        private const val CHANNEL_ID = "sandie_voice"
        private const val NOTIFICATION_ID = 100
    }
}
