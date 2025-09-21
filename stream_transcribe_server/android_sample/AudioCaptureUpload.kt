/*
Conceptual Kotlin snippet. Copy into your app and adapt.
Requires coroutine support and OkHttp for uploads and WebSocket.
*/

import android.app.Activity
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException

class CaptureActivity : Activity() {
    companion object { const val REQ = 1234 }
    lateinit var mp: MediaProjection
    var recorder: AudioRecord? = null
    val client = OkHttpClient()
    var sessionId: String = "" // set after POST /start

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mp = mpm.getMediaProjection(resultCode, data)
            startCapture(mp)
        }
    }

    fun startCapture(mediaProjection: MediaProjection) {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        recorder = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build())
            .setBufferSizeInBytes(minBuf * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        recorder?.startRecording()

        GlobalScope.launch(Dispatchers.IO) {
            val chunkSeconds = 4
            val bufSize = sampleRate * 2 * chunkSeconds
            val buffer = ByteArray(bufSize)
            while (isActive) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // wrap into WAV bytes
                    val wav = pcmToWav(buffer, sampleRate, 1, 16)
                    uploadChunk(wav)
                }
            }
        }
    }

    fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        // Minimal WAV header writer
        val baos = ByteArrayOutputStream()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataSize = pcm.size
        try {
            // RIFF header
            baos.write("RIFF".toByteArray())
            baos.write(intToByteArray(36 + dataSize))
            baos.write("WAVE".toByteArray())
            baos.write("fmt ".toByteArray())
            baos.write(intToByteArray(16))
            baos.write(shortToByteArray(1)) // PCM
            baos.write(shortToByteArray(channels))
            baos.write(intToByteArray(sampleRate))
            baos.write(intToByteArray(byteRate))
            baos.write(shortToByteArray((channels * bitsPerSample / 8).toShort().toInt()))
            baos.write(shortToByteArray(bitsPerSample))
            baos.write("data".toByteArray())
            baos.write(intToByteArray(dataSize))
            baos.write(pcm)
        } catch (e: IOException) {}
        return baos.toByteArray()
    }

    fun intToByteArray(i: Int): ByteArray {
        return byteArrayOf((i and 0xff).toByte(), ((i shr 8) and 0xff).toByte(), ((i shr 16) and 0xff).toByte(), ((i shr 24) and 0xff).toByte())
    }

    fun shortToByteArray(s: Int): ByteArray {
        return byteArrayOf((s and 0xff).toByte(), ((s shr 8) and 0xff).toByte())
    }

    fun uploadChunk(wav: ByteArray) {
        val req = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("session_id", sessionId)
            .addFormDataPart("file", "chunk.wav", RequestBody.create(MediaType.parse("audio/wav"), wav))
            .build()

        val request = Request.Builder()
            .url("http://127.0.0.1:5000/upload_chunk")
            .post(req)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
