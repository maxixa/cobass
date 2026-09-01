#pragma once
#include <cmath>
#include <algorithm>

class Wavefolder {
public:
    Wavefolder() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        holdCounter_ = 0.0f;
        heldSample_ = 0.0f;
    }

    void setParameters(float driveGain, float folds, float symmetryBias, float bitDepth, float downsampleRatio) noexcept {
        driveGain_ = std::clamp(driveGain, 0.5f, 20.0f);
        folds_ = std::clamp(folds, 1.0f, 6.0f);
        bias_ = std::clamp(symmetryBias, -0.5f, 0.5f);
        bitDepth_ = std::clamp(bitDepth, 2.0f, 16.0f);
        downsampleRatio_ = std::clamp(downsampleRatio, 1.0f, 32.0f);
    }

    inline float process(float in) noexcept {
        // 1. Bitcrushing & Sample Rate Decimation
        holdCounter_ += 1.0f;
        if (holdCounter_ >= downsampleRatio_) {
            holdCounter_ = 0.0f;
            if (bitDepth_ < 15.5f) {
                const float levels = std::pow(2.0f, bitDepth_) * 0.5f;
                heldSample_ = std::round(in * levels) / levels;
            } else {
                heldSample_ = in;
            }
        }

        // 2. Drive & Asymmetric DC Offset
        float x = (heldSample_ + bias_) * driveGain_;

        // 3. Multi-Stage West-Coast Trigonometric Wavefolding
        for (int f = 0; f < static_cast<int>(folds_); ++f) {
            x = 0.63661977236f * std::asin(std::sin(3.14159265359f * x));
        }

        // 4. Soft Saturation Limiter
        return std::tanh(x - bias_ * 0.5f);
    }

private:
    float sampleRate_ = 48000.0f;
    float driveGain_ = 1.0f;
    float folds_ = 1.0f;
    float bias_ = 0.0f;
    float bitDepth_ = 16.0f;
    float downsampleRatio_ = 1.0f;

    float holdCounter_ = 0.0f;
    float heldSample_ = 0.0f;
};
