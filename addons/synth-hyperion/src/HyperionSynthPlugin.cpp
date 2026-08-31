#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor HYPERION_PARAMS[] = {
    {0, "Wave Shape", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Morph Saw", "Pulse 50", "Triangle Sine", "Dual Sync"}, 4},
    {1, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 3500.0f, 1.0f, true, {}, 0},
    {2, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 12.0f, 1.5f, 0.05f, false, {}, 0},
    {3, "Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 10.0f, 1.0f, false, {}, 0},
    {4, "Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 120.0f, 1.0f, false, {}, 0},
    {5, "Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.70f, 0.01f, false, {}, 0},
    {6, "Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 250.0f, 1.0f, false, {}, 0},
    {7, "Unison Detune", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.15f, 0.01f, false, {}, 0},
    {8, "Master Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest HYPERION_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.hyperion",
    "Hyperion Wavetable Synth",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_SYNTH,
    sizeof(HYPERION_PARAMS) / sizeof(CobassParamDescriptor),
    HYPERION_PARAMS,
    true,  // supportsMidi
    false  // supportsSidechain
};

class HyperionProcessor {
private:
    struct Voice {
        int32_t note = -1;
        float velocity = 0.0f;
        double phase = 0.0;
        double phase2 = 0.0;
        float freq = 440.0f;
        bool active = false;

        // ADSR State
        enum class EnvState { Idle, Attack, Decay, Sustain, Release } envState = EnvState::Idle;
        float envValue = 0.0f;

        // SVF Filter State
        float s1L = 0.0f, s2L = 0.0f;
        float s1R = 0.0f, s2R = 0.0f;

        void trigger(int32_t midiNote, float vel) {
            note = midiNote;
            velocity = vel;
            phase = 0.0;
            phase2 = 0.0;
            freq = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
            active = true;
            envState = EnvState::Attack;
            s1L = s2L = s1R = s2R = 0.0f;
        }

        void release() {
            if (active && envState != EnvState::Idle) {
                envState = EnvState::Release;
            }
        }

        void stop() {
            active = false;
            note = -1;
            envState = EnvState::Idle;
            envValue = 0.0f;
        }
    };

public:
    explicit HyperionProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : HYPERION_PARAMS) {
            params_[p.id] = p.defaultValue;
        }
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (auto& v : voices_) v.stop();
    }

    void setParam(uint32_t id, float value) {
        if (id < params_.size()) params_[id] = value;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void noteOn(int32_t note, float velocity) {
        Voice* target = nullptr;
        for (auto& v : voices_) {
            if (!v.active) {
                target = &v;
                break;
            }
        }
        if (!target) target = &voices_[0];
        target->trigger(note, velocity);
    }

    void noteOff(int32_t note) {
        for (auto& v : voices_) {
            if (v.active && v.note == note) v.release();
        }
    }

    void allNotesOff() {
        for (auto& v : voices_) v.stop();
    }

    void process(const float** /*inputs*/, float** outputs, uint32_t /*channels*/, uint32_t numFrames) {
        float* outL = outputs[0];
        float* outR = outputs[1];
        std::fill_n(outL, numFrames, 0.0f);
        std::fill_n(outR, numFrames, 0.0f);

        const int shape = static_cast<int>(params_[0]);
        const float cutoff = std::clamp(params_[1], 20.0f, sampleRate_ * 0.48f);
        const float resonance = std::clamp(params_[2], 0.5f, 12.0f);
        const float attackRate = 1.0f / (std::max(0.001f, params_[3] * 0.001f) * sampleRate_);
        const float decayRate = 1.0f / (std::max(0.001f, params_[4] * 0.001f) * sampleRate_);
        const float sustainLevel = std::clamp(params_[5], 0.0f, 1.0f);
        const float releaseRate = 1.0f / (std::max(0.001f, params_[6] * 0.001f) * sampleRate_);
        const float detuneFactor = params_[7] * 0.015f;
        const float masterGain = std::pow(10.0f, params_[8] / 20.0f) * 0.25f;

        // Precompute SVF Filter Coefficients
        const float g = std::tan(3.14159265f * cutoff / sampleRate_);
        const float k = 1.0f / resonance;
        const float a1 = 1.0f / (1.0f + g * (g + k));
        const float a2 = g * a1;
        const float a3 = g * a2;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sumL = 0.0f;
            float sumR = 0.0f;

            for (auto& v : voices_) {
                if (!v.active) continue;

                // Advance Envelope
                switch (v.envState) {
                    case Voice::EnvState::Attack:
                        v.envValue += attackRate;
                        if (v.envValue >= 1.0f) { v.envValue = 1.0f; v.envState = Voice::EnvState::Decay; }
                        break;
                    case Voice::EnvState::Decay:
                        v.envValue -= decayRate * (1.0f - sustainLevel);
                        if (v.envValue <= sustainLevel) { v.envValue = sustainLevel; v.envState = Voice::EnvState::Sustain; }
                        break;
                    case Voice::EnvState::Sustain:
                        v.envValue = sustainLevel;
                        break;
                    case Voice::EnvState::Release:
                        v.envValue -= releaseRate;
                        if (v.envValue <= 0.0f) { v.envValue = 0.0f; v.stop(); continue; }
                        break;
                    case Voice::EnvState::Idle:
                        continue;
                }

                // Generate Morphable Wavetable Sample (Dual Detuned Osc)
                float sOsc1 = generateOsc(v.phase, shape);
                float sOsc2 = generateOsc(v.phase2, shape);

                v.phase += v.freq / sampleRate_;
                if (v.phase >= 1.0) v.phase -= 1.0;

                v.phase2 += (v.freq * (1.0f + detuneFactor)) / sampleRate_;
                if (v.phase2 >= 1.0) v.phase2 -= 1.0;

                float amp = v.envValue * v.velocity;
                float rawL = sOsc1 * amp;
                float rawR = sOsc2 * amp;

                // Apply Stereo SVF Lowpass Filter
                float v3L = rawL - v.s2L;
                float v1L = a1 * v.s1L + a2 * v3L;
                float v2L = v.s2L + a2 * v.s1L + a3 * v3L;
                v.s1L = 2.0f * v1L - v.s1L;
                v.s2L = 2.0f * v2L - v.s2L;

                float v3R = rawR - v.s2R;
                float v1R = a1 * v.s1R + a2 * v3R;
                float v2R = v.s2R + a2 * v.s1R + a3 * v3R;
                v.s1R = 2.0f * v1R - v.s1R;
                v.s2R = 2.0f * v2R - v.s2R;

                sumL += v2L;
                sumR += v2R;
            }

            outL[i] = sumL * masterGain;
            outR[i] = sumR * masterGain;
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
        // Basic JSON key-value parser for numeric parameters
        for (size_t i = 0; i < params_.size(); ++i) {
            std::string key = "\"" + std::to_string(i) + "\":";
            const char* pos = std::strstr(json, key.c_str());
            if (pos) {
                params_[i] = std::strtof(pos + key.size(), nullptr);
            }
        }
        return true;
    }

private:
    static float generateOsc(double ph, int shape) {
        switch (shape) {
            case 0: return static_cast<float>(2.0 * (ph - std::floor(ph + 0.5))); // Morph Saw
            case 1: return (ph < 0.5) ? 1.0f : -1.0f;                             // Pulse 50
            case 2: return static_cast<float>(std::sin(ph * 6.28318530718));       // Sine
            case 3: return static_cast<float>(4.0 * std::fabs(ph - 0.5) - 1.0);   // Triangle
            default: return 0.0f;
        }
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 9> params_{};
    std::array<Voice, 16> voices_{};
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &HYPERION_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new HyperionProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<HyperionProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<HyperionProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<HyperionProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle handle, int32_t note, float velocity) {
    if (handle) static_cast<HyperionProcessor*>(handle)->noteOn(note, velocity);
}

void cobass_plugin_note_off(CobassHandle handle, int32_t note) {
    if (handle) static_cast<HyperionProcessor*>(handle)->noteOff(note);
}

void cobass_plugin_all_notes_off(CobassHandle handle) {
    if (handle) static_cast<HyperionProcessor*>(handle)->allNotesOff();
}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<HyperionProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<HyperionProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<HyperionProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<HyperionProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
