#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>
#include <array>
#include <vector>
#include "BiquadFilter.hpp"

enum class ZdfFilterMode : int32_t {
    Ladder24 = 0,       // 4-Pole ZDF Moog Ladder (24dB/oct with transistor saturation)
    Diode18 = 1,        // 3-Pole ZDF TB-303 Diode Ladder (18dB/oct acid squelch)
    Lowpass12 = 2,      // 2-Pole ZDF State Variable Lowpass (12dB/oct)
    Bandpass12 = 3,     // 2-Pole ZDF State Variable Bandpass (12dB/oct)
    Highpass12 = 4,     // 2-Pole ZDF State Variable Highpass (12dB/oct)
    Notch12 = 5,        // 2-Pole ZDF State Variable Band-Reject Notch
    FormantVowel = 6,   // 3-Resonator Vocal Tract Formant (A-E-I-O-U morphing)
    CombResonator = 7   // Tuned Feedback Delay Comb Resonator
};

enum class ZdfDriveModel : int32_t {
    Transistor = 0, // Clean progressive tanh saturation
    Diode = 1,      // Asymmetric Germanium diode clipping with 2nd harmonic bias
    Tube = 2,       // Triode soft-knee warm grid saturation
    Wavefold = 3    // West-Coast trigonometric wavefolding
};

class ZdfFilter {
public:
    ZdfFilter() {
        combBuffer_.assign(MAX_COMB_SAMPLES, 0.0f);
    }

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (int i = 0; i < 3; ++i) {
            formantBands_[i].setSampleRate(sampleRate_);
            formantBands_[i].reset();
        }
        std::fill(combBuffer_.begin(), combBuffer_.end(), 0.0f);
        updateCoefficients();
    }

    void setParameters(ZdfFilterMode mode, float cutoffHz, float resonance, float drive = 1.0f, float vowelMorph = 0.0f, int driveModel = 0) noexcept {
        mode_ = mode;
        const float maxCutoff = sampleRate_ * 0.45f;
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, maxCutoff);
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        drive_ = std::clamp(drive, 0.5f, 5.0f);
        vowelMorph_ = std::clamp(vowelMorph, 0.0f, 4.0f);
        driveModel_ = static_cast<ZdfDriveModel>(std::clamp(driveModel, 0, 3));
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

    void setDriveModel(ZdfDriveModel model) noexcept {
        driveModel_ = model;
    }

    void setVowelMorph(float vowelMorph) noexcept {
        vowelMorph_ = std::clamp(vowelMorph, 0.0f, 4.0f);
        updateFormants();
    }

    void setMode(ZdfFilterMode mode) noexcept {
        mode_ = mode;
        updateCoefficients();
    }

    inline float process(float in) noexcept {
        // Recovery guard for NaN / Inf denormals
        if (std::isnan(s1_) || std::isinf(s1_) || std::isnan(s4_) || std::isinf(s4_)) {
            reset();
        }

        // Apply selected nonlinear drive model to input stage
        float drivenIn = in * drive_;
        switch (driveModel_) {
            case ZdfDriveModel::Transistor:
                drivenIn = std::tanh(drivenIn);
                break;
            case ZdfDriveModel::Diode: {
                // Asymmetric Germanium diode with positive bias
                float x = drivenIn + 0.15f;
                drivenIn = (x > 0.0f) ? std::tanh(x * 1.35f) - 0.15f : (x * 0.85f);
                break;
            }
            case ZdfDriveModel::Tube: {
                // Triode soft-saturation with warm even harmonics
                float x = drivenIn;
                drivenIn = x / (1.0f + std::abs(x) * 0.5f);
                break;
            }
            case ZdfDriveModel::Wavefold: {
                // Saturated West-Coast folding
                float x = drivenIn * 1.5f;
                drivenIn = 0.63661977f * std::asin(std::sin(3.14159265f * x));
                break;
            }
        }

        // 1. Formant Vowel Mode (3-Band Parallel Vocal Formant Resonators)
        if (mode_ == ZdfFilterMode::FormantVowel) {
            float sum = 0.0f;
            for (int i = 0; i < 3; ++i) {
                sum += formantBands_[i].process(drivenIn) * formantGains_[i];
            }
            return std::tanh(sum * 1.6f);
        }

        // 2. Comb Resonator Mode
        if (mode_ == ZdfFilterMode::CombResonator) {
            float delaySamples = std::clamp(sampleRate_ / std::max(20.0f, cutoffHz_), 2.0f, static_cast<float>(MAX_COMB_SAMPLES - 4));
            float readPos = static_cast<float>(combWriteIdx_) - delaySamples;
            while (readPos < 0.0f) readPos += static_cast<float>(MAX_COMB_SAMPLES);

            size_t idx0 = static_cast<size_t>(readPos) % MAX_COMB_SAMPLES;
            size_t idx1 = (idx0 + 1) % MAX_COMB_SAMPLES;
            float frac = readPos - static_cast<float>(static_cast<size_t>(readPos));

            float delayed = combBuffer_[idx0] * (1.0f - frac) + combBuffer_[idx1] * frac;
            float feedback = std::clamp(resonance_ / 16.0f, 0.0f, 0.98f);

            combDampState_ = (delayed * 0.8f) + (combDampState_ * 0.2f);
            float newSample = drivenIn + (combDampState_ * feedback);

            if (std::isnan(newSample) || std::isinf(newSample)) {
                newSample = 0.0f;
                combDampState_ = 0.0f;
            }

            combBuffer_[combWriteIdx_] = newSample;
            combWriteIdx_ = (combWriteIdx_ + 1) % MAX_COMB_SAMPLES;

            return std::tanh(delayed);
        }

        // 3. TB-303 18dB Diode Ladder Acid Filter with Passband Resonance Compensation
        if (mode_ == ZdfFilterMode::Diode18) {
            const float k = std::clamp((resonance_ / (resonance_ + 1.1f)) * 4.4f, 0.0f, 4.1f);
            const float satFeedback = std::tanh(s3_);
            const float u = (drivenIn - k * satFeedback) / (1.0f + k * G3_);

            const float v1 = (u - s1_) * g1_;
            const float y1 = v1 + s1_;
            s1_ = std::clamp(y1 + v1, -20.0f, 20.0f);

            const float v2 = (y1 - s2_) * g1_;
            const float y2 = v2 + s2_;
            s2_ = std::clamp(y2 + v2, -20.0f, 20.0f);

            const float v3 = (y2 - s3_) * g1_;
            const float y3 = v3 + s3_;
            s3_ = std::clamp(y3 + v3, -20.0f, 20.0f);

            // Passband makeup compensation
            const float comp = 1.0f + (resonance_ * 0.22f);
            return std::tanh(y3 * 1.30f * comp);
        }

        // 4. Moog 24dB Transistor Ladder Filter with Passband Resonance Compensation
        if (mode_ == ZdfFilterMode::Ladder24) {
            const float k = std::clamp((resonance_ / (resonance_ + 1.2f)) * 4.25f, 0.0f, 3.96f);
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

            // Passband makeup compensation prevents thinning at high resonance
            const float comp = 1.0f + (resonance_ * 0.28f);
            return std::tanh(y4 * comp);
        }

        // 5. 2-Pole ZDF State Variable Filter (SVF)
        const float hp = (drivenIn - (2.0f * R_ + g_) * s1_ - s2_) / h_;
        const float bp = g_ * hp + s1_;
        s1_ = std::clamp(g_ * hp + bp, -20.0f, 20.0f);

        const float lp = g_ * bp + s2_;
        s2_ = std::clamp(g_ * bp + lp, -20.0f, 20.0f);

        switch (mode_) {
            case ZdfFilterMode::Lowpass12:  return std::tanh(lp * (1.0f + resonance_ * 0.12f));
            case ZdfFilterMode::Bandpass12: return std::tanh(bp * 1.2f);
            case ZdfFilterMode::Highpass12: return std::tanh(hp * (1.0f + resonance_ * 0.12f));
            case ZdfFilterMode::Notch12:    return std::tanh((hp + lp) * 1.05f);
            default: return std::tanh(lp);
        }
    }

    void reset() noexcept {
        s1_ = s2_ = s3_ = s4_ = 0.0f;
        combWriteIdx_ = 0;
        combDampState_ = 0.0f;
        std::fill(combBuffer_.begin(), combBuffer_.end(), 0.0f);
        for (int i = 0; i < 3; ++i) formantBands_[i].reset();
    }

private:
    void updateCoefficients() noexcept {
        if (sampleRate_ <= 0.0f) return;
        const float w = 3.14159265358979323846f * cutoffHz_ / sampleRate_;
        g_ = std::tan(w);
        R_ = 1.0f / (2.0f * std::clamp(resonance_, 0.2f, 20.0f));
        h_ = 1.0f + 2.0f * R_ * g_ + g_ * g_;

        g1_ = g_ / (1.0f + g_);
        G3_ = g1_ * g1_ * g1_;
        G4_ = G3_ * g1_;

        if (mode_ == ZdfFilterMode::FormantVowel) {
            updateFormants();
        }
    }

    void updateFormants() noexcept {
        static constexpr float FORMANT_F1[5] = {800.0f, 500.0f, 300.0f, 500.0f, 350.0f};
        static constexpr float FORMANT_F2[5] = {1200.0f, 1800.0f, 2300.0f, 900.0f, 700.0f};
        static constexpr float FORMANT_F3[5] = {2500.0f, 2600.0f, 3000.0f, 2400.0f, 2300.0f};

        int idx0 = static_cast<int>(vowelMorph_);
        int idx1 = std::min(4, idx0 + 1);
        float frac = vowelMorph_ - static_cast<float>(idx0);

        float f1 = FORMANT_F1[idx0] * (1.0f - frac) + FORMANT_F1[idx1] * frac;
        float f2 = FORMANT_F2[idx0] * (1.0f - frac) + FORMANT_F2[idx1] * frac;
        float f3 = FORMANT_F3[idx0] * (1.0f - frac) + FORMANT_F3[idx1] * frac;

        float q = std::max(1.5f, resonance_);
        formantBands_[0].setParameters(FilterType::BandPass, std::clamp(f1, 50.0f, sampleRate_ * 0.45f), 0.0f, q);
        formantBands_[1].setParameters(FilterType::BandPass, std::clamp(f2, 100.0f, sampleRate_ * 0.45f), 0.0f, q);
        formantBands_[2].setParameters(FilterType::BandPass, std::clamp(f3, 200.0f, sampleRate_ * 0.45f), 0.0f, q);

        formantGains_[0] = 1.0f;
        formantGains_[1] = 0.70f;
        formantGains_[2] = 0.40f;
    }

    static constexpr size_t MAX_COMB_SAMPLES = 4096;

    float sampleRate_ = 48000.0f;
    ZdfFilterMode mode_ = ZdfFilterMode::Ladder24;
    ZdfDriveModel driveModel_ = ZdfDriveModel::Transistor;
    float cutoffHz_ = 2500.0f;
    float resonance_ = 1.0f;
    float drive_ = 1.0f;
    float vowelMorph_ = 0.0f;

    float g_ = 0.1f;
    float g1_ = 0.09f;
    float R_ = 0.5f;
    float h_ = 1.0f;
    float G3_ = 0.0f;
    float G4_ = 0.0f;

    float s1_ = 0.0f, s2_ = 0.0f, s3_ = 0.0f, s4_ = 0.0f;

    std::array<BiquadFilter, 3> formantBands_;
    std::array<float, 3> formantGains_{1.0f, 0.70f, 0.40f};

    std::vector<float> combBuffer_;
    size_t combWriteIdx_ = 0;
    float combDampState_ = 0.0f;
};
