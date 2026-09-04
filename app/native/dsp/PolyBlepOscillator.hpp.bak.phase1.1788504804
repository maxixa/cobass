#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class OscillatorWaveform : int32_t {
    Sine = 0,
    Sawtooth = 1,
    Pulse = 2,
    Triangle = 3,
    Noise = 4,
    Hypersaw = 5,
    FutureDonk = 6,
    VowelTalk = 7,
    MetallicFM = 8,
    DirtyReese = 9,
    DigitalSync = 10,
    ScreamerSaw = 11
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
                sample = static_cast<float>(2.0 * phase_ - 1.0);
                sample -= polyBlep(phase_, phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Pulse: {
                sample = (phase_ < pulseWidth_) ? 1.0f : -1.0f;
                sample += polyBlep(phase_, phaseIncrement_);
                sample -= polyBlep(std::fmod(phase_ + (1.0 - pulseWidth_), 1.0), phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Triangle: {
                sample = static_cast<float>((phase_ < 0.5) ? (4.0 * phase_ - 1.0) : (3.0 - 4.0 * phase_));
                break;
            }

            case OscillatorWaveform::Noise: {
                noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
                sample = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
                break;
            }

            case OscillatorWaveform::Hypersaw: {
                // Multi-cluster 3-saw inside single osc
                double p1 = phase_;
                double p2 = std::fmod(phase_ * 1.008 + 0.25, 1.0);
                double p3 = std::fmod(phase_ * 0.992 + 0.75, 1.0);
                float s1 = static_cast<float>(2.0 * p1 - 1.0) - polyBlep(p1, phaseIncrement_);
                float s2 = static_cast<float>(2.0 * p2 - 1.0) - polyBlep(p2, phaseIncrement_);
                float s3 = static_cast<float>(2.0 * p3 - 1.0) - polyBlep(p3, phaseIncrement_);
                sample = (s1 + s2 + s3) * 0.577f;
                break;
            }

            case OscillatorWaveform::FutureDonk: {
                // 2-Op FM harmonic donk wave for Slap/Future House
                double mod = std::sin(phase_ * 12.566370614359172) * 0.55;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::VowelTalk: {
                // Vowel formant acoustic table approximation
                double s1 = std::sin(phase_ * 6.283185307179586);
                double s2 = std::sin(phase_ * 18.84955592153876) * 0.55;
                double s3 = std::sin(phase_ * 31.41592653589793) * 0.35;
                sample = static_cast<float>((s1 + s2 + s3) * 0.55);
                break;
            }

            case OscillatorWaveform::MetallicFM: {
                // Inharmonic FM bell for trance/psy plucks
                double mod = std::sin(phase_ * 22.4283185307) * 0.85;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::DirtyReese: {
                // Phase-cancelling dual saw table
                double p1 = phase_;
                double p2 = std::fmod(phase_ + 0.08, 1.0);
                float s1 = static_cast<float>(2.0 * p1 - 1.0) - polyBlep(p1, phaseIncrement_);
                float s2 = static_cast<float>(2.0 * p2 - 1.0) - polyBlep(p2, phaseIncrement_);
                sample = (s1 + s2) * 0.5f;
                break;
            }

            case OscillatorWaveform::DigitalSync: {
                // Hard sync slave saw
                double slaveP = std::fmod(phase_ * 2.35, 1.0);
                sample = static_cast<float>(2.0 * slaveP - 1.0) - polyBlep(slaveP, phaseIncrement_ * 2.35);
                break;
            }

            case OscillatorWaveform::ScreamerSaw: {
                // Asymmetric non-linear saturated saw
                float rawSaw = static_cast<float>(2.0 * phase_ - 1.0) - polyBlep(phase_, phaseIncrement_);
                sample = std::tanh(rawSaw * 2.5f);
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
