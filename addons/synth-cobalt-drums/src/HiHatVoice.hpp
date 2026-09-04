#pragma once
#include <cmath>
#include <array>
#include <algorithm>
#include "BiquadFilter.hpp"

class HiHatVoice {
public:
    HiHatVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        filterBP_.setSampleRate(sampleRate_);
        filterHP_.setSampleRate(sampleRate_);
        for (auto& p : oscPhases_) p = 0.0;
        closedEnv_ = openEnv_ = 0.0f;
        fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = 0.0f;
        updateRates();
        updateFilters();
    }

    void setParameters(float toneHz, float closedDecayMs, float openDecayMs, bool chokeEnabled, float sizzlePct) noexcept {
        toneHz_ = std::clamp(toneHz, 3000.0f, 15000.0f);
        closedDecayMs_ = std::clamp(closedDecayMs, 10.0f, 150.0f);
        openDecayMs_ = std::clamp(openDecayMs, 80.0f, 1500.0f);
        chokeEnabled_ = chokeEnabled;
        sizzlePct_ = std::clamp(sizzlePct, 0.0f, 1.0f);
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
        if (chokeEnabled_) {
            openEnv_ = 0.0f; // Voice choke
        }
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
        lastOutL_ = lastOutR_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (closedEnv_ <= 0.0005f && openEnv_ <= 0.0005f && fadeEnv_ <= 0.001f) {
            outL = outR = 0.0f;
            return;
        }

        // 6-Oscillator Schmitt Trigger Analog Metallic Cluster
        static constexpr float OSC_FREQS[6] = {245.3f, 306.4f, 367.6f, 428.8f, 543.7f, 678.9f};
        float metal = 0.0f;

        for (size_t i = 0; i < 6; ++i) {
            oscPhases_[i] += OSC_FREQS[i] / sampleRate_;
            if (oscPhases_[i] >= 1.0) oscPhases_[i] -= 1.0;
            metal += (oscPhases_[i] < 0.5) ? 0.166f : -0.166f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float noise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float mixed = (metal * (1.0f - sizzlePct_ * 0.5f)) + (noise * sizzlePct_ * 0.7f);

        const float filtered = filterHP_.process(filterBP_.process(mixed));

        const float closedSig = filtered * closedEnv_ * closedVel_;
        const float openSig = filtered * openEnv_ * openVel_;

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
        filterBP_.setParameters(FilterType::BandPass, toneHz_, 0.0f, 3.5f);
        filterHP_.setParameters(FilterType::HighPass, std::min(sampleRate_ * 0.45f, toneHz_ * 0.85f), 0.0f, 0.707f);
    }

    float sampleRate_ = 48000.0f;
    float toneHz_ = 8500.0f;
    float closedDecayMs_ = 45.0f;
    float openDecayMs_ = 450.0f;
    bool chokeEnabled_ = true;
    float sizzlePct_ = 0.40f;

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

    uint32_t noiseSeed_ = 1234567;
    float closedDecayCoeff_ = 0.99f;
    float openDecayCoeff_ = 0.999f;
    BiquadFilter filterBP_;
    BiquadFilter filterHP_;
};
