#pragma once
#include <array>
#include <atomic>
#include <memory>
#include <string>
#include <cmath>
#include <algorithm>
#include "Track.hpp"
#include "SynthTrack.hpp"
#include "AudioTrack.hpp"

class Mixer {
public:
    static constexpr size_t MAX_TRACKS = 32;

    Mixer() {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            tracks_[i].store(nullptr, std::memory_order_relaxed);
        }
    }

    ~Mixer() {
        clearAllTracks();
    }

    void clearAllTracks() {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].exchange(nullptr);
            delete t;
        }
        nextTrackId_.store(1);
    }

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].load(std::memory_order_acquire);
            if (t) t->setSampleRate(sampleRate);
        }
    }

    int32_t addSynthTrack(const std::string& name) {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* expected = nullptr;
            if (tracks_[i].load(std::memory_order_relaxed) == nullptr) {
                const int32_t id = nextTrackId_++;
                Track* track = new SynthTrack(id, name);
                track->setSampleRate(sampleRate_);
                if (tracks_[i].compare_exchange_strong(expected, track, std::memory_order_release)) {
                    return id;
                }
                delete track;
            }
        }
        return -1;
    }

    int32_t addAudioTrack(const std::string& name) {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* expected = nullptr;
            if (tracks_[i].load(std::memory_order_relaxed) == nullptr) {
                const int32_t id = nextTrackId_++;
                Track* track = new AudioTrack(id, name);
                track->setSampleRate(sampleRate_);
                if (tracks_[i].compare_exchange_strong(expected, track, std::memory_order_release)) {
                    return id;
                }
                delete track;
            }
        }
        return -1;
    }

    bool removeTrack(int32_t id) {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].load(std::memory_order_acquire);
            if (t && t->getId() == id) {
                tracks_[i].store(nullptr, std::memory_order_release);
                delete t;
                return true;
            }
        }
        return false;
    }

    Track* getTrack(int32_t id) {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].load(std::memory_order_acquire);
            if (t && t->getId() == id) return t;
        }
        return nullptr;
    }

    void setMasterVolume(float vol) noexcept { masterVolume_ = std::clamp(vol, 0.0f, 2.0f); }
    float getMasterVolume() const noexcept { return masterVolume_; }

    void setMasterLimiter(bool enabled) noexcept { masterLimiterEnabled_ = enabled; }
    bool isMasterLimiterEnabled() const noexcept { return masterLimiterEnabled_; }

    float getMasterPeakL() const noexcept { return masterPeakL_.load(std::memory_order_relaxed); }
    float getMasterPeakR() const noexcept { return masterPeakR_.load(std::memory_order_relaxed); }

    int32_t getTrackCount() const noexcept {
        int32_t count = 0;
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            if (tracks_[i].load(std::memory_order_relaxed) != nullptr) count++;
        }
        return count;
    }

    void renderMix(float* output, int32_t numFrames) {
        std::fill_n(output, numFrames * 2, 0.0f);

        bool hasSolo = false;
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].load(std::memory_order_acquire);
            if (t && t->isSolo()) {
                hasSolo = true;
                break;
            }
        }

        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* t = tracks_[i].load(std::memory_order_acquire);
            if (!t) continue;
            if (hasSolo && !t->isSolo()) {
                // Decay peaks on tracks silenced by solo
                continue;
            }
            t->render(output, numFrames);
        }

        float maxL = 0.0f, maxR = 0.0f;
        for (int32_t i = 0; i < numFrames; ++i) {
            float sL = output[i * 2] * masterVolume_;
            float sR = output[i * 2 + 1] * masterVolume_;

            if (masterLimiterEnabled_) {
                if (sL > 0.95f || sL < -0.95f) sL = std::tanh(sL);
                if (sR > 0.95f || sR < -0.95f) sR = std::tanh(sR);
            }

            output[i * 2]     = sL;
            output[i * 2 + 1] = sR;

            maxL = std::max(maxL, std::abs(sL));
            maxR = std::max(maxR, std::abs(sR));
        }

        float currentPeakL = masterPeakL_.load(std::memory_order_relaxed);
        float currentPeakR = masterPeakR_.load(std::memory_order_relaxed);
        masterPeakL_.store(std::max(maxL, currentPeakL * 0.985f), std::memory_order_relaxed);
        masterPeakR_.store(std::max(maxR, currentPeakR * 0.985f), std::memory_order_relaxed);
    }

private:
    float sampleRate_ = 48000.0f;
    float masterVolume_ = 1.0f;
    bool masterLimiterEnabled_ = true;
    std::atomic<float> masterPeakL_{0.0f};
    std::atomic<float> masterPeakR_{0.0f};
    std::atomic<int32_t> nextTrackId_{1};
    std::array<std::atomic<Track*>, MAX_TRACKS> tracks_;
};
