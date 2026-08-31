#pragma once
#include <string>
#include <vector>
#include <memory>
#include <array>
#include <atomic>
#include <algorithm>
#include "AudioNode.hpp"
#include "ParametricEQ.hpp"
#include "Compressor.hpp"
#include "ReverbEffect.hpp"
#include "DelayEffect.hpp"
#include "../plugin/PluginChain.hpp"

enum class TrackType : int32_t { Synth = 0, Audio = 1, Bus = 2 };

class Track {
public:
    Track(int32_t id, TrackType type, const std::string& name)
        : id_(id), type_(type), name_(name) {
        eqNode_ = std::make_unique<ParametricEQ>();
        compNode_ = std::make_unique<Compressor>();
        delayNode_ = std::make_unique<DelayEffect>();
        reverbNode_ = std::make_unique<ReverbEffect>();
        tempFxBuffer_.assign(8192, 0.0f);
    }

    virtual ~Track() = default;

    int32_t getId() const noexcept { return id_; }
    TrackType getType() const noexcept { return type_; }
    const std::string& getName() const noexcept { return name_; }
    void setName(const std::string& name) { name_ = name; }

    void setVolume(float vol) noexcept { volume_ = std::clamp(vol, 0.0f, 2.0f); }
    float getVolume() const noexcept { return volume_; }

    void setPan(float pan) noexcept { pan_ = std::clamp(pan, -1.0f, 1.0f); }
    float getPan() const noexcept { return pan_; }

    void setMute(bool mute) noexcept { isMuted_ = mute; }
    bool isMuted() const noexcept { return isMuted_; }

    void setSolo(bool solo) noexcept { isSolo_ = solo; }
    bool isSolo() const noexcept { return isSolo_; }

    void setPhaseInvert(bool invert) noexcept { phaseInvert_ = invert; }
    bool isPhaseInverted() const noexcept { return phaseInvert_; }

    float getPeakL() const noexcept { return peakL_.load(std::memory_order_relaxed); }
    float getPeakR() const noexcept { return peakR_.load(std::memory_order_relaxed); }

    virtual void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        eqNode_->reset(sampleRate);
        compNode_->reset(sampleRate);
        delayNode_->reset(sampleRate);
        reverbNode_->reset(sampleRate);
        pluginChain_.setSampleRate(sampleRate);
    }

    virtual void render(float* outStereoBuffer, int32_t numFrames) = 0;

    virtual void noteOn(int32_t /*note*/, float /*vel*/) {}
    virtual void noteOff(int32_t /*note*/) {}
    virtual void allNotesOff() {}
    virtual void setParam(uint32_t /*paramId*/, float /*value*/) {}
    virtual void loadSampleData(const float* /*data*/, int32_t /*length*/, int32_t /*channels*/) {}

    // Legacy FX Controls
    void setFxParam(int32_t fxSlot, uint32_t paramId, float value) {
        switch (fxSlot) {
            case 0: eqNode_->setParameter(paramId, value); break;
            case 1: compNode_->setParameter(paramId, value); break;
            case 2: delayNode_->setParameter(paramId, value); break;
            case 3: reverbNode_->setParameter(paramId, value); break;
        }
    }

    // Modular Insert FX Rack API
    PluginChain& getPluginChain() noexcept { return pluginChain_; }
    const PluginChain& getPluginChain() const noexcept { return pluginChain_; }

protected:
    void applyFxAndGain(float* buffer, int32_t numFrames) {
        if (tempFxBuffer_.size() < static_cast<size_t>(numFrames * 2)) {
            tempFxBuffer_.resize(numFrames * 2, 0.0f);
        }

        // 1. Fixed Core Utility Chain
        eqNode_->process(buffer, tempFxBuffer_.data(), numFrames);
        compNode_->process(tempFxBuffer_.data(), buffer, numFrames);
        delayNode_->process(buffer, tempFxBuffer_.data(), numFrames);
        reverbNode_->process(tempFxBuffer_.data(), buffer, numFrames);

        // 2. Modular Plugin Insert Rack (Slots 0..7)
        pluginChain_.process(buffer, numFrames);

        // 3. Constant-Power Panning & Phase Invert
        const float phaseMult = phaseInvert_ ? -1.0f : 1.0f;
        const float leftGain = volume_ * (pan_ <= 0.0f ? 1.0f : (1.0f - pan_)) * phaseMult;
        const float rightGain = volume_ * (pan_ >= 0.0f ? 1.0f : (1.0f + pan_)) * phaseMult;

        float maxL = 0.0f;
        float maxR = 0.0f;

        for (int32_t i = 0; i < numFrames; ++i) {
            float sL = buffer[i * 2] * leftGain;
            float sR = buffer[i * 2 + 1] * rightGain;

            buffer[i * 2]     = sL;
            buffer[i * 2 + 1] = sR;

            maxL = std::max(maxL, std::abs(sL));
            maxR = std::max(maxR, std::abs(sR));
        }

        float currentPeakL = peakL_.load(std::memory_order_relaxed);
        float currentPeakR = peakR_.load(std::memory_order_relaxed);
        peakL_.store(std::max(maxL, currentPeakL * 0.985f), std::memory_order_relaxed);
        peakR_.store(std::max(maxR, currentPeakR * 0.985f), std::memory_order_relaxed);
    }

    int32_t id_;
    TrackType type_;
    std::string name_;
    float sampleRate_ = 48000.0f;

    float volume_ = 0.8f;
    float pan_ = 0.0f;
    bool isMuted_ = false;
    bool isSolo_ = false;
    bool phaseInvert_ = false;

    std::atomic<float> peakL_{0.0f};
    std::atomic<float> peakR_{0.0f};

    std::unique_ptr<AudioNode> eqNode_;
    std::unique_ptr<AudioNode> compNode_;
    std::unique_ptr<AudioNode> delayNode_;
    std::unique_ptr<AudioNode> reverbNode_;
    PluginChain pluginChain_;
    std::vector<float> tempFxBuffer_;
};
