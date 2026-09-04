#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

class ClapVoice {
public:
    ClapVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        filter_.setSampleRate(sampleRate_);
        burstTimer_ = 0;
        burstIndex_ = 0;
        currentBurstEnv_ = 0.0f;
        tailEnv_ = 0.0f;
        fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        updateRates();
        updateFilter();
    }

    void setParameters(float toneHz, float spreadMs, float decayMs, float roomPct) noexcept {
        toneHz_ = std::clamp(toneHz, 600.0f, 4000.0f);
        spreadMs_ = std::clamp(spreadMs, 5.0f, 30.0f);
        decayMs_ = std::clamp(decayMs, 50.0f, 600.0f);
        roomPct_ = std::clamp(roomPct, 0.0f, 1.0f);
        updateRates();
        updateFilter();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && (tailEnv_ > 0.01f || currentBurstEnv_ > 0.01f)) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 88888.0f);

        burstIndex_ = 0;
        burstTimer_ = 0;
        currentBurstEnv_ = 1.0f;
        tailEnv_ = 0.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        tailEnv_ = 0.0f;
        currentBurstEnv_ = 0.0f;
        fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        if (burstIndex_ < 3) {
            burstTimer_++;
            if (burstTimer_ >= spreadSamples_) {
                burstTimer_ = 0;
                burstIndex_++;
                currentBurstEnv_ = 1.0f;
                if (burstIndex_ == 3) {
                    tailEnv_ = 1.0f;
                }
            }
        }

        currentBurstEnv_ *= burstDecayCoeff_;
        tailEnv_ *= tailDecayCoeff_;

        const float totalEnv = (burstIndex_ < 3 ? currentBurstEnv_ : 0.0f) + (tailEnv_ * (0.8f + roomPct_ * 0.4f));

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float filtered = filter_.process(rawNoise);

        float out = std::tanh(filtered * totalEnv * velocity_ * 1.5f);

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        if (burstIndex_ >= 3 && tailEnv_ <= 0.0005f) {
            active_ = false;
            tailEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        spreadSamples_ = static_cast<int32_t>((spreadMs_ * 0.001f) * sampleRate_);
        burstDecayCoeff_ = std::exp(-1.0f / (0.003f * sampleRate_)); // 3ms micro impulse
        tailDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilter() noexcept {
        filter_.setParameters(FilterType::BandPass, toneHz_, 0.0f, 2.5f);
    }

    float sampleRate_ = 48000.0f;
    float toneHz_ = 1800.0f;
    float spreadMs_ = 14.0f;
    float decayMs_ = 240.0f;
    float roomPct_ = 0.30f;

    int32_t spreadSamples_ = 672;
    int32_t burstTimer_ = 0;
    int32_t burstIndex_ = 0;
    float currentBurstEnv_ = 0.0f;
    float tailEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 999111;
    float burstDecayCoeff_ = 0.90f;
    float tailDecayCoeff_ = 0.999f;
    BiquadFilter filter_;
};
