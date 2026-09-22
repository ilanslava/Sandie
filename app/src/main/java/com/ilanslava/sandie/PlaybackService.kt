package com.ilanslava.sandie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var reverb: PresetReverb? = null
    private var reverbEnabled = false
    private var currentTitle = "Luna"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown track"
                if (uri != null) play(uri)
            }
            ACTION_TOGGLE_REVERB -> {
                reverbEnabled = !reverbEnabled
                applyReverb()
                updateNotification(if (reverbEnabled) "Reverb ON" else "Reverb OFF")
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    private fun play(uri: Uri) {
        stopPlayback(updateStatus = false)

        player = MediaPlayer.create(this, uri)
        if (player == null) {
            updateNotification("Could not play $currentTitle")
            return
        }

        player?.setOnCompletionListener {
            updateNotification("Finished: $currentTitle")
            stopPlayback()
        }

        applyReverb()
        player?.start()
        updateNotification(if (reverbEnabled) "Playing • Reverb ON" else "Playing")
    }

    private fun applyReverb() {
        reverb?.release()
        reverb = null

        val currentPlayer = player ?: return
        if (!reverbEnabled) {
            currentPlayer.setAuxEffectSendLevel(0f)
            return
        }

        try {
            val effect = PresetReverb(0, currentPlayer.audioSessionId)
            effect.preset = PresetReverb.PRESET_LARGEHALL
            effect.enabled = true

            // PresetReverb is an auxiliary effect. It must be attached to
            // the MediaPlayer and receive a non-zero send level.
            currentPlayer.attachAuxEffect(effect.id)
            currentPlayer.setAuxEffectSendLevel(1.0f)

            reverb = effect
        } catch (_: Exception) {
            reverbEnabled = false
            currentPlayer.setAuxEffectSendLevel(0f)
            updateNotification("Reverb unavailable on this device")
        }
    }

    private fun stopPlayback(updateStatus: Boolean = true) {
        reverb?.release()
        reverb = null
        player?.release()
        player = null
        if (updateStatus) updateNotification("Ready")
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(status)
            .setOngoing(player?.isPlaying == true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Luna playback",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopPlayback(updateStatus = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "com.ilanslava.sandie.PLAY"
        const val ACTION_TOGGLE_REVERB = "com.ilanslava.sandie.TOGGLE_REVERB"
        const val ACTION_STOP = "com.ilanslava.sandie.STOP"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "luna_playback"
        private const val NOTIFICATION_ID = 200
    }
}
