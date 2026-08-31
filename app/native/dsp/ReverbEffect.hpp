#pragma once
#include "AudioNode.hpp"
#include <vector>
#include <array>
#include <algorithm>

// Schroeder Algorithmic Reverb with Stereo Decorrelation
class ReverbEffect : public AudioNode {
private:
    struct CombFilter {
        std::vector<float> buffer;
        size_t index = 0;
        float feedback = 0.75f;
        float damp = 0.25f;
        float filterStore = 0.0f;

        void resize(size_t size) {
            buffer.assign(size, 0.0f);
            index = 0;
            filterStore = 0.0f;
        }

        inline float process(float in) {
            float out = buffer[index];
            filterStore = (out * (1.0f - damp)) + (filterStore * damp);
            buffer[index] = in + (filterStore * feedback);
            if (++index >= buffer.size()) index = 0;
            return out;
        }
    };

    struct AllPassFilter {
        std::vector<float> buffer;
        size_t index = 0;
        float feedback = 0.5f;

        void resize(size_t size) {
            buffer.assign(size, 0.0f);
            index = 0;
        }

        inline float process(float in) {
            float bufOut = buffer[index];
            float out = -in + bufOut;
            buffer[index] = in + (bufOut * feedback);
            if (++index >= buffer.size()) index = 0;
            return out;
        }
    };

public:
    ReverbEffect() {
        const int combTuning[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        const int allpassTuning[4] = {556, 441, 341, 225};

        for (int i = 0; i < 8; ++i) combFilters_[i].resize(combTuning[i]);
        for (int i = 0; i < 4; ++i) allPassFilters_[i].resize(allpassTuning[i]);
        updateParameters();
    }

    void reset(float sampleRate) override {
        sampleRate_ = sampleRate;
        updateParameters();
    }

    void process(const float* inBuffer, float* outBuffer, int32_t numFrames) override {
        if (!enabled_) {
            for (int32_t i = 0; i < numFrames * 2; ++i) outBuffer[i] = inBuffer[i];
            return;
        }

        for (int32_t i = 0; i < numFrames; ++i) {
            float in = (inBuffer[i * 2] + inBuffer[i * 2 + 1]) * 0.5f * 0.025f;
            float combSumL = 0.0f;
            float combSumR = 0.0f;

            // Split 8 Comb filters for stereo decorrelation (Even -> Left, Odd -> Right)
            for (int k = 0; k < 8; k += 2) {
                combSumL += combFilters_[k].process(in);
                combSumR += combFilters_[k + 1].process(in);
            }

            // All-pass diffusers (2 per channel)
            float outL = allPassFilters_[1].process(allPassFilters_[0].process(combSumL));
            float outR = allPassFilters_[3].process(allPassFilters_[2].process(combSumR));

            float wet = wetMix_;
            float dry = (1.0f - wetMix_);

            outBuffer[i * 2]     = inBuffer[i * 2] * dry + outL * wet;
            outBuffer[i * 2 + 1] = inBuffer[i * 2 + 1] * dry + outR * wet;
        }
    }

    void setParameter(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: enabled_ = (value > 0.5f); break;
            case 1: roomSize_ = std::clamp(value, 0.1f, 0.98f); break;
            case 2: damping_ = std::clamp(value, 0.0f, 1.0f); break;
            case 3: wetMix_ = std::clamp(value, 0.0f, 1.0f); break;
        }
        updateParameters();
    }

    float getParameter(uint32_t paramId) const override {
        switch (paramId) {
            case 0: return enabled_ ? 1.0f : 0.0f;
            case 1: return roomSize_;
            case 2: return damping_;
            case 3: return wetMix_;
            default: return 0.0f;
        }
    }

private:
    void updateParameters() {
        for (auto& cf : combFilters_) {
            cf.feedback = roomSize_;
            cf.damp = damping_;
        }
    }

    float sampleRate_ = 48000.0f;
    bool enabled_ = true;
    float roomSize_ = 0.75f;
    float damping_ = 0.25f;
    float wetMix_ = 0.3f;

    std::array<CombFilter, 8> combFilters_;
    std::array<AllPassFilter, 4> allPassFilters_;
};
