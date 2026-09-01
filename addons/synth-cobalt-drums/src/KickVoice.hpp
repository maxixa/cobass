#pragma once
#include <cmath>
#include <algorithm>

class KickVoice {
public:
    KickVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = 0.0f;
        pitchEnv_ = 0.0f;
        clickEnv_ = 0.0f;
        active_ = false;
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
    }

    inline float render() noexcept {
        if (!active_) return 0.0f;

        // Exponential pitch drop
        const float pitchSweep = baseFreq_ * (1.0f + (pitchDropPct_ * 5.0f * pitchEnv_));
        const double phaseInc = pitchSweep / sampleRate_;

        phase_ += phaseInc;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float body = static_cast<float>(std::sin(phase_ * 6.283185307179586));

        // Click transient generator (impulse + high-passed burst)
        float click = (clickEnv_ > 0.01f) ? (clickEnv_ * clickPct_ * 1.2f) : 0.0f;

        float out = (body * ampEnv_ + click) * velocity_;

        // Sub-bass saturation
        out = std::tanh(out * drive_);

        // Envelope decays
        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;
        clickEnv_ *= clickDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        return out;
    }

    bool isActive() const noexcept { return active_; }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.025f * sampleRate_)); // fast 25ms punch drop
        clickDecayCoeff_ = std::exp(-1.0f / (0.004f * sampleRate_)); // ultra-short 4ms click
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

    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
    float clickDecayCoeff_ = 0.90f;
};
