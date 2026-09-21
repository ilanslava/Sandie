package com.ilanslava.sandie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var wakePhrase: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        wakePhrase = findViewById(R.id.wakePhrase)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            if (!hasMicPermission()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_MIC
                )
            } else {
                startVoiceService()
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            status.text = "Stopped"
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun startVoiceService() {
        val phrase = wakePhrase.text.toString().trim().ifBlank { "Hey Sandie" }
        val intent = Intent(this, VoiceService::class.java)
            .putExtra(VoiceService.EXTRA_WAKE_PHRASE, phrase)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Listening for: $phrase"
    }

    companion object {
        private const val REQUEST_MIC = 1001
    }
}
