#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

#include "KickVoice.hpp"
#include "SnareVoice.hpp"
#include "ClapVoice.hpp"
#include "HiHatVoice.hpp"
#include "TomVoice.hpp"
#include "PercVoice.hpp"

static const CobassParamDescriptor COBALT_DRUM_PARAMS[] = {
    // --- MASTER & KIT SETTINGS [0..3] ---
    {0, "Kit Type", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"808 Analog", "909 Modern", "Electro FM", "Industrial"}, 4},
    {1, "Master Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 24.0f, 3.0f, 0.1f, false, {}, 0},
    {2, "Tone Tilt", "dB", COBASS_PARAM_TYPE_FLOAT, -6.0f, 6.0f, 0.0f, 0.1f, false, {}, 0},
    {3, "Master Out", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0},

    // --- VOICE 1: KICK [4..8] ---
    {4, "Kick Tune", "Hz", COBASS_PARAM_TYPE_FLOAT, 30.0f, 90.0f, 48.0f, 1.0f, false, {}, 0},
    {5, "Kick Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 50.0f, 1200.0f, 350.0f, 1.0f, false, {}, 0},
    {6, "Kick Drop", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.75f, 0.01f, false, {}, 0},
    {7, "Kick Click", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},
    {8, "Kick Sat", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.20f, 0.01f, false, {}, 0},

    // --- VOICE 2: SNARE [9..13] ---
    {9, "Snare Tune", "Hz", COBASS_PARAM_TYPE_FLOAT, 120.0f, 350.0f, 185.0f, 1.0f, false, {}, 0},
    {10, "Snare Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 20.0f, 400.0f, 140.0f, 1.0f, false, {}, 0},
    {11, "Snare Snappy", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.65f, 0.01f, false, {}, 0},
    {12, "Snare Wire Dec", "ms", COBASS_PARAM_TYPE_FLOAT, 30.0f, 600.0f, 220.0f, 1.0f, false, {}, 0},
    {13, "Snare Filter", "Hz", COBASS_PARAM_TYPE_FLOAT, 1000.0f, 10000.0f, 4500.0f, 10.0f, true, {}, 0},

    // --- VOICE 3: CLAP [14..17] ---
    {14, "Clap Tone", "Hz", COBASS_PARAM_TYPE_FLOAT, 800.0f, 4000.0f, 1800.0f, 10.0f, true, {}, 0},
    {15, "Clap Spread", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 30.0f, 14.0f, 1.0f, false, {}, 0},
    {16, "Clap Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 50.0f, 600.0f, 240.0f, 1.0f, false, {}, 0},
    {17, "Clap Room", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},

    // --- VOICE 4 & 5: HI-HATS [18..22] ---
    {18, "Hat Tone", "Hz", COBASS_PARAM_TYPE_FLOAT, 4000.0f, 14000.0f, 8500.0f, 10.0f, true, {}, 0},
    {19, "Cl. Hat Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 10.0f, 150.0f, 45.0f, 1.0f, false, {}, 0},
    {20, "Op. Hat Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 100.0f, 1500.0f, 450.0f, 1.0f, false, {}, 0},
    {21, "Hat Choke", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 1.0f, 1.0f, false, {}, 0},
    {22, "Hat Sizzle", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.40f, 0.01f, false, {}, 0},

    // --- VOICE 6: TOM / PERC [23..27] ---
    {23, "Tom Tune", "Hz", COBASS_PARAM_TYPE_FLOAT, 60.0f, 400.0f, 120.0f, 1.0f, false, {}, 0},
    {24, "Tom Sweep", "st", COBASS_PARAM_TYPE_FLOAT, -24.0f, 24.0f, -7.0f, 1.0f, false, {}, 0},
    {25, "Tom Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 50.0f, 800.0f, 260.0f, 1.0f, false, {}, 0},
    {26, "Tom FM Depth", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.10f, 0.01f, false, {}, 0},
    {27, "Tom Impact", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.15f, 0.01f, false, {}, 0},

    // --- VOICE 7 & 8: RIM & COWBELL [28..31] ---
    {28, "Rim Pitch", "Hz", COBASS_PARAM_TYPE_FLOAT, 800.0f, 3000.0f, 1750.0f, 10.0f, true, {}, 0},
    {29, "Rim Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 80.0f, 25.0f, 1.0f, false, {}, 0},
    {30, "Cowbell Tune", "Hz", COBASS_PARAM_TYPE_FLOAT, 300.0f, 1000.0f, 540.0f, 1.0f, false, {}, 0},
    {31, "Cowbell Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 30.0f, 500.0f, 180.0f, 1.0f, false, {}, 0}
};

static const CobassPluginManifest COBALT_DRUMS_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.cobalt_drums",
    "Cobalt Drum Synth",
    "Maxica Audio",
    "1.0.0",
    COBASS_PLUGIN_TYPE_SYNTH,
    sizeof(COBALT_DRUM_PARAMS) / sizeof(CobassParamDescriptor),
    COBALT_DRUM_PARAMS,
    true,  // supportsMidi
    false  // supportsSidechain
};

class CobaltDrumsProcessor {
public:
    explicit CobaltDrumsProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : COBALT_DRUM_PARAMS) {
            params_[p.id] = p.defaultValue;
        }
        reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        kick_.reset(sampleRate_);
        snare_.reset(sampleRate_);
        clap_.reset(sampleRate_);
        hihat_.reset(sampleRate_);
        tom_.reset(sampleRate_);
        perc_.reset(sampleRate_);
        updateAllVoiceParameters();
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) {
            params_[id] = val;
            updateVoiceParameters(id);
        }
    }

    float getParam(uint32_t id) const {
        return id < params_.size() ? params_[id] : 0.0f;
    }

    void noteOn(int32_t note, float velocity) {
        switch (note) {
            case 36: // C1: Bass Drum / Kick
            case 35:
                kick_.trigger(velocity);
                break;
            case 38: // D1: Snare
            case 40:
                snare_.trigger(velocity);
                break;
            case 39: // D#1: Hand Clap
                clap_.trigger(velocity);
                break;
            case 42: // F#1: Closed Hi-Hat (Triggers Choke)
            case 44:
                hihat_.triggerClosed(velocity);
                break;
            case 46: // A#1: Open Hi-Hat
                hihat_.triggerOpen(velocity);
                break;
            case 41: // Low Tom
            case 45: // Mid Tom
            case 48: // High Tom
                tom_.trigger(note, velocity);
                break;
            case 37: // Rimshot
                perc_.triggerRim(velocity);
                break;
            case 56: // Cowbell
                perc_.triggerCowbell(velocity);
                break;
            default:
                if (note < 38) kick_.trigger(velocity);
                else if (note < 41) snare_.trigger(velocity);
                else if (note == 43 || note == 47) tom_.trigger(note, velocity);
                else hihat_.triggerClosed(velocity);
                break;
        }
    }

    void noteOff(int32_t /*note*/) {}

    void allNotesOff() {
        kick_.stop();
        snare_.stop();
        clap_.stop();
        hihat_.stop();
        tom_.stop();
        perc_.stop();
    }

    void process(const float** /*inputs*/, float** outputs, uint32_t /*channels*/, uint32_t numFrames) {
        float* outL = outputs[0];
        float* outR = outputs[1];
        std::fill_n(outL, numFrames, 0.0f);
        std::fill_n(outR, numFrames, 0.0f);

        const float masterDrive = std::pow(10.0f, params_[1] / 20.0f);
        const float masterGain  = std::pow(10.0f, params_[3] / 20.0f) * 0.90f;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sKick  = kick_.render();
            float sSnare = snare_.render();
            float sClap  = clap_.render();
            float sHatL  = 0.0f, sHatR = 0.0f;
            hihat_.renderStereo(sHatL, sHatR);
            float sTomL  = 0.0f, sTomR = 0.0f;
            tom_.renderStereo(sTomL, sTomR);
            float sPerc  = perc_.render();

            float mixL = sKick + sSnare + sClap * 0.9f + sHatL + sTomL + sPerc * 0.85f;
            float mixR = sKick + sSnare + sClap * 1.1f + sHatR + sTomR + sPerc * 0.85f;

            if (masterDrive > 1.01f) {
                mixL = std::tanh(mixL * masterDrive);
                mixR = std::tanh(mixR * masterDrive);
            }

            outL[i] = mixL * masterGain;
            outR[i] = mixR * masterGain;
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
    void updateVoiceParameters(uint32_t id) {
        if (id >= 4 && id <= 8)   kick_.setParameters(params_[4], params_[5], params_[6], params_[7], params_[8]);
        if (id >= 9 && id <= 13)  snare_.setParameters(params_[9], params_[10], params_[11], params_[12], params_[13]);
        if (id >= 14 && id <= 17) clap_.setParameters(params_[14], params_[15], params_[16], params_[17]);
        if (id >= 18 && id <= 22) hihat_.setParameters(params_[18], params_[19], params_[20], params_[21] > 0.5f, params_[22]);
        if (id >= 23 && id <= 27) tom_.setParameters(params_[23], params_[24], params_[25], params_[26], params_[27]);
        if (id >= 28 && id <= 31) perc_.setParameters(params_[28], params_[29], params_[30], params_[31]);
    }

    void updateAllVoiceParameters() {
        kick_.setParameters(params_[4], params_[5], params_[6], params_[7], params_[8]);
        snare_.setParameters(params_[9], params_[10], params_[11], params_[12], params_[13]);
        clap_.setParameters(params_[14], params_[15], params_[16], params_[17]);
        hihat_.setParameters(params_[18], params_[19], params_[20], params_[21] > 0.5f, params_[22]);
        tom_.setParameters(params_[23], params_[24], params_[25], params_[26], params_[27]);
        perc_.setParameters(params_[28], params_[29], params_[30], params_[31]);
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 32> params_{};

    KickVoice  kick_;
    SnareVoice snare_;
    ClapVoice  clap_;
    HiHatVoice hihat_;
    TomVoice   tom_;
    PercVoice  perc_;
};

extern "C" {

const CobassPluginManifest* cobass_plugin_get_manifest(void) {
    return &COBALT_DRUMS_MANIFEST;
}

CobassHandle cobass_plugin_create_instance(float sampleRate) {
    return new CobaltDrumsProcessor(sampleRate);
}

void cobass_plugin_destroy_instance(CobassHandle handle) {
    delete static_cast<CobaltDrumsProcessor*>(handle);
}

void cobass_plugin_reset(CobassHandle handle, float sampleRate) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->reset(sampleRate);
}

void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t channels, uint32_t numFrames) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->process(inputs, outputs, channels, numFrames);
}

void cobass_plugin_note_on(CobassHandle handle, int32_t note, float velocity) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->noteOn(note, velocity);
}

void cobass_plugin_note_off(CobassHandle handle, int32_t note) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->noteOff(note);
}

void cobass_plugin_all_notes_off(CobassHandle handle) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->allNotesOff();
}

void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
    if (handle) static_cast<CobaltDrumsProcessor*>(handle)->setParam(paramId, value);
}

float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
    return handle ? static_cast<CobaltDrumsProcessor*>(handle)->getParam(paramId) : 0.0f;
}

uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
    return handle ? static_cast<CobaltDrumsProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
}

bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
    return handle && static_cast<CobaltDrumsProcessor*>(handle)->setStateJson(jsonBuffer);
}

} // extern "C"
