package com.taoleeeee.tensorhub.server.routes

import com.taoleeeee.tensorhub.inference.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles /v1/audio/transcriptions
 * OpenAI-compatible multipart form upload.
 *
 * TODO: Wire up actual Whisper inference pipeline.
 * Currently returns a placeholder response.
 */
class AudioRoutes(private val inferenceEngine: InferenceEngine) {

    private val json = Json { prettyPrint = true }

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
        // 1. Decode audio file (WAV/OGG/MP3) to PCM
        // 2. Resample to 16kHz mono
        // 3. Compute mel spectrogram
        // 4. Run encoder + decoder inference
        // 5. Decode tokens to text

        val result = when (responseFormat) {
            "text" -> "Transcription placeholder - Whisper pipeline not yet implemented"
            "verbose_json" -> json.encodeToString(mapOf(
                "task" to "transcribe",
                "language" to language,
                "duration" to 0.0,
                "text" to "Transcription placeholder - Whisper pipeline not yet implemented",
                "segments" to emptyList<Any>()
            ))
            else -> json.encodeToString(mapOf(
                "text" to "Transcription placeholder - Whisper pipeline not yet implemented"
            ))
        }

        val contentType = if (responseFormat == "text") "text/plain" else "application/json"
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, contentType, result)
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            json.encodeToString(mapOf("error" to message))
        )
    }
}
