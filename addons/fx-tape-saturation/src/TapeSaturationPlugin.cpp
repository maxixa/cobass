#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor TAPE_PARAMS[] = {
    {0, "Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 36.0f, 8.0f, 0.2f, false, {}, 0},
    {1, "Warmth", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},
    {2, "Tape Mode", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 2.0f, 0.0f, 1.0f, false, {"Warm Tape", "Tube Triode", "Hard Saturate"}, 3},
    {3, "Tone Tilt", "dB", COBASS_PARAM_TYPE_FLOAT, -6.0f, 6.0f, 0.0f, 0.1f, false, {}, 0},
    {4, "Output Trim", "dB", COBASS_PARAM_TYPE_FLOAT, -18.0f, 6.0f, -2.0f, 0.1f, false, {}, 0},
    {5, "Dry / Wet", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest TAPE_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.tape_saturation",
    "Tube & Tape Saturator",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(TAPE_PARAMS) / sizeof(CobassParamDescriptor),
    TAPE_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class TapeSaturationProcessor {
public:
    explicit TapeSaturationProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : TAPE_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        prevWarmthL_ = prevWarmthR_ = 0.0f;
    }

    void setParam(uint32_t id, float value) {
        if (id < params_.size()) params_[id] = value;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void process(const float** inputs, float** outputs, uint32_t /*channels*/, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float driveGain = std::pow(10.0f, params_[0] / 20.0f);
        const float warmthCoeff = std::clamp(params_[1], 0.0f, 0.95f) * 0.45f;
        const int mode = static_cast<int>(params_[2]);
        const float outTrim = std::pow(10.0f, params_[4] / 20.0f);
        const float wet = std::clamp(params_[5], 0.0f, 1.0f);
        const float dry = 1.0f - wet;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sL = inL[i];
            float sR = inR[i];

            // Warmth Pre-Emphasis Filter (1st-order low-pass harmonic bias)
            prevWarmthL_ = (sL * (1.0f - warmthCoeff)) + (prevWarmthL_ * warmthCoeff);
            prevWarmthR_ = (sR * (1.0f - warmthCoeff)) + (prevWarmthR_ * warmthCoeff);

            float drivenL = (sL + prevWarmthL_ * 0.35f) * driveGain;
            float drivenR = (sR + prevWarmthR_ * 0.35f) * driveGain;

            float satL = saturateSample(drivenL, mode);
            float satR = saturateSample(drivenR, mode);

            outL[i] = sL * dry + (satL * outTrim) * wet;
            outR[i] = sR * dry + (satR * outTrim) * wet;
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
                params_[i] = std::strtof(pos, nullptr);
            }
        }
        return true;
    }

private:
    static float saturateSample(float x, int mode) {
        switch (mode) {
            case 0: // Warm Analog Tape (Hyperbolic Tangent soft clip)
                return std::tanh(x);
            case 1: // Tube Triode (Asymmetric quadratic saturation)
                if (x >= 0.0f) {
                    return x / (1.0f + x * 0.6f);
                } else {
                    return std::tanh(x * 1.25f) * 0.85f;
                }
            case 2: // Hard Saturate / Soft Clipper
            default:
                if (x > 1.0f) return 1.0f - std::exp(-x);
                if (x < -1.0f) return -1.0f + std::exp(x);
                return x - (x * x * x) / 3.0f;
        }
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    float prevWarmthL_ = 0.0f;
    float prevWarmthR_ = 0.0f;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &TAPE_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new TapeSaturationProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<TapeSaturationProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<TapeSaturationProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<TapeSaturationProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
void cobass_plugin_note_off(CobassHandle, int32_t) {}
void cobass_plugin_all_notes_off(CobassHandle) {}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<TapeSaturationProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<TapeSaturationProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<TapeSaturationProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<TapeSaturationProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
