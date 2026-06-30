package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.json.*

/**
 * Handles /v1/audio/transcriptions
 * OpenAI-compatible multipart form upload.
 *
 * TODO: Wire up actual Whisper inference pipeline.
 * Currently returns a placeholder response.
 */
class AudioRoutes(private val inferenceEngine: InferenceEngine) {

    fun handleTranscription(session: IHTTPSession): Response {
        // Parse multipart body
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return errorResponse(Response.Status.BAD_REQUEST, "Failed to parse body: ${e.message}")
        }

        // Get uploaded file
        val tempFilePath = files["file"]
            ?: return errorResponse(Response.Status.BAD_REQUEST, "Missing 'file' field")

        val model = session.parms["model"] ?: "whisper-base"
        val language = session.parms["language"] ?: "en"
        val responseFormat = session.parms["response_format"] ?: "json"

        // Check model is loaded
        if (!inferenceEngine.isLoaded(model)) {
            return errorResponse(
                Response.Status.BAD_REQUEST,
                "Model '$model' is not loaded. POST /v1/models/load first."
            )
        }

        // TODO: Process audio through Whisper pipeline
        val placeholderText = "Transcription placeholder - Whisper pipeline not yet implemented"

        val result = when (responseFormat) {
            "text" -> placeholderText
            "verbose_json" -> buildJsonObject {
                put("task", "transcribe")
                put("language", language)
                put("duration", 0.0)
                put("text", placeholderText)
                put("segments", buildJsonArray {})
            }.toString()
            else -> buildJsonObject {
                put("text", placeholderText)
            }.toString()
        }

        val contentType = if (responseFormat == "text") "text/plain" else "application/json"
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, contentType, result)
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
