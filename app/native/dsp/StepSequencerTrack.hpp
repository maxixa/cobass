#pragma once
#include "Track.hpp"
#include "../plugin/PluginInstance.hpp"
#include <array>
#include <vector>
#include <random>
#include <cmath>
#include <mutex>
#include <algorithm>

struct NativeStep {
    bool active = false;
    float velocity = 0.85f;
    int32_t pitchOffset = 0;
    float gate = 0.75f;
    float nudge = 0.0f;
    int32_t ratchets = 1;
    float probability = 1.0f;
};

struct NativeLane {
    int32_t id = 0;
    int32_t midiNote = 60;
    int32_t stepCount = 16;
    int32_t stepTicks = 120; // 1/16th note at PPQ=480
    float volume = 0.8f;
    float pan = 0.0f;
    bool isMuted = false;
    bool isSolo = false;

    // Sampler Pad Buffer
    std::vector<float> sampleData;
    int32_t channels = 1;
    double playbackPos = 0.0;
    bool isPlayingSample = false;
    float currentPitch = 1.0f;
    float currentVelocity = 1.0f;

    std::array<NativeStep, 64> steps{};
};

class StepSequencerTrack : public Track {
public:
    static constexpr size_t MAX_LANES = 16;

    StepSequencerTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::StepSequencer, name), rng_(std::random_device{}()) {
        lanes_.resize(MAX_LANES);
        for (size_t i = 0; i < MAX_LANES; ++i) {
            lanes_[i].id = static_cast<int32_t>(i);
        }
        tempBuffer_.assign(8192, 0.0f);
        synthBuffer_.assign(8192, 0.0f);
    }

    void setSampleRate(float sampleRate) override {
        Track::setSampleRate(sampleRate);
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->reset(sampleRate);
        }
    }

    void setCustomInstrument(std::unique_ptr<PluginInstance> instrument) {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        }
        if (instrument) {
            instrument->reset(sampleRate_);
        }
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
        }

        // Trigger sampler fallback if sample buffer loaded
        for (auto& lane : lanes_) {
            if (lane.midiNote == note && !lane.sampleData.empty()) {
                lane.playbackPos = 0.0;
                lane.currentPitch = 1.0f;
                lane.currentVelocity = vel;
                lane.isPlayingSample = true;
            }
        }
    }

    void noteOff(int32_t note) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOff(note);
        }
    }

    void allNotesOff() override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        }
        for (auto& lane : lanes_) {
            lane.isPlayingSample = false;
        }
    }

    void loadLaneSample(size_t laneIndex, const float* data, int32_t length, int32_t channels) {
        if (laneIndex >= MAX_LANES || !data || length <= 0) return;
        lanes_[laneIndex].sampleData.assign(data, data + length);
        lanes_[laneIndex].channels = std::clamp(channels, 1, 2);
        lanes_[laneIndex].playbackPos = 0.0;
        lanes_[laneIndex].isPlayingSample = false;
    }

    void setLaneStep(size_t laneIndex, size_t stepIndex, bool active, float velocity,
                     int32_t pitch, float gate, float nudge, int32_t ratchets, float prob) {
        if (laneIndex >= MAX_LANES || stepIndex >= 64) return;
        auto& s = lanes_[laneIndex].steps[stepIndex];
        s.active = active;
        s.velocity = std::clamp(velocity, 0.0f, 1.0f);
        s.pitchOffset = std::clamp(pitch, -24, 24);
        s.gate = std::clamp(gate, 0.05f, 2.0f);
        s.nudge = std::clamp(nudge, -0.5f, 0.5f);
        s.ratchets = std::clamp(ratchets, 1, 8);
        s.probability = std::clamp(prob, 0.0f, 1.0f);
    }

    void setLaneParams(size_t laneIndex, int32_t midiNote, int32_t stepCount, int32_t stepTicks,
                       float volume, float pan, bool mute, bool solo) {
        if (laneIndex >= MAX_LANES) return;
        auto& l = lanes_[laneIndex];
        l.midiNote = midiNote;
        l.stepCount = std::clamp(stepCount, 1, 64);
        l.stepTicks = std::max(10, stepTicks);
        l.volume = std::clamp(volume, 0.0f, 2.0f);
        l.pan = std::clamp(pan, -1.0f, 1.0f);
        l.isMuted = mute;
        l.isSolo = solo;
    }

    void clearLaneSteps(size_t laneIndex) {
        if (laneIndex >= MAX_LANES) return;
        for (auto& s : lanes_[laneIndex].steps) {
            s.active = false;
        }
    }

    void advancePlayback(int64_t startTick, int64_t endTick, bool loopWrapped) {
        std::uniform_real_distribution<float> dist(0.0f, 1.0f);
        std::lock_guard<std::mutex> lock(instrumentMutex_);

        for (auto& lane : lanes_) {
            if (lane.stepCount <= 0 || lane.isMuted) continue;

            const int64_t laneLoopTicks = static_cast<int64_t>(lane.stepCount * lane.stepTicks);
            if (laneLoopTicks <= 0) continue;

            for (int32_t s = 0; s < lane.stepCount; ++s) {
                const auto& step = lane.steps[s];
                if (!step.active) continue;

                const int32_t ratchets = std::max(1, step.ratchets);
                const int64_t subStepDuration = lane.stepTicks / ratchets;

                for (int32_t r = 0; r < ratchets; ++r) {
                    const int64_t stepTickInPattern = (s * lane.stepTicks) + (r * subStepDuration) + static_cast<int64_t>(step.nudge * lane.stepTicks);

                    bool shouldTrigger = false;
                    if (!loopWrapped) {
                        int64_t patternStart = startTick % laneLoopTicks;
                        int64_t patternEnd = endTick % laneLoopTicks;
                        if (patternEnd > patternStart) {
                            shouldTrigger = (patternStart <= stepTickInPattern && patternEnd > stepTickInPattern);
                        } else {
                            shouldTrigger = (patternStart <= stepTickInPattern || patternEnd > stepTickInPattern);
                        }
                    } else {
                        shouldTrigger = true;
                    }

                    if (shouldTrigger) {
                        if (step.probability < 0.999f && dist(rng_) > step.probability) {
                            continue;
                        }

                        // 1. Route to Custom Drum Synth Plugin if attached
                        if (customInstrument_) {
                            const int32_t triggeredPitch = std::clamp(lane.midiNote + step.pitchOffset, 0, 127);
                            customInstrument_->noteOn(triggeredPitch, step.velocity * lane.volume);
                        }

                        // 2. Route to Sampler Buffer if samples loaded
                        if (!lane.sampleData.empty()) {
                            lane.playbackPos = 0.0;
                            lane.currentPitch = std::pow(2.0f, step.pitchOffset / 12.0f);
                            lane.currentVelocity = step.velocity;
                            lane.isPlayingSample = true;
                        }
                    }
                }
            }
        }
    }

    void render(float* outStereoBuffer, int32_t numFrames) override {
        if (isMuted_) {
            peakL_.store(0.0f, std::memory_order_relaxed);
            peakR_.store(0.0f, std::memory_order_relaxed);
            return;
        }

        const size_t reqSize = static_cast<size_t>(numFrames * 2);
        if (tempBuffer_.size() < reqSize) tempBuffer_.resize(reqSize, 0.0f);
        if (synthBuffer_.size() < reqSize) synthBuffer_.resize(reqSize, 0.0f);

        std::fill_n(tempBuffer_.data(), reqSize, 0.0f);
        std::fill_n(synthBuffer_.data(), reqSize, 0.0f);

        // 1. Process Custom Drum Synth Plugin
        {
            std::lock_guard<std::mutex> lock(instrumentMutex_);
            if (customInstrument_) {
                customInstrument_->process(nullptr, synthBuffer_.data(), numFrames);
                for (size_t i = 0; i < reqSize; ++i) {
                    tempBuffer_[i] += synthBuffer_[i];
                }
            }
        }

        // 2. Process Sampler Voices
        for (auto& lane : lanes_) {
            if (!lane.isPlayingSample || lane.sampleData.empty() || lane.isMuted) continue;

            const size_t totalFrames = lane.sampleData.size() / lane.channels;
            const float panL = lane.pan <= 0.0f ? 1.0f : (1.0f - lane.pan);
            const float panR = lane.pan >= 0.0f ? 1.0f : (1.0f + lane.pan);
            const float gainL = lane.volume * lane.currentVelocity * panL;
            const float gainR = lane.volume * lane.currentVelocity * panR;

            for (int32_t i = 0; i < numFrames; ++i) {
                if (lane.playbackPos >= totalFrames) {
                    lane.isPlayingSample = false;
                    break;
                }

                const size_t frameIdx = static_cast<size_t>(lane.playbackPos);
                float sL = 0.0f, sR = 0.0f;
                if (lane.channels == 1) {
                    sL = sR = lane.sampleData[frameIdx];
                } else {
                    sL = lane.sampleData[frameIdx * 2];
                    sR = lane.sampleData[frameIdx * 2 + 1];
                }

                tempBuffer_[i * 2]     += sL * gainL;
                tempBuffer_[i * 2 + 1] += sR * gainR;
                lane.playbackPos += lane.currentPitch;
            }
        }

        applyFxAndGain(tempBuffer_.data(), numFrames);

        for (int32_t i = 0; i < numFrames * 2; ++i) {
            outStereoBuffer[i] += tempBuffer_[i];
        }
    }

private:
    std::vector<NativeLane> lanes_;
    std::vector<float> tempBuffer_;
    std::vector<float> synthBuffer_;
    std::mt19937 rng_;

    mutable std::mutex instrumentMutex_;
    std::unique_ptr<PluginInstance> customInstrument_;
};
