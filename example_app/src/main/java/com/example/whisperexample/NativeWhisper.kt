package com.example.whisperexample

object NativeWhisper {
    init {
        try {
            System.loadLibrary("whisper_jni")
        } catch (e: UnsatisfiedLinkError) {
            // library not present; native calls will fail
            e.printStackTrace()
        }
    }

    external fun initModel(modelPath: String): Boolean

    // stub for other native calls
    external fun transcribeFile(filePath: String, translate: Boolean): String
}
