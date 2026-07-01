#include "whisper_mel.h"
#include <algorithm>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "WhisperMelNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

WhisperMel::WhisperMel() : dft(N_FFT) {
    // 1. Precompute Hann window
    hann_window.resize(N_FFT);
    for (int i = 0; i < N_FFT; ++i) {
        hann_window[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / N_FFT));
    }

    // 2. Precompute mel filterbank
    build_mel_filterbank();
    LOGI("WhisperMel initialized: n_fft=%d, hop=%d, mels=%d, frames=%d", N_FFT, HOP_LENGTH, N_MELS, N_FRAMES);
}

float WhisperMel::hz_to_mel(float hz) const {
    return 2595.0f * std::log10(1.0f + hz / 700.0f);
}

float WhisperMel::mel_to_hz(float mel) const {
    return 700.0f * (std::pow(10.0f, mel / 2595.0f) - 1.0f);
}

void WhisperMel::build_mel_filterbank() {
    float mel_min = hz_to_mel(F_MIN);
    float mel_max = hz_to_mel(F_MAX);

    std::vector<float> mel_points(N_MELS + 2);
    for (int i = 0; i < N_MELS + 2; ++i) {
        mel_points[i] = mel_min + i * (mel_max - mel_min) / (N_MELS + 1);
    }

    std::vector<float> hz_points(mel_points.size());
    for (size_t i = 0; i < mel_points.size(); ++i) {
        hz_points[i] = mel_to_hz(mel_points[i]);
    }

    std::vector<int> bin_points(hz_points.size());
    for (size_t i = 0; i < hz_points.size(); ++i) {
        bin_points[i] = static_cast<int>(std::floor((N_FFT + 1) * hz_points[i] / SAMPLE_RATE));
        bin_points[i] = std::max(0, std::min(bin_points[i], N_FFT / 2));
    }

    mel_filterbank.assign(N_MELS, std::vector<float>(N_FFT / 2 + 1, 0.0f));
    for (int m = 0; m < N_MELS; ++m) {
        int left = bin_points[m];
        int center = bin_points[m + 1];
        int right = bin_points[m + 2];

        // Rising slope
        for (int k = left; k < center; ++k) {
            if (center != left) {
                mel_filterbank[m][k] = static_cast<float>(k - left) / (center - left);
            }
        }
        // Falling slope
        for (int k = center; k < right; ++k) {
            if (right != center) {
                mel_filterbank[m][k] = static_cast<float>(right - k) / (right - center);
            }
        }
    }
}

void WhisperMel::compute(const float* samples, int sample_count, float* mel_out) {
    int n_freq_bins = N_FFT / 2 + 1;
    int n_frames = 1 + (sample_count - N_FFT) / HOP_LENGTH;
    
    if (n_frames <= 0) {
        // Fallback for short audio: pad with zeros to N_FFT
        std::vector<float> padded(N_FFT, 0.0f);
        for (int i = 0; i < std::min(sample_count, N_FFT); ++i) {
            padded[i] = samples[i];
        }
        compute(padded.data(), N_FFT, mel_out);
        return;
    }

    // 1. Compute power spectrogram for each frame
    std::vector<std::vector<float>> spectrogram(n_frames, std::vector<float>(n_freq_bins));
    std::vector<float> frame_buf(N_FFT);

    for (int t = 0; t < n_frames; ++t) {
        int start = t * HOP_LENGTH;
        for (int i = 0; i < N_FFT; ++i) {
            int idx = start + i;
            float s = (idx < sample_count) ? samples[idx] : 0.0f;
            frame_buf[i] = s * hann_window[i];
        }
        dft.compute_power_spectrum(frame_buf.data(), spectrogram[t].data());
    }

    // 2. Apply mel filterbank: [80][201] * [n_frames][201] -> [80][n_frames]
    std::vector<std::vector<float>> mel_spec(N_MELS, std::vector<float>(n_frames, 0.0f));
    float max_val = -1e10f;

    for (int m = 0; m < N_MELS; ++m) {
        const auto& filter_row = mel_filterbank[m];
        for (int t = 0; t < n_frames; ++t) {
            float sum = 0.0f;
            const auto& spec_frame = spectrogram[t];
            for (int k = 0; k < n_freq_bins; ++k) {
                sum += filter_row[k] * spec_frame[k];
            }
            mel_spec[m][t] = sum;
            if (sum > max_val) max_val = sum;
        }
    }

    // 3. Log scale + Normalize
    float log_max = (max_val > 0.0f) ? std::log10(max_val) : 0.0f;
    float floor_val = log_max - 8.0f;

    for (int m = 0; m < N_MELS; ++m) {
        for (int t = 0; t < n_frames; ++t) {
            float val = mel_spec[m][t];
            float log_val = std::log10(std::max(val, 1e-10f));
            float clamped = std::max(log_val, floor_val);
            mel_spec[m][t] = (clamped + 4.0f) / 4.0f; // Normalize to ~[-1, 1]
        }
    }

    // 4. Pad or truncate to exactly N_FRAMES (3000) and output flat layout [1, 80, 3000]
    // Layout in memory is: band 0 (3000 frames), band 1 (3000 frames), ...
    for (int m = 0; m < N_MELS; ++m) {
        float* dest = mel_out + m * N_FRAMES;
        for (int t = 0; t < N_FRAMES; ++t) {
            dest[t] = (t < n_frames) ? mel_spec[m][t] : 0.0f;
        }
    }
}
