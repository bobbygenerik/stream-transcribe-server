Stream transcription & translation prototype (Option A)
===============================================

This prototype implements Option A: a small server that pulls an IPTV stream, splits it into short audio chunks with ffmpeg, transcribes each chunk, translates the text, and assembles a WebVTT subtitle file you can point your player at.

Features
- Starts a background ffmpeg process to segment audio from a stream.
- Transcribes each segment using OpenAI (if OPENAI_API_KEY is set) or local Whisper if installed.
- Translates using googletrans if available; otherwise returns original text.
- Writes a progressive WebVTT file and serves it at /subtitles/{id}.vtt

Requirements
- ffmpeg must be installed and on PATH.
- Python 3.10+
- Optional: OpenAI API key in environment (OPENAI_API_KEY) to use OpenAI speech-to-text.
- Optional: whisper (openai-whisper) and torch if you want local transcription fallback.
- Optional: googletrans for translation (pip package: googletrans==4.0.0-rc1).

Advanced STT backends
- WhisperX: higher-accuracy timestamps. Install instructions are at https://github.com/m-bain/whisperX. Requires PyTorch and CUDA for best speed.
- Faster-Whisper: fast CPU/GPU inference. Install via pip: `pip install faster-whisper` and follow model download instructions.

To select a backend, set the environment variable `STT_BACKEND` to one of: `openai`, `whisperx`, `faster-whisper`, or `whisper`.
If `openai` is selected, `OPENAI_API_KEY` must be set.

Install (inside the dev container or server):

```bash
cd /workspaces/codespaces-blank/stream_transcribe_server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
# If you want local whisper transcription, follow instructions at https://github.com/openai/whisper
```

Run the server:

```bash
uvicorn server:app --host 0.0.0.0 --port 5000
```

Start processing a stream (example):

```bash
curl -X POST http://127.0.0.1:5000/start \
  -H 'Content-Type: application/json' \
  -d '{"stream_url":"https://example.com/live/stream.m3u8","target_lang":"en","chunk_seconds":8}'

# Response will include a session id and a subtitles URL you can add to your player.
```

Stop processing a session:

```bash
curl -X POST http://127.0.0.1:5000/stop -H 'Content-Type: application/json' -d '{"id":"<session-id>"}'
```

Notes
- This is a prototype: production should handle errors, authentication, scaling, and rate limits.
- Latency depends on chunk_seconds and chosen transcription backend.

If you prefer on-device transcription (no server), see the `../whisper_android` module scaffold. It shows how to integrate `whisper.cpp` via JNI and capture audio on the device.
