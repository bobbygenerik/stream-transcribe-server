// Minimal stub for builds where whisper.cpp isn't available.
#include <jni.h>
#include <string>
#include <android/log.h>

static const char* TAG = "whisper_stub";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_whisper_NativeWhisper_initModel(JNIEnv* env, jobject /* this */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "(stub) initModel: %s", path);
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whisper_NativeWhisper_transcribeFile(JNIEnv* env, jobject /* this */, jstring filePath, jboolean translate) {
    const char* path = env->GetStringUTFChars(filePath, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "(stub) transcribeFile: %s translate=%d", path, translate);
    env->ReleaseStringUTFChars(filePath, path);
    std::string out = "(stub transcription)";
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whisper_NativeWhisper_transcribeBuffer(JNIEnv* env, jobject /* this */, jbyteArray pcm, jint length, jint sampleRate, jboolean translate) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "(stub) transcribeBuffer: len=%d sr=%d translate=%d", length, sampleRate, translate);
    std::string out = "(stub transcription from buffer)";
    return env->NewStringUTF(out.c_str());
}
