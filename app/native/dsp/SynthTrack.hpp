#pragma once
#include "Track.hpp"
#include "SynthVoice.hpp"
#include "../plugin/PluginInstance.hpp"
#include <array>
#include <vector>
#include <mutex>

class SynthTrack : public Track {
public:
    static constexpr size_t MAX_VOICES = 16;

    SynthTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::Synth, name) {
        tempBuffer_.assign(8192, 0.0f);
        for (auto& v : voices_) v.setSampleRate(sampleRate_);
    }

    void setSampleRate(float sampleRate) override {
        Track::setSampleRate(sampleRate);
        for (auto& v : voices_) v.setSampleRate(sampleRate);
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) customInstrument_->reset(sampleRate);
    }

    void setCustomInstrument(std::unique_ptr<PluginInstance> instrument) {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (instrument) instrument->reset(sampleRate_);
        customInstrument_ = std::move(instrument);
    }

    void removeCustomInstrument() {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
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

        SynthVoice* targetVoice = nullptr;
        for (auto& v : voices_) {
            if (!v.isActive()) {
                targetVoice = &v;
                break;
            }
        }
        if (!targetVoice) targetVoice = &voices_[0];
        targetVoice->noteOn(note, vel, waveform_, cutoffHz_, resonance_);
    }

    void noteOff(int32_t note) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOff(note);
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
        }
        for (auto& v : voices_) v.setCutoff(cutoffHz_, resonance_);
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
                    float monoSum = 0.0f;
                    for (auto& v : voices_) {
                        if (v.isActive()) monoSum += v.renderSample();
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

    mutable std::mutex instrumentMutex_;
    std::unique_ptr<PluginInstance> customInstrument_;
    std::vector<float> tempBuffer_;
};
