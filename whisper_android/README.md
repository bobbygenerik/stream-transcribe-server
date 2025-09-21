whisper.cpp Android module scaffold
=================================

What this is
------------
This folder contains a minimal Android library module scaffold to integrate whisper.cpp (GGML) on-device via a JNI wrapper.
It does NOT include prebuilt native binaries or model files. You must build the native code with the Android NDK on your machine and download a GGML model.

Overview
--------
- `src/main/cpp/whisper_jni.cpp` — JNI entry points (C++) that load the model and expose simple `transcribeFile` and `transcribeBuffer` functions.
- `src/main/java/com/example/whisper/NativeWhisper.kt` — Kotlin wrapper to call the native functions.
- `CMakeLists.txt` — cmake to build the JNI library, link to whisper.cpp sources (you'll add them or use a submodule).
- `build.gradle` — Android library module configuration.

Prerequisites
-------------
- Android Studio (or command-line Gradle) and Android NDK installed.
- CMake installed.
- A local checkout of `whisper.cpp` (https://github.com/ggerganov/whisper.cpp) somewhere on disk. We'll reference it when building.
- A GGML model file (e.g., `ggml-small.bin`). Place it in app storage at runtime or bundle via expansion file.

Quick build & test (local machine)
---------------------------------
1. Clone whisper.cpp somewhere, for example:

   ```bash
   git clone https://github.com/ggerganov/whisper.cpp /path/to/whisper.cpp
   ```

2. In Android Studio, import this directory as a module or add it to your existing app's settings.gradle:

   settings.gradle:
   ```groovy
   include ':whisper_android'
   project(':whisper_android').projectDir = file('whisper_android')
   ```

3. Edit `CMakeLists.txt` inside this module to point to the location of your `whisper.cpp` sources. We ship a template; update `WHISPER_CPP_DIR` accordingly.

4. Build the project in Android Studio. The NDK will compile `libwhisper_jni.so` for selected ABIs.

5. On first run, download a ggml model (recommended `ggml-small.bin`) and place it in your app files directory. Example downloader (in your app):

   ```kotlin
   val modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/models/ggml-small.bin"
   // download model to filesDir/ggml-small.bin
   ```

6. Use `NativeWhisper.transcribeFile(path, translate)` from Kotlin to transcribe.

Notes & tips
------------
- Building whisper.cpp for Android may require tweaking compiler flags and disabling SSE/AVX. See `whisper.cpp` README for Android build tips.
- On-device translation: whisper.cpp supports a `translate` mode; ensure your JNI wrapper passes the argument.
- Model selection: `ggml-tiny.bin` is fastest but least accurate; `ggml-small.bin` is a good balance.

Security & privacy
------------------
- Models and transcription run entirely on-device; no audio leaves the device unless your app sends it off-device.

Support
-------
If you want, I can: create a full Android Studio sample project, or provide more detailed NDK/CMake flags tuned for specific CPU architectures. Tell me which ABI targets (armeabi-v7a, arm64-v8a) you need.
