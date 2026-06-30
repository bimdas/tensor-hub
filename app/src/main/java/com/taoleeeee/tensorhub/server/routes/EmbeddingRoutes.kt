package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles /v1/embeddings
 * OpenAI-compatible text embedding endpoint.
 *
 * TODO: Wire up actual embedding model inference.
 */
class EmbeddingRoutes(private val inferenceEngine: InferenceEngine) {

    private val json = Json { prettyPrint = true }

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

        val model = body?.get("model")?.toString()?.trim('"') ?: "all-MiniLM-L6-v2"
        val input = body?.get("input")?.toString()?.trim('"')
            ?: return errorResponse(Response.Status.BAD_REQUEST, "Missing 'input' field")

        // Check model is loaded
        if (!inferenceEngine.isLoaded(model)) {
            return errorResponse(
                Response.Status.BAD_REQUEST,
                "Model '$model' is not loaded. POST /v1/models/load first."
            )
        }

        // TODO: Run actual embedding inference
        // 1. Tokenize input text
        // 2. Run through embedding model
        // 3. Return embedding vector

        // Placeholder: return dummy embedding
        val embedding = FloatArray(384) { 0.0f }

        val result = json.encodeToString(mapOf(
            "object" to "list",
            "data" to listOf(mapOf(
                "object" to "embedding",
                "index" to 0,
                "embedding" to embedding.toList()
            )),
            "model" to model,
            "usage" to mapOf(
                "prompt_tokens" to input.split(" ").size,
                "total_tokens" to input.split(" ").size
            )
        ))

        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", result)
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            json.encodeToString(mapOf("error" to message))
        )
    }
}
