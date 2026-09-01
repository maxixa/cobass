#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"
#include "BiquadFilter.hpp"

static const CobassParamDescriptor OTT_PARAMS[] = {
    {0, "Depth", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0},
    {1, "Time Scale", "%", COBASS_PARAM_TYPE_FLOAT, 0.1f, 3.0f, 1.0f, 0.05f, false, {}, 0},
    {2, "Input Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 18.0f, 0.0f, 0.1f, false, {}, 0},
    {3, "Output Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 18.0f, 0.0f, 0.1f, false, {}, 0},
    {4, "Low Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 18.0f, 0.0f, 0.1f, false, {}, 0},
    {5, "Mid Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 18.0f, 0.0f, 0.1f, false, {}, 0},
    {6, "High Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 18.0f, 0.0f, 0.1f, false, {}, 0},
    {7, "Upward Ratio", ":1", COBASS_PARAM_TYPE_FLOAT, 1.0f, 6.0f, 2.5f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest OTT_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.ott_compressor",
    "OTT Multiband Dynamics",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(OTT_PARAMS) / sizeof(CobassParamDescriptor),
    OTT_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class OttBandDynamics {
public:
    void reset(float sampleRate, float downThreshDb, float upThreshDb, float downRatio, float upRatio) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        downThreshDb_ = downThreshDb;
        upThreshDb_ = upThreshDb;
        downRatio_ = downRatio;
        upRatio_ = upRatio;
        envL_ = envR_ = 0.0f;
        updateCoeffs(1.0f);
    }

    void updateCoeffs(float timeScale) {
        float attSec = 0.005f * std::clamp(timeScale, 0.1f, 3.0f);
        float relSec = 0.050f * std::clamp(timeScale, 0.1f, 3.0f);
        attCoeff_ = std::exp(-1.0f / (attSec * sampleRate_));
        relCoeff_ = std::exp(-1.0f / (relSec * sampleRate_));
    }

    void setUpwardRatio(float ratio) {
        upRatio_ = std::clamp(ratio, 1.0f, 8.0f);
    }

    inline void process(float inL, float inR, float gainDb, float depth, float& outL, float& outR) noexcept {
        float peakL = std::abs(inL);
        float peakR = std::abs(inR);

        envL_ = (peakL > envL_) ? (attCoeff_ * envL_ + (1.0f - attCoeff_) * peakL)
                               : (relCoeff_ * envL_ + (1.0f - relCoeff_) * peakL);
        envR_ = (peakR > envR_) ? (attCoeff_ * envR_ + (1.0f - attCoeff_) * peakR)
                               : (relCoeff_ * envR_ + (1.0f - relCoeff_) * peakR);

        float avgEnv = std::max(1e-5f, (envL_ + envR_) * 0.5f);
        float envDb = 20.0f * std::log10(avgEnv);

        float gainChangeDb = 0.0f;

        // Downward Compression (Above threshold: squashes peaks)
        if (envDb > downThreshDb_) {
            gainChangeDb += (downThreshDb_ - envDb) * (1.0f - (1.0f / downRatio_));
        }
        // Upward Compression (Below threshold: lifts quiet details)
        else if (envDb < upThreshDb_) {
            gainChangeDb += (upThreshDb_ - envDb) * (1.0f - (1.0f / upRatio_));
        }

        gainChangeDb = std::clamp(gainChangeDb, -24.0f, 24.0f);
        float targetGainLinear = std::pow(10.0f, (gainChangeDb * depth + gainDb) / 20.0f);

        outL = inL * targetGainLinear;
        outR = inR * targetGainLinear;
    }

private:
    float sampleRate_ = 48000.0f;
    float downThreshDb_ = -18.0f;
    float upThreshDb_ = -36.0f;
    float downRatio_ = 4.0f;
    float upRatio_ = 2.5f;

    float attCoeff_ = 0.99f;
    float relCoeff_ = 0.999f;
    float envL_ = 0.0f;
    float envR_ = 0.0f;
};

class OttProcessor {
public:
    explicit OttProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : OTT_PARAMS) params_[p.id] = p.defaultValue;
        reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);

        // Configure 4th-order Linkwitz-Riley crossover filters (cascaded Butterworth Q=0.7071)
        lowSplitL1_.setSampleRate(sampleRate_); lowSplitL2_.setSampleRate(sampleRate_);
        lowSplitR1_.setSampleRate(sampleRate_); lowSplitR2_.setSampleRate(sampleRate_);
        highSplitL1_.setSampleRate(sampleRate_); highSplitL2_.setSampleRate(sampleRate_);
        highSplitR1_.setSampleRate(sampleRate_); highSplitR2_.setSampleRate(sampleRate_);

        lowSplitL1_.setParameters(FilterType::LowPass, 140.0f, 0.0f, 0.7071f);
        lowSplitL2_.setParameters(FilterType::LowPass, 140.0f, 0.0f, 0.7071f);
        lowSplitR1_.setParameters(FilterType::LowPass, 140.0f, 0.0f, 0.7071f);
        lowSplitR2_.setParameters(FilterType::LowPass, 140.0f, 0.0f, 0.7071f);

        highSplitL1_.setParameters(FilterType::HighPass, 2500.0f, 0.0f, 0.7071f);
        highSplitL2_.setParameters(FilterType::HighPass, 2500.0f, 0.0f, 0.7071f);
        highSplitR1_.setParameters(FilterType::HighPass, 2500.0f, 0.0f, 0.7071f);
        highSplitR2_.setParameters(FilterType::HighPass, 2500.0f, 0.0f, 0.7071f);

        bandLow_.reset(sampleRate_, -18.0f, -38.0f, 6.0f, params_[7]);
        bandMid_.reset(sampleRate_, -15.0f, -34.0f, 4.0f, params_[7]);
        bandHigh_.reset(sampleRate_, -12.0f, -30.0f, 5.0f, params_[7]);
    }

    void setParam(uint32_t id, float value) {
        if (id < params_.size()) {
            params_[id] = value;
            if (id == 1) { // Time scale
                bandLow_.updateCoeffs(params_[1]);
                bandMid_.updateCoeffs(params_[1]);
                bandHigh_.updateCoeffs(params_[1]);
            } else if (id == 7) { // Upward ratio
                bandLow_.setUpwardRatio(params_[7]);
                bandMid_.setUpwardRatio(params_[7]);
                bandHigh_.setUpwardRatio(params_[7]);
            }
        }
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void process(const float** inputs, float** outputs, uint32_t /*channels*/, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float inGain = std::pow(10.0f, params_[2] / 20.0f);
        const float outGain = std::pow(10.0f, params_[3] / 20.0f);
        const float depth = std::clamp(params_[0], 0.0f, 1.0f);

        const float gainLow = params_[4];
        const float gainMid = params_[5];
        const float gainHigh = params_[6];

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sL = inL[i] * inGain;
            float sR = inR[i] * inGain;

            // 1. Linkwitz-Riley 3-Band Frequency Split
            float lowL = lowSplitL2_.process(lowSplitL1_.process(sL));
            float lowR = lowSplitR2_.process(lowSplitR1_.process(sR));

            float highL = highSplitL2_.process(highSplitL1_.process(sL));
            float highR = highSplitR2_.process(highSplitR1_.process(sR));

            float midL = sL - lowL - highL;
            float midR = sR - lowR - highR;

            // 2. Dynamics processing per band
            float procLowL = 0.0f, procLowR = 0.0f;
            float procMidL = 0.0f, procMidR = 0.0f;
            float procHighL = 0.0f, procHighR = 0.0f;

            bandLow_.process(lowL, lowR, gainLow, depth, procLowL, procLowR);
            bandMid_.process(midL, midR, gainMid, depth, procMidL, procMidR);
            bandHigh_.process(highL, highR, gainHigh, depth, procHighL, procHighR);

            // 3. Summing & Wet/Dry Blend
            float sumL = (procLowL + procMidL + procHighL) * outGain;
            float sumR = (procLowR + procMidR + procHighR) * outGain;

            outL[i] = sL * (1.0f - depth) + sumL * depth;
            outR[i] = sR * (1.0f - depth) + sumR * depth;
        }
    }

    uint32_t getStateJson(char* outBuffer, uint32_t maxLen) const {
        std::string json = "{";
        for (size_t i = 0; i < params_.size(); ++i) {
            json += "\"" + std::to_string(i) + "\":" + std::to_string(params_[i]);
            if (i < params_.size() - 1) json += ",";
        }
        json += "}";
        if (json.size() >= maxLen) return 0;
        std::memcpy(outBuffer, json.c_str(), json.size() + 1);
        return static_cast<uint32_t>(json.size());
    }

    bool setStateJson(const char* json) {
        if (!json) return false;
        for (size_t i = 0; i < params_.size(); ++i) {
            std::string key = "\"" + std::to_string(i) + "\"";
            const char* pos = std::strstr(json, key.c_str());
            if (pos) {
                pos += key.size();
                while (*pos == ' ' || *pos == '\t' || *pos == ':') pos++;
                setParam(static_cast<uint32_t>(i), std::strtof(pos, nullptr));
            }
        }
        return true;
    }

private:
    float sampleRate_ = 48000.0f;
    std::array<float, 8> params_{};

    BiquadFilter lowSplitL1_, lowSplitL2_;
    BiquadFilter lowSplitR1_, lowSplitR2_;
    BiquadFilter highSplitL1_, highSplitL2_;
    BiquadFilter highSplitR1_, highSplitR2_;

    OttBandDynamics bandLow_;
    OttBandDynamics bandMid_;
    OttBandDynamics bandHigh_;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &OTT_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new OttProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<OttProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<OttProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<OttProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
void cobass_plugin_note_off(CobassHandle, int32_t) {}
void cobass_plugin_all_notes_off(CobassHandle) {}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<OttProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<OttProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<OttProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<OttProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
