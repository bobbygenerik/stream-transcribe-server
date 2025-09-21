#include <jni.h>
#include <string>
#include <android/log.h>

static const char* TAG = "whisper_jni";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_whisper_NativeWhisper_initModel(JNIEnv* env, jobject /* this */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "initModel: %s", path);
    // TODO: load model with whisper.cpp APIs
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whisper_NativeWhisper_transcribeFile(JNIEnv* env, jobject /* this */, jstring filePath, jboolean translate) {
    const char* path = env->GetStringUTFChars(filePath, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "transcribeFile: %s translate=%d", path, translate);
    // TODO: call whisper.cpp transcribe implementation
    std::string out = "(transcription placeholder)";
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whisper_NativeWhisper_transcribeBuffer(JNIEnv* env, jobject /* this */, jbyteArray pcm, jint length, jint sampleRate, jboolean translate) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "transcribeBuffer: len=%d sr=%d translate=%d", length, sampleRate, translate);
    // TODO: convert pcm jbyteArray to native buffer and call whisper
    std::string out = "(transcription placeholder from buffer)";
    return env->NewStringUTF(out.c_str());
}
