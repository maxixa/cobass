#pragma once
#include <cmath>
#include <algorithm>

enum class TomModel : int32_t {
    AnalogSweep = 0,   // Classic exponential pitch drop tom
    ModalMembrane = 1, // Circular membrane physical resonator
    SlapLaser = 2,     // Fast exponential drop with 2nd-harmonic FM
    AfroConga = 3      // Wooden shell resonance + slap transient
};

class TomVoice {
public:
    TomVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = 0.0f;
        active_ = false;
        updateRates();
    }

    void setParameters(float baseTuneHz, float pitchBendSt, float decayMs, float fmDepthPct, float noiseImpactPct, int model = 0, float damping = 0.5f) noexcept {
        baseTuneHz_ = std::clamp(baseTuneHz, 50.0f, 500.0f);
        pitchBendSt_ = std::clamp(pitchBendSt, -24.0f, 24.0f);
        decayMs_ = std::clamp(decayMs, 40.0f, 900.0f);
        fmDepthPct_ = std::clamp(fmDepthPct, 0.0f, 1.0f);
        noiseImpactPct_ = std::clamp(noiseImpactPct, 0.0f, 1.0f);
        model_ = static_cast<TomModel>(std::clamp(model, 0, 3));
        damping_ = std::clamp(damping, 0.0f, 1.0f);
        updateRates();
    }

    void trigger(int32_t midiNote, float velocity) noexcept {
        float noteMultiplier = 1.0f;
        if (midiNote == 41) noteMultiplier = 0.70f;       // Low Floor Tom (F1)
        else if (midiNote == 43) noteMultiplier = 0.85f;  // High Floor Tom (G1)
        else if (midiNote == 45) noteMultiplier = 1.00f;  // Low-Mid Tom (A1)
        else if (midiNote == 47) noteMultiplier = 1.20f;  // High-Mid Tom (B1)
        else if (midiNote == 48) noteMultiplier = 1.45f;  // High Tom (C2)
        else if (midiNote == 50) noteMultiplier = 1.70f;  // High Timbale (D2)

        voiceBaseFreq_ = baseTuneHz_ * noteMultiplier;
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && ampEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 44444.0f);

        phase_ = 0.0;
        ampEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        ampEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) {
            outL = outR = 0.0f;
            return;
        }

        const float pitchSweep = voiceBaseFreq_ * std::pow(2.0f, (pitchBendSt_ * pitchEnv_) / 12.0f);
        phase_ += pitchSweep / sampleRate_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float tone = 0.0f;
        if (model_ == TomModel::ModalMembrane) {
            double p2 = std::fmod(phase_ * 1.59, 1.0);
            double p3 = std::fmod(phase_ * 2.14, 1.0);
            tone = static_cast<float>(std::sin(phase_ * 6.283185307179586) * 0.60 +
                                      std::sin(p2 * 6.283185307179586) * 0.25 +
                                      std::sin(p3 * 6.283185307179586) * 0.15);
        } else {
            tone = static_cast<float>(std::sin(phase_ * 6.283185307179586));
            if (fmDepthPct_ > 0.01f) {
                float mod = static_cast<float>(std::sin(phase_ * 12.566370614359172)) * fmDepthPct_ * pitchEnv_;
                tone = std::sin(phase_ * 6.283185307179586 + mod);
            }
        }

        float noise = 0.0f;
        if (noiseImpactPct_ > 0.01f && pitchEnv_ > 0.1f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            noise = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * noiseImpactPct_ * pitchEnv_;
        }

        float out = (tone + noise * 0.35f) * ampEnv_ * velocity_;
        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        float sL = out * 0.9f;
        float sR = out * 1.1f;

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

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.038f * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    float baseTuneHz_ = 120.0f;
    float voiceBaseFreq_ = 120.0f;
    float pitchBendSt_ = -7.0f;
    float decayMs_ = 260.0f;
    float fmDepthPct_ = 0.10f;
    float noiseImpactPct_ = 0.15f;
    TomModel model_ = TomModel::AnalogSweep;
    float damping_ = 0.5f;

    double phase_ = 0.0;
    float ampEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastOutL_ = 0.0f;
    float lastOutR_ = 0.0f;
    float fadeSampleL_ = 0.0f;
    float fadeSampleR_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 777123;
    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
};
