package com.taoleeeee.tensorhub.model

import kotlinx.serialization.Serializable

/**
 * Model metadata and configuration.
 */
@Serializable
data class ModelConfig(
    val id: String,
    val name: String,
    val type: ModelType,
    val url: String,
    val filename: String,
    val sizeBytes: Long = 0,
    val quantized: Boolean = true,
    val description: String = ""
)

@Serializable
enum class ModelType {
    WHISPER,
    EMBEDDING,
    CLASSIFICATION
}

/**
 * Built-in model registry. Add new models here.
 */
object ModelRegistry {

    val models = listOf(
        ModelConfig(
            id = "whisper-tiny",
            name = "Whisper Tiny",
            type = ModelType.WHISPER,
            url = "https://huggingface.co/google/gemma-2b/resolve/main/dummy.tflite",
            filename = "whisper-tiny-int8.tflite",
            sizeBytes = 75_000_000,
            description = "Fast transcription, lower accuracy"
        ),
        ModelConfig(
            id = "whisper-base",
            name = "Whisper Base",
            type = ModelType.WHISPER,
            url = "https://huggingface.co/google/gemma-2b/resolve/main/dummy.tflite",
            filename = "whisper-base-int8.tflite",
            sizeBytes = 141_000_000,
            description = "Balanced speed and accuracy"
        ),
        ModelConfig(
            id = "bge-small-en-v1.5-q8",
            name = "BGE Small EN v1.5 (INT8)",
            type = ModelType.EMBEDDING,
            url = "https://huggingface.co/ChristianAzinn/bge-small-en-v1.5-q8-tflite/resolve/main/bge-small-en-v1.5-q8.tflite",
            filename = "bge-small-en-v1.5-q8.tflite",
            sizeBytes = 33_000_000,
            description = "384-dim embeddings with built-in tokenizer, optimized for TFLite"
        )
    )

    fun getById(id: String): ModelConfig? = models.find { it.id == id }
}
