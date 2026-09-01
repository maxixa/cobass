#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"
#include "Wavefolder.hpp"

static const CobassParamDescriptor WAVEFOLDER_PARAMS[] = {
    {0, "Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 36.0f, 8.0f, 0.2f, false, {}, 0},
    {1, "Folds", "x", COBASS_PARAM_TYPE_FLOAT, 1.0f, 5.0f, 1.5f, 0.1f, false, {}, 0},
    {2, "Symmetry", "%", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 0.0f, 1.0f, false, {}, 0},
    {3, "Bit Depth", "bits", COBASS_PARAM_TYPE_INT, 2.0f, 16.0f, 16.0f, 1.0f, false, {}, 0},
    {4, "Downsample", "x", COBASS_PARAM_TYPE_FLOAT, 1.0f, 32.0f, 1.0f, 1.0f, false, {}, 0},
    {5, "Dry / Wet", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest WAVEFOLDER_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.wavefolder_crush",
    "Analog Wavefolder & Crusher",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(WAVEFOLDER_PARAMS) / sizeof(CobassParamDescriptor),
    WAVEFOLDER_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class WavefolderProcessor {
public:
    explicit WavefolderProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : WAVEFOLDER_PARAMS) params_[p.id] = p.defaultValue;
        reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        folderL_.reset(sampleRate_);
        folderR_.reset(sampleRate_);
        updateParameters();
    }

    void setParam(uint32_t id, float value) {
        if (id < params_.size()) {
            params_[id] = value;
            updateParameters();
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

        const float wet = std::clamp(params_[5], 0.0f, 1.0f);
        const float dry = 1.0f - wet;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sL = inL[i];
            float sR = inR[i];

            float foldedL = folderL_.process(sL);
            float foldedR = folderR_.process(sR);

            outL[i] = sL * dry + foldedL * wet;
            outR[i] = sR * dry + foldedR * wet;
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
    void updateParameters() {
        const float driveGain = std::pow(10.0f, params_[0] / 20.0f);
        const float folds = params_[1];
        const float bias = (params_[2] * 0.01f) * 0.4f;
        const float bitDepth = params_[3];
        const float downsample = params_[4];

        folderL_.setParameters(driveGain, folds, bias, bitDepth, downsample);
        folderR_.setParameters(driveGain, folds, bias, bitDepth, downsample);
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    Wavefolder folderL_;
    Wavefolder folderR_;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &WAVEFOLDER_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new WavefolderProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<WavefolderProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<WavefolderProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<WavefolderProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
void cobass_plugin_note_off(CobassHandle, int32_t) {}
void cobass_plugin_all_notes_off(CobassHandle) {}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<WavefolderProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<WavefolderProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<WavefolderProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<WavefolderProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
