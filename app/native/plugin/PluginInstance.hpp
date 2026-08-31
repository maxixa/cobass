#pragma once
#include <string>
#include <vector>
#include <memory>
#include <algorithm>
#include "../include/CobassPluginABI.h"
#include "PluginDescriptor.hpp"

class PluginInstance {
public:
    PluginInstance(
        CobassHandle handle,
        PluginDescriptor descriptor,
        CobassDestroyInstanceFunc destroyFunc,
        CobassResetFunc resetFunc,
        CobassProcessFunc processFunc,
        CobassNoteOnFunc noteOnFunc,
        CobassNoteOffFunc noteOffFunc,
        CobassAllNotesOffFunc allNotesOffFunc,
        CobassSetParamFunc setParamFunc,
        CobassGetParamFunc getParamFunc,
        CobassGetStateFunc getStateFunc,
        CobassSetStateFunc setStateFunc)
        : handle_(handle),
          descriptor_(std::move(descriptor)),
          destroyFunc_(destroyFunc),
          resetFunc_(resetFunc),
          processFunc_(processFunc),
          noteOnFunc_(noteOnFunc),
          noteOffFunc_(noteOffFunc),
          allNotesOffFunc_(allNotesOffFunc),
          setParamFunc_(setParamFunc),
          getParamFunc_(getParamFunc),
          getStateFunc_(getStateFunc),
          setStateFunc_(setStateFunc) {
        
        inPointers_.resize(2, nullptr);
        outPointers_.resize(2, nullptr);
        channelScratchL_.resize(4096, 0.0f);
        channelScratchR_.resize(4096, 0.0f);
        outChannelL_.resize(4096, 0.0f);
        outChannelR_.resize(4096, 0.0f);
    }

    ~PluginInstance() {
        if (handle_ && destroyFunc_) {
            destroyFunc_(handle_);
            handle_ = nullptr;
        }
    }

    // Disable copy, enable move
    PluginInstance(const PluginInstance&) = delete;
    PluginInstance& operator=(const PluginInstance&) = delete;
    PluginInstance(PluginInstance&& other) noexcept = default;
    PluginInstance& operator=(PluginInstance&& other) noexcept = default;

    const PluginDescriptor& getDescriptor() const noexcept { return descriptor_; }
    bool isValid() const noexcept { return handle_ != nullptr; }

    void reset(float sampleRate) {
        if (handle_ && resetFunc_) resetFunc_(handle_, sampleRate);
    }

    void process(const float* inInterleaved, float* outInterleaved, int32_t numFrames) {
        if (!handle_ || !processFunc_ || numFrames <= 0) {
            if (inInterleaved != outInterleaved && inInterleaved && outInterleaved) {
                std::copy_n(inInterleaved, numFrames * 2, outInterleaved);
            }
            return;
        }

        if (channelScratchL_.size() < static_cast<size_t>(numFrames)) {
            channelScratchL_.resize(numFrames, 0.0f);
            channelScratchR_.resize(numFrames, 0.0f);
            outChannelL_.resize(numFrames, 0.0f);
            outChannelR_.resize(numFrames, 0.0f);
        }

        // Deinterleave stereo inputs
        if (inInterleaved) {
            for (int32_t i = 0; i < numFrames; ++i) {
                channelScratchL_[i] = inInterleaved[i * 2];
                channelScratchR_[i] = inInterleaved[i * 2 + 1];
            }
            inPointers_[0] = channelScratchL_.data();
            inPointers_[1] = channelScratchR_.data();
        } else {
            inPointers_[0] = nullptr;
            inPointers_[1] = nullptr;
        }

        outPointers_[0] = outChannelL_.data();
        outPointers_[1] = outChannelR_.data();

        processFunc_(handle_, inPointers_.data(), outPointers_.data(), 2, static_cast<uint32_t>(numFrames));

        // Interleave back into output buffer
        for (int32_t i = 0; i < numFrames; ++i) {
            outInterleaved[i * 2]     = outChannelL_[i];
            outInterleaved[i * 2 + 1] = outChannelR_[i];
        }
    }

    void noteOn(int32_t note, float velocity) {
        if (handle_ && noteOnFunc_) noteOnFunc_(handle_, note, velocity);
    }

    void noteOff(int32_t note) {
        if (handle_ && noteOffFunc_) noteOffFunc_(handle_, note);
    }

    void allNotesOff() {
        if (handle_ && allNotesOffFunc_) allNotesOffFunc_(handle_);
    }

    void setParameter(uint32_t paramId, float value) {
        if (handle_ && setParamFunc_) setParamFunc_(handle_, paramId, value);
    }

    float getParameter(uint32_t paramId) const {
        if (handle_ && getParamFunc_) return getParamFunc_(handle_, paramId);
        return 0.0f;
    }

    std::string getStateJson() const {
        if (!handle_ || !getStateFunc_) return "{}";
        std::vector<char> buffer(16384, 0);
        uint32_t written = getStateFunc_(handle_, buffer.data(), static_cast<uint32_t>(buffer.size()));
        return written > 0 ? std::string(buffer.data()) : "{}";
    }

    bool setStateJson(const std::string& json) {
        if (handle_ && setStateFunc_) return setStateFunc_(handle_, json.c_str());
        return false;
    }

private:
    CobassHandle handle_ = nullptr;
    PluginDescriptor descriptor_;

    CobassDestroyInstanceFunc destroyFunc_ = nullptr;
    CobassResetFunc resetFunc_ = nullptr;
    CobassProcessFunc processFunc_ = nullptr;
    CobassNoteOnFunc noteOnFunc_ = nullptr;
    CobassNoteOffFunc noteOffFunc_ = nullptr;
    CobassAllNotesOffFunc allNotesOffFunc_ = nullptr;
    CobassSetParamFunc setParamFunc_ = nullptr;
    CobassGetParamFunc getParamFunc_ = nullptr;
    CobassGetStateFunc getStateFunc_ = nullptr;
    CobassSetStateFunc setStateFunc_ = nullptr;

    std::vector<const float*> inPointers_;
    std::vector<float*> outPointers_;
    std::vector<float> channelScratchL_;
    std::vector<float> channelScratchR_;
    std::vector<float> outChannelL_;
    std::vector<float> outChannelR_;
};
