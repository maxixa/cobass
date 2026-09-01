#pragma once
#include <cmath>
#include <algorithm>
#include <array>
#include "BiquadFilter.hpp"

class FormantFilter {
public:
    FormantFilter() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (int i = 0; i < 3; ++i) {
            bandL_[i].setSampleRate(sampleRate_);
            bandR_[i].setSampleRate(sampleRate_);
            bandL_[i].reset();
            bandR_[i].reset();
        }
        updateFormants();
    }

    void setParameters(float vowelMorph, float resonanceQ, float drive = 1.0f) noexcept {
        vowelMorph_ = std::clamp(vowelMorph, 0.0f, 4.0f); // 0=A, 1=E, 2=I, 3=O, 4=U
        resonanceQ_ = std::clamp(resonanceQ, 1.0f, 15.0f);
        drive_ = std::clamp(drive, 0.5f, 4.0f);
        updateFormants();
    }

    void processStereo(float inL, float inR, float& outL, float& outR) noexcept {
        const float drivenL = std::tanh(inL * drive_);
        const float drivenR = std::tanh(inR * drive_);

        float sumL = 0.0f;
        float sumR = 0.0f;

        for (int i = 0; i < 3; ++i) {
            sumL += bandL_[i].process(drivenL) * gains_[i];
            sumR += bandR_[i].process(drivenR) * gains_[i];
        }

        outL = std::tanh(sumL * 1.5f);
        outR = std::tanh(sumR * 1.5f);
    }

    inline float processMono(float in) noexcept {
        float outL = 0.0f, outR = 0.0f;
        processStereo(in, in, outL, outR);
        return (outL + outR) * 0.5f;
    }

private:
    void updateFormants() noexcept {
        // Formant Tables (F1, F2, F3 in Hz for A, E, I, O, U)
        static constexpr float FORMANT_F1[5] = {800.0f, 500.0f, 300.0f, 500.0f, 350.0f};
        static constexpr float FORMANT_F2[5] = {1200.0f, 1800.0f, 2300.0f, 900.0f, 700.0f};
        static constexpr float FORMANT_F3[5] = {2500.0f, 2600.0f, 3000.0f, 2400.0f, 2300.0f};

        int idx0 = static_cast<int>(vowelMorph_);
        int idx1 = std::min(4, idx0 + 1);
        float frac = vowelMorph_ - static_cast<float>(idx0);

        float f1 = FORMANT_F1[idx0] * (1.0f - frac) + FORMANT_F1[idx1] * frac;
        float f2 = FORMANT_F2[idx0] * (1.0f - frac) + FORMANT_F2[idx1] * frac;
        float f3 = FORMANT_F3[idx0] * (1.0f - frac) + FORMANT_F3[idx1] * frac;

        float freqs[3] = {
            std::clamp(f1, 50.0f, sampleRate_ * 0.45f),
            std::clamp(f2, 100.0f, sampleRate_ * 0.45f),
            std::clamp(f3, 200.0f, sampleRate_ * 0.45f)
        };

        gains_[0] = 1.0f;
        gains_[1] = 0.65f;
        gains_[2] = 0.35f;

        for (int i = 0; i < 3; ++i) {
            bandL_[i].setParameters(FilterType::BandPass, freqs[i], 0.0f, resonanceQ_);
            bandR_[i].setParameters(FilterType::BandPass, freqs[i], 0.0f, resonanceQ_);
        }
    }

    float sampleRate_ = 48000.0f;
    float vowelMorph_ = 0.0f;
    float resonanceQ_ = 4.0f;
    float drive_ = 1.0f;

    std::array<float, 3> gains_{1.0f, 0.65f, 0.35f};
    std::array<BiquadFilter, 3> bandL_;
    std::array<BiquadFilter, 3> bandR_;
};
