package com.taoleeeee.tensorhub.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Complete Whisper transcription pipeline.
 *
 * Audio → MelSpectrogram → Encoder → Autoregressive Decoder → Text
 *
 * Uses TFLite signature API for models with named "encode" and "decode" sub-graphs.
 * All tensor I/O uses direct ByteBuffers for zero-copy NNAPI delegation.
 *
 * Decode logic follows Google's official LiteRT ASR sample:
 * https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/speech_recognition
 *
 * Runs on Dispatchers.Default to avoid blocking the main thread.
 */
class WhisperTranscription(
    private val interpreter: Interpreter,
    private val vocabFile: File
) {

    companion object {
        private const val TAG = "WhisperTranscription"

        // Encoder: mel spectrogram → hidden states
        private const val MEL_BANDS = 80
        private const val MEL_FRAMES = 3000
        private const val ENCODER_DIM = 512
        private const val ENCODER_SEQ_LEN = 1500

        // Decoder: autoregressive token generation
        private const val DECODER_MAX_TOKENS = 128
        private const val VOCAB_SIZE = 51865

        // Causal mask values — must match Google's reference implementation
        private const val MASKED_IN = 0.0f
        private const val MASKED_OUT = -0.7f * Float.MAX_VALUE  // ~-2.4e38
    }

    data class TranscriptionResult(
        val text: String,
        val language: String,
        val durationSeconds: Float,
        val tokenCount: Int,
        val inferenceTimeMs: Long
    )

    private val tokenizer = WhisperTokenizer(vocabFile)
    private var encoderInitialized = false

    // Resolved decode input names — discovered dynamically via tensor dimensionality
    private var decEncOutputName: String? = null
    private var decTokenIdsName: String? = null
    private var decMaskName: String? = null
    private var decLogitsName: String? = null

    /**
     * Transcribe audio from a WAV file.
     * Runs on a background thread — safe to call from coroutine context.
     */
    suspend fun transcribe(
        audioFile: File,
        language: String = "en"
    ): Result<TranscriptionResult> = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()

            // 1. Decode audio to 16kHz mono float samples
            val samples = AudioDecoder.decode(audioFile).getOrThrow()
            val durationSec = samples.size.toFloat() / 16000

            // 2. Compute mel spectrogram [1, 80, 3000]
            val melSpec = MelSpectrogram.compute(samples)

            // 3. Encode: mel → encoder hidden states
            val encoderOutput = encode(melSpec)

            // 4. Decode: autoregressive loop
            val tokenIds = decode(encoderOutput, language)

            // 5. Decode tokens to text
            val text = tokenizer.decode(tokenIds)
            val elapsed = System.currentTimeMillis() - startTime

            Log.i(TAG, "Transcribed ${durationSec}s audio in ${elapsed}ms → \"${text.take(80)}\"")

            Result.success(TranscriptionResult(
                text = text,
                language = language,
                durationSeconds = durationSec,
                tokenCount = tokenIds.size,
                inferenceTimeMs = elapsed
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Run the encoder: float32[1,80,3000] → float32[1,1500,512]
     * Uses direct ByteBuffer for zero-copy NNAPI delegation.
     */
    private fun encode(melSpec: Array<Array<FloatArray>>): ByteBuffer {
        // Allocate direct ByteBuffer for input: 1 × 80 × 3000 × 4 bytes
        val inputSize = 1 * MEL_BANDS * MEL_FRAMES * 4
        val inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
        }
        for (m in 0 until MEL_BANDS) {
            for (t in 0 until MEL_FRAMES) {
                inputBuffer.putFloat(melSpec[0][m][t])
            }
        }
        inputBuffer.rewind()

        // Allocate direct ByteBuffer for output: 1 × 1500 × 512 × 4 bytes
        val outputSize = 1 * ENCODER_SEQ_LEN * ENCODER_DIM * 4
        val outputBuffer = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Discover tensor names and resolve decode input mapping on first run
        if (!encoderInitialized) {
            val encIn = interpreter.getSignatureInputs("encode")
            val encOut = interpreter.getSignatureOutputs("encode")
            Log.i(TAG, "Encode inputs: ${encIn.contentToString()}, outputs: ${encOut.contentToString()}")
            val decIn = interpreter.getSignatureInputs("decode")
            val decOut = interpreter.getSignatureOutputs("decode")
            Log.i(TAG, "Decode inputs: ${decIn.contentToString()}, outputs: ${decOut.contentToString()}")

            // Resolve decode input names by tensor dimensionality
            // args_0 = encoder output [1,1500,512] (3D → numDim=3)
            // args_1 = token ids [1,128] (2D → numDim=2)
            // args_2 = causal mask [1,1,128,128] (4D → numDim=4)
            resolveDecodeInputNames(decIn)
            decLogitsName = decOut[0]
            encoderInitialized = true
        }

        // Run encoder via runSignature(inputs, outputs, signatureName)
        val encInputs = HashMap<String, Any>()
        encInputs[interpreter.getSignatureInputs("encode")[0]] = inputBuffer
        val encOutputs = HashMap<String, Any>()
        encOutputs[interpreter.getSignatureOutputs("encode")[0]] = outputBuffer
        interpreter.runSignature(encInputs, encOutputs, "encode")

        Log.d(TAG, "Encoder output ready (${outputSize} bytes)")

        // Diagnostic: verify encoder output is not garbage
        outputBuffer.rewind()
        var nonZero = 0
        var sum = 0.0
        val first5 = FloatArray(5)
        for (i in 0 until ENCODER_SEQ_LEN * ENCODER_DIM) {
            val v = outputBuffer.float
            if (v != 0.0f) nonZero++
            sum += v
            if (i < 5) first5[i] = v
        }
        outputBuffer.rewind()
        Log.i(TAG, "Encoder output: ${nonZero}/${ENCODER_SEQ_LEN * ENCODER_DIM} non-zero, " +
                "sum=${"%.4f".format(sum)}, first5=${first5.contentToString()}")

        return outputBuffer
    }

    /**
     * Dynamically resolve decode signature input names by tensor dimensionality.
     * Avoids relying on alphabetical sort order of getSignatureInputs().
     *
     * Decode inputs:
     *   - 3D (float32[1,1500,512]) → encoder output
     *   - 2D (int32[1,128])        → token IDs
     *   - 4D (float32[1,1,128,128]) → causal mask
     */
    private fun resolveDecodeInputNames(inputNames: Array<String>) {
        for (name in inputNames) {
            try {
                val tensor = interpreter.getInputTensorFromSignature(name, "decode")
                val numDim = tensor.numDimensions()
                when (numDim) {
                    3 -> {
                        decEncOutputName = name
                        Log.i(TAG, "Decode: encoder output → '$name' (3D: ${tensor.shape().contentToString()})")
                    }
                    2 -> {
                        decTokenIdsName = name
                        Log.i(TAG, "Decode: token IDs → '$name' (2D: ${tensor.shape().contentToString()})")
                    }
                    4 -> {
                        decMaskName = name
                        Log.i(TAG, "Decode: causal mask → '$name' (4D: ${tensor.shape().contentToString()})")
                    }
                    else -> Log.w(TAG, "Decode: unknown input '$name' (${numDim}D)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot introspect decode input '$name': ${e.message}")
            }
        }

        // Fallback: if introspection failed, use alphabetical order
        if (decEncOutputName == null || decTokenIdsName == null || decMaskName == null) {
            Log.w(TAG, "Dynamic resolution failed, falling back to alphabetical order")
            decEncOutputName = inputNames[0]
            decTokenIdsName = inputNames[1]
            decMaskName = inputNames[2]
        }

        Log.i(TAG, "Resolved decode inputs: enc='$decEncOutputName', ids='$decTokenIdsName', mask='$decMaskName'")
    }

    /**
     * Autoregressive decoder loop.
     *
     * Decode signature:
     *   inputs:  float32[1,1500,512] (encoder output), int32[1,128] (token ids), float32[1,1,128,128] (causal mask)
     *   outputs: float32[1,128,51865] (logits)
     *
     * Key fixes:
     * - Dynamic signature input name resolution via tensor dimensionality
     * - Logits index: (step - 1) * VOCAB_SIZE — reads prediction for next token after position step-1
     * - EOT detection: uses tokenizer.isEndOfText() to also catch NOSPEECH (50362)
     */
    private fun decode(encoderOutput: ByteBuffer, language: String): IntArray {
        val sotSequence = tokenizer.buildSotSequence(language)
        val generatedTokens = mutableListOf<Int>()

        // Build causal mask: lower triangular [1, 1, 128, 128]
        val maskSize = DECODER_MAX_TOKENS * DECODER_MAX_TOKENS
        val causalMask = FloatArray(maskSize) { MASKED_OUT }
        for (r in 0 until DECODER_MAX_TOKENS) {
            for (c in 0..r) {
                causalMask[r * DECODER_MAX_TOKENS + c] = MASKED_IN
            }
        }
        val maskBuffer = ByteBuffer.allocateDirect(maskSize * 4).apply {
            order(ByteOrder.nativeOrder())
            for (v in causalMask) putFloat(v)
            rewind()
        }

        // Token IDs: full SOT sequence at start, rest zeros
        val tokenIds = IntArray(DECODER_MAX_TOKENS) { 0 }
        for (i in sotSequence.indices) {
            tokenIds[i] = sotSequence[i]
        }
        var step = sotSequence.size  // position of next token to predict

        Log.i(TAG, "SOT sequence: ${sotSequence.contentToString()}, starting at step=$step")

        // Output logits buffer: [1, 128, 51865]
        val numLogits = DECODER_MAX_TOKENS * VOCAB_SIZE

        // Autoregressive loop
        for (iteration in 0 until DECODER_MAX_TOKENS) {
            // Write token IDs to direct ByteBuffer
            val idsBuffer = ByteBuffer.allocateDirect(DECODER_MAX_TOKENS * 4).apply {
                order(ByteOrder.nativeOrder())
                for (id in tokenIds) putInt(id)
                rewind()
            }

            // Allocate logits output buffer
            val logitsBuffer = ByteBuffer.allocateDirect(numLogits * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Run decoder using dynamically resolved input names
            val decInputs = HashMap<String, Any>()
            decInputs[decEncOutputName!!] = encoderOutput
            decInputs[decTokenIdsName!!] = idsBuffer
            decInputs[decMaskName!!] = maskBuffer
            val decOutputs = HashMap<String, Any>()
            decOutputs[decLogitsName!!] = logitsBuffer
            interpreter.runSignature(decInputs, decOutputs, "decode")

            // Read logits as flat float array
            val logits = FloatArray(numLogits)
            logitsBuffer.rewind()
            logitsBuffer.order(ByteOrder.nativeOrder())
            for (idx in 0 until numLogits) {
                logits[idx] = logitsBuffer.float
            }

            // FIX: Read logits at (step - 1) — the model predicts the NEXT token
            // given all tokens up to position step-1. Position step contains padding/zeros,
            // so reading at step would give predictions for after the padding.
            val predictPos = step - 1
            val startIndex = predictPos * VOCAB_SIZE
            val endIndex = (predictPos + 1) * VOCAB_SIZE

            // Argmax over vocab at this position
            var bestToken = startIndex
            var bestScore = logits[startIndex]
            for (idx in startIndex + 1 until endIndex) {
                if (logits[idx] > bestScore) {
                    bestScore = logits[idx]
                    bestToken = idx
                }
            }
            val tokenId = bestToken - startIndex

            Log.d(TAG, "Step $iteration (pos=$step, readAt=$predictPos): token=$tokenId (score=${"%.4f".format(bestScore)})")

            // FIX: Use isEndOfText() to catch both EOT (50257) and NOSPEECH (50362)
            if (tokenizer.isEndOfText(tokenId)) {
                Log.d(TAG, "End-of-sequence at step $iteration (token=$tokenId)")
                break
            }

            // Collect non-special tokens for output
            if (!tokenizer.isSpecial(tokenId)) {
                generatedTokens.add(tokenId)
            }

            // Feed predicted token back for next step
            if (step < DECODER_MAX_TOKENS - 1) {
                tokenIds[step] = tokenId
                step++
            } else {
                Log.d(TAG, "Max decoder length reached")
                break
            }
        }

        Log.d(TAG, "Decoded ${generatedTokens.size} text tokens in $step steps")
        return generatedTokens.toIntArray()
    }
}
