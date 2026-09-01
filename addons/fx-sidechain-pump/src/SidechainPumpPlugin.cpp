#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"
#include "BiquadFilter.hpp"

static const CobassParamDescriptor PUMP_PARAMS[] = {
    {0, "Sync Rate", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 5.0f, 2.0f, 1.0f, false, {"1/1 Bar", "1/2 Beat", "1/4 Beat", "1/8 Beat", "1/16 Beat", "1/4 Triplet"}, 6},
    {1, "Curve Shape", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 1.0f, 1.0f, false, {"Smooth Ramp", "Exponential Dip", "Fast Kick Duck", "S-Curve"}, 4},
    {2, "Pump Depth", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0},
    {3, "Decay Length", "%", COBASS_PARAM_TYPE_FLOAT, 0.20f, 1.0f, 0.85f, 0.01f, false, {}, 0},
    {4, "Low-Band Split", "Hz", COBASS_PARAM_TYPE_FLOAT, 50.0f, 20000.0f, 20000.0f, 10.0f, true, {}, 0},
    {5, "Dry / Wet", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest PUMP_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.sidechain_pump",
    "Sidechain Envelope Pumper",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(PUMP_PARAMS) / sizeof(CobassParamDescriptor),
    PUMP_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class SidechainPumpProcessor {
public:
    explicit SidechainPumpProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : PUMP_PARAMS) params_[p.id] = p.defaultValue;
        reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        lowSplitL_.setSampleRate(sampleRate_);
        lowSplitR_.setSampleRate(sampleRate_);
        updateCrossover();
    }

    void setParam(uint32_t id, float value) {
        if (id < params_.size()) {
            params_[id] = value;
            if (id == 4) updateCrossover();
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

        const int rateChoice = static_cast<int>(params_[0]);
        const int curveChoice = static_cast<int>(params_[1]);
        const float depth = std::clamp(params_[2], 0.0f, 1.0f);
        const float decayLength = std::clamp(params_[3], 0.20f, 1.0f);
        const float splitFreq = params_[4];
        const float mix = std::clamp(params_[5], 0.0f, 1.0f);
        const float dry = 1.0f - mix;

        // Base 120 BPM cycle increment
        static constexpr float BEAT_MULTIPLIERS[6] = {0.25f, 0.5f, 1.0f, 2.0f, 4.0f, 1.5f};
        const float beatFreq = 2.0f * BEAT_MULTIPLIERS[std::min(5, rateChoice)]; // 2.0Hz @ 120BPM for 1/4 note
        const double phaseInc = beatFreq / sampleRate_;

        const bool isFullBand = (splitFreq >= 19500.0f);

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sL = inL[i];
            float sR = inR[i];

            // 1. Calculate Ducking Gain Envelope (0.0 at beat start -> 1.0 at beat end)
            float t = static_cast<float>(phase_) / decayLength;
            float duckGain = 1.0f;

            if (t < 1.0f) {
                switch (curveChoice) {
                    case 0: // Smooth linear ramp
                        duckGain = t;
                        break;
                    case 1: // Exponential Dip (Fast recovery)
                        duckGain = std::pow(t, 2.2f);
                        break;
                    case 2: // Fast Kick Duck (Instant dip, held open)
                        duckGain = 1.0f - std::exp(-t * 6.0f);
                        break;
                    case 3: // S-Curve (Smooth analog pump)
                        duckGain = 0.5f * (1.0f - std::cos(t * 3.14159265359f));
                        break;
                }
            } else {
                duckGain = 1.0f;
            }

            float activeGain = (1.0f - depth) + (duckGain * depth);

            phase_ += phaseInc;
            if (phase_ >= 1.0) phase_ -= 1.0;

            // 2. Frequency-Split Application (Low-Band vs Full-Band)
            float wetL = 0.0f, wetR = 0.0f;
            if (isFullBand) {
                wetL = sL * activeGain;
                wetR = sR * activeGain;
            } else {
                float lowL = lowSplitL_.process(sL);
                float lowR = lowSplitR_.process(sR);
                float highL = sL - lowL;
                float highR = sR - lowR;

                wetL = (lowL * activeGain) + highL;
                wetR = (lowR * activeGain) + highR;
            }

            outL[i] = sL * dry + wetL * mix;
            outR[i] = sR * dry + wetR * mix;
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
    void updateCrossover() {
        float freq = std::clamp(params_[4], 50.0f, sampleRate_ * 0.45f);
        lowSplitL_.setParameters(FilterType::LowPass, freq, 0.0f, 0.7071f);
        lowSplitR_.setParameters(FilterType::LowPass, freq, 0.0f, 0.7071f);
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    double phase_ = 0.0;
    BiquadFilter lowSplitL_;
    BiquadFilter lowSplitR_;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &PUMP_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new SidechainPumpProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<SidechainPumpProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<SidechainPumpProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<SidechainPumpProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
void cobass_plugin_note_off(CobassHandle, int32_t) {}
void cobass_plugin_all_notes_off(CobassHandle) {}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<SidechainPumpProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<SidechainPumpProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<SidechainPumpProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<SidechainPumpProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
