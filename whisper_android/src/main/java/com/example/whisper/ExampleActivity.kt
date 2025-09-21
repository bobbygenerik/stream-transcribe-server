package com.example.whisper

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ExampleActivity : Activity() {
    companion object { const val REQ = 101 }
    lateinit var mp: MediaProjection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start capture consent
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mp = mpm.getMediaProjection(resultCode, data)
            startLocalCapture()
        }
    }

    fun startLocalCapture() {
        GlobalScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            recorder.startRecording()
            val buffer = ByteArray(sampleRate * 2 * 4)
            while (true) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    // Ideally call NativeWhisper.transcribeBuffer
                    val tmpPath = filesDir.absolutePath + "/chunk.wav"
                    // write WAV and call transcribeFile for prototype
                    val out = NativeWhisper.transcribeFile(tmpPath, true)
                }
            }
        }
    }
}
