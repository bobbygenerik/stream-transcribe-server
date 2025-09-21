Local build instructions for `whisper_android` module
=====================================================

This module is a scaffold for integrating `whisper.cpp` into an Android project via JNI. You should run the build on a machine with Android SDK/NDK (Android Studio recommended).

Steps:
1. Clone `whisper.cpp` somewhere on your machine, e.g. `/home/you/whisper.cpp`.
2. Edit `whisper_android/CMakeLists.txt` and set `WHISPER_CPP_DIR` to that path.
3. Open the project in Android Studio or run the Gradle wrapper from the project root:

   ./gradlew :whisper_android:assembleDebug

4. If CMake fails, open the C++ build output in Android Studio and follow the missing include/library hints.

Model note:
- Download a GGML model (e.g. `ggml-small.bin`) and place it in your app's files directory at runtime. Do NOT bundle large models inside the APK.

If you hit build errors, copy the last 200 lines of the Gradle build log and paste them here and I'll help diagnose.
