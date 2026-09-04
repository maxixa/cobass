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
#include "BiquadFilter.hpp"

static const CobassParamDescriptor COBALT_DRUM_PARAMS[] = {
    // --- MASTER & KIT SETTINGS [0..3] ---
    {0, "Kit Type", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"808 Analog", "909 Modern", "Electro FM", "Industrial"}, 4},
    {1, "Master Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 24.0f, 0.0f, 0.1f, false, {}, 0},
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
        tiltFilterL_.setSampleRate(sampleRate_);
        tiltFilterR_.setSampleRate(sampleRate_);
        updateToneTilt();
        updateAllVoiceParameters();
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) {
            params_[id] = val;
            if (id == 0) {
                // BUG-3: Kit Type change updates voice characteristics
                updateAllVoiceParameters();
            } else if (id == 2) {
                // BUG-2: Tone tilt updates filter coefficients
                updateToneTilt();
            } else {
                updateAllVoiceParameters();
            }
        }
    }

    float getParam(uint32_t id) const {
        return id < params_.size() ? params_[id] : 0.0f;
    }

    void noteOn(int32_t note, float velocity) {
        switch (note) {
            case 35: // Acoustic Bass Drum
            case 36: // C1: Bass Drum / Kick
                kick_.trigger(velocity);
                break;
            case 38: // D1: Acoustic Snare
            case 40: // Electric Snare
                snare_.trigger(velocity);
                break;
            case 39: // D#1: Hand Clap
                clap_.trigger(velocity);
                break;
            case 42: // F#1: Closed Hi-Hat (Triggers Choke)
            case 44: // Pedal Hi-Hat
                hihat_.triggerClosed(velocity);
                break;
            case 46: // A#1: Open Hi-Hat
                hihat_.triggerOpen(velocity);
                break;
            case 41: // Low Floor Tom (F1)
            case 43: // High Floor Tom (G1)
            case 45: // Low-Mid Tom (A1)
            case 47: // High-Mid Tom (B1)
            case 48: // High Tom (C2)
            case 50: // High Timbale (D2)
                tom_.trigger(note, velocity);
                break;
            case 37: // Side Stick / Rimshot
                perc_.triggerRim(velocity);
                break;
            case 56: // Cowbell
                perc_.triggerCowbell(velocity);
                break;
            default:
                if (note < 38) kick_.trigger(velocity);
                else if (note < 41) snare_.trigger(velocity);
                else if (note <= 50) tom_.trigger(note, velocity);
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

        // BUG-3: Kit Type Character Multipliers
        const int kitType = std::clamp(static_cast<int>(params_[0]), 0, 3);
        const float kitDriveBoost = (kitType == 3) ? 1.25f : ((kitType == 1) ? 1.10f : 1.0f);
        const float masterDrive = std::pow(10.0f, (params_[1] * kitDriveBoost) / 20.0f);
        // Master bus gain: 0.32f calibration ensures multi-voice hits peak cleanly around -6 dBFS
        const float masterGain  = std::pow(10.0f, params_[3] / 20.0f) * 0.32f;

        // Balanced individual voice gains
        const float kickGain = 0.72f;
        const float snareGain = 0.62f;
        const float clapGainL = ((kitType == 0) ? 0.48f : 0.45f);
        const float clapGainR = ((kitType == 0) ? 0.52f : 0.55f);
        const float hatGain = 0.42f;
        const float tomGain = 0.52f;
        const float percGain = ((kitType == 2) ? 0.58f : 0.46f);

        for (uint32_t i = 0; i < numFrames; ++i) {
            float sKick  = kick_.render() * kickGain;
            float sSnare = snare_.render() * snareGain;
            float sClap  = clap_.render();
            float sHatL  = 0.0f, sHatR = 0.0f;
            hihat_.renderStereo(sHatL, sHatR);
            float sTomL  = 0.0f, sTomR = 0.0f;
            tom_.renderStereo(sTomL, sTomR);
            float sPerc  = perc_.render() * percGain;

            float mixL = sKick + sSnare + (sClap * clapGainL) + (sHatL * hatGain) + (sTomL * tomGain) + sPerc;
            float mixR = sKick + sSnare + (sClap * clapGainR) + (sHatR * hatGain) + (sTomR * tomGain) + sPerc;

            // Master Tone Tilt Filtering
            mixL = tiltFilterL_.process(mixL);
            mixR = tiltFilterR_.process(mixR);

            // Soft-knee bus saturation (only when drive > 0 dB)
            if (params_[1] > 0.05f) {
                mixL = std::tanh(mixL * masterDrive) / std::sqrt(masterDrive);
                mixR = std::tanh(mixR * masterDrive) / std::sqrt(masterDrive);
            } else {
                mixL = std::tanh(mixL);
                mixR = std::tanh(mixR);
            }

            outL[i] = mixL * masterGain;
            outR[i] = mixR * masterGain;
        }
    }

    uint32_t getStateJson(char* outBuffer, uint32_t maxLen) const {
        std::string json = "{";
        char numBuf[32];
        for (size_t i = 0; i < params_.size(); ++i) {
            std::snprintf(numBuf, sizeof(numBuf), "%.6g", static_cast<double>(params_[i]));
            json += "\"" + std::to_string(i) + "\":" + numBuf;
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
    void updateToneTilt() noexcept {
        // BUG-2 FIX: Master LowShelf tilt filter centered at 1000 Hz
        const float toneTiltDb = std::clamp(params_[2], -6.0f, 6.0f);
        tiltFilterL_.setParameters(FilterType::LowShelf, 1000.0f, -toneTiltDb, 0.707f);
        tiltFilterR_.setParameters(FilterType::LowShelf, 1000.0f, -toneTiltDb, 0.707f);
    }

    void updateVoiceParameters(uint32_t id) {
        const int kit = std::clamp(static_cast<int>(params_[0]), 0, 3);
        const float kitDecayMult = (kit == 0) ? 1.25f : ((kit == 1) ? 0.90f : 1.0f);
        const float kitSnappyMult = (kit == 1) ? 1.20f : ((kit == 3) ? 1.30f : 1.0f);

        if (id >= 4 && id <= 8)   kick_.setParameters(params_[4], params_[5] * kitDecayMult, params_[6], params_[7], params_[8]);
        if (id >= 9 && id <= 13)  snare_.setParameters(params_[9], params_[10], std::clamp(params_[11] * kitSnappyMult, 0.0f, 1.0f), params_[12] * kitDecayMult, params_[13]);
        if (id >= 14 && id <= 17) clap_.setParameters(params_[14], params_[15], params_[16] * kitDecayMult, params_[17]);
        if (id >= 18 && id <= 22) hihat_.setParameters(params_[18], params_[19], params_[20] * kitDecayMult, params_[21] > 0.5f, params_[22]);
        if (id >= 23 && id <= 27) tom_.setParameters(params_[23], params_[24], params_[25] * kitDecayMult, params_[26] * (kit == 2 ? 1.5f : 1.0f), params_[27]);
        if (id >= 28 && id <= 31) perc_.setParameters(params_[28], params_[29], params_[30], params_[31] * kitDecayMult);
    }

    void updateAllVoiceParameters() {
        const int kit = std::clamp(static_cast<int>(params_[0]), 0, 3);
        // Kit Mapping: 0 = 808 Analog, 1 = 909 Modern, 2 = Electro FM, 3 = Industrial
        const int kickModel  = (kit == 0) ? 0 : ((kit == 1) ? 1 : ((kit == 2) ? 2 : 1));
        const int snareModel = (kit == 0) ? 0 : ((kit == 1) ? 2 : ((kit == 2) ? 1 : 3));
        const int hatModel   = (kit == 0) ? 0 : ((kit == 1) ? 0 : ((kit == 2) ? 1 : 2));
        const int clapModel  = (kit == 0) ? 0 : ((kit == 1) ? 1 : ((kit == 2) ? 3 : 2));
        const int tomModel   = (kit == 0) ? 0 : ((kit == 1) ? 0 : ((kit == 2) ? 2 : 1));
        const int rimModel   = (kit == 0) ? 0 : ((kit == 1) ? 1 : ((kit == 2) ? 3 : 2));
        const int cowModel   = (kit == 0) ? 0 : ((kit == 1) ? 0 : ((kit == 2) ? 1 : 2));

        const float kitDecayMult = (kit == 0) ? 1.30f : ((kit == 1) ? 0.90f : 1.0f);
        const float kitSnappyMult = (kit == 1) ? 1.25f : ((kit == 3) ? 1.35f : 1.0f);

        kick_.setParameters(params_[4], params_[5] * kitDecayMult, params_[6], params_[7], params_[8], kickModel, kit == 2 ? 0.45f : 0.15f);
        snare_.setParameters(params_[9], params_[10], std::clamp(params_[11] * kitSnappyMult, 0.0f, 1.0f), params_[12] * kitDecayMult, params_[13], snareModel, kit == 2 ? 2.5f : 1.0f);
        clap_.setParameters(params_[14], params_[15], params_[16] * kitDecayMult, params_[17], clapModel, 0.6f);
        hihat_.setParameters(params_[18], params_[19], params_[20] * kitDecayMult, params_[21] > 0.5f, params_[22], hatModel, kit == 2 ? 3.0f : 0.0f);
        tom_.setParameters(params_[23], params_[24], params_[25] * kitDecayMult, params_[26] * (kit == 2 ? 1.8f : 1.0f), params_[27], tomModel, 0.5f);
        perc_.setParameters(params_[28], params_[29], params_[30], params_[31] * kitDecayMult, rimModel, cowModel, kit == 2 ? 0.6f : 0.0f);
    }

    float sampleRate_ = 48000.0f;
    std::array<float, 32> params_{};

    KickVoice  kick_;
    SnareVoice snare_;
    ClapVoice  clap_;
    HiHatVoice hihat_;
    TomVoice   tom_;
    PercVoice  perc_;

    BiquadFilter tiltFilterL_;
    BiquadFilter tiltFilterR_;
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
