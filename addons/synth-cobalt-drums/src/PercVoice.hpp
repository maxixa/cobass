#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class RimMode : int32_t {
    ClassicRim = 0,    // High-Q acoustic woodblock impulse
    HardWoodblock = 1, // Resonant modal clave
    Shaker = 2,        // Granular shaker burst
    ElectronicClave = 3// Tuned electronic click
};

enum class CowbellMode : int32_t {
    Cowbell808 = 0,   // Dual square wave 808 cowbell
    FMAgogo = 1,      // 2-Op inharmonic FM Agogo bell
    MetallicZap = 2,  // Laser sweep perc zap
    ResonantTri = 3   // Pure resonant triangle
};

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

    void setParameters(float rimPitchHz, float rimDecayMs, float cowTuneHz, float cowDecayMs, int rimMode = 0, int cowMode = 0, float ringModDepth = 0.0f) noexcept {
        rimPitchHz_ = std::clamp(rimPitchHz, 500.0f, 3500.0f);
        rimDecayMs_ = std::clamp(rimDecayMs, 5.0f, 100.0f);
        cowTuneHz_ = std::clamp(cowTuneHz, 250.0f, 1200.0f);
        cowDecayMs_ = std::clamp(cowDecayMs, 30.0f, 600.0f);
        rimMode_ = static_cast<RimMode>(std::clamp(rimMode, 0, 3));
        cowMode_ = static_cast<CowbellMode>(std::clamp(cowMode, 0, 3));
        ringMod_ = std::clamp(ringModDepth, 0.0f, 1.0f);
        updateRates();
        updateFilters();
    }

    void triggerRim(float velocity) noexcept {
        cowEnv_ = 0.0f;

        if (active_ && rimEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 33333.0f);
        rimVel_ = std::clamp(velocity, 0.05f, 1.0f);
        rimEnv_ = 1.0f;
        active_ = true;
    }

    void triggerCowbell(float velocity) noexcept {
        rimEnv_ = 0.0f;

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

        if (rimEnv_ > 0.0005f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            const float click = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * rimEnv_;
            out += rimFilter_.process(click) * rimVel_ * 1.8f;
            rimEnv_ *= rimDecayCoeff_;
        }

        if (cowEnv_ > 0.0005f) {
            float cowSig = 0.0f;
            if (cowMode_ == CowbellMode::FMAgogo) {
                const float f1 = cowTuneHz_;
                const float f2 = cowTuneHz_ * 1.618f;
                phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
                phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
                float mod = std::sin(phase2_ * 6.283185307179586) * 1.5f;
                cowSig = std::sin(phase1_ * 6.283185307179586 + mod);
            } else {
                const float f1 = cowTuneHz_;
                const float f2 = cowTuneHz_ * 1.4815f;
                phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
                phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
                const float sq1 = (phase1_ < 0.5) ? 0.5f : -0.5f;
                const float sq2 = (phase2_ < 0.5) ? 0.5f : -0.5f;
                cowSig = cowFilter_.process(sq1 + sq2);
            }

            out += cowSig * cowEnv_ * cowVel_ * 1.25f;
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
        rimFilter_.setParameters(FilterType::BandPass, rimPitchHz_, 0.0f, 5.5f);
        cowFilter_.setParameters(FilterType::BandPass, cowTuneHz_ * 2.5f, 0.0f, 3.5f);
    }

    float sampleRate_ = 48000.0f;
    float rimPitchHz_ = 1750.0f;
    float rimDecayMs_ = 25.0f;
    float cowTuneHz_ = 540.0f;
    float cowDecayMs_ = 180.0f;
    RimMode rimMode_ = RimMode::ClassicRim;
    CowbellMode cowMode_ = CowbellMode::Cowbell808;
    float ringMod_ = 0.0f;

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
