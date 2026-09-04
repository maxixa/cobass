#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

class KickVoice {
public:
    KickVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = 0.0f;
        pitchEnv_ = 0.0f;
        clickEnv_ = 0.0f;
        fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        clickFilter_.setSampleRate(sampleRate_);
        clickFilter_.setParameters(FilterType::HighPass, 4500.0f, 0.0f, 1.2f);
        updateRates();
    }

    void setParameters(float tuneHz, float decayMs, float pitchDropPct, float clickPct, float distPct) noexcept {
        baseFreq_ = std::clamp(tuneHz, 30.0f, 120.0f);
        decayMs_ = std::clamp(decayMs, 30.0f, 1500.0f);
        pitchDropPct_ = std::clamp(pitchDropPct, 0.0f, 1.0f);
        clickPct_ = std::clamp(clickPct, 0.0f, 1.0f);
        drive_ = 1.0f + (std::clamp(distPct, 0.0f, 1.0f) * 3.5f);
        updateRates();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);
        
        // BUG-1 FIX: Retrigger micro-fade to eliminate clicks/pops
        if (active_ && ampEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        // ISSUE-1 FIX: Randomize noise seed per trigger
        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 99999.0f);

        phase_ = 0.0;
        ampEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        clickEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        ampEnv_ = 0.0f;
        pitchEnv_ = 0.0f;
        clickEnv_ = 0.0f;
        fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        // Exponential pitch drop
        const float pitchSweep = baseFreq_ * (1.0f + (pitchDropPct_ * 5.0f * pitchEnv_));
        const double phaseInc = pitchSweep / sampleRate_;

        phase_ += phaseInc;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float body = static_cast<float>(std::sin(phase_ * 6.283185307179586));

        // BUG-4 FIX: High-pass filtered noise burst click transient
        float click = 0.0f;
        if (clickEnv_ > 0.005f && clickPct_ > 0.01f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
            click = clickFilter_.process(rawNoise) * clickEnv_ * clickPct_ * 1.6f;
        }

        float out = (body * ampEnv_ + click) * velocity_;

        // Sub-bass saturation
        out = std::tanh(out * drive_);

        // Apply anti-click retrigger fade
        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        // Envelope decays
        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;
        clickEnv_ *= clickDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.025f * sampleRate_)); // fast 25ms punch drop
        clickDecayCoeff_ = std::exp(-1.0f / (0.005f * sampleRate_)); // 5ms click burst
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_)); // 1.5ms retrigger fade
    }

    float sampleRate_ = 48000.0f;
    float baseFreq_ = 48.0f;
    float decayMs_ = 350.0f;
    float pitchDropPct_ = 0.75f;
    float clickPct_ = 0.50f;
    float drive_ = 1.2f;

    double phase_ = 0.0;
    float ampEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float clickEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 54321;
    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
    float clickDecayCoeff_ = 0.90f;
    BiquadFilter clickFilter_;
};
