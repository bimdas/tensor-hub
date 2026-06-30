package com.taoleeeee.tensorhub.inference

import android.util.Log
import kotlin.math.*

/**
 * Computes Whisper-compatible mel spectrograms from raw audio samples.
 *
 * Whisper's preprocessing pipeline (matching OpenAI Whisper exactly):
 * 1. STFT with n_fft=400, hop_length=160, Hann window
 * 2. Power spectrum (magnitude squared)
 * 3. 80-band mel filterbank (0-8000 Hz) — librosa.filters.mel(sr=16000, n_fft=400, n_mels=80)
 *    Uses Slaney mel scale (linear < 1kHz, log >= 1kHz) with Slaney normalization
 * 4. Log10 → clamp to max-8.0 → normalize to [-1, 1]
 * 5. Pad/truncate to 3000 frames (30 seconds)
 *
 * Output shape: [1, 80, 3000] for the encoder.
 */
object MelSpectrogram {

    private const val TAG = "MelSpectrogram"
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 400
    private const val HOP_LENGTH = 160
    private const val N_MELS = 80
    private const val N_FRAMES = 3000  // 30 seconds at 16kHz with hop=160
    private const val F_MIN = 0.0f
    private const val F_MAX = 8000.0f

    // Precomputed: Hann window
    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5f * (1.0f - cos(2.0f * PI.toFloat() * i / N_FFT)))
    }

    // Precomputed: DFT twiddle factors for n_fft=400, first 201 bins
    private const val N_FREQ_BINS = N_FFT / 2 + 1  // 201
    private val cosTable: Array<FloatArray>
    private val sinTable: Array<FloatArray>

    // Precomputed: mel filterbank matrix [80][201]
    // Matches librosa.filters.mel(sr=16000, n_fft=400, n_mels=80, fmin=0, fmax=8000, htk=False, norm="slaney")
    private val melFilterbank: Array<FloatArray>

    init {
        // Build DFT twiddle factor tables
        cosTable = Array(N_FREQ_BINS) { k ->
            FloatArray(N_FFT) { n ->
                cos(2.0f * PI.toFloat() * k * n / N_FFT)
            }
        }
        sinTable = Array(N_FREQ_BINS) { k ->
            FloatArray(N_FFT) { n ->
                -sin(2.0f * PI.toFloat() * k * n / N_FFT)
            }
        }

        // Build mel filterbank (Slaney mel scale + Slaney normalization)
        melFilterbank = buildMelFilterbank()
        Log.i(TAG, "Initialized: n_fft=$N_FFT, hop=$HOP_LENGTH, mels=$N_MELS, frames=$N_FRAMES")
    }

    /**
     * Compute mel spectrogram from raw 16kHz mono float samples.
     * Returns float array of shape [1, 80, 3000].
     */
    fun compute(samples: FloatArray): Array<Array<FloatArray>> {
        // 1. Frame the audio
        val nFrames = 1 + (samples.size - N_FFT) / HOP_LENGTH
        if (nFrames <= 0) {
            Log.w(TAG, "Audio too short (${samples.size} samples), padding to minimum")
            return compute(FloatArray(N_FFT) { if (it < samples.size) samples[it] else 0f })
        }

        // 2. Compute power spectrogram for each frame
        val spectrogram = Array(nFrames) { frameIdx ->
            val start = frameIdx * HOP_LENGTH
            val frame = FloatArray(N_FFT) { i ->
                val idx = start + i
                val sample = if (idx < samples.size) samples[idx] else 0.0f
                sample * hannWindow[i]
            }
            computePowerSpectrum(frame)
        }

        // 3. Apply mel filterbank: [80][201] × [nFrames][201] → [80][nFrames]
        val melSpec = Array(N_MELS) { m ->
            FloatArray(nFrames) { t ->
                var sum = 0.0f
                for (k in 0 until N_FREQ_BINS) {
                    sum += melFilterbank[m][k] * spectrogram[t][k]
                }
                sum
            }
        }

        // 4. Log scale + normalize (matching OpenAI Whisper exactly)
        // Find max value for dynamic range compression
        var maxVal = Float.MIN_VALUE
        for (m in 0 until N_MELS) {
            for (t in 0 until nFrames) {
                if (melSpec[m][t] > maxVal) maxVal = melSpec[m][t]
            }
        }

        val logMax = if (maxVal > 0) log10(maxVal) else 0.0f
        val floor = logMax - 8.0f  // 80 dB dynamic range

        for (m in 0 until N_MELS) {
            for (t in 0 until nFrames) {
                val logVal = log10(melSpec[m][t].coerceAtLeast(1e-10f))
                val clamped = max(logVal, floor)
                melSpec[m][t] = (clamped + 4.0f) / 4.0f  // Normalize to ~[-1, 1]
            }
        }

        // 5. Pad or truncate to exactly N_FRAMES (3000)
        val result = Array(1) {
            Array(N_MELS) { m ->
                FloatArray(N_FRAMES) { t ->
                    if (t < nFrames) melSpec[m][t] else 0.0f
                }
            }
        }

        Log.d(TAG, "Mel spectrogram: ${nFrames} frames → [1, $N_MELS, $N_FRAMES]")
        return result
    }

    /**
     * Compute power spectrum of a single frame using direct DFT.
     * Returns magnitude squared for positive frequencies (0..200).
     */
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val power = FloatArray(N_FREQ_BINS)
        for (k in 0 until N_FREQ_BINS) {
            var re = 0.0f
            var im = 0.0f
            val cosRow = cosTable[k]
            val sinRow = sinTable[k]
            for (n in 0 until N_FFT) {
                re += frame[n] * cosRow[n]
                im += frame[n] * sinRow[n]
            }
            power[k] = re * re + im * im
        }
        return power
    }

    /**
     * Build 80-band mel filterbank matrix [80][201].
     *
     * Uses Slaney mel scale (librosa default, htk=False):
     *   - Linear below 1000 Hz: mel = f * 3/200
     *   - Log above 1000 Hz: mel = 15 + ln(f/1000) / ln(6.4)/27
     *
     * With Slaney normalization: each filter divided by its bandwidth in Hz.
     * This matches librosa.filters.mel(sr=16000, n_fft=400, n_mels=80, norm="slaney")
     * which is what OpenAI Whisper's mel_filters.npz was generated from.
     */
    private fun buildMelFilterbank(): Array<FloatArray> {
        // FFT frequency bins: [0, 40, 80, ..., 8000] Hz (201 bins)
        val fftFreqs = FloatArray(N_FREQ_BINS) { i ->
            i.toFloat() * SAMPLE_RATE / N_FFT
        }

        // Mel-spaced frequencies: n_mels + 2 points from fmin to fmax
        val melMin = hzToMelSlaney(F_MIN)
        val melMax = hzToMelSlaney(F_MAX)
        val melPoints = FloatArray(N_MELS + 2) { i ->
            melMin + i * (melMax - melMin) / (N_MELS + 1)
        }
        val hzPoints = FloatArray(melPoints.size) { melToHzSlaney(melPoints[it]) }

        // Find fractional bin positions via linear interpolation (like librosa)
        val binPositions = FloatArray(hzPoints.size) { i ->
            interpolate(hzPoints[i], fftFreqs)
        }

        // Build filterbank using librosa's triangle filter method
        val fdiff = FloatArray(hzPoints.size - 1) { i ->
            hzPoints[i + 1] - hzPoints[i]
        }

        val filterbank = Array(N_MELS) { FloatArray(N_FREQ_BINS) }
        for (m in 0 until N_MELS) {
            for (k in 0 until N_FREQ_BINS) {
                val lower = if (fdiff[m] != 0.0f) -(hzPoints[m] - fftFreqs[k]) / fdiff[m] else 0.0f
                val upper = if (fdiff[m + 1] != 0.0f) (hzPoints[m + 2] - fftFreqs[k]) / fdiff[m + 1] else 0.0f
                filterbank[m][k] = max(0.0f, min(lower, upper))
            }

            // Slaney normalization: divide by bandwidth in Hz
            val bandwidth = hzPoints[m + 2] - hzPoints[m]
            if (bandwidth > 0.0f) {
                val normFactor = 2.0f / bandwidth
                for (k in 0 until N_FREQ_BINS) {
                    filterbank[m][k] *= normFactor
                }
            }
        }

        // Diagnostic: log some filter stats
        val peak0 = filterbank[0].max()
        val peak79 = filterbank[79].max()
        Log.i(TAG, "Mel filterbank: Slaney scale, Slaney norm. Peak[0]=${"%.6f".format(peak0)}, Peak[79]=${"%.6f".format(peak79)}")

        return filterbank
    }

    /**
     * Linear interpolation: find the position of value in sorted array.
     * Equivalent to numpy's np.interp(value, xp, fp).
     */
    private fun interpolate(value: Float, xp: FloatArray): Float {
        if (value <= xp[0]) return 0.0f
        if (value >= xp[xp.size - 1]) return (xp.size - 1).toFloat()
        for (i in 0 until xp.size - 1) {
            if (value >= xp[i] && value <= xp[i + 1]) {
                val frac = (value - xp[i]) / (xp[i + 1] - xp[i])
                return i + frac
            }
        }
        return (xp.size - 1).toFloat()
    }

    /**
     * Slaney mel scale (librosa default, htk=False):
     * Linear below 1000 Hz: mel = f * 3/200
     * Log above 1000 Hz: mel = 15 + ln(f/1000) / (ln(6.4)/27)
     */
    private fun hzToMelSlaney(hz: Float): Float {
        val fSp = 200.0f / 3.0f
        return if (hz < 1000.0f) {
            hz / fSp
        } else {
            15.0f + ln(hz / 1000.0f) / (ln(6.4f) / 27.0f)
        }
    }

    /**
     * Inverse Slaney mel scale:
     * Below mel=15: f = mel * 200/3
     * Above mel=15: f = 1000 * exp((mel - 15) * ln(6.4)/27)
     */
    private fun melToHzSlaney(mel: Float): Float {
        val fSp = 200.0f / 3.0f
        return if (mel < 15.0f) {
            mel * fSp
        } else {
            1000.0f * exp((mel - 15.0f) * (ln(6.4f) / 27.0f))
        }
    }
}
