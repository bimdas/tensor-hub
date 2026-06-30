package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.EmbeddingInference
import com.taoleeeee.tensorhub.inference.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.tensorflow.lite.Interpreter

/**
 * Handles /v1/embeddings
 * OpenAI-compatible text embedding endpoint.
 *
 * Uses BGE-small-en-v1.5-q8 TFLite model which includes
 * embedded WordPiece tokenization (no external tokenizer needed).
 */
class EmbeddingRoutes(private val inferenceEngine: InferenceEngine) {

    private val json = Json { prettyPrint = true }
    private val embeddingPipelines = mutableMapOf<String, EmbeddingInference>()

    fun handleEmbedding(session: IHTTPSession): Response {
        // Read JSON body
        val bodyBytes = ByteArray(session.headers["content-length"]?.toIntOrNull() ?: 0)
        session.inputStream.read(bodyBytes)
        val bodyStr = String(bodyBytes)

        // Parse model and input from JSON
        val body = try {
            json.parseToJsonElement(bodyStr).let { it as? kotlinx.serialization.json.JsonObject }
        } catch (e: Exception) {
            return errorResponse(Response.Status.BAD_REQUEST, "Invalid JSON body")
        }

        val model = body?.get("model")?.toString()?.trim('"') ?: "bge-small-en-v1.5-q8"
        val input = body?.get("input")?.toString()?.trim('"')
            ?: return errorResponse(Response.Status.BAD_REQUEST, "Missing 'input' field")

        // Check model is loaded
        if (!inferenceEngine.isLoaded(model)) {
            return errorResponse(
                Response.Status.BAD_REQUEST,
                "Model '$model' is not loaded. POST /v1/models/load first."
            )
        }

        // Get or create embedding pipeline
        val pipeline = embeddingPipelines.getOrPut(model) {
            // Get interpreter from inference engine
            val interpreter = inferenceEngine.getInterpreter(model)
                ?: return errorResponse(Response.Status.INTERNAL_ERROR, "Failed to get interpreter for $model")
            EmbeddingInference(interpreter)
        }

        // Run embedding inference
        val result = pipeline.embed(input)

        return result.fold(
            onSuccess = { embeddingResult ->
                val response = json.encodeToString(mapOf(
                    "object" to "list",
                    "data" to listOf(mapOf(
                        "object" to "embedding",
                        "index" to 0,
                        "embedding" to embeddingResult.embedding.toList()
                    )),
                    "model" to model,
                    "usage" to mapOf(
                        "prompt_tokens" to embeddingResult.tokensUsed,
                        "total_tokens" to embeddingResult.tokensUsed
                    )
                ))
                NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", response)
            },
            onFailure = { e ->
                errorResponse(Response.Status.INTERNAL_ERROR, "Embedding failed: ${e.message}")
            }
        )
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            json.encodeToString(mapOf("error" to message))
        )
    }
}
