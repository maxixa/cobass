#pragma once
#include <cmath>
#include "ADSR.hpp"
#include "BiquadFilter.hpp"

enum class Waveform { Sine = 0, Sawtooth = 1, Square = 2, Triangle = 3 };

class SynthVoice {
public:
    SynthVoice() = default;

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        ampEnv_.setSampleRate(sampleRate);
        filter_.setSampleRate(sampleRate);
    }

    void noteOn(int32_t midiNote, float velocity, Waveform wave, float cutoff, float resonance) {
        note_ = midiNote;
        velocity_ = velocity;
        waveform_ = wave;
        frequency_ = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
        phase_ = 0.0;

        filter_.setParameters(FilterType::LowPass, cutoff, 0.0f, resonance);
        ampEnv_.gate(true);
        active_ = true;
    }

    void noteOff() noexcept {
        ampEnv_.gate(false);
    }

    void hardStop() noexcept {
        ampEnv_.reset();
        active_ = false;
        note_ = -1;
    }

    void setCutoff(float cutoff, float resonance) {
        filter_.setParameters(FilterType::LowPass, cutoff, 0.0f, resonance);
    }

    inline float renderSample() noexcept {
        if (!active_) return 0.0f;

        const float env = ampEnv_.getNextSample();
        if (!ampEnv_.isActive()) {
            active_ = false;
            note_ = -1;
            return 0.0f;
        }

        float rawSample = 0.0f;
        switch (waveform_) {
            case Waveform::Sine:
                rawSample = static_cast<float>(std::sin(phase_ * 6.28318530718));
                break;
            case Waveform::Sawtooth:
                rawSample = static_cast<float>(2.0 * (phase_ - std::floor(phase_ + 0.5)));
                break;
            case Waveform::Square:
                rawSample = (phase_ < 0.5) ? 1.0f : -1.0f;
                break;
            case Waveform::Triangle:
                rawSample = static_cast<float>(2.0 * std::fabs(2.0 * (phase_ - std::floor(phase_ + 0.5))) - 1.0);
                break;
        }

        phase_ += frequency_ / sampleRate_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        const float filtered = filter_.process(rawSample);
        return filtered * env * velocity_;
    }

    bool isActive() const noexcept { return active_; }
    int32_t getNote() const noexcept { return note_; }

private:
    float sampleRate_ = 48000.0f;
    int32_t note_ = -1;
    float velocity_ = 0.0f;
    float frequency_ = 440.0f;
    double phase_ = 0.0;
    Waveform waveform_ = Waveform::Sawtooth;

    bool active_ = false;
    ADSR ampEnv_;
    BiquadFilter filter_;
};
