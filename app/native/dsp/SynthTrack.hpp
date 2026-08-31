#pragma once
#include "Track.hpp"
#include "SynthVoice.hpp"
#include "LFO.hpp"
#include "../plugin/PluginInstance.hpp"
#include <array>
#include <vector>
#include <mutex>
#include <algorithm>

enum class VoiceMode : int32_t {
    Polyphonic = 0,
    MonoRetrigger = 1,
    MonoLegato = 2
};

class SynthTrack : public Track {
public:
    static constexpr size_t MAX_VOICES = 16;

    SynthTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::Synth, name) {
        tempBuffer_.assign(8192, 0.0f);
        for (auto& v : voices_) v.setSampleRate(sampleRate_);
        lfo1_.setSampleRate(sampleRate_);
        lfo1_.setFrequency(1.5f);
        lfo1_.setWaveform(LfoWaveform::Triangle);
        heldNotesStack_.reserve(16);
    }

    void setSampleRate(float sampleRate) override {
        Track::setSampleRate(sampleRate);
        for (auto& v : voices_) v.setSampleRate(sampleRate);
        lfo1_.setSampleRate(sampleRate);
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) customInstrument_->reset(sampleRate);
    }

    void setCustomInstrument(std::unique_ptr<PluginInstance> instrument) {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        } else {
            for (auto& v : voices_) v.hardStop();
        }
        if (instrument) instrument->reset(sampleRate_);
        customInstrument_ = std::move(instrument);
    }

    void removeCustomInstrument() {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        }
        customInstrument_.reset();
    }

    PluginInstance* getCustomInstrument() {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        return customInstrument_.get();
    }

    bool hasCustomInstrument() const {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        return customInstrument_ != nullptr;
    }

    void noteOn(int32_t note, float vel) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOn(note, vel);
            return;
        }

        // Monophonic & Legato Note Stack Handling
        if (voiceMode_ != VoiceMode::Polyphonic) {
            heldNotesStack_.erase(std::remove(heldNotesStack_.begin(), heldNotesStack_.end(), note), heldNotesStack_.end());
            heldNotesStack_.push_back(note);
            lastVelocity_ = vel;

            const bool isLegato = (voiceMode_ == VoiceMode::MonoLegato && voices_[0].isActive());
            voices_[0].noteOn(note, vel, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, isLegato);
            return;
        }

        // Polyphonic Intelligent Voice Allocation
        SynthVoice* targetVoice = nullptr;

        // 1. Find an idle voice
        for (auto& v : voices_) {
            if (!v.isActive()) {
                targetVoice = &v;
                break;
            }
        }

        // 2. Lowest-energy voice stealing
        if (!targetVoice) {
            float minEnergy = 999.0f;
            for (auto& v : voices_) {
                float energy = v.getEnvelopeEnergy();
                if (energy < minEnergy) {
                    minEnergy = energy;
                    targetVoice = &v;
                }
            }
        }

        if (!targetVoice) targetVoice = &voices_[0];
        targetVoice->noteOn(note, vel, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, false);
    }

    void noteOff(int32_t note) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOff(note);
            return;
        }

        if (voiceMode_ != VoiceMode::Polyphonic) {
            heldNotesStack_.erase(std::remove(heldNotesStack_.begin(), heldNotesStack_.end(), note), heldNotesStack_.end());

            if (!heldNotesStack_.empty()) {
                int32_t prevNote = heldNotesStack_.back();
                const bool isLegato = (voiceMode_ == VoiceMode::MonoLegato);
                voices_[0].noteOn(prevNote, lastVelocity_, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, isLegato);
            } else {
                voices_[0].noteOff();
            }
            return;
        }

        for (auto& v : voices_) {
            if (v.isActive() && v.getNote() == note) {
                v.noteOff();
            }
        }
    }

    void allNotesOff() override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        heldNotesStack_.clear();
        if (customInstrument_) {
            customInstrument_->allNotesOff();
            return;
        }

        for (auto& v : voices_) {
            v.hardStop();
        }
    }

    void setParam(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: waveform_ = static_cast<Waveform>(static_cast<int32_t>(value) % 4); break;
            case 1: cutoffHz_ = value; break;
            case 2: resonance_ = value; break;
            case 3: filterMode_ = static_cast<ZdfFilterMode>(static_cast<int32_t>(value) % 5); break;
            case 4: filterDrive_ = std::clamp(value, 0.5f, 5.0f); break;
            case 5: filterEnvAmount_ = std::clamp(value, -1.0f, 1.0f); break;
            case 6: lfo1_.setFrequency(std::clamp(value, 0.05f, 30.0f)); break;
            case 7: lfoCutoffDepth_ = std::clamp(value, 0.0f, 1.0f); break;
            case 8: lfoPitchDepth_ = std::clamp(value, 0.0f, 2.0f); break;
            case 9: lfo1_.setWaveform(static_cast<LfoWaveform>(static_cast<int32_t>(value) % 5)); break;
            case 10: voiceMode_ = static_cast<VoiceMode>(static_cast<int32_t>(value) % 3); break;
            case 11: glideTimeSec_ = std::clamp(value * 0.001f, 0.0f, 1.0f); break;
        }
        for (auto& v : voices_) {
            v.setFilterParameters(filterMode_, cutoffHz_, resonance_, filterDrive_, filterEnvAmount_);
        }
    }

    void render(float* outStereoBuffer, int32_t numFrames) override {
        if (isMuted_) {
            peakL_.store(0.0f, std::memory_order_relaxed);
            peakR_.store(0.0f, std::memory_order_relaxed);
            return;
        }

        if (tempBuffer_.size() < static_cast<size_t>(numFrames * 2)) {
            tempBuffer_.resize(numFrames * 2, 0.0f);
        }
        std::fill_n(tempBuffer_.data(), numFrames * 2, 0.0f);

        {
            std::lock_guard<std::mutex> lock(instrumentMutex_);
            if (customInstrument_) {
                customInstrument_->process(nullptr, tempBuffer_.data(), numFrames);
            } else {
                for (int32_t i = 0; i < numFrames; ++i) {
                    const float lfoVal = lfo1_.getNextSample();
                    const float pitchMod = lfoVal * lfoPitchDepth_;
                    const float cutoffMod = std::max(0.05f, 1.0f + (lfoVal * lfoCutoffDepth_ * 0.75f));

                    float monoSum = 0.0f;
                    for (auto& v : voices_) {
                        if (v.isActive()) {
                            monoSum += v.renderSampleModulated(pitchMod, cutoffMod);
                        }
                    }
                    tempBuffer_[i * 2]     = monoSum;
                    tempBuffer_[i * 2 + 1] = monoSum;
                }
            }
        }

        applyFxAndGain(tempBuffer_.data(), numFrames);

        for (int32_t i = 0; i < numFrames * 2; ++i) {
            outStereoBuffer[i] += tempBuffer_[i];
        }
    }

private:
    std::array<SynthVoice, MAX_VOICES> voices_;
    Waveform waveform_ = Waveform::Sawtooth;
    float cutoffHz_ = 3500.0f;
    float resonance_ = 1.2f;
    ZdfFilterMode filterMode_ = ZdfFilterMode::Ladder24;
    float filterDrive_ = 1.0f;
    float filterEnvAmount_ = 0.5f;

    VoiceMode voiceMode_ = VoiceMode::Polyphonic;
    float glideTimeSec_ = 0.0f;
    std::vector<int32_t> heldNotesStack_;
    float lastVelocity_ = 0.8f;

    LFO lfo1_;
    float lfoCutoffDepth_ = 0.0f;
    float lfoPitchDepth_ = 0.0f;

    mutable std::mutex instrumentMutex_;
    std::unique_ptr<PluginInstance> customInstrument_;
    std::vector<float> tempBuffer_;
};
