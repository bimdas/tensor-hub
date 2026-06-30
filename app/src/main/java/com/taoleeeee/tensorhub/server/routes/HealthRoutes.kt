package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import com.taoleeeee.tensorhub.model.ModelManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.json.*

class HealthRoutes(
    private val inferenceEngine: InferenceEngine,
    private val modelManager: ModelManager
) {
    fun handle(): Response {
        val loadedModels = buildJsonArray {
            inferenceEngine.getLoadedModels().forEach { modelId ->
                add(buildJsonObject {
                    put("id", modelId)
                    put("delegate", inferenceEngine.getDelegateType(modelId)?.name ?: "unknown")
                })
            }
        }

        val body = buildJsonObject {
            put("status", "ok")
            put("server", "tensor-hub")
            put("version", com.taoleeeee.tensorhub.BuildConfig.VERSION_NAME)
            put("models_loaded", loadedModels)
        }

        return NanoHTTPD.newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            body.toString()
        )
    }
}
