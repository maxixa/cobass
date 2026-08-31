#pragma once
#include "AudioNode.hpp"
#include "BiquadFilter.hpp"
#include <cmath>

class ParametricEQ : public AudioNode {
public:
    ParametricEQ() = default;

    void reset(float sampleRate) override {
        sampleRate_ = sampleRate;
        band1L_.setSampleRate(sampleRate);
        band1R_.setSampleRate(sampleRate);
        band2L_.setSampleRate(sampleRate);
        band2R_.setSampleRate(sampleRate);
        band3L_.setSampleRate(sampleRate);
        band3R_.setSampleRate(sampleRate);
        band4L_.setSampleRate(sampleRate);
        band4R_.setSampleRate(sampleRate);
        updateFilters();
    }

    void process(const float* inBuffer, float* outBuffer, int32_t numFrames) override {
        if (!enabled_) {
            for (int32_t i = 0; i < numFrames * 2; ++i) outBuffer[i] = inBuffer[i];
            return;
        }

        for (int32_t i = 0; i < numFrames; ++i) {
            float left = inBuffer[i * 2];
            float right = inBuffer[i * 2 + 1];

            // Left Channel True EQ Cascading Chain
            left = band1L_.process(left);
            left = band2L_.process(left);
            left = band3L_.process(left);
            left = band4L_.process(left);

            // Right Channel True EQ Cascading Chain
            right = band1R_.process(right);
            right = band2R_.process(right);
            right = band3R_.process(right);
            right = band4R_.process(right);

            outBuffer[i * 2]     = left;
            outBuffer[i * 2 + 1] = right;
        }
    }

    void setParameter(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: enabled_ = (value > 0.5f); break;
            case 1: lowGainDb_ = std::clamp(value, -18.0f, 18.0f); break;     // Low Shelf
            case 2: midLowGainDb_ = std::clamp(value, -18.0f, 18.0f); break;  // Mid Low Peak
            case 3: midHighGainDb_ = std::clamp(value, -18.0f, 18.0f); break; // Mid High Peak
            case 4: highGainDb_ = std::clamp(value, -18.0f, 18.0f); break;    // High Shelf
        }
        updateFilters();
    }

    float getParameter(uint32_t paramId) const override {
        switch (paramId) {
            case 0: return enabled_ ? 1.0f : 0.0f;
            case 1: return lowGainDb_;
            case 2: return midLowGainDb_;
            case 3: return midHighGainDb_;
            case 4: return highGainDb_;
            default: return 0.0f;
        }
    }

private:
    void updateFilters() {
        band1L_.setParameters(FilterType::LowShelf, 100.0f, lowGainDb_, 0.707f);
        band1R_.setParameters(FilterType::LowShelf, 100.0f, lowGainDb_, 0.707f);

        band2L_.setParameters(FilterType::PeakingEQ, 600.0f, midLowGainDb_, 1.2f);
        band2R_.setParameters(FilterType::PeakingEQ, 600.0f, midLowGainDb_, 1.2f);

        band3L_.setParameters(FilterType::PeakingEQ, 2500.0f, midHighGainDb_, 1.2f);
        band3R_.setParameters(FilterType::PeakingEQ, 2500.0f, midHighGainDb_, 1.2f);

        band4L_.setParameters(FilterType::HighShelf, 8000.0f, highGainDb_, 0.707f);
        band4R_.setParameters(FilterType::HighShelf, 8000.0f, highGainDb_, 0.707f);
    }

    float sampleRate_ = 48000.0f;
    bool enabled_ = true;
    float lowGainDb_ = 0.0f;
    float midLowGainDb_ = 0.0f;
    float midHighGainDb_ = 0.0f;
    float highGainDb_ = 0.0f;

    BiquadFilter band1L_, band1R_;
    BiquadFilter band2L_, band2R_;
    BiquadFilter band3L_, band3R_;
    BiquadFilter band4L_, band4R_;
};
