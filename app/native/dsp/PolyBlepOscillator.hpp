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
    ScreamerSaw = 11,
    WavetableAcid = 12,
    ResoSweep = 13,
    OrganFM = 14,
    ChimeCluster = 15
};

enum class NoiseType : int32_t {
    White = 0,
    Pink = 1,          // 3-pole 1/f Voss-McCartney approximation
    Brown = 2,         // Integrated 6dB/oct low-frequency rumble
    VinylCrackle = 3,  // Sparse Poisson impulse crackles
    MetallicBurst = 4, // Resonant bandpass metallic noise
    VelvetAir = 5      // Ultra-smooth top-end shimmer
};

class PolyBlepOscillator {
public:
    PolyBlepOscillator() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updatePhaseIncrement();
    }

    void setFrequency(float freqHz) noexcept {
        frequency_ = std::clamp(freqHz, 2.0f, sampleRate_ * 0.48f);
        updatePhaseIncrement();
    }

    void setPulseWidth(float pw) noexcept {
        pulseWidth_ = std::clamp(pw, 0.05f, 0.95f);
    }

    void setWaveform(OscillatorWaveform wave) noexcept {
        waveform_ = wave;
    }

    void setNoiseType(NoiseType nType) noexcept {
        noiseType_ = nType;
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
                sample = renderColoredNoise();
                break;
            }

            case OscillatorWaveform::Hypersaw: {
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
                double mod = std::sin(phase_ * 12.566370614359172) * 0.65;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::VowelTalk: {
                double s1 = std::sin(phase_ * 6.283185307179586);
                double s2 = std::sin(phase_ * 18.84955592153876) * 0.55;
                double s3 = std::sin(phase_ * 31.41592653589793) * 0.35;
                sample = static_cast<float>((s1 + s2 + s3) * 0.55);
                break;
            }

            case OscillatorWaveform::MetallicFM: {
                double mod = std::sin(phase_ * 22.4283185307) * 0.85;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::DirtyReese: {
                double p1 = phase_;
                double p2 = std::fmod(phase_ + 0.08, 1.0);
                float s1 = static_cast<float>(2.0 * p1 - 1.0) - polyBlep(p1, phaseIncrement_);
                float s2 = static_cast<float>(2.0 * p2 - 1.0) - polyBlep(p2, phaseIncrement_);
                sample = (s1 + s2) * 0.5f;
                break;
            }

            case OscillatorWaveform::DigitalSync: {
                double slaveP = std::fmod(phase_ * 2.35, 1.0);
                sample = static_cast<float>(2.0 * slaveP - 1.0) - polyBlep(slaveP, phaseIncrement_ * 2.35);
                break;
            }

            case OscillatorWaveform::ScreamerSaw: {
                float rawSaw = static_cast<float>(2.0 * phase_ - 1.0) - polyBlep(phase_, phaseIncrement_);
                sample = std::tanh(rawSaw * 2.5f);
                break;
            }

            case OscillatorWaveform::WavetableAcid: {
                // Diode-saturated squarish wave with 3rd harmonic fold
                double s1 = std::sin(phase_ * 6.283185307179586);
                double s3 = std::sin(phase_ * 18.84955592153876) * 0.40;
                sample = std::tanh(static_cast<float>(s1 + s3) * 2.0f);
                break;
            }

            case OscillatorWaveform::ResoSweep: {
                // Dual formant swept band
                double p1 = phase_;
                double p2 = std::fmod(phase_ * 3.14159, 1.0);
                float s1 = static_cast<float>(std::sin(p1 * 6.283185307179586));
                float s2 = static_cast<float>(std::sin(p2 * 6.283185307179586) * 0.5);
                sample = s1 + s2;
                break;
            }

            case OscillatorWaveform::OrganFM: {
                // 3-Op drawbar harmonic cluster (1st, 2nd, 4th harmonics)
                double h1 = std::sin(phase_ * 6.283185307179586);
                double h2 = std::sin(phase_ * 12.566370614359172) * 0.50;
                double h4 = std::sin(phase_ * 25.132741228718345) * 0.25;
                sample = static_cast<float>(h1 + h2 + h4) * 0.57f;
                break;
            }

            case OscillatorWaveform::ChimeCluster: {
                // Inharmonic bells (1.0 : 1.414 : 2.73)
                double b1 = std::sin(phase_ * 6.283185307179586);
                double b2 = std::sin(phase_ * 8.88568) * 0.45;
                double b3 = std::sin(phase_ * 17.1531) * 0.25;
                sample = static_cast<float>(b1 + b2 + b3) * 0.58f;
                break;
            }
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        return sample;
    }

    inline float renderSampleWithWavefold(float foldDrive) noexcept {
        float raw = renderSample();
        if (foldDrive > 0.01f) {
            float x = raw * (1.0f + foldDrive * 3.2f);
            raw = 0.63661977236f * std::asin(std::sin(3.14159265359f * x));
        }
        return raw;
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

    inline float renderColoredNoise() noexcept {
        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float raw = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;

        switch (noiseType_) {
            case NoiseType::White:
                return raw;

            case NoiseType::Pink: {
                // 3-pole 1/f Voss-McCartney filter
                b0_ = 0.99886f * b0_ + raw * 0.0555179f;
                b1_ = 0.99332f * b1_ + raw * 0.0750759f;
                b2_ = 0.96900f * b2_ + raw * 0.1538520f;
                return (b0_ + b1_ + b2_ + raw * 0.5362f) * 0.25f;
            }

            case NoiseType::Brown: {
                // 6dB/oct low frequency integrator
                brownState_ = (brownState_ * 0.95f) + (raw * 0.05f);
                return brownState_ * 4.0f;
            }

            case NoiseType::VinylCrackle: {
                // Sparse Poisson clicks
                if (std::abs(raw) > 0.985f) {
                    return (raw > 0.0f ? 1.0f : -1.0f) * 0.85f;
                }
                return raw * 0.04f;
            }

            case NoiseType::MetallicBurst: {
                // Resonant bandpass noise
                noiseBandState_ = (noiseBandState_ * 0.70f) + (raw * 0.30f);
                return (raw - noiseBandState_) * 1.5f;
            }

            case NoiseType::VelvetAir: {
                // Smooth high-pass air shimmer
                noiseAirState_ = (noiseAirState_ * 0.85f) + (raw * 0.15f);
                return (raw - noiseAirState_) * 0.90f;
            }
        }
        return raw;
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
    NoiseType noiseType_ = NoiseType::White;

    double phase_ = 0.0;
    double phaseIncrement_ = 0.00916666666;
    uint32_t noiseSeed_ = 22222;

    // Colored noise states
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
    float brownState_ = 0.0f;
    float noiseBandState_ = 0.0f;
    float noiseAirState_ = 0.0f;
};
