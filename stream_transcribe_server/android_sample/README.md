Kotlin sample: AudioPlaybackCapture -> chunk upload -> WebSocket captions
=====================================================================

This folder contains a minimal Kotlin sketch (snippets) showing how to:
- request MediaProjection permission,
- start AudioPlaybackCapture and read PCM chunks,
- upload chunks to the server `/upload_chunk` endpoint,
- connect to the server WebSocket `/ws/{sid}` to receive caption segments,
- draw an accessibility overlay using `TYPE_ACCESSIBILITY_OVERLAY`.

This is a reference for integrating into your AccessibilityService or companion Activity. The code is not a full Android project — copy relevant parts into your app.
