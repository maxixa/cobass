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
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, sampleRate_ * 0.48f);
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        drive_ = std::clamp(drive, 0.5f, 5.0f);
        updateCoefficients();
    }

    void setCutoff(float cutoffHz) noexcept {
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, sampleRate_ * 0.48f);
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
        // Non-linear input saturation
        const float drivenIn = std::tanh(in * drive_);

        if (mode_ == ZdfFilterMode::Ladder24) {
            // 4-Pole ZDF Moog Ladder resolved via trapezoidal integrators
            const float k = resonance_ * 0.95f;
            const float u = (drivenIn - k * s4_) / (1.0f + k * G4_);

            const float v1 = (u - s1_) * g_ / (1.0f + g_);
            const float y1 = v1 + s1_;
            s1_ = y1 + v1;

            const float v2 = (y1 - s2_) * g_ / (1.0f + g_);
            const float y2 = v2 + s2_;
            s2_ = y2 + v2;

            const float v3 = (y2 - s3_) * g_ / (1.0f + g_);
            const float y3 = v3 + s3_;
            s3_ = y3 + v3;

            const float v4 = (y3 - s4_) * g_ / (1.0f + g_);
            const float y4 = v4 + s4_;
            s4_ = y4 + v4;

            // Output non-linear soft limiter
            return std::tanh(y4);
        } else {
            // 2-Pole ZDF State Variable Filter (SVF) with zero-delay feedback
            const float hp = (drivenIn - (2.0f * R_ + g_) * s1_ - s2_) / h_;
            const float bp = g_ * hp + s1_;
            s1_ = g_ * hp + bp;

            const float lp = g_ * bp + s2_;
            s2_ = g_ * bp + lp;

            switch (mode_) {
                case ZdfFilterMode::Lowpass12:  return lp;
                case ZdfFilterMode::Bandpass12: return bp;
                case ZdfFilterMode::Highpass12: return hp;
                case ZdfFilterMode::Notch12:    return hp + lp;
                default: return lp;
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
        R_ = 1.0f / (2.0f * std::max(0.5f, resonance_));
        h_ = 1.0f + 2.0f * R_ * g_ + g_ * g_;

        // Moog Ladder precomputations
        const float g1 = g_ / (1.0f + g_);
        G4_ = g1 * g1 * g1 * g1;
    }

    float sampleRate_ = 48000.0f;
    ZdfFilterMode mode_ = ZdfFilterMode::Ladder24;
    float cutoffHz_ = 2500.0f;
    float resonance_ = 1.0f;
    float drive_ = 1.0f;

    float g_ = 0.1f;
    float R_ = 0.5f;
    float h_ = 1.0f;
    float G4_ = 0.0f;

    float s1_ = 0.0f, s2_ = 0.0f, s3_ = 0.0f, s4_ = 0.0f;
};
