#pragma once
#include <algorithm>

enum class EnvelopeState { Idle, Attack, Decay, Sustain, Release };

class ADSR {
public:
    ADSR() {
        updateRates();
    }

    void setSampleRate(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateRates();
    }

    void setParameters(float attackSec, float decaySec, float sustainLevel, float releaseSec) {
        attackSec_ = std::max(0.001f, attackSec);
        decaySec_ = std::max(0.001f, decaySec);
        sustainLevel_ = std::clamp(sustainLevel, 0.0f, 1.0f);
        releaseSec_ = std::max(0.002f, releaseSec);
        updateRates();
    }

    void gate(bool on) noexcept {
        if (on) {
            state_ = EnvelopeState::Attack;
        } else {
            if (state_ != EnvelopeState::Idle) {
                state_ = EnvelopeState::Release;
            }
        }
    }

    inline float getNextSample() noexcept {
        switch (state_) {
            case EnvelopeState::Attack:
                currentValue_ += attackRate_;
                if (currentValue_ >= 1.0f) {
                    currentValue_ = 1.0f;
                    state_ = EnvelopeState::Decay;
                }
                break;

            case EnvelopeState::Decay:
                currentValue_ -= decayRate_;
                if (currentValue_ <= sustainLevel_) {
                    currentValue_ = sustainLevel_;
                    state_ = EnvelopeState::Sustain;
                }
                break;

            case EnvelopeState::Sustain:
                currentValue_ = sustainLevel_;
                break;

            case EnvelopeState::Release:
                // Decrement cleanly to 0
                currentValue_ -= releaseRate_;
                if (currentValue_ <= 0.0f) {
                    currentValue_ = 0.0f;
                    state_ = EnvelopeState::Idle;
                }
                break;

            case EnvelopeState::Idle:
                currentValue_ = 0.0f;
                break;
        }
        return currentValue_;
    }

    bool isActive() const noexcept { return state_ != EnvelopeState::Idle; }
    
    void reset() noexcept {
        state_ = EnvelopeState::Idle;
        currentValue_ = 0.0f;
    }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        attackRate_ = 1.0f / (std::max(0.001f, attackSec_) * sampleRate_);
        decayRate_ = std::max(0.0f, (1.0f - sustainLevel_)) / (std::max(0.001f, decaySec_) * sampleRate_);
        // FIXED: Release rate must ramp down linearly to 0 in releaseSec_ seconds
        releaseRate_ = 1.0f / (std::max(0.002f, releaseSec_) * sampleRate_);
    }

    float sampleRate_ = 48000.0f;
    float attackSec_ = 0.01f;
    float decaySec_ = 0.08f;
    float sustainLevel_ = 0.6f;
    float releaseSec_ = 0.15f;

    float attackRate_ = 0.002f;
    float decayRate_ = 0.001f;
    float releaseRate_ = 0.001f;

    float currentValue_ = 0.0f;
    EnvelopeState state_ = EnvelopeState::Idle;
};
