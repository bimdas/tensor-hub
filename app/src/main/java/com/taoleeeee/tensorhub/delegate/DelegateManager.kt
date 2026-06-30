package com.taoleeeee.tensorhub.delegate

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.nio.MappedByteBuffer

/**
 * Manages hardware delegates for LiteRT inference.
 * Tries NNAPI first (routes to TPU/GPU on Pixel), falls back to GPU, then CPU.
 */
class DelegateManager(private val context: Context) {

    companion object {
        private const val TAG = "DelegateManager"
    }

    enum class DelegateType {
        NNAPI, GPU, CPU
    }

    data class BenchmarkResult(
        val type: DelegateType,
        val avgMs: Float,
        val success: Boolean
    )

    var activeDelegate: DelegateType = DelegateType.CPU
        private set

    private var gpuDelegate: GpuDelegate? = null

    /**
     * Create an interpreter with the best available delegate.
     */
    fun createInterpreter(model: MappedByteBuffer): Interpreter {
        return createInterpreter(model, null)
    }

    /**
     * Create an interpreter with a specific delegate type (or null for auto).
     */
    fun createInterpreter(model: MappedByteBuffer, forceDelegate: DelegateType?): Interpreter {
        if (forceDelegate != null) {
            return createWithDelegate(model, forceDelegate)
        }

        val options = Interpreter.Options()

        // Try NNAPI first (TPU/GPU on Pixel)
        try {
            options.setUseNNAPI(true)
            val interpreter = Interpreter(model, options)
            activeDelegate = DelegateType.NNAPI
            Log.i(TAG, "Using NNAPI delegate (TPU/GPU)")
            return interpreter
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI delegate failed: ${e.message}")
        }

        // Try GPU delegate
        try {
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
            val interpreter = Interpreter(model, options)
            activeDelegate = DelegateType.GPU
            Log.i(TAG, "Using GPU delegate")
            return interpreter
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate failed: ${e.message}")
            gpuDelegate?.close()
            gpuDelegate = null
        }

        // CPU fallback
        return createWithDelegate(model, DelegateType.CPU)
    }

    private fun createWithDelegate(model: MappedByteBuffer, type: DelegateType): Interpreter {
        val options = Interpreter.Options()
        return when (type) {
            DelegateType.NNAPI -> {
                options.setUseNNAPI(true)
                val interpreter = Interpreter(model, options)
                activeDelegate = DelegateType.NNAPI
                Log.i(TAG, "Using NNAPI delegate (TPU/GPU)")
                interpreter
            }
            DelegateType.GPU -> {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                val interpreter = Interpreter(model, options)
                activeDelegate = DelegateType.GPU
                Log.i(TAG, "Using GPU delegate")
                interpreter
            }
            DelegateType.CPU -> {
                options.setUseNNAPI(false)
                val interpreter = Interpreter(model, options)
                activeDelegate = DelegateType.CPU
                Log.i(TAG, "Using CPU (no hardware acceleration)")
                interpreter
            }
        }
    }

    /**
     * Benchmark each delegate with a simple model to find the fastest.
     */
    fun benchmark(model: MappedByteBuffer): List<BenchmarkResult> {
        val results = mutableListOf<BenchmarkResult>()

        // Benchmark NNAPI
        results.add(benchmarkDelegate(model, DelegateType.NNAPI) { options ->
            options.setUseNNAPI(true)
        })

        // Benchmark GPU
        results.add(benchmarkDelegate(model, DelegateType.GPU) { options ->
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
        })

        // Benchmark CPU
        results.add(benchmarkDelegate(model, DelegateType.CPU) { options ->
            // defaults to CPU
        })

        return results
    }

    private fun benchmarkDelegate(
        model: MappedByteBuffer,
        type: DelegateType,
        configure: (Interpreter.Options) -> Unit
    ): BenchmarkResult {
        return try {
            val options = Interpreter.Options()
            configure(options)
            val interpreter = Interpreter(model, options)

            // Warm up
            val input = Array(1) { FloatArray(1) { 0f } }
            val output = Array(1) { FloatArray(1) { 0f } }
            repeat(3) { interpreter.run(input, output) }

            // Benchmark
            val runs = 10
            val start = System.nanoTime()
            repeat(runs) { interpreter.run(input, output) }
            val elapsed = (System.nanoTime() - start) / 1_000_000f / runs

            interpreter.close()
            gpuDelegate?.close()
            gpuDelegate = null

            BenchmarkResult(type, elapsed, true)
        } catch (e: Exception) {
            Log.w(TAG, "Benchmark failed for $type: ${e.message}")
            gpuDelegate?.close()
            gpuDelegate = null
            BenchmarkResult(type, Float.MAX_VALUE, false)
        }
    }

    fun close() {
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
