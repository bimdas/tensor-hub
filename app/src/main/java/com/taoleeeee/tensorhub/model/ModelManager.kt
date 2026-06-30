package com.taoleeeee.tensorhub.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Manages model download, caching, loading and lifecycle.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
    }

    data class ModelState(
        val config: ModelConfig,
        val downloaded: Boolean = false,
        val loaded: Boolean = false,
        val loading: Boolean = false
    )

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    private val _modelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelState>> = _modelStates

    init {
        // Scan for already-downloaded models on startup
        val states = mutableMapOf<String, ModelState>()
        for (config in ModelRegistry.models) {
            val file = File(modelsDir, config.filename)
            states[config.id] = ModelState(
                config = config,
                downloaded = file.exists()
            )
        }
        _modelStates.value = states
    }

    /**
     * Get the local file for a model.
     */
    fun getModelFile(modelId: String): File? {
        val config = ModelRegistry.getById(modelId) ?: return null
        return File(modelsDir, config.filename)
    }

    /**
     * Load a TFLite model into a MappedByteBuffer.
     */
    fun loadModel(modelId: String): MappedByteBuffer? {
        val file = getModelFile(modelId) ?: return null
        if (!file.exists()) {
            Log.w(TAG, "Model file not found: ${file.absolutePath}")
            return null
        }
        Log.i(TAG, "Loading model: $modelId (${file.length()} bytes)")
        val fis = FileInputStream(file)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    }

    /**
     * Check if a model is downloaded.
     */
    fun isDownloaded(modelId: String): Boolean {
        return getModelFile(modelId)?.exists() == true
    }

    /**
     * Get model info as JSON-compatible map.
     */
    fun getModelInfo(modelId: String): Map<String, Any>? {
        val config = ModelRegistry.getById(modelId) ?: return null
        val file = getModelFile(modelId)
        return mapOf(
            "id" to config.id,
            "name" to config.name,
            "type" to config.type.name.lowercase(),
            "downloaded" to (file?.exists() == true),
            "size_bytes" to (file?.length() ?: config.sizeBytes),
            "description" to config.description
        )
    }

    /**
     * List all available models.
     */
    fun listModels(): List<Map<String, Any>> {
        return ModelRegistry.models.map { config ->
            val file = getModelFile(config.id)
            mapOf(
                "id" to config.id,
                "name" to config.name,
                "type" to config.type.name.lowercase(),
                "downloaded" to (file?.exists() == true),
                "size_bytes" to (file?.length() ?: config.sizeBytes),
                "description" to config.description
            )
        }
    }
}
