package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import com.taoleeeee.tensorhub.model.ModelManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles /v1/models/* endpoints for model management.
 */
class ModelRoutes(
    private val inferenceEngine: InferenceEngine,
    private val modelManager: ModelManager
) {
    private val json = Json { prettyPrint = true }

    fun handleList(): Response {
        val models = modelManager.listModels().map { model ->
            mapOf(
                "id" to model["id"],
                "object" to "model",
                "owned_by" to "tensor-hub",
                "loaded" to inferenceEngine.isLoaded(model["id"] as String)
            )
        }

        val body = json.encodeToString(mapOf(
            "object" to "list",
            "data" to models
        ))

        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", body)
    }

    fun handleLoad(session: IHTTPSession): Response {
        val bodyBytes = ByteArray(session.headers["content-length"]?.toIntOrNull() ?: 0)
        session.inputStream.read(bodyBytes)
        val bodyStr = String(bodyBytes)

        val body = try {
            json.parseToJsonElement(bodyStr) as? kotlinx.serialization.json.JsonObject
        } catch (e: Exception) {
            return errorResponse(Response.Status.BAD_REQUEST, "Invalid JSON body")
        }

        val modelId = body?.get("model")?.toString()?.trim('"')
            ?: return errorResponse(Response.Status.BAD_REQUEST, "Missing 'model' field")

        // Run loading in a blocking way (server thread handles it)
        val result = kotlinx.coroutines.runBlocking {
            inferenceEngine.loadModel(modelId)
        }

        return if (result.isSuccess) {
            val responseBody = json.encodeToString(mapOf(
                "status" to "ok",
                "model" to modelId,
                "delegate" to (inferenceEngine.getDelegateType(modelId)?.name ?: "unknown")
            ))
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", responseBody)
        } else {
            errorResponse(Response.Status.INTERNAL_ERROR, result.exceptionOrNull()?.message ?: "Load failed")
        }
    }

    fun handleUnload(session: IHTTPSession): Response {
        val bodyBytes = ByteArray(session.headers["content-length"]?.toIntOrNull() ?: 0)
        session.inputStream.read(bodyBytes)
        val bodyStr = String(bodyBytes)

        val body = try {
            json.parseToJsonElement(bodyStr) as? kotlinx.serialization.json.JsonObject
        } catch (e: Exception) {
            return errorResponse(Response.Status.BAD_REQUEST, "Invalid JSON body")
        }

        val modelId = body?.get("model")?.toString()?.trim('"')
            ?: return errorResponse(Response.Status.BAD_REQUEST, "Missing 'model' field")

        inferenceEngine.unloadModel(modelId)

        val responseBody = json.encodeToString(mapOf(
            "status" to "ok",
            "model" to modelId
        ))
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", responseBody)
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            json.encodeToString(mapOf("error" to message))
        )
    }
}
