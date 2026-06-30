# Architecture

## System Overview

```
┌─────────────────────────────────────────┐
│            Tensor Hub (Android)          │
├─────────────────────────────────────────┤
│  Jetpack Compose UI                     │
│  ├── Home: Start/stop server, status    │
│  ├── Models: Download, load, manage     │
│  └── Settings: Port, delegate, info     │
├─────────────────────────────────────────┤
│  Foreground Service (InferenceService)  │
│  ├── NotificationCompat + startForeground│
│  ├── Keeps server alive in background   │
│  └── WAKE_LOCK for CPU persistence      │
├─────────────────────────────────────────┤
│  HTTP Server (NanoHTTPD on 127.0.0.1)  │
│  ├── /health                            │
│  ├── /v1/audio/transcriptions           │
│  ├── /v1/embeddings                     │
│  ├── /v1/models                         │
│  └── /v1/models/load, /unload           │
├─────────────────────────────────────────┤
│  Inference Engine                       │
│  ├── Whisper Pipeline (audio→text)      │
│  ├── Embedding Pipeline (text→vector)   │
│  └── Model Manager (download/cache)     │
├─────────────────────────────────────────┤
│  LiteRT Runtime                         │
│  ├── NNAPI Delegate → TPU/GPU           │
│  ├── GPU Delegate (fallback)            │
│  └── CPU (always works)                 │
└─────────────────────────────────────────┘
```

## Key Design Decisions

### 1. Localhost-Only Binding
The HTTP server binds strictly to `127.0.0.1`. This means:
- Accessible from Termux on the same device
- Accessible via ADB port forwarding from a PC
- **Never** exposed to Wi-Fi or cellular networks
- No authentication needed (local-only)

### 2. Foreground Service
Android aggressively kills background processes. The inference server runs as a foreground service with:
- `NotificationCompat` for the persistent notification
- `startForeground()` called within 5 seconds of service start
- `START_STICKY` for automatic restart
- `WAKE_LOCK` to prevent CPU sleep

### 3. NNAPI Delegate Priority
On Pixel devices with Tensor chips, NNAPI routes inference to the TPU automatically. The delegate selection order:
1. NNAPI (TPU/GPU) - fastest on Pixel
2. GPU delegate - fallback for non-Pixel
3. CPU - always works

### 4. OpenAI-Compatible API
Existing tools (whisper clients, embedding libraries) work without modification. The API format matches OpenAI's specification.

## Data Flow: Transcription

```
Audio file (WAV/OGG/MP3)
    ↓
AudioProcessor.decode() → PCM samples
    ↓
AudioProcessor.resample() → 16kHz mono
    ↓
MelFilterBank.compute() → mel spectrogram
    ↓
WhisperEncoder.run() → encoder output
    ↓
WhisperDecoder.run() → token sequence
    ↓
Tokenizer.decode() → text string
    ↓
JSON response (OpenAI format)
```

## Security Model

- No network exposure (127.0.0.1 only)
- No API keys required
- Models stored in app-private storage
- No data leaves the device
- No analytics or telemetry
