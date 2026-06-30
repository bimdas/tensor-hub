# Tensor Hub

On-device AI inference server for Google Pixel phones. Runs ML models locally on the Tensor TPU/GPU via LiteRT and exposes an OpenAI-compatible HTTP API.

## What It Does

Tensor Hub turns your Pixel into a local AI server. Run Whisper for transcription, embedding models for semantic search, and more, all on-device with hardware acceleration. No cloud, no API keys, no data leaving your phone.

## Features

- **OpenAI-compatible API** - Drop-in replacement for cloud endpoints
- **Hardware-accelerated** - LiteRT with NNAPI delegate targets Tensor TPU/GPU
- **Whisper transcription** - Local speech-to-text (tiny/base/small models)
- **Text embeddings** - Local embedding generation for RAG/search
- **Foreground service** - Runs in background with persistent notification
- **Model management** - Download, cache, load/unload models from the UI

## Quick Start

1. Build and install the APK on your Pixel device
2. Open Tensor Hub, tap "Start Server"
3. The server runs on `http://localhost:8190`

```bash
# Test transcription
curl -X POST http://localhost:8190/v1/audio/transcriptions \
  -F "file=@audio.wav" \
  -F "model=whisper-base" \
  -F "response_format=json"

# Test embeddings
curl -X POST http://localhost:8190/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model": "bge-small-en-v1.5-q8", "input": "hello world"}'

# Health check
curl http://localhost:8190/health
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Server status, loaded models, active delegate |
| `/v1/audio/transcriptions` | POST | Whisper transcription (multipart audio upload) |
| `/v1/embeddings` | POST | Text embeddings |
| `/v1/models` | GET | List available models |
| `/v1/models/load` | POST | Load a model into memory |
| `/v1/models/unload` | POST | Unload a model |

## Supported Models

| Model | Size | Use Case | Speed (Tensor G1) |
|-------|------|----------|-------------------|
| Whisper Tiny (INT8) | ~75MB | Fast transcription | ~2-3s |
| Whisper Base (INT8) | ~141MB | Balanced | ~5-8s |
| Whisper Small (INT8) | ~466MB | Accurate | ~15-25s |
| bge-small-en-v1.5-q8 | ~33MB | Text embeddings | ~100ms |

## Hardware Acceleration

Tensor Hub uses LiteRT's delegate system to automatically route inference to the best available hardware:

1. **NNAPI** (Tensor TPU/GPU) - Preferred, fastest on Pixel devices
2. **GPU delegate** - Fallback for non-Pixel Android devices
3. **CPU** - Always works, slowest

On first launch, Tensor Hub benchmarks each delegate and selects the fastest one.

## Building

```bash
# Clone
git clone git@github.com:taoleeeee/tensor-hub.git
cd tensor-hub

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Requirements

- Android 10+ (API 29)
- Pixel device recommended for TPU acceleration
- ~500MB free storage for models

## Architecture

```
┌─────────────────────────────────────┐
│           Tensor Hub App            │
├─────────────────────────────────────┤
│  Jetpack Compose UI                 │
│  ├── Home (server status)           │
│  ├── Models (download/manage)       │
│  └── Settings (port, delegate)      │
├─────────────────────────────────────┤
│  HTTP Server (NanoHTTPD :8190)      │
│  ├── /v1/audio/transcriptions       │
│  ├── /v1/embeddings                 │
│  ├── /v1/models                     │
│  └── /health                        │
├─────────────────────────────────────┤
│  Inference Engine                   │
│  ├── Whisper Pipeline               │
│  ├── Embedding Pipeline             │
│  └── Model Manager                  │
├─────────────────────────────────────┤
│  LiteRT Runtime                     │
│  ├── NNAPI Delegate (TPU/GPU)       │
│  ├── GPU Delegate (fallback)        │
│  └── CPU (fallback)                 │
└─────────────────────────────────────┘
```

## License

MIT

<!-- rebuild 1782808043 -->
