#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

class SnareVoice {
public:
    SnareVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase1_ = phase2_ = 0.0;
        bodyEnv_ = 0.0f;
        noiseEnv_ = 0.0f;
        pitchEnv_ = 0.0f;
        active_ = false;
        noiseFilter_.setSampleRate(sampleRate_);
        updateRates();
        updateFilter();
    }

    void setParameters(float tuneHz, float bodyDecayMs, float snappyPct, float noiseDecayMs, float filterCutoffHz) noexcept {
        baseFreq_ = std::clamp(tuneHz, 100.0f, 350.0f);
        bodyDecayMs_ = std::clamp(bodyDecayMs, 20.0f, 400.0f);
        snappyPct_ = std::clamp(snappyPct, 0.0f, 1.0f);
        noiseDecayMs_ = std::clamp(noiseDecayMs, 30.0f, 600.0f);
        filterCutoffHz_ = std::clamp(filterCutoffHz, 800.0f, 12000.0f);
        updateRates();
        updateFilter();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);
        phase1_ = phase2_ = 0.0;
        bodyEnv_ = 1.0f;
        noiseEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        bodyEnv_ = noiseEnv_ = pitchEnv_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_) return 0.0f;

        // Dual-harmonic body tone
        const float f1 = baseFreq_ * (1.0f + 0.5f * pitchEnv_);
        const float f2 = f1 * 1.62f;

        phase1_ += f1 / sampleRate_;
        if (phase1_ >= 1.0) phase1_ -= 1.0;
        phase2_ += f2 / sampleRate_;
        if (phase2_ >= 1.0) phase2_ -= 1.0;

        const float body = (static_cast<float>(std::sin(phase1_ * 6.283185307179586)) * 0.6f +
                            static_cast<float>(std::sin(phase2_ * 6.283185307179586)) * 0.4f) * bodyEnv_;

        // Filtered noise snare wires
        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float filteredNoise = noiseFilter_.process(rawNoise) * noiseEnv_;

        const float out = ((body * (1.0f - snappyPct_ * 0.5f)) + (filteredNoise * snappyPct_ * 1.4f)) * velocity_;

        bodyEnv_ *= bodyDecayCoeff_;
        noiseEnv_ *= noiseDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (bodyEnv_ <= 0.0005f && noiseEnv_ <= 0.0005f) {
            active_ = false;
            bodyEnv_ = noiseEnv_ = 0.0f;
        }

        return std::tanh(out * 1.2f);
    }

    bool isActive() const noexcept { return active_; }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        bodyDecayCoeff_ = std::exp(-1.0f / ((bodyDecayMs_ * 0.001f) * sampleRate_));
        noiseDecayCoeff_ = std::exp(-1.0f / ((noiseDecayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.015f * sampleRate_));
    }

    void updateFilter() noexcept {
        noiseFilter_.setParameters(FilterType::HighPass, filterCutoffHz_, 0.0f, 1.2f);
    }

    float sampleRate_ = 48000.0f;
    float baseFreq_ = 185.0f;
    float bodyDecayMs_ = 140.0f;
    float snappyPct_ = 0.65f;
    float noiseDecayMs_ = 220.0f;
    float filterCutoffHz_ = 4500.0f;

    double phase1_ = 0.0;
    double phase2_ = 0.0;
    float bodyEnv_ = 0.0f;
    float noiseEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;
    uint32_t noiseSeed_ = 424242;

    float bodyDecayCoeff_ = 0.99f;
    float noiseDecayCoeff_ = 0.99f;
    float pitchDecayCoeff_ = 0.90f;
    BiquadFilter noiseFilter_;
};
