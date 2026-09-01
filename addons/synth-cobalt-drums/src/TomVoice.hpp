#pragma once
#include <cmath>
#include <algorithm>

class TomVoice {
public:
    TomVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = pitchEnv_ = 0.0f;
        active_ = false;
        updateRates();
    }

    void setParameters(float baseTuneHz, float pitchBendSt, float decayMs, float fmDepthPct, float noiseImpactPct) noexcept {
        baseTuneHz_ = std::clamp(baseTuneHz, 50.0f, 500.0f);
        pitchBendSt_ = std::clamp(pitchBendSt, -24.0f, 24.0f);
        decayMs_ = std::clamp(decayMs, 40.0f, 900.0f);
        fmDepthPct_ = std::clamp(fmDepthPct, 0.0f, 1.0f);
        noiseImpactPct_ = std::clamp(noiseImpactPct, 0.0f, 1.0f);
        updateRates();
    }

    void trigger(int32_t midiNote, float velocity) noexcept {
        float noteMultiplier = 1.0f;
        if (midiNote == 41) noteMultiplier = 0.75f;      // Low Tom
        else if (midiNote == 48) noteMultiplier = 1.45f;  // High Tom
        else noteMultiplier = 1.0f;                       // Mid Tom (45)

        voiceBaseFreq_ = baseTuneHz_ * noteMultiplier;
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);
        phase_ = 0.0;
        ampEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        ampEnv_ = pitchEnv_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (!active_) {
            outL = outR = 0.0f;
            return;
        }

        const float pitchSweep = voiceBaseFreq_ * std::pow(2.0f, (pitchBendSt_ * pitchEnv_) / 12.0f);
        phase_ += pitchSweep / sampleRate_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float tone = static_cast<float>(std::sin(phase_ * 6.283185307179586));

        // 2nd harmonic FM punch
        if (fmDepthPct_ > 0.01f) {
            float mod = static_cast<float>(std::sin(phase_ * 12.566370614359172)) * fmDepthPct_ * pitchEnv_;
            tone = std::sin(phase_ * 6.283185307179586 + mod);
        }

        float noise = 0.0f;
        if (noiseImpactPct_ > 0.01f && pitchEnv_ > 0.1f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            noise = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * noiseImpactPct_ * pitchEnv_;
        }

        const float out = (tone + noise * 0.4f) * ampEnv_ * velocity_;

        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        outL = out * 0.9f;
        outR = out * 1.1f;
    }

    bool isActive() const noexcept { return active_; }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.040f * sampleRate_)); // 40ms pitch sweep
    }

    float sampleRate_ = 48000.0f;
    float baseTuneHz_ = 120.0f;
    float voiceBaseFreq_ = 120.0f;
    float pitchBendSt_ = -7.0f;
    float decayMs_ = 260.0f;
    float fmDepthPct_ = 0.10f;
    float noiseImpactPct_ = 0.15f;

    double phase_ = 0.0;
    float ampEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;
    uint32_t noiseSeed_ = 777123;

    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
};
