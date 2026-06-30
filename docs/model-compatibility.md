# Model Compatibility

## Supported Formats

Tensor Hub uses **LiteRT** (TensorFlow Lite successor) with `.tflite` model files. Models must be:
- Quantized to INT8 for best TPU performance
- Converted to TFLite format
- Compatible with NNAPI ops (most standard ops are supported)

## Whisper Models

| Model | Params | INT8 Size | Latency (Tensor G1) | Accuracy |
|-------|--------|-----------|---------------------|----------|
| whisper-tiny | 39M | ~75MB | 2-3s | Good for clear speech |
| whisper-base | 74M | ~141MB | 5-8s | Good for most use cases |
| whisper-small | 244M | ~466MB | 15-25s | High accuracy |

### Converting Whisper to TFLite

```python
# Using optimum or tflite_model_maker
from transformers import WhisperForConditionalGeneration
import tensorflow as tf

# Load and convert
model = WhisperForConditionalGeneration.from_pretrained("openai/whisper-base")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.int8]
tflite_model = converter.convert()

with open("whisper-base-int8.tflite", "wb") as f:
    f.write(tflite_model)
```

## Embedding Models

| Model | Dimensions | INT8 Size | Latency |
|-------|-----------|-----------|---------|
| bge-small-en-v1.5-q8 | 384 | ~33MB | ~100ms |
| all-mpnet-base-v2 | 768 | ~110MB | ~200ms |

## Image Classification (Future)

| Model | Classes | INT8 Size | Latency |
|-------|---------|-----------|---------|
| EfficientNet-B0 | 1000 | ~20MB | ~30ms |
| MobileNetV3-Small | 1000 | ~6MB | ~15ms |

## Quantization Notes

- INT8 quantization is required for TPU acceleration
- Post-training quantization (PTQ) works for most models
- Quantization-aware training (QAT) gives better accuracy for critical use cases
- FP16 models will run on GPU but not TPU
- FP32 models fall back to CPU

## Adding Custom Models

1. Convert your model to TFLite INT8 format
2. Add entry to `ModelRegistry` in `ModelConfig.kt`
3. Host the `.tflite` file at a download URL
4. Implement the inference pipeline in `inference/`
