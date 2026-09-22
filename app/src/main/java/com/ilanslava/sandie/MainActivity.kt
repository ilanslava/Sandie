package com.ilanslava.sandie

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var songList: LinearLayout
    private lateinit var nowPlaying: TextView
    private var player: MediaPlayer? = null
    private var reverb: PresetReverb? = null
    private var reverbEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        songList = findViewById(R.id.songList)
        nowPlaying = findViewById(R.id.nowPlaying)

        findViewById<Button>(R.id.scanButton).setOnClickListener { requestOrScan() }
        findViewById<Button>(R.id.reverbButton).setOnClickListener { toggleReverb() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopPlayback() }
        requestOrScan()
    }

    private fun requestOrScan() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        } else {
            scanMusic()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 100 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            status.text = "Music access is needed to scan your files"
        }
    }

    private fun scanMusic() {
        songList.removeAllViews()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        var count = 0
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown track"
                val artist = cursor.getString(artistColumn) ?: "Unknown artist"
                val button = Button(this).apply {
                    text = "$title\n$artist"
                    isAllCaps = false
                    setOnClickListener {
                        play(Uri.withAppendedPath(collection, id.toString()), title)
                    }
                }
                songList.addView(button)
                count++
            }
        }
        status.text = "$count song(s) found"
        if (count == 0) status.text = "No music found. Put an MP3 on your phone and scan again."
    }

    private fun play(uri: Uri, title: String) {
        stopPlayback()
        player = MediaPlayer.create(this, uri)
        player?.setOnCompletionListener { nowPlaying.text = "Finished: $title" }
        player?.start()
        applyReverb()
        nowPlaying.text = "Playing: $title"
    }

    private fun toggleReverb() {
        reverbEnabled = !reverbEnabled
        findViewById<Button>(R.id.reverbButton).text =
            if (reverbEnabled) "Reverb: ON" else "Reverb: OFF"
        applyReverb()
    }

    private fun applyReverb() {
        reverb?.release()
        reverb = null
        val audioSession = player?.audioSessionId ?: return
        if (reverbEnabled) {
            reverb = PresetReverb(0, audioSession).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = true
            }
        }
    }

    private fun stopPlayback() {
        reverb?.release()
        reverb = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
