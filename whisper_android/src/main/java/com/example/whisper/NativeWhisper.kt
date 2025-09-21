package com.example.whisper

object NativeWhisper {
    init { System.loadLibrary("whisper_jni") }

    external fun initModel(modelPath: String): Boolean
    external fun transcribeFile(path: String, translate: Boolean): String
    external fun transcribeBuffer(pcm: ByteArray, length: Int, sampleRate: Int, translate: Boolean): String
}
