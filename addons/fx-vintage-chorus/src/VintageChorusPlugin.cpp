#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor CHORUS_PARAMS[] = {
    {0, "Rate", "Hz", COBASS_PARAM_TYPE_FLOAT, 0.05f, 8.0f, 0.85f, 0.01f, false, {}, 0},
    {1, "Depth", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.65f, 0.01f, false, {}, 0},
    {2, "Feedback", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 0.90f, 0.25f, 0.01f, false, {}, 0},
    {3, "Delay Base", "ms", COBASS_PARAM_TYPE_FLOAT, 2.0f, 35.0f, 12.0f, 0.5f, false, {}, 0},
    {4, "Stereo Phase", "deg", COBASS_PARAM_TYPE_FLOAT, 0.0f, 180.0f, 90.0f, 1.0f, false, {}, 0},
    {5, "Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.50f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest CHORUS_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.vintage_chorus",
    "Vintage Analog Chorus",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(CHORUS_PARAMS) / sizeof(CobassParamDescriptor),
    CHORUS_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class VintageChorusProcessor {
public:
    explicit VintageChorusProcessor(float sampleRate) : sampleRate_(sampleRate) {
        bufferL_.assign(MAX_DELAY_SAMPLES, 0.0f);
        bufferR_.assign(MAX_DELAY_SAMPLES, 0.0f);
        for (const auto& p : CHORUS_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
        std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
        writeIndex_ = 0;
        lfoPhase_ = 0.0;
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

        const float rate = std::clamp(params_[0], 0.05f, 8.0f);
        const float depth = std::clamp(params_[1], 0.0f, 1.0f);
        const float feedback = std::clamp(params_[2], 0.0f, 0.90f);
        const float baseDelayMs = std::clamp(params_[3], 2.0f, 35.0f);
        const float phaseOffsetRad = (params_[4] * 3.14159265f) / 180.0f;
        const float mix = std::clamp(params_[5], 0.0f, 1.0f);

        const float baseDelaySamples = (baseDelayMs * 0.001f) * sampleRate_;
        const float modDepthSamples = (depth * 8.0f * 0.001f) * sampleRate_;
        const double lfoInc = (rate * 6.28318530718) / sampleRate_;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sL = inL[i];
            float sR = inR[i];

            double lfoL = std::sin(lfoPhase_);
            double lfoR = std::sin(lfoPhase_ + phaseOffsetRad);
            lfoPhase_ += lfoInc;
            if (lfoPhase_ >= 6.28318530718) lfoPhase_ -= 6.28318530718;

            float delayL = baseDelaySamples + static_cast<float>(lfoL) * modDepthSamples;
            float delayR = baseDelaySamples + static_cast<float>(lfoR) * modDepthSamples;

            float wetL = readInterpolated(bufferL_, writeIndex_, delayL);
            float wetR = readInterpolated(bufferR_, writeIndex_, delayR);

            bufferL_[writeIndex_] = sL + wetL * feedback;
            bufferR_[writeIndex_] = sR + wetR * feedback;

            writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;

            outL[i] = sL * (1.0f - mix) + wetL * mix;
            outR[i] = sR * (1.0f - mix) + wetR * mix;
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
    static constexpr size_t MAX_DELAY_SAMPLES = 8192;

    static float readInterpolated(const std::vector<float>& buf, size_t writeIdx, float delaySamples) {
        float readPos = static_cast<float>(writeIdx) - delaySamples;
        while (readPos < 0.0f) readPos += MAX_DELAY_SAMPLES;

        size_t idx0 = static_cast<size_t>(readPos) % MAX_DELAY_SAMPLES;
        size_t idx1 = (idx0 + 1) % MAX_DELAY_SAMPLES;
        float frac = readPos - static_cast<float>(static_cast<size_t>(readPos));

        return buf[idx0] * (1.0f - frac) + buf[idx1] * frac;
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t writeIndex_ = 0;
    double lfoPhase_ = 0.0;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &CHORUS_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new VintageChorusProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<VintageChorusProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<VintageChorusProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<VintageChorusProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
void cobass_plugin_note_off(CobassHandle, int32_t) {}
void cobass_plugin_all_notes_off(CobassHandle) {}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<VintageChorusProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<VintageChorusProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<VintageChorusProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<VintageChorusProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
