#pragma once
#include <cmath>
#include <vector>
#include <algorithm>

class CombFilter {
public:
    CombFilter() {
        buffer_.assign(MAX_DELAY_SAMPLES, 0.0f);
    }

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        writeIndex_ = 0;
        dampState_ = 0.0f;
    }

    void setParameters(float tunedFreqHz, float feedback, float dampingPct) noexcept {
        tunedFreqHz_ = std::clamp(tunedFreqHz, 20.0f, sampleRate_ * 0.45f);
        feedback_ = std::clamp(feedback, -0.98f, 0.98f);
        dampingPct_ = std::clamp(dampingPct, 0.0f, 0.95f);
        delaySamples_ = std::clamp(sampleRate_ / tunedFreqHz_, 2.0f, static_cast<float>(MAX_DELAY_SAMPLES - 4));
    }

    inline float process(float in) noexcept {
        float readPos = static_cast<float>(writeIndex_) - delaySamples_;
        while (readPos < 0.0f) readPos += static_cast<float>(MAX_DELAY_SAMPLES);

        size_t idx0 = static_cast<size_t>(readPos) % MAX_DELAY_SAMPLES;
        size_t idx1 = (idx0 + 1) % MAX_DELAY_SAMPLES;
        float frac = readPos - static_cast<float>(static_cast<size_t>(readPos));

        // Linear interpolation of delay tap
        float delayed = buffer_[idx0] * (1.0f - frac) + buffer_[idx1] * frac;

        // 1-Pole Lowpass Feedback Damping
        dampState_ = (delayed * (1.0f - dampingPct_)) + (dampState_ * dampingPct_);

        float newSample = in + (dampState_ * feedback_);
        if (std::isnan(newSample) || std::isinf(newSample)) {
            newSample = 0.0f;
            dampState_ = 0.0f;
        }

        buffer_[writeIndex_] = newSample;
        writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;

        return delayed;
    }

private:
    static constexpr size_t MAX_DELAY_SAMPLES = 4096;
    float sampleRate_ = 48000.0f;
    float tunedFreqHz_ = 220.0f;
    float feedback_ = 0.75f;
    float dampingPct_ = 0.20f;
    float delaySamples_ = 218.18f;

    std::vector<float> buffer_;
    size_t writeIndex_ = 0;
    float dampState_ = 0.0f;
};
