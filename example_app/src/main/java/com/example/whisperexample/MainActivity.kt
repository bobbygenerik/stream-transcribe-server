package com.example.whisperexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DownloadUI()
            }
        }
    }
}

@Composable
fun DownloadUI() {
    var status by remember { mutableStateOf("Idle") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("https://example.com/path/to/whisper-model.bin") }
    var checksum by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var showIndeterminate by remember { mutableStateOf(false) }
    var transcribePath by remember { mutableStateOf("") }
    var transcriptionResult by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Model downloader")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Model URL") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = checksum, onValueChange = { checksum = it }, label = { Text("SHA-256 (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            status = "Downloading..."
            progress = 0f
            scope.launch(Dispatchers.IO) {
                showIndeterminate = false
                val (res, bytes) = downloadModelWithProgress(ctx, url, "whisper-model.bin") { fraction ->
                    // fraction = -1 means unknown length -> indeterminate
                    if (fraction < 0f) {
                        showIndeterminate = true
                    } else {
                        // progress callback will be invoked on Main dispatcher from inside the download function
                        progress = fraction
                    }
                }
                if (res) {
                    if (checksum.isNotBlank()) {
                        val ok = verifySha256(ctx, "whisper-model.bin", checksum)
                        if (ok) {
                            status = "Download + checksum OK"
                            // initialize model via native wrapper on main thread
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val modelPath = File(ctx.filesDir, "whisper-model.bin").absolutePath
                                    val inited = NativeWhisper.initModel(modelPath)
                                    status = if (inited) "Model initialized" else "Model init failed"
                                } catch (e: Throwable) {
                                    status = "Native init error"
                                }
                            }
                        } else {
                            status = "Checksum mismatch"
                        }
                    } else {
                        status = "Download complete (${bytes} bytes)"
                        // try to init native model without checksum
                        scope.launch(Dispatchers.Main) {
                            try {
                                val modelPath = File(ctx.filesDir, "whisper-model.bin").absolutePath
                                val inited = NativeWhisper.initModel(modelPath)
                                status = if (inited) "Model initialized" else "Model init failed"
                            } catch (e: Throwable) {
                                status = "Native init error"
                            }
                        }
                    }
                } else {
                    status = "Download failed"
                }
            }
        }) {
            Text("Download model")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (showIndeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Status: $status")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = transcribePath, onValueChange = { transcribePath = it }, label = { Text("Audio file path (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            // Call native transcribe on main thread to update UI
            scope.launch(Dispatchers.Main) {
                try {
                    val path = if (transcribePath.isBlank()) File(ctx.filesDir, "whisper-model.bin").absolutePath else transcribePath
                    val out = NativeWhisper.transcribeFile(path, false)
                    transcriptionResult = out
                } catch (e: Throwable) {
                    transcriptionResult = "Transcription failed: ${e.message}"
                }
            }
        }) {
            Text("Transcribe (native)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Transcription: $transcriptionResult")
    }
}

suspend fun downloadModelWithProgress(context: android.content.Context, url: String, fileName: String, progressCallback: (Float) -> Unit): Pair<Boolean, Long> {
    return try {
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        val contentLength = connection.contentLengthLong
        var downloaded: Long = 0
        connection.getInputStream().use { input ->
            val outFile = File(context.filesDir, fileName)
            outFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (contentLength > 0) {
                        val frac = downloaded.toFloat() / contentLength.toFloat()
                        // call callback on main thread
                        withContext(Dispatchers.Main) {
                            progressCallback(frac.coerceIn(0f, 1f))
                        }
                    }
                }
                output.flush()
            }
        }
        Pair(true, downloaded)
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, 0L)
    }
}

fun verifySha256(context: android.content.Context, fileName: String, expectedHex: String): Boolean {
    try {
        val f = File(context.filesDir, fileName)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        val digest = md.digest()
        val actualHex = digest.joinToString("") { String.format("%02x", it) }
        return actualHex.equals(expectedHex.trim().lowercase(), ignoreCase = true)
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}
