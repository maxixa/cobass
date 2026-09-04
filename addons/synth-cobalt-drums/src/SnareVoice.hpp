#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class SnareModel : int32_t {
    AnalogDual = 0,    // Warm dual-sine shell + bandpass noise wires
    ModalMembrane = 1, // 3-band resonant modal filter bank
    Modern909 = 2,     // Tight triangle body punch + bright wire snap
    IndustrialGrit = 3 // Asymmetric diode clipping + wavefolded tone
};

class SnareVoice {
public:
    SnareVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase1_ = phase2_ = phase3_ = 0.0;
        bodyEnv_ = noiseEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = noiseSmooth_ = 0.0f;
        active_ = false;
        noiseFilter_.setSampleRate(sampleRate_);
        modalFilter_.setSampleRate(sampleRate_);
        updateRates();
        updateFilter();
    }

    void setParameters(float tuneHz, float bodyDecayMs, float snappyPct, float noiseDecayMs, float filterCutoffHz, int model = 0, float shellRes = 1.0f) noexcept {
        baseFreq_ = std::clamp(tuneHz, 100.0f, 350.0f);
        bodyDecayMs_ = std::clamp(bodyDecayMs, 20.0f, 400.0f);
        snappyPct_ = std::clamp(snappyPct, 0.0f, 1.0f);
        noiseDecayMs_ = std::clamp(noiseDecayMs, 30.0f, 600.0f);
        filterCutoffHz_ = std::clamp(filterCutoffHz, 800.0f, 10000.0f);
        model_ = static_cast<SnareModel>(std::clamp(model, 0, 3));
        shellRes_ = std::clamp(shellRes, 0.5f, 5.0f);
        updateRates();
        updateFilter();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && (bodyEnv_ > 0.01f || noiseEnv_ > 0.01f)) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 77777.0f);

        phase1_ = phase2_ = phase3_ = 0.0;
        bodyEnv_ = 1.0f;
        noiseEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        bodyEnv_ = noiseEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = noiseSmooth_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        const float pitchMult = (model_ == SnareModel::Modern909) ? 0.90f : 0.65f;
        const float f1 = baseFreq_ * (1.0f + pitchMult * pitchEnv_);
        const float f2 = f1 * 1.58f;
        const float f3 = f1 * 2.31f;

        phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
        phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
        phase3_ += f3 / sampleRate_; if (phase3_ >= 1.0) phase3_ -= 1.0;

        float body = 0.0f;
        if (model_ == SnareModel::ModalMembrane) {
            body = static_cast<float>(std::sin(phase1_ * 6.283185307179586) * 0.50 +
                                      std::sin(phase2_ * 6.283185307179586) * 0.30 +
                                      std::sin(phase3_ * 6.283185307179586) * 0.20) * bodyEnv_;
            body = modalFilter_.process(body);
        } else {
            body = static_cast<float>(std::sin(phase1_ * 6.283185307179586) * 0.65 +
                                      std::sin(phase2_ * 6.283185307179586) * 0.35) * bodyEnv_;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        float filteredNoise = noiseFilter_.process(rawNoise);

        noiseSmooth_ = 0.65f * noiseSmooth_ + 0.35f * filteredNoise;
        const float wires = noiseSmooth_ * noiseEnv_;

        float out = ((body * (1.0f - snappyPct_ * 0.4f)) + (wires * snappyPct_ * 1.05f)) * velocity_;
        out = (model_ == SnareModel::IndustrialGrit) ? std::tanh(out * 1.6f) : std::tanh(out * 1.10f);

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        bodyEnv_ *= bodyDecayCoeff_;
        noiseEnv_ *= noiseDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (bodyEnv_ <= 0.0005f && noiseEnv_ <= 0.0005f) {
            active_ = false;
            bodyEnv_ = noiseEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        bodyDecayCoeff_ = std::exp(-1.0f / ((bodyDecayMs_ * 0.001f) * sampleRate_));
        noiseDecayCoeff_ = std::exp(-1.0f / ((noiseDecayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.022f * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilter() noexcept {
        noiseFilter_.setParameters(FilterType::BandPass, std::clamp(filterCutoffHz_, 1000.0f, 6500.0f), 0.0f, 1.05f);
        modalFilter_.setParameters(FilterType::BandPass, baseFreq_ * 1.58f, 0.0f, shellRes_ * 2.0f);
    }

    float sampleRate_ = 48000.0f;
    float baseFreq_ = 185.0f;
    float bodyDecayMs_ = 140.0f;
    float snappyPct_ = 0.65f;
    float noiseDecayMs_ = 220.0f;
    float filterCutoffHz_ = 4500.0f;
    SnareModel model_ = SnareModel::AnalogDual;
    float shellRes_ = 1.0f;

    double phase1_ = 0.0;
    double phase2_ = 0.0;
    double phase3_ = 0.0;
    float bodyEnv_ = 0.0f;
    float noiseEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;
    float noiseSmooth_ = 0.0f;

    uint32_t noiseSeed_ = 424242;
    float bodyDecayCoeff_ = 0.99f;
    float noiseDecayCoeff_ = 0.99f;
    float pitchDecayCoeff_ = 0.90f;
    BiquadFilter noiseFilter_;
    BiquadFilter modalFilter_;
};
