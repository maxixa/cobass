#pragma once
#include "Track.hpp"
#include <cmath>

class AudioTrack : public Track {
public:
    AudioTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::Audio, name) {
        tempBuffer_.assign(8192, 0.0f);
    }

    void loadSampleData(const float* data, int32_t length, int32_t channels) override {
        sampleData_.assign(data, data + length);
        channels_ = std::clamp(channels, 1, 2);
        playbackIndex_ = 0.0;
        isPlaying_ = false;
    }

    void noteOn(int32_t /*note*/, float /*vel*/) override {
        playbackIndex_ = 0.0;
        isPlaying_ = !sampleData_.empty();
    }

    void noteOff(int32_t /*note*/) override {
        if (!isLooping_) isPlaying_ = false;
    }

    void stopPlayback() noexcept {
        isPlaying_ = false;
        playbackIndex_ = 0.0;
    }

    void setTrimAndFade(float trimStart, float trimEnd, float fadeIn, float fadeOut) noexcept {
        trimStartRatio_ = std::clamp(trimStart, 0.0f, 0.99f);
        trimEndRatio_   = std::clamp(trimEnd, trimStartRatio_ + 0.01f, 1.0f);
        fadeInRatio_    = std::clamp(fadeIn, 0.0f, 0.5f);
        fadeOutRatio_   = std::clamp(fadeOut, 0.0f, 0.5f);
    }

    void setParam(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: isLooping_ = (value > 0.5f); break;
            case 1: playbackPitch_ = std::clamp(value, 0.25f, 4.0f); break;
        }
    }

    float getPlaybackFraction() const noexcept {
        if (sampleData_.empty() || channels_ <= 0) return 0.0f;
        const size_t totalFrames = sampleData_.size() / channels_;
        if (totalFrames == 0) return 0.0f;

        const size_t startFrame = static_cast<size_t>(trimStartRatio_ * totalFrames);
        const size_t endFrame   = static_cast<size_t>(trimEndRatio_ * totalFrames);
        const size_t activeFrames = (endFrame > startFrame) ? (endFrame - startFrame) : totalFrames;
        if (activeFrames == 0) return 0.0f;

        double currentFrame = startFrame + playbackIndex_;
        return static_cast<float>(currentFrame / static_cast<double>(totalFrames));
    }

    bool isPlayingSample() const noexcept { return isPlaying_; }

    void render(float* outStereoBuffer, int32_t numFrames) override {
        if (isMuted_ || !isPlaying_ || sampleData_.empty()) {
            peakL_.store(0.0f, std::memory_order_relaxed);
            peakR_.store(0.0f, std::memory_order_relaxed);
            return;
        }

        if (tempBuffer_.size() < static_cast<size_t>(numFrames * 2)) {
            tempBuffer_.resize(numFrames * 2, 0.0f);
        }
        std::fill_n(tempBuffer_.data(), numFrames * 2, 0.0f);

        const size_t totalFrames = sampleData_.size() / channels_;
        const size_t startFrame  = static_cast<size_t>(trimStartRatio_ * totalFrames);
        const size_t endFrame    = static_cast<size_t>(trimEndRatio_ * totalFrames);
        const size_t activeFrames = (endFrame > startFrame) ? (endFrame - startFrame) : totalFrames;

        const size_t fadeInFrames  = static_cast<size_t>(fadeInRatio_ * activeFrames);
        const size_t fadeOutFrames = static_cast<size_t>(fadeOutRatio_ * activeFrames);

        for (int32_t i = 0; i < numFrames; ++i) {
            if (playbackIndex_ >= activeFrames) {
                if (isLooping_) {
                    playbackIndex_ = 0.0;
                } else {
                    isPlaying_ = false;
                    break;
                }
            }

            const size_t relFrame = static_cast<size_t>(playbackIndex_);
            const size_t currentFrame = startFrame + relFrame;
            if (currentFrame >= totalFrames) {
                isPlaying_ = false;
                break;
            }

            // Equal-Power Sine Fade Calculation: sin(t * PI / 2)
            float envGain = 1.0f;
            if (fadeInFrames > 0 && relFrame < fadeInFrames) {
                float t = static_cast<float>(relFrame) / static_cast<float>(fadeInFrames);
                envGain *= std::sin(t * 1.57079632679f);
            }
            if (fadeOutFrames > 0 && relFrame >= (activeFrames - fadeOutFrames)) {
                float t = static_cast<float>(activeFrames - relFrame) / static_cast<float>(fadeOutFrames);
                envGain *= std::sin(t * 1.57079632679f);
            }

            float sampleL = 0.0f, sampleR = 0.0f;
            if (channels_ == 1) {
                sampleL = sampleR = sampleData_[currentFrame] * envGain;
            } else {
                sampleL = sampleData_[currentFrame * 2] * envGain;
                sampleR = sampleData_[currentFrame * 2 + 1] * envGain;
            }

            tempBuffer_[i * 2]     = sampleL;
            tempBuffer_[i * 2 + 1] = sampleR;
            playbackIndex_ += playbackPitch_;
        }

        applyFxAndGain(tempBuffer_.data(), numFrames);

        for (int32_t i = 0; i < numFrames * 2; ++i) {
            outStereoBuffer[i] += tempBuffer_[i];
        }
    }

private:
    std::vector<float> sampleData_;
    int32_t channels_ = 1;
    double playbackIndex_ = 0.0;
    float playbackPitch_ = 1.0f;
    bool isPlaying_ = false;
    bool isLooping_ = false;

    float trimStartRatio_ = 0.0f;
    float trimEndRatio_ = 1.0f;
    float fadeInRatio_ = 0.0f;
    float fadeOutRatio_ = 0.0f;

    std::vector<float> tempBuffer_;
};
