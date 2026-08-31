#pragma once
#include <atomic>
#include <cstdint>
#include <algorithm>

class Transport {
public:
    static constexpr int32_t PPQ = 480;

    Transport() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = sampleRate;
        updateSamplesPerTick();
    }

    void setBpm(float bpm) noexcept {
        bpm_ = std::clamp(bpm, 20.0f, 300.0f);
        updateSamplesPerTick();
    }

    float getBpm() const noexcept { return bpm_; }

    void playFromStart() noexcept {
        const int64_t start = loopEnabled_.load(std::memory_order_relaxed) ? loopStartTick_ : 0;
        currentTick_.store(start, std::memory_order_release);
        accumulatedSamples_ = 0.0;
        isPlaying_.store(true, std::memory_order_release);
    }

    void play() noexcept {
        if (!isPlaying_.load(std::memory_order_relaxed)) {
            if (loopEnabled_.load(std::memory_order_relaxed) && currentTick_.load(std::memory_order_relaxed) >= loopEndTick_) {
                currentTick_.store(loopStartTick_, std::memory_order_release);
            }
            accumulatedSamples_ = 0.0;
            isPlaying_.store(true, std::memory_order_release);
        }
    }

    void pause() noexcept { isPlaying_.store(false, std::memory_order_release); }

    void stop() noexcept {
        isPlaying_.store(false, std::memory_order_release);
        const int64_t start = loopEnabled_.load(std::memory_order_relaxed) ? loopStartTick_ : 0;
        currentTick_.store(start, std::memory_order_release);
        accumulatedSamples_ = 0.0;
    }

    void seekToTick(int64_t tick) noexcept {
        currentTick_.store(std::max<int64_t>(0, tick), std::memory_order_release);
        accumulatedSamples_ = 0.0;
    }

    int64_t getCurrentTick() const noexcept {
        return currentTick_.load(std::memory_order_acquire);
    }

    bool isPlaying() const noexcept {
        return isPlaying_.load(std::memory_order_acquire);
    }

    void setLoop(int64_t startTick, int64_t endTick, bool enabled) noexcept {
        loopStartTick_ = std::max<int64_t>(0, startTick);
        loopEndTick_ = std::max(loopStartTick_ + PPQ / 4, endTick);
        loopEnabled_.store(enabled, std::memory_order_release);
    }

    bool isLoopEnabled() const noexcept { return loopEnabled_.load(std::memory_order_acquire); }
    int64_t getLoopStart() const noexcept { return loopStartTick_; }
    int64_t getLoopEnd() const noexcept { return loopEndTick_; }

    int64_t advance(int32_t numFrames) noexcept {
        if (!isPlaying_.load(std::memory_order_relaxed)) {
            return currentTick_.load(std::memory_order_relaxed);
        }

        accumulatedSamples_ += numFrames;
        const double samplesPerTick = samplesPerTick_;
        if (samplesPerTick <= 0.0) return currentTick_.load(std::memory_order_relaxed);

        const int64_t deltaTicks = static_cast<int64_t>(accumulatedSamples_ / samplesPerTick);
        if (deltaTicks > 0) {
            accumulatedSamples_ -= deltaTicks * samplesPerTick;
            int64_t newTick = currentTick_.load(std::memory_order_relaxed) + deltaTicks;

            if (loopEnabled_.load(std::memory_order_relaxed) && newTick >= loopEndTick_) {
                const int64_t loopLength = loopEndTick_ - loopStartTick_;
                if (loopLength > 0) {
                    newTick = loopStartTick_ + ((newTick - loopStartTick_) % loopLength);
                }
            }
            currentTick_.store(newTick, std::memory_order_relaxed);
        }

        return currentTick_.load(std::memory_order_relaxed);
    }

private:
    void updateSamplesPerTick() noexcept {
        if (sampleRate_ > 0.0f && bpm_ > 0.0f) {
            samplesPerTick_ = (sampleRate_ * 60.0) / (bpm_ * PPQ);
        }
    }

    float sampleRate_ = 48000.0f;
    float bpm_ = 120.0f;
    double samplesPerTick_ = 50.0;
    double accumulatedSamples_ = 0.0;

    std::atomic<bool> isPlaying_{false};
    std::atomic<int64_t> currentTick_{0};

    std::atomic<bool> loopEnabled_{true};
    int64_t loopStartTick_ = 0;
    int64_t loopEndTick_ = PPQ * 16;
};
