package com.taoleeeee.tensorhub.inference

import android.util.Log
import com.taoleeeee.tensorhub.delegate.DelegateManager
import com.taoleeeee.tensorhub.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.Closeable

/**
 * Core inference engine. Manages loaded models and delegates inference requests.
 */
class InferenceEngine(
    private val modelManager: ModelManager,
    private val delegateManager: DelegateManager
) : Closeable {

    companion object {
        private const val TAG = "InferenceEngine"
    }

    private val loadedInterpreters = mutableMapOf<String, Interpreter>()
    private val activeDelegateType = mutableMapOf<String, DelegateManager.DelegateType>()

    /**
     * Load a model into memory with the best available delegate.
     */
    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (loadedInterpreters.containsKey(modelId)) {
                Log.i(TAG, "Model already loaded: $modelId")
                return@withContext Result.success(Unit)
            }

            val buffer = modelManager.loadModel(modelId)
                ?: return@withContext Result.failure(Exception("Model not found: $modelId"))

            val interpreter = delegateManager.createInterpreter(buffer)
            loadedInterpreters[modelId] = interpreter
            activeDelegateType[modelId] = delegateManager.activeDelegate

            Log.i(TAG, "Loaded model: $modelId (delegate: ${delegateManager.activeDelegate})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model $modelId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Unload a model from memory.
     */
    fun unloadModel(modelId: String) {
        loadedInterpreters[modelId]?.close()
        loadedInterpreters.remove(modelId)
        activeDelegateType.remove(modelId)
        Log.i(TAG, "Unloaded model: $modelId")
    }

    /**
     * Check if a model is loaded.
     */
    fun isLoaded(modelId: String): Boolean = loadedInterpreters.containsKey(modelId)

    /**
     * Get the active delegate type for a loaded model.
     */
    fun getDelegateType(modelId: String): DelegateManager.DelegateType? =
        activeDelegateType[modelId]

    /**
     * Get all loaded model IDs.
     */
    fun getLoadedModels(): Set<String> = loadedInterpreters.keys.toSet()

    /**
     * Run inference on a loaded model. Input/output shapes depend on the model type.
     */
    fun runInference(modelId: String, input: Any, output: Any): Result<Unit> {
        val interpreter = loadedInterpreters[modelId]
            ?: return Result.failure(Exception("Model not loaded: $modelId"))

        return try {
            interpreter.run(input, output)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed for $modelId: ${e.message}")
            Result.failure(e)
        }
    }

    override fun close() {
        loadedInterpreters.values.forEach { it.close() }
        loadedInterpreters.clear()
        activeDelegateType.clear()
        delegateManager.close()
    }
}
