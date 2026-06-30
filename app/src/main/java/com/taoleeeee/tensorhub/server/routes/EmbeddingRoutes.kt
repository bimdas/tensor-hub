package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.EmbeddingInference
import com.taoleeeee.tensorhub.inference.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.json.*

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
            json.parseToJsonElement(bodyStr).let { it as? JsonObject }
        } catch (e: Exception) {
            return errorResponse(Response.Status.BAD_REQUEST, "Invalid JSON body")
        }

        val model = body?.get("model")?.jsonPrimitive?.content ?: "bge-small-en-v1.5-q8"
        val input = body?.get("input")?.jsonPrimitive?.content
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
                val embeddingArray = buildJsonArray {
                    embeddingResult.embedding.forEach { add(it) }
                }

                val response = buildJsonObject {
                    put("object", "list")
                    put("data", buildJsonArray {
                        add(buildJsonObject {
                            put("object", "embedding")
                            put("index", 0)
                            put("embedding", embeddingArray)
                        })
                    })
                    put("model", model)
                    put("usage", buildJsonObject {
                        put("prompt_tokens", embeddingResult.tokensUsed)
                        put("total_tokens", embeddingResult.tokensUsed)
                    })
                }

                NanoHTTPD.newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    response.toString()
                )
            },
            onFailure = { e ->
                errorResponse(Response.Status.INTERNAL_ERROR, "Embedding failed: ${e.message}")
            }
        )
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        val errorObj = buildJsonObject {
            put("error", message)
        }
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            errorObj.toString()
        )
    }
}
