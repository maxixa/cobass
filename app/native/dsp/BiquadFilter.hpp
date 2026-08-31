#pragma once
#include <cmath>
#include <algorithm>

enum class FilterType {
    LowPass,
    HighPass,
    BandPass,
    LowShelf,
    HighShelf,
    PeakingEQ
};

class BiquadFilter {
public:
    BiquadFilter() = default;

    void setSampleRate(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateCoefficients();
    }

    void setParameters(FilterType type, float freqHz, float gainDb, float q = 0.707f) {
        type_ = type;
        freqHz_ = std::clamp(freqHz, 20.0f, sampleRate_ * 0.48f);
        gainDb_ = gainDb;
        q_ = std::clamp(q, 0.1f, 18.0f);
        updateCoefficients();
    }

    inline float process(float in) noexcept {
        const float out = b0_ * in + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
        x2_ = x1_;
        x1_ = in;
        y2_ = y1_;
        y1_ = out;
        return out;
    }

    void reset() noexcept {
        x1_ = x2_ = y1_ = y2_ = 0.0f;
    }

private:
    void updateCoefficients() {
        if (sampleRate_ <= 0.0f) return;

        const float A = std::pow(10.0f, gainDb_ / 40.0f);
        const float w0 = 6.28318530718f * (freqHz_ / sampleRate_);
        const float cosw0 = std::cos(w0);
        const float sinw0 = std::sin(w0);
        const float alpha = sinw0 / (2.0f * q_);

        float a0 = 1.0f;

        switch (type_) {
            case FilterType::LowPass:
                b0_ = (1.0f - cosw0) * 0.5f;
                b1_ = 1.0f - cosw0;
                b2_ = (1.0f - cosw0) * 0.5f;
                a0  = 1.0f + alpha;
                a1_ = -2.0f * cosw0;
                a2_ = 1.0f - alpha;
                break;

            case FilterType::HighPass:
                b0_ = (1.0f + cosw0) * 0.5f;
                b1_ = -(1.0f + cosw0);
                b2_ = (1.0f + cosw0) * 0.5f;
                a0  = 1.0f + alpha;
                a1_ = -2.0f * cosw0;
                a2_ = 1.0f - alpha;
                break;

            case FilterType::PeakingEQ: {
                b0_ = 1.0f + alpha * A;
                b1_ = -2.0f * cosw0;
                b2_ = 1.0f - alpha * A;
                a0  = 1.0f + alpha / A;
                a1_ = -2.0f * cosw0;
                a2_ = 1.0f - alpha / A;
                break;
            }

            case FilterType::LowShelf: {
                const float sqrtA2 = 2.0f * std::sqrt(A) * alpha;
                b0_ = A * ((A + 1.0f) - (A - 1.0f) * cosw0 + sqrtA2);
                b1_ = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosw0);
                b2_ = A * ((A + 1.0f) - (A - 1.0f) * cosw0 - sqrtA2);
                a0  = (A + 1.0f) + (A - 1.0f) * cosw0 + sqrtA2;
                a1_ = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosw0);
                a2_ = (A + 1.0f) + (A - 1.0f) * cosw0 - sqrtA2;
                break;
            }

            case FilterType::HighShelf: {
                const float sqrtA2 = 2.0f * std::sqrt(A) * alpha;
                b0_ = A * ((A + 1.0f) + (A - 1.0f) * cosw0 + sqrtA2);
                b1_ = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosw0);
                b2_ = A * ((A + 1.0f) + (A - 1.0f) * cosw0 - sqrtA2);
                a0  = (A + 1.0f) - (A - 1.0f) * cosw0 + sqrtA2;
                a1_ = 2.0f * ((A - 1.0f) - (A + 1.0f) * cosw0);
                a2_ = (A + 1.0f) - (A - 1.0f) * cosw0 - sqrtA2;
                break;
            }

            default:
                b0_ = 1.0f; b1_ = 0.0f; b2_ = 0.0f;
                a0  = 1.0f; a1_ = 0.0f; a2_ = 0.0f;
                break;
        }

        b0_ /= a0;
        b1_ /= a0;
        b2_ /= a0;
        a1_ /= a0;
        a2_ /= a0;
    }

    float sampleRate_ = 48000.0f;
    FilterType type_ = FilterType::PeakingEQ;
    float freqHz_ = 1000.0f;
    float gainDb_ = 0.0f;
    float q_ = 0.707f;

    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f, y1_ = 0.0f, y2_ = 0.0f;
};
