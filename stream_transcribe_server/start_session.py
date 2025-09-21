import requests
import sys

if len(sys.argv) < 2:
    print("Usage: start_session.py <stream_url> [target_lang] [chunk_seconds]")
    sys.exit(1)

stream = sys.argv[1]
lang = sys.argv[2] if len(sys.argv) > 2 else "en"
chunk = int(sys.argv[3]) if len(sys.argv) > 3 else 8

resp = requests.post("http://127.0.0.1:5000/start", json={"stream_url": stream, "target_lang": lang, "chunk_seconds": chunk})
print(resp.json())
