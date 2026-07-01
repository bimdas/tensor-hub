#ifndef TENSORHUB_WHISPER_MEL_H
#define TENSORHUB_WHISPER_MEL_H

#include <vector>
#include "fft.h"

class WhisperMel {
private:
    static constexpr int SAMPLE_RATE = 16000;
    static constexpr int N_FFT = 400;
    static constexpr int HOP_LENGTH = 160;
    static constexpr int N_MELS = 80;
    static constexpr int N_FRAMES = 3000;
    static constexpr float F_MIN = 0.0f;
    static constexpr float F_MAX = 8000.0f;

    std::vector<float> hann_window;
    std::vector<std::vector<float>> mel_filterbank;
    DirectDFT dft;

    float hz_to_mel(float hz) const;
    float mel_to_hz(float mel) const;
    void build_mel_filterbank();

public:
    WhisperMel();
    
    // Computes the mel spectrogram and writes it directly to the output buffer
    // Output buffer size must be 1 * 80 * 3000 * sizeof(float)
    void compute(const float* samples, int sample_count, float* mel_out);
};

#endif // TENSORHUB_WHISPER_MEL_H
