import asyncio
import os
import shlex
import subprocess
import uuid
import json
from datetime import timedelta
from pathlib import Path
from typing import Dict, Optional

from fastapi import FastAPI, HTTPException, UploadFile, File, Form, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
import fastapi.responses

import requests

try:
    from googletrans import Translator
    translator = Translator()
except Exception:
    translator = None

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")
STT_BACKEND = os.environ.get("STT_BACKEND", "openai")

BASE_DIR = Path(__file__).parent
SESSIONS_DIR = BASE_DIR / "sessions"
SESSIONS_DIR.mkdir(exist_ok=True)

app = FastAPI()


class StartRequest(BaseModel):
    stream_url: str
    target_lang: Optional[str] = "en"
    chunk_seconds: Optional[int] = 8


class StopRequest(BaseModel):
    id: str


sessions: Dict[str, Dict] = {}


def start_ffmpeg_segmenter(stream_url: str, outdir: Path, chunk_seconds: int):
    # Use ffmpeg segmenter to write WAV chunks
    outdir.mkdir(parents=True, exist_ok=True)
    template = str(outdir / "chunk_%05d.wav")
    cmd = f"ffmpeg -hide_banner -loglevel error -i {shlex.quote(stream_url)} -vn -ac 1 -ar 16000 -f segment -segment_time {chunk_seconds} -reset_timestamps 1 {shlex.quote(template)}"
    # Start as background process
    proc = subprocess.Popen(shlex.split(cmd))
    return proc


async def transcribe_file(path: Path) -> str:
    # Select backend by environment variable STT_BACKEND (openai | whisperx | faster-whisper | whisper)
    backend = STT_BACKEND.lower() if STT_BACKEND else "openai"

    if backend == "openai" and OPENAI_API_KEY:
        try:
            import openai
            openai.api_key = OPENAI_API_KEY
            with path.open("rb") as f:
                resp = openai.Audio.transcriptions.create(file=f, model="gpt-4o-mini-transcribe")
                return resp.get("text") or ""
        except Exception:
            pass

    if backend == "whisperx":
        try:
            import whisperx
            # whisperx expects a whisper model for segmentation; it will load model internally
            # Use default device selection
            model = whisperx.load_model("small", device="cuda" if torch_available() else "cpu")
            result = whisperx.transcribe(model, str(path))
            # result may contain text
            return result.get("text", "")
        except Exception:
            # fallthrough
            pass

    if backend == "faster-whisper":
        try:
            from faster_whisper import WhisperModel
            # load a small model (cpu or gpu)
            device = "cuda" if torch_available() else "cpu"
            fw_model = WhisperModel("small", device=device)
            segments, info = fw_model.transcribe(str(path), beam_size=5)
            texts = []
            for segment in segments:
                texts.append(segment.text)
            return " ".join(texts)
        except Exception:
            pass

    # Generic whisper fallback
    try:
        import whisper
        model = whisper.load_model("small")
        res = model.transcribe(str(path))
        return res.get("text", "")
    except Exception:
        pass

    # Final fallback: return filename as placeholder
    return path.name


def torch_available() -> bool:
    try:
        import torch
        return torch.cuda.is_available()
    except Exception:
        return False


def translate_text(text: str, target: str) -> str:
    if not translator:
        return text
    try:
        res = translator.translate(text, dest=target)
        return res.text
    except Exception:
        return text


def write_vtt(entries, outpath: Path):
    lines = ["WEBVTT", ""]
    for start_s, end_s, text in entries:
        def fmt(t):
            td = timedelta(seconds=t)
            total = td.total_seconds()
            hrs = int(total // 3600)
            mins = int((total % 3600) // 60)
            secs = total % 60
            return f"{hrs:02}:{mins:02}:{secs:06.3f}".replace(".", ".")

        lines.append(f"{fmt(start_s)} --> {fmt(end_s)}")
        lines.append(text)
        lines.append("")

    outpath.write_text("\n".join(lines))


async def session_worker(sid: str):
    sess = sessions[sid]
    outdir = Path(sess["dir"]) / "chunks"
    vtt_path = Path(sess["dir"]) / "subtitles.vtt"
    entries = []

    processed = set()
    chunk_seconds = sess.get("chunk_seconds", 8)
    while not sess.get("stop"):
        # list chunk files
        files = sorted(outdir.glob("chunk_*.wav"))
        for f in files:
            if f.name in processed:
                continue
            # transcribe
            text = await transcribe_file(f)
            translated = translate_text(text, sess.get("target_lang", "en"))
            # compute timestamps from filename index
            try:
                idx = int(f.stem.split("_")[-1])
            except Exception:
                idx = 0
            start = idx * chunk_seconds
            end = start + chunk_seconds
            entries.append((start, end, translated))
            processed.add(f.name)
            write_vtt(entries, vtt_path)
            # push to connected websockets (if any)
            ws_clients = sess.get("websockets") or set()
            payload = {"start": start, "end": end, "text": translated}
            to_remove = []
            for ws in list(ws_clients):
                try:
                    await ws.send_text(json.dumps(payload))
                except Exception:
                    # mark for cleanup
                    to_remove.append(ws)
            for ws in to_remove:
                ws_clients.discard(ws)
            sess["websockets"] = ws_clients
        await asyncio.sleep(1)


@app.post("/start")
async def start(req: StartRequest):
    sid = uuid.uuid4().hex[:8]
    d = SESSIONS_DIR / sid
    d.mkdir(exist_ok=True)
    sess = {"id": sid, "dir": str(d), "stop": False, "stream_url": req.stream_url, "target_lang": req.target_lang, "chunk_seconds": req.chunk_seconds}
    sessions[sid] = sess
    # start ffmpeg
    proc = start_ffmpeg_segmenter(req.stream_url, d / "chunks", req.chunk_seconds)
    sess["ffmpeg_pid"] = proc.pid
    # start worker
    loop = asyncio.get_event_loop()
    sess["task"] = loop.create_task(session_worker(sid))
    return {"id": sid, "subtitles_url": f"/subtitles/{sid}.vtt", "pid": proc.pid}


@app.post("/stop")
async def stop(req: StopRequest):
    sid = req.id
    if sid not in sessions:
        raise HTTPException(status_code=404, detail="session not found")
    sess = sessions[sid]
    sess["stop"] = True
    proc_pid = sess.get("ffmpeg_pid")
    if proc_pid:
        try:
            os.kill(proc_pid, 9)
        except Exception:
            pass
    task = sess.get("task")
    if task:
        task.cancel()
    return {"stopped": True}


@app.get("/subtitles/{sid}.vtt")
async def get_vtt(sid: str):
    d = SESSIONS_DIR / sid
    vtt = d / "subtitles.vtt"
    if not vtt.exists():
        raise HTTPException(status_code=404, detail="vtt not ready")
    return fastapi.responses.FileResponse(str(vtt), media_type="text/vtt")


@app.post("/upload_chunk")
async def upload_chunk(session_id: str = Form(...), file: UploadFile = File(...)):
    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="session not found")
    sess = sessions[session_id]
    d = Path(sess["dir"]) / "chunks"
    d.mkdir(parents=True, exist_ok=True)
    # compute next index
    existing = sorted(d.glob("chunk_*.wav"))
    if existing:
        last_idx = max(int(p.stem.split("_")[-1]) for p in existing)
    else:
        last_idx = -1
    next_idx = last_idx + 1
    fname = d / f"chunk_{next_idx:05d}.wav"
    # save file
    with fname.open("wb") as out:
        content = await file.read()
        out.write(content)
    return {"saved": str(fname.name)}


@app.websocket("/ws/{sid}")
async def websocket_endpoint(websocket: WebSocket, sid: str):
    await websocket.accept()
    if sid not in sessions:
        await websocket.close(code=1008)
        return
    sess = sessions[sid]
    ws_clients = sess.get("websockets") or set()
    ws_clients.add(websocket)
    sess["websockets"] = ws_clients
    try:
        while True:
            # keep connection alive; server pushes captions
            await websocket.receive_text()
    except WebSocketDisconnect:
        ws_clients.discard(websocket)
        sess["websockets"] = ws_clients
