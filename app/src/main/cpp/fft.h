#ifndef TENSORHUB_FFT_H
#define TENSORHUB_FFT_H

#include <vector>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

class DirectDFT {
private:
    int n_fft;
    int n_freq_bins;
    std::vector<std::vector<float>> cos_table;
    std::vector<std::vector<float>> sin_table;

public:
    DirectDFT(int n_fft) : n_fft(n_fft) {
        n_freq_bins = n_fft / 2 + 1;
        cos_table.assign(n_freq_bins, std::vector<float>(n_fft, 0.0f));
        sin_table.assign(n_freq_bins, std::vector<float>(n_fft, 0.0f));

        for (int k = 0; k < n_freq_bins; ++k) {
            for (int n = 0; n < n_fft; ++n) {
                cos_table[k][n] = std::cos(2.0f * M_PI * k * n / n_fft);
                sin_table[k][n] = -std::sin(2.0f * M_PI * k * n / n_fft);
            }
        }
    }

    void compute_power_spectrum(const float* frame, float* power_out) const {
        for (int k = 0; k < n_freq_bins; ++k) {
            float re = 0.0f;
            float im = 0.0f;
            const auto& cos_row = cos_table[k];
            const auto& sin_row = sin_table[k];

            // Loop is easily vectorized by compiler
            for (int n = 0; n < n_fft; ++n) {
                re += frame[n] * cos_row[n];
                im += frame[n] * sin_row[n];
            }
            power_out[k] = re * re + im * im;
        }
    }
};

#endif // TENSORHUB_FFT_H
