#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class LfoWaveform : int32_t {
    Sine = 0,
    Triangle = 1,
    Sawtooth = 2,
    Square = 3,
    SampleAndHold = 4
};

class LFO {
public:
    LFO() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateIncrement();
    }

    void setFrequency(float freqHz) noexcept {
        freqHz_ = std::clamp(freqHz, 0.02f, 40.0f);
        updateIncrement();
    }

    void setTempoSyncedFrequency(float bpm, int32_t divisionFactor) noexcept {
        const float beatFreq = (bpm / 60.0f);
        freqHz_ = std::clamp(beatFreq * (4.0f / static_cast<float>(std::max(1, divisionFactor))), 0.02f, 40.0f);
        updateIncrement();
    }

    void setWaveform(LfoWaveform wave) noexcept { waveform_ = wave; }
    LfoWaveform getWaveform() const noexcept { return waveform_; }

    void reset() noexcept {
        phase_ = 0.0;
        currentValue_ = 0.0f;
        shHoldValue_ = 0.0f;
    }

    inline float getNextSample() noexcept {
        switch (waveform_) {
            case LfoWaveform::Sine:
                currentValue_ = static_cast<float>(std::sin(phase_ * 6.283185307179586));
                break;
            case LfoWaveform::Triangle:
                currentValue_ = static_cast<float>((phase_ < 0.5) ? (4.0 * phase_ - 1.0) : (3.0 - 4.0 * phase_));
                break;
            case LfoWaveform::Sawtooth:
                currentValue_ = static_cast<float>(1.0 - 2.0 * phase_);
                break;
            case LfoWaveform::Square:
                currentValue_ = (phase_ < 0.5) ? 1.0f : -1.0f;
                break;
            case LfoWaveform::SampleAndHold:
                currentValue_ = shHoldValue_;
                break;
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0) {
            phase_ -= 1.0;
            // Generate next pseudo-random S&H value [-1.0, 1.0]
            seed_ = 1664525L * seed_ + 1013904223L;
            shHoldValue_ = static_cast<float>((seed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        }

        return currentValue_;
    }

    float getCurrentValue() const noexcept { return currentValue_; }

private:
    void updateIncrement() noexcept {
        if (sampleRate_ > 0.0f) {
            phaseIncrement_ = freqHz_ / sampleRate_;
        }
    }

    float sampleRate_ = 48000.0f;
    float freqHz_ = 1.0f;
    LfoWaveform waveform_ = LfoWaveform::Triangle;
    double phase_ = 0.0;
    double phaseIncrement_ = 0.000020833;

    float currentValue_ = 0.0f;
    float shHoldValue_ = 0.0f;
    uint32_t seed_ = 54321;
};
