#pragma once
#include <cmath>
#include <algorithm>
#include "ADSR.hpp"
#include "ZdfFilter.hpp"
#include "PolyBlepOscillator.hpp"

enum class Waveform { Sine = 0, Sawtooth = 1, Square = 2, Triangle = 3 };

class SynthVoice {
public:
    SynthVoice() = default;

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        ampEnv_.setSampleRate(sampleRate);
        modEnv_.setSampleRate(sampleRate);
        filter_.setSampleRate(sampleRate);
        oscMain_.setSampleRate(sampleRate);
        oscSub_.setSampleRate(sampleRate);
    }

    void noteOn(int32_t midiNote, float velocity, Waveform wave, float cutoff, float resonance,
                ZdfFilterMode filterMode = ZdfFilterMode::Ladder24, float drive = 1.0f,
                float filterEnvAmount = 0.5f, float glideTimeSec = 0.0f, bool legato = false) {
        const float newTargetFreq = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
        targetFrequency_ = newTargetFreq;

        if (legato && active_ && currentFrequency_ > 10.0f && glideTimeSec > 0.001f) {
            // Legato Glide: Smoothly slew pitch without resetting phase or re-triggering envelopes
            glideCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideTimeSec * 0.35f) * sampleRate_));
            note_ = midiNote;
            velocity_ = velocity;
            baseCutoff_ = cutoff;
            baseResonance_ = resonance;
            filterEnvAmount_ = filterEnvAmount;
            return;
        }

        if (glideTimeSec > 0.001f && currentFrequency_ > 10.0f) {
            // Portamento with envelope retrigger
            glideCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideTimeSec * 0.35f) * sampleRate_));
        } else {
            currentFrequency_ = targetFrequency_;
            glideCoeff_ = 1.0f;
        }

        note_ = midiNote;
        velocity_ = velocity;
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filterEnvAmount_ = filterEnvAmount;

        OscillatorWaveform oscWave = OscillatorWaveform::Sawtooth;
        switch (wave) {
            case Waveform::Sine:     oscWave = OscillatorWaveform::Sine; break;
            case Waveform::Sawtooth: oscWave = OscillatorWaveform::Sawtooth; break;
            case Waveform::Square:   oscWave = OscillatorWaveform::Pulse; break;
            case Waveform::Triangle: oscWave = OscillatorWaveform::Triangle; break;
        }

        oscMain_.setWaveform(oscWave);
        oscMain_.setFrequency(currentFrequency_);
        oscMain_.setPulseWidth(0.5f);
        oscMain_.resetPhase(0.0);

        oscSub_.setWaveform(OscillatorWaveform::Pulse);
        oscSub_.setFrequency(currentFrequency_ * 0.5f);
        oscSub_.setPulseWidth(0.5f);
        oscSub_.resetPhase(0.0);

        filter_.setParameters(filterMode, cutoff, resonance, drive);
        ampEnv_.gate(true);
        modEnv_.gate(true);
        active_ = true;
    }

    void noteOff() noexcept {
        ampEnv_.gate(false);
        modEnv_.gate(false);
    }

    void hardStop() noexcept {
        ampEnv_.reset();
        modEnv_.reset();
        filter_.reset();
        active_ = false;
        note_ = -1;
    }

    void setCutoff(float cutoff, float resonance) {
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filter_.setCutoff(cutoff);
        filter_.setResonance(resonance);
    }

    void setFilterParameters(ZdfFilterMode mode, float cutoff, float resonance, float drive, float filterEnvAmount = 0.5f) {
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filterEnvAmount_ = filterEnvAmount;
        filter_.setParameters(mode, cutoff, resonance, drive);
    }

    void setModEnvParameters(float attackSec, float decaySec, float sustainLevel, float releaseSec) {
        modEnv_.setParameters(attackSec, decaySec, sustainLevel, releaseSec);
    }

    float getEnvelopeEnergy() const noexcept {
        return active_ ? ampEnv_.getCurrentValue() : 0.0f;
    }

    inline float renderSampleModulated(float pitchModSemitones, float cutoffModMultiplier) noexcept {
        if (!active_) return 0.0f;

        const float amp = ampEnv_.getNextSample();
        const float modEnvVal = modEnv_.getNextSample();

        if (!ampEnv_.isActive()) {
            active_ = false;
            note_ = -1;
            return 0.0f;
        }

        // 1. Portamento Slewing (f_current -> f_target)
        if (currentFrequency_ != targetFrequency_) {
            currentFrequency_ += (targetFrequency_ - currentFrequency_) * glideCoeff_;
            if (std::abs(targetFrequency_ - currentFrequency_) < 0.05f) {
                currentFrequency_ = targetFrequency_;
            }
        }

        // 2. Pitch Modulation (Vibrato / Pitch Bend)
        float activeFreq = currentFrequency_;
        if (std::abs(pitchModSemitones) > 0.001f) {
            activeFreq *= std::pow(2.0f, pitchModSemitones / 12.0f);
        }

        oscMain_.setFrequency(activeFreq);
        oscSub_.setFrequency(activeFreq * 0.5f);

        // 3. Dynamic Filter Cutoff Modulation
        const float envCutoffDelta = baseCutoff_ * filterEnvAmount_ * modEnvVal * 4.0f;
        const float totalCutoff = std::clamp((baseCutoff_ + envCutoffDelta) * cutoffModMultiplier, 20.0f, sampleRate_ * 0.48f);
        filter_.setCutoff(totalCutoff);

        // 4. Oscillators + Filter Output
        const float rawSample = (oscMain_.renderSample() * 0.85f) + (oscSub_.renderSample() * 0.15f);
        const float filtered = filter_.process(rawSample);
        return filtered * amp * velocity_;
    }

    inline float renderSample() noexcept {
        return renderSampleModulated(0.0f, 1.0f);
    }

    bool isActive() const noexcept { return active_; }
    int32_t getNote() const noexcept { return note_; }

private:
    float sampleRate_ = 48000.0f;
    int32_t note_ = -1;
    float velocity_ = 0.0f;

    float currentFrequency_ = 440.0f;
    float targetFrequency_ = 440.0f;
    float glideCoeff_ = 1.0f;

    float baseCutoff_ = 3500.0f;
    float baseResonance_ = 1.2f;
    float filterEnvAmount_ = 0.5f;

    bool active_ = false;
    ADSR ampEnv_;
    ADSR modEnv_;
    ZdfFilter filter_;
    PolyBlepOscillator oscMain_;
    PolyBlepOscillator oscSub_;
};
