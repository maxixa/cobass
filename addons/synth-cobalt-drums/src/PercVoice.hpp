#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

class PercVoice {
public:
    PercVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        rimFilter_.setSampleRate(sampleRate_);
        cowFilter_.setSampleRate(sampleRate_);
        phase1_ = phase2_ = 0.0;
        rimEnv_ = cowEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        updateRates();
        updateFilters();
    }

    void setParameters(float rimPitchHz, float rimDecayMs, float cowTuneHz, float cowDecayMs) noexcept {
        rimPitchHz_ = std::clamp(rimPitchHz, 800.0f, 3500.0f);
        rimDecayMs_ = std::clamp(rimDecayMs, 5.0f, 100.0f);
        cowTuneHz_ = std::clamp(cowTuneHz, 300.0f, 1200.0f);
        cowDecayMs_ = std::clamp(cowDecayMs, 30.0f, 600.0f);
        updateRates();
        updateFilters();
    }

    void triggerRim(float velocity) noexcept {
        // ISSUE-2 FIX: Mutual voice stealing prevents rim + cowbell overlap distortion
        cowEnv_ = 0.0f;

        // BUG-1 FIX: Retrigger micro-fade
        if (active_ && rimEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        // ISSUE-1 FIX: Randomize noise seed
        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 33333.0f);

        rimVel_ = std::clamp(velocity, 0.05f, 1.0f);
        rimEnv_ = 1.0f;
        active_ = true;
    }

    void triggerCowbell(float velocity) noexcept {
        // ISSUE-2 FIX: Mutual voice stealing
        rimEnv_ = 0.0f;

        // BUG-1 FIX: Retrigger micro-fade
        if (active_ && cowEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        cowVel_ = std::clamp(velocity, 0.05f, 1.0f);
        cowEnv_ = 1.0f;
        phase1_ = phase2_ = 0.0;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        rimEnv_ = cowEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        float out = 0.0f;

        // Rimshot woodblock impulse
        if (rimEnv_ > 0.0005f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            const float click = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * rimEnv_;
            out += rimFilter_.process(click) * rimVel_ * 2.0f;
            rimEnv_ *= rimDecayCoeff_;
        }

        // 808 Cowbell (Dual tuned square waves: f1 = cowTuneHz, f2 = cowTuneHz * 1.4815)
        if (cowEnv_ > 0.0005f) {
            const float f1 = cowTuneHz_;
            const float f2 = cowTuneHz_ * 1.4815f;

            phase1_ += f1 / sampleRate_;
            if (phase1_ >= 1.0) phase1_ -= 1.0;
            phase2_ += f2 / sampleRate_;
            if (phase2_ >= 1.0) phase2_ -= 1.0;

            const float sq1 = (phase1_ < 0.5) ? 0.5f : -0.5f;
            const float sq2 = (phase2_ < 0.5) ? 0.5f : -0.5f;

            const float cowSig = cowFilter_.process(sq1 + sq2) * cowEnv_ * cowVel_;
            out += cowSig * 1.4f;
            cowEnv_ *= cowDecayCoeff_;
        }

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        if (rimEnv_ <= 0.0005f && cowEnv_ <= 0.0005f) {
            active_ = false;
            rimEnv_ = cowEnv_ = 0.0f;
        }

        out = std::tanh(out);
        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        rimDecayCoeff_ = std::exp(-1.0f / ((rimDecayMs_ * 0.001f) * sampleRate_));
        cowDecayCoeff_ = std::exp(-1.0f / ((cowDecayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilters() noexcept {
        rimFilter_.setParameters(FilterType::BandPass, rimPitchHz_, 0.0f, 6.0f);
        cowFilter_.setParameters(FilterType::BandPass, cowTuneHz_ * 2.5f, 0.0f, 4.0f);
    }

    float sampleRate_ = 48000.0f;
    float rimPitchHz_ = 1750.0f;
    float rimDecayMs_ = 25.0f;
    float cowTuneHz_ = 540.0f;
    float cowDecayMs_ = 180.0f;

    double phase1_ = 0.0;
    double phase2_ = 0.0;
    float rimEnv_ = 0.0f;
    float cowEnv_ = 0.0f;
    float rimVel_ = 1.0f;
    float cowVel_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 555121;
    float rimDecayCoeff_ = 0.95f;
    float cowDecayCoeff_ = 0.995f;
    BiquadFilter rimFilter_;
    BiquadFilter cowFilter_;
};
