package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import com.taoleeeee.tensorhub.model.ModelManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HealthRoutes(
    private val inferenceEngine: InferenceEngine,
    private val modelManager: ModelManager
) {
    private val json = Json { prettyPrint = true }

    fun handle(): Response {
        val loadedModels = inferenceEngine.getLoadedModels().map { modelId ->
            mapOf(
                "id" to modelId,
                "delegate" to (inferenceEngine.getDelegateType(modelId)?.name ?: "unknown")
            )
        }

        val body = json.encodeToString(mapOf(
            "status" to "ok",
            "server" to "tensor-hub",
            "version" to "0.1.0",
            "models_loaded" to loadedModels
        ))

        return NanoHTTPD.newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            body
        )
    }
}
