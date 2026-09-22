package com.ilanslava.sandie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var songList: LinearLayout
    private lateinit var nowPlaying: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) scanMusic()
            else status.text = "Music access is needed to scan your files"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        songList = findViewById(R.id.songList)
        nowPlaying = findViewById(R.id.nowPlaying)

        findViewById<Button>(R.id.scanButton).setOnClickListener { requestOrScan() }
        findViewById<Button>(R.id.reverbButton).setOnClickListener {
            startPlaybackService(PlaybackService.ACTION_TOGGLE_REVERB)
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            startPlaybackService(PlaybackService.ACTION_STOP)
            nowPlaying.text = "Stopped"
        }

        requestOrScan()
    }

    private fun requestOrScan() {
        val permissions = buildList {
            add(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) scanMusic() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun scanMusic() {
        status.text = "Scanning music..."
        songList.removeAllViews()

        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) {
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
                )
                val result = mutableListOf<Triple<Uri, String, String>>()

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
                        result += Triple(Uri.withAppendedPath(collection, id.toString()), title, artist)
                    }
                }
                result
            }

            songList.removeAllViews()
            songs.forEach { (uri, title, artist) ->
                val button = Button(this@MainActivity).apply {
                    text = "$title\n$artist"
                    isAllCaps = false
                    setOnClickListener {
                        startPlaybackService(PlaybackService.ACTION_PLAY, uri, title)
                        nowPlaying.text = "Playing: $title"
                    }
                }
                songList.addView(button)
            }

            status.text = if (songs.isEmpty()) {
                "No music found. Put an MP3 on your phone and scan again."
            } else {
                "${songs.size} song(s) found"
            }
        }
    }

    private fun startPlaybackService(action: String, uri: Uri? = null, title: String? = null) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
            if (uri != null) putExtra(PlaybackService.EXTRA_URI, uri.toString())
            if (title != null) putExtra(PlaybackService.EXTRA_TITLE, title)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
