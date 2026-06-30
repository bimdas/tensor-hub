# API Reference

Base URL: `http://127.0.0.1:8190`

## Health Check

```
GET /health
```

Response:
```json
{
  "status": "ok",
  "server": "tensor-hub",
  "version": "0.1.0",
  "models_loaded": [
    { "id": "whisper-base", "delegate": "NNAPI" }
  ]
}
```

## Audio Transcription

```
POST /v1/audio/transcriptions
Content-Type: multipart/form-data
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| file | audio file | Yes | WAV, OGG, MP3, or M4A |
| model | string | No | Model ID (default: whisper-base) |
| language | string | No | Language code (default: en) |
| response_format | string | No | json, text, or verbose_json |

Response (json):
```json
{ "text": "transcribed text here" }
```

Response (verbose_json):
```json
{
  "task": "transcribe",
  "language": "en",
  "duration": 5.2,
  "text": "transcribed text here",
  "segments": [
    { "start": 0.0, "end": 5.2, "text": "transcribed text here" }
  ]
}
```

## Embeddings

```
POST /v1/embeddings
Content-Type: application/json
```

Body:
```json
{
  "model": "bge-small-en-v1.5-q8",
  "input": "text to embed"
}
```

Response:
```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0123, -0.0456, ...]
    }
  ],
  "model": "bge-small-en-v1.5-q8",
  "usage": { "prompt_tokens": 4, "total_tokens": 4 }
}
```

## List Models

```
GET /v1/models
```

Response:
```json
{
  "object": "list",
  "data": [
    {
      "id": "whisper-base",
      "object": "model",
      "owned_by": "tensor-hub",
      "loaded": true
    }
  ]
}
```

## Load Model

```
POST /v1/models/load
Content-Type: application/json
```

Body:
```json
{ "model": "whisper-base" }
```

Response:
```json
{
  "status": "ok",
  "model": "whisper-base",
  "delegate": "NNAPI"
}
```

## Unload Model

```
POST /v1/models/unload
Content-Type: application/json
```

Body:
```json
{ "model": "whisper-base" }
```

Response:
```json
{
  "status": "ok",
  "model": "whisper-base"
}
```
