#pragma once
#include "AudioNode.hpp"
#include <cmath>
#include <algorithm>

class Compressor : public AudioNode {
public:
    Compressor() = default;

    void reset(float sampleRate) override {
        sampleRate_ = sampleRate;
        envelope_ = 0.0f;
        updateCoefficients();
    }

    void process(const float* inBuffer, float* outBuffer, int32_t numFrames) override {
        if (!enabled_) {
            for (int32_t i = 0; i < numFrames * 2; ++i) outBuffer[i] = inBuffer[i];
            return;
        }

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = inBuffer[i * 2];
            float inR = inBuffer[i * 2 + 1];

            float peak = std::max(std::abs(inL), std::abs(inR));

            if (peak > envelope_) {
                envelope_ = attackCoeff_ * (envelope_ - peak) + peak;
            } else {
                envelope_ = releaseCoeff_ * (envelope_ - peak) + peak;
            }

            float gainReduction = 1.0f;
            if (envelope_ > thresholdLinear_ && envelope_ > 1e-5f) {
                float excessDb = 20.0f * std::log10(envelope_ / thresholdLinear_);
                float reducedDb = excessDb * (1.0f - (1.0f / ratio_));
                gainReduction = std::pow(10.0f, -reducedDb / 20.0f);
            }

            float finalGain = gainReduction * makeupLinear_;
            outBuffer[i * 2]     = inL * finalGain;
            outBuffer[i * 2 + 1] = inR * finalGain;
        }
    }

    void setParameter(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: enabled_ = (value > 0.5f); break;
            case 1: thresholdDb_ = std::clamp(value, -40.0f, 0.0f); break;
            case 2: ratio_ = std::clamp(value, 1.0f, 20.0f); break;
            case 3: attackMs_ = std::clamp(value, 1.0f, 100.0f); break;
            case 4: releaseMs_ = std::clamp(value, 10.0f, 500.0f); break;
            case 5: makeupDb_ = std::clamp(value, 0.0f, 24.0f); break;
        }
        updateCoefficients();
    }

    float getParameter(uint32_t paramId) const override {
        switch (paramId) {
            case 0: return enabled_ ? 1.0f : 0.0f;
            case 1: return thresholdDb_;
            case 2: return ratio_;
            case 3: return attackMs_;
            case 4: return releaseMs_;
            case 5: return makeupDb_;
            default: return 0.0f;
        }
    }

private:
    void updateCoefficients() {
        thresholdLinear_ = std::pow(10.0f, thresholdDb_ / 20.0f);
        makeupLinear_ = std::pow(10.0f, makeupDb_ / 20.0f);
        attackCoeff_ = std::exp(-1.0f / ((attackMs_ * 0.001f) * sampleRate_));
        releaseCoeff_ = std::exp(-1.0f / ((releaseMs_ * 0.001f) * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    bool enabled_ = true;
    float thresholdDb_ = -12.0f;
    float ratio_ = 3.0f;
    float attackMs_ = 10.0f;
    float releaseMs_ = 100.0f;
    float makeupDb_ = 0.0f;

    float thresholdLinear_ = 0.25f;
    float makeupLinear_ = 1.0f;
    float attackCoeff_ = 0.99f;
    float releaseCoeff_ = 0.999f;
    float envelope_ = 0.0f;
};
