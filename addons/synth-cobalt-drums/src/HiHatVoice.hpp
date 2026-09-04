#pragma once
#include <cmath>
#include <array>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class HatModel : int32_t {
    SchmittMetal = 0, // 6-Osc analog Schmitt-trigger square cluster
    LinearFM = 1,     // 4-Operator inharmonic FM metallic cymbal
    SpectralNoise = 2,// Trap/Drill sizzling granular noise
    ResonantBell = 3  // Ring-modulated high-Q metallic ride/bell
};

class HiHatVoice {
public:
    HiHatVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        filterBP_.setSampleRate(sampleRate_);
        filterHP_.setSampleRate(sampleRate_);
        for (auto& p : oscPhases_) p = 0.0;
        closedEnv_ = openEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = metalSmooth_ = 0.0f;
        updateRates();
        updateFilters();
    }

    void setParameters(float toneHz, float closedDecayMs, float openDecayMs, bool chokeEnabled, float sizzlePct, int model = 0, float pitchShiftSt = 0.0f) noexcept {
        toneHz_ = std::clamp(toneHz, 3000.0f, 12000.0f);
        closedDecayMs_ = std::clamp(closedDecayMs, 10.0f, 150.0f);
        openDecayMs_ = std::clamp(openDecayMs, 80.0f, 1500.0f);
        chokeEnabled_ = chokeEnabled;
        sizzlePct_ = std::clamp(sizzlePct, 0.0f, 1.0f);
        model_ = static_cast<HatModel>(std::clamp(model, 0, 3));
        pitchMult_ = std::pow(2.0f, std::clamp(pitchShiftSt, -12.0f, 12.0f) / 12.0f);
        updateRates();
        updateFilters();
    }

    void triggerClosed(float velocity) noexcept {
        if (closedEnv_ > 0.01f || openEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 654321.0f);
        closedVel_ = std::clamp(velocity, 0.05f, 1.0f);
        closedEnv_ = 1.0f;
        if (chokeEnabled_) openEnv_ = 0.0f;
    }

    void triggerOpen(float velocity) noexcept {
        if (closedEnv_ > 0.01f || openEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 123456.0f);
        openVel_ = std::clamp(velocity, 0.05f, 1.0f);
        openEnv_ = 1.0f;
    }

    void stop() noexcept {
        closedEnv_ = openEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = metalSmooth_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (closedEnv_ <= 0.0005f && openEnv_ <= 0.0005f && fadeEnv_ <= 0.001f) {
            outL = outR = 0.0f;
            return;
        }

        float metal = 0.0f;
        if (model_ == HatModel::LinearFM) {
            // 4-Op inharmonic FM metallic generator (1.0 : 1.414 : 2.828 : 4.23)
            static constexpr float FM_RATIOS[4] = {1.0f, 1.4142f, 2.8284f, 4.231f};
            float baseF = 440.0f * pitchMult_;
            for (size_t i = 0; i < 4; ++i) {
                oscPhases_[i] += (baseF * FM_RATIOS[i]) / sampleRate_;
                if (oscPhases_[i] >= 1.0) oscPhases_[i] -= 1.0;
            }
            float mod = std::sin(oscPhases_[3] * 6.283185307179586) * 1.8f;
            metal = std::sin(oscPhases_[0] * 6.283185307179586 + mod) * 0.5f +
                    std::sin(oscPhases_[1] * 6.283185307179586) * 0.3f +
                    std::sin(oscPhases_[2] * 6.283185307179586) * 0.2f;
        } else {
            // 6-Oscillator Schmitt Trigger Analog Metallic Cluster
            static constexpr float OSC_FREQS[6] = {245.3f, 306.4f, 367.6f, 428.8f, 543.7f, 678.9f};
            for (size_t i = 0; i < 6; ++i) {
                oscPhases_[i] += (OSC_FREQS[i] * pitchMult_) / sampleRate_;
                if (oscPhases_[i] >= 1.0) oscPhases_[i] -= 1.0;
                metal += (oscPhases_[i] < 0.5) ? 0.166f : -0.166f;
            }
            metal = std::tanh(metal * 1.35f);
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float noise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float mixed = (metal * (1.0f - sizzlePct_ * 0.45f)) + (noise * sizzlePct_ * 0.45f);

        float filtered = filterHP_.process(filterBP_.process(mixed));
        metalSmooth_ = 0.55f * metalSmooth_ + 0.45f * filtered;

        const float closedSig = metalSmooth_ * closedEnv_ * closedVel_;
        const float openSig = metalSmooth_ * openEnv_ * openVel_;

        closedEnv_ *= closedDecayCoeff_;
        openEnv_ *= openDecayCoeff_;

        float sL = (closedSig * 0.95f) + (openSig * 1.05f);
        float sR = (closedSig * 1.05f) + (openSig * 0.95f);

        if (fadeEnv_ > 0.001f) {
            sL += fadeSampleL_ * fadeEnv_;
            sR += fadeSampleR_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        lastOutL_ = sL;
        lastOutR_ = sR;
        outL = sL;
        outR = sR;
    }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        closedDecayCoeff_ = std::exp(-1.0f / ((closedDecayMs_ * 0.001f) * sampleRate_));
        openDecayCoeff_ = std::exp(-1.0f / ((openDecayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilters() noexcept {
        filterBP_.setParameters(FilterType::BandPass, toneHz_, 0.0f, 1.75f);
        filterHP_.setParameters(FilterType::HighPass, std::min(sampleRate_ * 0.35f, toneHz_ * 0.60f), 0.0f, 0.707f);
    }

    float sampleRate_ = 48000.0f;
    float toneHz_ = 8500.0f;
    float closedDecayMs_ = 45.0f;
    float openDecayMs_ = 450.0f;
    bool chokeEnabled_ = true;
    float sizzlePct_ = 0.40f;
    HatModel model_ = HatModel::SchmittMetal;
    float pitchMult_ = 1.0f;

    std::array<double, 6> oscPhases_{};
    float closedEnv_ = 0.0f;
    float openEnv_ = 0.0f;
    float closedVel_ = 1.0f;
    float openVel_ = 1.0f;

    float lastOutL_ = 0.0f;
    float lastOutR_ = 0.0f;
    float fadeSampleL_ = 0.0f;
    float fadeSampleR_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;
    float metalSmooth_ = 0.0f;

    uint32_t noiseSeed_ = 1234567;
    float closedDecayCoeff_ = 0.99f;
    float openDecayCoeff_ = 0.999f;
    BiquadFilter filterBP_;
    BiquadFilter filterHP_;
};
