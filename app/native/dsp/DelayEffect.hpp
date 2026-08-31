#pragma once
#include "AudioNode.hpp"
#include <vector>
#include <algorithm>

class DelayEffect : public AudioNode {
public:
    DelayEffect() {
        bufferL_.resize(MAX_DELAY_SAMPLES, 0.0f);
        bufferR_.resize(MAX_DELAY_SAMPLES, 0.0f);
    }

    void reset(float sampleRate) override {
        sampleRate_ = sampleRate;
        std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
        std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
        writeIndex_ = 0;
    }

    void process(const float* inBuffer, float* outBuffer, int32_t numFrames) override {
        if (!enabled_) {
            for (int32_t i = 0; i < numFrames * 2; ++i) {
                outBuffer[i] = inBuffer[i];
            }
            return;
        }

        const int32_t delaySamples = std::clamp(static_cast<int32_t>(delayTimeSec_ * sampleRate_), 1, MAX_DELAY_SAMPLES - 1);

        for (int32_t i = 0; i < numFrames; ++i) {
            const float inL = inBuffer[i * 2];
            const float inR = inBuffer[i * 2 + 1];

            int32_t readIndex = writeIndex_ - delaySamples;
            if (readIndex < 0) readIndex += MAX_DELAY_SAMPLES;

            const float delayedL = bufferL_[readIndex];
            const float delayedR = bufferR_[readIndex];

            bufferL_[writeIndex_] = inL + delayedL * feedback_;
            bufferR_[writeIndex_] = inR + delayedR * feedback_;

            outBuffer[i * 2]     = inL + delayedL * mix_;
            outBuffer[i * 2 + 1] = inR + delayedR * mix_;

            writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;
        }
    }

    void setParameter(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: enabled_ = (value > 0.5f); break;
            case 1: delayTimeSec_ = std::clamp(value, 0.01f, 1.5f); break;
            case 2: feedback_ = std::clamp(value, 0.0f, 0.95f); break;
            case 3: mix_ = std::clamp(value, 0.0f, 1.0f); break;
        }
    }

    float getParameter(uint32_t paramId) const override {
        switch (paramId) {
            case 0: return enabled_ ? 1.0f : 0.0f;
            case 1: return delayTimeSec_;
            case 2: return feedback_;
            case 3: return mix_;
            default: return 0.0f;
        }
    }

private:
    static constexpr int32_t MAX_DELAY_SAMPLES = 96000;
    float sampleRate_ = 48000.0f;
    bool enabled_ = true;
    float delayTimeSec_ = 0.35f;
    float feedback_ = 0.45f;
    float mix_ = 0.35f;

    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    int32_t writeIndex_ = 0;
};
