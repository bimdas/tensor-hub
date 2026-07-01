#ifndef TENSORHUB_WHISPER_DECODER_H
#define TENSORHUB_WHISPER_DECODER_H

#include <string>
#include <vector>
#include "tflite_minimal.h"
#include "whisper_mel.h"

class WhisperDecoder {
private:
    TfLiteModel* model = nullptr;
    TfLiteInterpreter* interpreter = nullptr;

    TfLiteSignatureRunner* encode_runner = nullptr;
    TfLiteSignatureRunner* decode_runner = nullptr;

    WhisperMel mel_processor;
    bool initialized = false;

    std::vector<int> build_sot_sequence(const std::string& lang) const;
    bool is_special_token(int token_id) const;
    bool is_end_of_text(int token_id) const;

public:
    WhisperDecoder() = default;
    ~WhisperDecoder();

    bool load_model(const std::string& model_path, bool use_nnapi = true);
    std::vector<int> transcribe(const float* pcm_samples, int sample_count, const std::string& lang);
};

#endif // TENSORHUB_WHISPER_DECODER_H
