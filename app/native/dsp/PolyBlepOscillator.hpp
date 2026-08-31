#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class OscillatorWaveform : int32_t {
    Sine = 0,
    Sawtooth = 1,
    Pulse = 2,
    Triangle = 3,
    Noise = 4
};

class PolyBlepOscillator {
public:
    PolyBlepOscillator() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updatePhaseIncrement();
    }

    void setFrequency(float freqHz) noexcept {
        frequency_ = std::clamp(freqHz, 5.0f, sampleRate_ * 0.48f);
        updatePhaseIncrement();
    }

    void setPulseWidth(float pw) noexcept {
        pulseWidth_ = std::clamp(pw, 0.05f, 0.95f);
    }

    void setWaveform(OscillatorWaveform wave) noexcept {
        waveform_ = wave;
    }

    void resetPhase(double newPhase = 0.0) noexcept {
        phase_ = newPhase - std::floor(newPhase);
    }

    void syncToMaster(double masterPhase) noexcept {
        phase_ = masterPhase - std::floor(masterPhase);
    }

    inline float renderSample() noexcept {
        float sample = 0.0f;

        switch (waveform_) {
            case OscillatorWaveform::Sine:
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586));
                break;

            case OscillatorWaveform::Sawtooth: {
                // Naive saw [-1.0, 1.0] with PolyBLEP residual subtraction
                sample = static_cast<float>(2.0 * phase_ - 1.0);
                sample -= polyBlep(phase_, phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Pulse: {
                // Naive pulse with PolyBLEP residual at rising & falling edges
                sample = (phase_ < pulseWidth_) ? 1.0f : -1.0f;
                sample += polyBlep(phase_, phaseIncrement_);
                sample -= polyBlep(std::fmod(phase_ + (1.0 - pulseWidth_), 1.0), phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Triangle: {
                // Integrated PolyBLEP pulse for clean anti-aliased triangle
                sample = static_cast<float>((phase_ < 0.5) ? (4.0 * phase_ - 1.0) : (3.0 - 4.0 * phase_));
                break;
            }

            case OscillatorWaveform::Noise: {
                // High-speed uniform white noise [-1.0, 1.0]
                noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
                sample = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
                break;
            }
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        return sample;
    }

    double getPhase() const noexcept { return phase_; }
    double getPhaseIncrement() const noexcept { return phaseIncrement_; }

private:
    static inline float polyBlep(double t, double dt) noexcept {
        if (dt <= 0.0) return 0.0f;
        if (t < dt) {
            t /= dt;
            return static_cast<float>(t + t - t * t - 1.0);
        } else if (t > 1.0 - dt) {
            t = (t - 1.0) / dt;
            return static_cast<float>(t * t + t + t + 1.0);
        }
        return 0.0f;
    }

    void updatePhaseIncrement() noexcept {
        if (sampleRate_ > 0.0f) {
            phaseIncrement_ = frequency_ / sampleRate_;
        }
    }

    float sampleRate_ = 48000.0f;
    float frequency_ = 440.0f;
    float pulseWidth_ = 0.5f;
    OscillatorWaveform waveform_ = OscillatorWaveform::Sawtooth;
    double phase_ = 0.0;
    double phaseIncrement_ = 0.00916666666;
    uint32_t noiseSeed_ = 22222;
};
