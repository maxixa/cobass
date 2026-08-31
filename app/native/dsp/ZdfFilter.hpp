#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class ZdfFilterMode : int32_t {
    Ladder24 = 0,    // 4-Pole ZDF Moog Ladder (24dB/oct with non-linear saturation)
    Lowpass12 = 1,   // 2-Pole ZDF State Variable Lowpass (12dB/oct)
    Bandpass12 = 2,  // 2-Pole ZDF State Variable Bandpass (12dB/oct)
    Highpass12 = 3,  // 2-Pole ZDF State Variable Highpass (12dB/oct)
    Notch12 = 4      // 2-Pole ZDF State Variable Band-Reject Notch
};

class ZdfFilter {
public:
    ZdfFilter() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateCoefficients();
    }

    void setParameters(ZdfFilterMode mode, float cutoffHz, float resonance, float drive = 1.0f) noexcept {
        mode_ = mode;
        const float maxCutoff = sampleRate_ * 0.45f;
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, maxCutoff);
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        drive_ = std::clamp(drive, 0.5f, 5.0f);
        updateCoefficients();
    }

    void setCutoff(float cutoffHz) noexcept {
        const float maxCutoff = sampleRate_ * 0.45f;
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, maxCutoff);
        updateCoefficients();
    }

    void setResonance(float resonance) noexcept {
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        updateCoefficients();
    }

    void setDrive(float drive) noexcept {
        drive_ = std::clamp(drive, 0.5f, 5.0f);
    }

    void setMode(ZdfFilterMode mode) noexcept {
        mode_ = mode;
        updateCoefficients();
    }

    inline float process(float in) noexcept {
        // Recovery guard: reset state if NaN or Inf ever occurs
        if (std::isnan(s1_) || std::isinf(s1_) || std::isnan(s4_) || std::isinf(s4_)) {
            reset();
        }

        // Non-linear input saturation
        const float drivenIn = std::tanh(in * drive_);

        if (mode_ == ZdfFilterMode::Ladder24) {
            // 4-Pole ZDF Moog Ladder with saturating transistor feedback loop
            // Resonance (0.1 .. 16.0) smoothly maps to k in [0.0, 3.96] (screaming acid self-oscillation)
            const float k = std::clamp((resonance_ / (resonance_ + 1.2f)) * 4.25f, 0.0f, 3.96f);

            // Saturated feedback prevents exponential state overflow
            const float satFeedback = std::tanh(s4_);
            const float u = (drivenIn - k * satFeedback) / (1.0f + k * G4_);

            const float v1 = (u - s1_) * g1_;
            const float y1 = v1 + s1_;
            s1_ = std::clamp(y1 + v1, -20.0f, 20.0f);

            const float v2 = (y1 - s2_) * g1_;
            const float y2 = v2 + s2_;
            s2_ = std::clamp(y2 + v2, -20.0f, 20.0f);

            const float v3 = (y2 - s3_) * g1_;
            const float y3 = v3 + s3_;
            s3_ = std::clamp(y3 + v3, -20.0f, 20.0f);

            const float v4 = (y3 - s4_) * g1_;
            const float y4 = v4 + s4_;
            s4_ = std::clamp(y4 + v4, -20.0f, 20.0f);

            // Output stage analog warmth
            return std::tanh(y4);
        } else {
            // 2-Pole ZDF State Variable Filter (SVF) with zero-delay feedback
            const float hp = (drivenIn - (2.0f * R_ + g_) * s1_ - s2_) / h_;
            const float bp = g_ * hp + s1_;
            s1_ = std::clamp(g_ * hp + bp, -20.0f, 20.0f);

            const float lp = g_ * bp + s2_;
            s2_ = std::clamp(g_ * bp + lp, -20.0f, 20.0f);

            switch (mode_) {
                case ZdfFilterMode::Lowpass12:  return std::tanh(lp);
                case ZdfFilterMode::Bandpass12: return std::tanh(bp);
                case ZdfFilterMode::Highpass12: return std::tanh(hp);
                case ZdfFilterMode::Notch12:    return std::tanh(hp + lp);
                default: return std::tanh(lp);
            }
        }
    }

    void reset() noexcept {
        s1_ = s2_ = s3_ = s4_ = 0.0f;
    }

private:
    void updateCoefficients() noexcept {
        if (sampleRate_ <= 0.0f) return;
        const float w = 3.14159265358979323846f * cutoffHz_ / sampleRate_;
        g_ = std::tan(w);
        R_ = 1.0f / (2.0f * std::clamp(resonance_, 0.2f, 20.0f));
        h_ = 1.0f + 2.0f * R_ * g_ + g_ * g_;

        // Moog Ladder precomputations
        g1_ = g_ / (1.0f + g_);
        G4_ = g1_ * g1_ * g1_ * g1_;
    }

    float sampleRate_ = 48000.0f;
    ZdfFilterMode mode_ = ZdfFilterMode::Ladder24;
    float cutoffHz_ = 2500.0f;
    float resonance_ = 1.0f;
    float drive_ = 1.0f;

    float g_ = 0.1f;
    float g1_ = 0.09f;
    float R_ = 0.5f;
    float h_ = 1.0f;
    float G4_ = 0.0f;

    float s1_ = 0.0f, s2_ = 0.0f, s3_ = 0.0f, s4_ = 0.0f;
};
