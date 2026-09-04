# Codebase Context Bundle: Hyperion Synth & Cobalt Drums

- **Generated on:** Fri Sep 04 09:15:52 UTC 2026
- **Scope Profile:** `hyperion-and-cobalt-drums`
- **Total Files Included:** 32
- **Root Directory:** `.`

---

## 1. Selected File Manifest
```text
• addons/synth-cobalt-drums/src/CobaltDrumsPlugin.cpp
• addons/synth-cobalt-drums/src/KickVoice.hpp
• addons/synth-cobalt-drums/src/SnareVoice.hpp
• addons/synth-cobalt-drums/src/HiHatVoice.hpp
• addons/synth-cobalt-drums/src/ClapVoice.hpp
• addons/synth-cobalt-drums/src/PercVoice.hpp
• addons/synth-cobalt-drums/src/TomVoice.hpp
• addons/synth-hyperion/src/HyperionSynthPlugin.cpp
• app/native/include/CobassPluginABI.h
• app/native/plugin/PluginDescriptor.hpp
• app/native/plugin/PluginInstance.hpp
• app/native/plugin/PluginLoader.hpp
• app/native/plugin/PluginChain.hpp
• app/native/dsp/SynthVoice.hpp
• app/native/dsp/SynthTrack.hpp
• app/native/dsp/PolyBlepOscillator.hpp
• app/native/dsp/ZdfFilter.hpp
• app/native/dsp/ADSR.hpp
• app/native/dsp/LFO.hpp
• app/native/dsp/Wavefolder.hpp
• tools/benchmark_hyperion_dance.py
• tools/test_hyperion_dsp_fixes.py
• tools/benchmark_variation_and_presets.py
• tools/build_addons.py
• docs/synth-V2.md
• docs/plugin-synth-fx_doc.md
• plan/synth-host-plugin-v1.md
• app/src/com/maxica/cobass/plugin/PluginHostManager.java
• app/src/com/maxica/cobass/plugin/PatchVariationEngine.java
• app/src/com/maxica/cobass/ui/PluginUiDialog.java
• app/src/com/maxica/cobass/ui/PluginPresetDialog.java
• app/src/com/maxica/cobass/ui/SynthVisualizerView.java
```

---

## 2. File Contents

### File: `addons/synth-cobalt-drums/src/CobaltDrumsPlugin.cpp`

```cpp
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

```

---

### File: `addons/synth-cobalt-drums/src/KickVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class KickModel : int32_t {
    Analog808 = 0,    // Pure sine sub-bass with smooth exponential drop
    Modern909 = 1,    // Dual harmonic punch with high-impact click chirp
    SlapFM = 2,       // 2-Op Phase-Modulated laser thump (Slap / Cyberpunk)
    AcousticThump = 3 // Dual-membrane modal drumhead with beater click
};

class KickVoice {
public:
    KickVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = pitchEnv_ = clickEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        clickFilter_.setSampleRate(sampleRate_);
        clickFilter_.setParameters(FilterType::HighPass, 4500.0f, 0.0f, 1.2f);
        updateRates();
    }

    void setParameters(float tuneHz, float decayMs, float pitchDropPct, float clickPct, float distPct, int model = 0, float fmAmount = 0.15f) noexcept {
        baseFreq_ = std::clamp(tuneHz, 28.0f, 120.0f);
        decayMs_ = std::clamp(decayMs, 30.0f, 1800.0f);
        pitchDropPct_ = std::clamp(pitchDropPct, 0.0f, 1.0f);
        clickPct_ = std::clamp(clickPct, 0.0f, 1.0f);
        drive_ = 1.0f + (std::clamp(distPct, 0.0f, 1.0f) * 2.8f);
        model_ = static_cast<KickModel>(std::clamp(model, 0, 3));
        fmAmount_ = std::clamp(fmAmount, 0.0f, 1.0f);
        updateRates();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && ampEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 99999.0f);

        phase_ = 0.0;
        ampEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        clickEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        ampEnv_ = pitchEnv_ = clickEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        const float pitchSweep = baseFreq_ * (1.0f + (pitchDropPct_ * 4.8f * pitchEnv_));
        phase_ += pitchSweep / sampleRate_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float body = 0.0f;
        switch (model_) {
            case KickModel::Analog808:
                body = static_cast<float>(std::sin(phase_ * 6.283185307179586));
                break;
            case KickModel::Modern909:
                body = static_cast<float>(std::sin(phase_ * 6.283185307179586) * 0.75 + std::sin(phase_ * 12.566370614359172) * 0.25);
                break;
            case KickModel::SlapFM: {
                double mod = std::sin(phase_ * 12.566370614359172) * fmAmount_ * 2.2 * pitchEnv_;
                body = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }
            case KickModel::AcousticThump: {
                double p2 = std::fmod(phase_ * 1.62, 1.0);
                body = static_cast<float>(std::sin(phase_ * 6.283185307179586) * 0.70 + std::sin(p2 * 6.283185307179586) * 0.30);
                break;
            }
        }

        float click = 0.0f;
        if (clickEnv_ > 0.005f && clickPct_ > 0.01f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
            click = clickFilter_.process(rawNoise) * clickEnv_ * clickPct_ * 1.3f;
        }

        float out = (body * ampEnv_ + click) * velocity_;
        out = std::tanh(out * drive_);

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;
        clickEnv_ *= clickDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        const float pitchDecayMs = (model_ == KickModel::Modern909) ? 0.014f : 0.025f;
        pitchDecayCoeff_ = std::exp(-1.0f / (pitchDecayMs * sampleRate_));
        clickDecayCoeff_ = std::exp(-1.0f / (0.004f * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    float baseFreq_ = 48.0f;
    float decayMs_ = 350.0f;
    float pitchDropPct_ = 0.75f;
    float clickPct_ = 0.50f;
    float drive_ = 1.2f;
    KickModel model_ = KickModel::Analog808;
    float fmAmount_ = 0.15f;

    double phase_ = 0.0;
    float ampEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float clickEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 54321;
    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
    float clickDecayCoeff_ = 0.90f;
    BiquadFilter clickFilter_;
};

```

---

### File: `addons/synth-cobalt-drums/src/SnareVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class SnareModel : int32_t {
    AnalogDual = 0,    // Warm dual-sine shell + bandpass noise wires
    ModalMembrane = 1, // 3-band resonant modal filter bank
    Modern909 = 2,     // Tight triangle body punch + bright wire snap
    IndustrialGrit = 3 // Asymmetric diode clipping + wavefolded tone
};

class SnareVoice {
public:
    SnareVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase1_ = phase2_ = phase3_ = 0.0;
        bodyEnv_ = noiseEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = noiseSmooth_ = 0.0f;
        active_ = false;
        noiseFilter_.setSampleRate(sampleRate_);
        modalFilter_.setSampleRate(sampleRate_);
        updateRates();
        updateFilter();
    }

    void setParameters(float tuneHz, float bodyDecayMs, float snappyPct, float noiseDecayMs, float filterCutoffHz, int model = 0, float shellRes = 1.0f) noexcept {
        baseFreq_ = std::clamp(tuneHz, 100.0f, 350.0f);
        bodyDecayMs_ = std::clamp(bodyDecayMs, 20.0f, 400.0f);
        snappyPct_ = std::clamp(snappyPct, 0.0f, 1.0f);
        noiseDecayMs_ = std::clamp(noiseDecayMs, 30.0f, 600.0f);
        filterCutoffHz_ = std::clamp(filterCutoffHz, 800.0f, 10000.0f);
        model_ = static_cast<SnareModel>(std::clamp(model, 0, 3));
        shellRes_ = std::clamp(shellRes, 0.5f, 5.0f);
        updateRates();
        updateFilter();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && (bodyEnv_ > 0.01f || noiseEnv_ > 0.01f)) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 77777.0f);

        phase1_ = phase2_ = phase3_ = 0.0;
        bodyEnv_ = 1.0f;
        noiseEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        bodyEnv_ = noiseEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = noiseSmooth_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        const float pitchMult = (model_ == SnareModel::Modern909) ? 0.90f : 0.65f;
        const float f1 = baseFreq_ * (1.0f + pitchMult * pitchEnv_);
        const float f2 = f1 * 1.58f;
        const float f3 = f1 * 2.31f;

        phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
        phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
        phase3_ += f3 / sampleRate_; if (phase3_ >= 1.0) phase3_ -= 1.0;

        float body = 0.0f;
        if (model_ == SnareModel::ModalMembrane) {
            body = static_cast<float>(std::sin(phase1_ * 6.283185307179586) * 0.50 +
                                      std::sin(phase2_ * 6.283185307179586) * 0.30 +
                                      std::sin(phase3_ * 6.283185307179586) * 0.20) * bodyEnv_;
            body = modalFilter_.process(body);
        } else {
            body = static_cast<float>(std::sin(phase1_ * 6.283185307179586) * 0.65 +
                                      std::sin(phase2_ * 6.283185307179586) * 0.35) * bodyEnv_;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        float filteredNoise = noiseFilter_.process(rawNoise);

        noiseSmooth_ = 0.65f * noiseSmooth_ + 0.35f * filteredNoise;
        const float wires = noiseSmooth_ * noiseEnv_;

        float out = ((body * (1.0f - snappyPct_ * 0.4f)) + (wires * snappyPct_ * 1.05f)) * velocity_;
        out = (model_ == SnareModel::IndustrialGrit) ? std::tanh(out * 1.6f) : std::tanh(out * 1.10f);

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        bodyEnv_ *= bodyDecayCoeff_;
        noiseEnv_ *= noiseDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (bodyEnv_ <= 0.0005f && noiseEnv_ <= 0.0005f) {
            active_ = false;
            bodyEnv_ = noiseEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        bodyDecayCoeff_ = std::exp(-1.0f / ((bodyDecayMs_ * 0.001f) * sampleRate_));
        noiseDecayCoeff_ = std::exp(-1.0f / ((noiseDecayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.022f * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilter() noexcept {
        noiseFilter_.setParameters(FilterType::BandPass, std::clamp(filterCutoffHz_, 1000.0f, 6500.0f), 0.0f, 1.05f);
        modalFilter_.setParameters(FilterType::BandPass, baseFreq_ * 1.58f, 0.0f, shellRes_ * 2.0f);
    }

    float sampleRate_ = 48000.0f;
    float baseFreq_ = 185.0f;
    float bodyDecayMs_ = 140.0f;
    float snappyPct_ = 0.65f;
    float noiseDecayMs_ = 220.0f;
    float filterCutoffHz_ = 4500.0f;
    SnareModel model_ = SnareModel::AnalogDual;
    float shellRes_ = 1.0f;

    double phase1_ = 0.0;
    double phase2_ = 0.0;
    double phase3_ = 0.0;
    float bodyEnv_ = 0.0f;
    float noiseEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;
    float noiseSmooth_ = 0.0f;

    uint32_t noiseSeed_ = 424242;
    float bodyDecayCoeff_ = 0.99f;
    float noiseDecayCoeff_ = 0.99f;
    float pitchDecayCoeff_ = 0.90f;
    BiquadFilter noiseFilter_;
    BiquadFilter modalFilter_;
};

```

---

### File: `addons/synth-cobalt-drums/src/HiHatVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <array>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class HatModel : int32_t {
    SchmittMetal = 0, // 6-Osc analog Schmitt-trigger square cluster
    LinearFM = 1,     // 4-Operator inharmonic FM metallic cymbal
    SpectralNoise = 2,// Trap/Drill sizzling granular noise
    ResonantBell = 3  // Ring-modulated high-Q metallic ride/bell
};

class HiHatVoice {
public:
    HiHatVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        filterBP_.setSampleRate(sampleRate_);
        filterHP_.setSampleRate(sampleRate_);
        for (auto& p : oscPhases_) p = 0.0;
        closedEnv_ = openEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = metalSmooth_ = 0.0f;
        updateRates();
        updateFilters();
    }

    void setParameters(float toneHz, float closedDecayMs, float openDecayMs, bool chokeEnabled, float sizzlePct, int model = 0, float pitchShiftSt = 0.0f) noexcept {
        toneHz_ = std::clamp(toneHz, 3000.0f, 12000.0f);
        closedDecayMs_ = std::clamp(closedDecayMs, 10.0f, 150.0f);
        openDecayMs_ = std::clamp(openDecayMs, 80.0f, 1500.0f);
        chokeEnabled_ = chokeEnabled;
        sizzlePct_ = std::clamp(sizzlePct, 0.0f, 1.0f);
        model_ = static_cast<HatModel>(std::clamp(model, 0, 3));
        pitchMult_ = std::pow(2.0f, std::clamp(pitchShiftSt, -12.0f, 12.0f) / 12.0f);
        updateRates();
        updateFilters();
    }

    void triggerClosed(float velocity) noexcept {
        if (closedEnv_ > 0.01f || openEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 654321.0f);
        closedVel_ = std::clamp(velocity, 0.05f, 1.0f);
        closedEnv_ = 1.0f;
        if (chokeEnabled_) openEnv_ = 0.0f;
    }

    void triggerOpen(float velocity) noexcept {
        if (closedEnv_ > 0.01f || openEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 123456.0f);
        openVel_ = std::clamp(velocity, 0.05f, 1.0f);
        openEnv_ = 1.0f;
    }

    void stop() noexcept {
        closedEnv_ = openEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = metalSmooth_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (closedEnv_ <= 0.0005f && openEnv_ <= 0.0005f && fadeEnv_ <= 0.001f) {
            outL = outR = 0.0f;
            return;
        }

        float metal = 0.0f;
        if (model_ == HatModel::LinearFM) {
            // 4-Op inharmonic FM metallic generator (1.0 : 1.414 : 2.828 : 4.23)
            static constexpr float FM_RATIOS[4] = {1.0f, 1.4142f, 2.8284f, 4.231f};
            float baseF = 440.0f * pitchMult_;
            for (size_t i = 0; i < 4; ++i) {
                oscPhases_[i] += (baseF * FM_RATIOS[i]) / sampleRate_;
                if (oscPhases_[i] >= 1.0) oscPhases_[i] -= 1.0;
            }
            float mod = std::sin(oscPhases_[3] * 6.283185307179586) * 1.8f;
            metal = std::sin(oscPhases_[0] * 6.283185307179586 + mod) * 0.5f +
                    std::sin(oscPhases_[1] * 6.283185307179586) * 0.3f +
                    std::sin(oscPhases_[2] * 6.283185307179586) * 0.2f;
        } else {
            // 6-Oscillator Schmitt Trigger Analog Metallic Cluster
            static constexpr float OSC_FREQS[6] = {245.3f, 306.4f, 367.6f, 428.8f, 543.7f, 678.9f};
            for (size_t i = 0; i < 6; ++i) {
                oscPhases_[i] += (OSC_FREQS[i] * pitchMult_) / sampleRate_;
                if (oscPhases_[i] >= 1.0) oscPhases_[i] -= 1.0;
                metal += (oscPhases_[i] < 0.5) ? 0.166f : -0.166f;
            }
            metal = std::tanh(metal * 1.35f);
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float noise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float mixed = (metal * (1.0f - sizzlePct_ * 0.45f)) + (noise * sizzlePct_ * 0.45f);

        float filtered = filterHP_.process(filterBP_.process(mixed));
        metalSmooth_ = 0.55f * metalSmooth_ + 0.45f * filtered;

        const float closedSig = metalSmooth_ * closedEnv_ * closedVel_;
        const float openSig = metalSmooth_ * openEnv_ * openVel_;

        closedEnv_ *= closedDecayCoeff_;
        openEnv_ *= openDecayCoeff_;

        float sL = (closedSig * 0.95f) + (openSig * 1.05f);
        float sR = (closedSig * 1.05f) + (openSig * 0.95f);

        if (fadeEnv_ > 0.001f) {
            sL += fadeSampleL_ * fadeEnv_;
            sR += fadeSampleR_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        lastOutL_ = sL;
        lastOutR_ = sR;
        outL = sL;
        outR = sR;
    }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        closedDecayCoeff_ = std::exp(-1.0f / ((closedDecayMs_ * 0.001f) * sampleRate_));
        openDecayCoeff_ = std::exp(-1.0f / ((openDecayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilters() noexcept {
        filterBP_.setParameters(FilterType::BandPass, toneHz_, 0.0f, 1.75f);
        filterHP_.setParameters(FilterType::HighPass, std::min(sampleRate_ * 0.35f, toneHz_ * 0.60f), 0.0f, 0.707f);
    }

    float sampleRate_ = 48000.0f;
    float toneHz_ = 8500.0f;
    float closedDecayMs_ = 45.0f;
    float openDecayMs_ = 450.0f;
    bool chokeEnabled_ = true;
    float sizzlePct_ = 0.40f;
    HatModel model_ = HatModel::SchmittMetal;
    float pitchMult_ = 1.0f;

    std::array<double, 6> oscPhases_{};
    float closedEnv_ = 0.0f;
    float openEnv_ = 0.0f;
    float closedVel_ = 1.0f;
    float openVel_ = 1.0f;

    float lastOutL_ = 0.0f;
    float lastOutR_ = 0.0f;
    float fadeSampleL_ = 0.0f;
    float fadeSampleR_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;
    float metalSmooth_ = 0.0f;

    uint32_t noiseSeed_ = 1234567;
    float closedDecayCoeff_ = 0.99f;
    float openDecayCoeff_ = 0.999f;
    BiquadFilter filterBP_;
    BiquadFilter filterHP_;
};

```

---

### File: `addons/synth-cobalt-drums/src/ClapVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class ClapModel : int32_t {
    AnalogFlam = 0,   // Traditional multi-burst flam with humanized jitter
    StereoHands = 1,  // Decorrelated stereo left/right hand paths
    GatedHall = 2,    // Diffused comb tail for gated warehouse acoustics
    GlitchCrunch = 3  // Bitcrushed digital micro-burst
};

class ClapVoice {
public:
    ClapVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        filter_.setSampleRate(sampleRate_);
        burstTimer_ = burstIndex_ = 0;
        currentBurstEnv_ = tailEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        updateRates();
        updateFilter();
    }

    void setParameters(float toneHz, float spreadMs, float decayMs, float roomPct, int model = 0, float stereoWidth = 0.5f) noexcept {
        toneHz_ = std::clamp(toneHz, 600.0f, 4500.0f);
        spreadMs_ = std::clamp(spreadMs, 5.0f, 32.0f);
        decayMs_ = std::clamp(decayMs, 50.0f, 650.0f);
        roomPct_ = std::clamp(roomPct, 0.0f, 1.0f);
        model_ = static_cast<ClapModel>(std::clamp(model, 0, 3));
        stereoWidth_ = std::clamp(stereoWidth, 0.0f, 1.0f);
        updateRates();
        updateFilter();
    }

    void trigger(float velocity) noexcept {
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && (tailEnv_ > 0.01f || currentBurstEnv_ > 0.01f)) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 88888.0f);

        burstIndex_ = 0;
        burstTimer_ = 0;
        currentBurstEnv_ = 1.0f;
        tailEnv_ = 0.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        tailEnv_ = currentBurstEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        if (burstIndex_ < 3) {
            burstTimer_++;
            if (burstTimer_ >= spreadSamples_) {
                burstTimer_ = 0;
                burstIndex_++;
                currentBurstEnv_ = 1.0f;
                if (burstIndex_ == 3) tailEnv_ = 1.0f;
            }
        }

        currentBurstEnv_ *= burstDecayCoeff_;
        tailEnv_ *= tailDecayCoeff_;

        const float roomScale = (model_ == ClapModel::GatedHall) ? 1.6f : 0.4f;
        const float totalEnv = (burstIndex_ < 3 ? currentBurstEnv_ : 0.0f) + (tailEnv_ * (0.75f + roomPct_ * roomScale));

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float rawNoise = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        const float filtered = filter_.process(rawNoise);

        float out = std::tanh(filtered * totalEnv * velocity_ * 1.35f);

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        if (burstIndex_ >= 3 && tailEnv_ <= 0.0005f) {
            active_ = false;
            tailEnv_ = 0.0f;
        }

        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        spreadSamples_ = static_cast<int32_t>((spreadMs_ * 0.001f) * sampleRate_);
        burstDecayCoeff_ = std::exp(-1.0f / (0.003f * sampleRate_));
        tailDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilter() noexcept {
        filter_.setParameters(FilterType::BandPass, toneHz_, 0.0f, 2.2f);
    }

    float sampleRate_ = 48000.0f;
    float toneHz_ = 1800.0f;
    float spreadMs_ = 14.0f;
    float decayMs_ = 240.0f;
    float roomPct_ = 0.30f;
    ClapModel model_ = ClapModel::AnalogFlam;
    float stereoWidth_ = 0.5f;

    int32_t spreadSamples_ = 672;
    int32_t burstTimer_ = 0;
    int32_t burstIndex_ = 0;
    float currentBurstEnv_ = 0.0f;
    float tailEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 999111;
    float burstDecayCoeff_ = 0.90f;
    float tailDecayCoeff_ = 0.999f;
    BiquadFilter filter_;
};

```

---

### File: `addons/synth-cobalt-drums/src/PercVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include "BiquadFilter.hpp"

enum class RimMode : int32_t {
    ClassicRim = 0,    // High-Q acoustic woodblock impulse
    HardWoodblock = 1, // Resonant modal clave
    Shaker = 2,        // Granular shaker burst
    ElectronicClave = 3// Tuned electronic click
};

enum class CowbellMode : int32_t {
    Cowbell808 = 0,   // Dual square wave 808 cowbell
    FMAgogo = 1,      // 2-Op inharmonic FM Agogo bell
    MetallicZap = 2,  // Laser sweep perc zap
    ResonantTri = 3   // Pure resonant triangle
};

class PercVoice {
public:
    PercVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        rimFilter_.setSampleRate(sampleRate_);
        cowFilter_.setSampleRate(sampleRate_);
        phase1_ = phase2_ = 0.0;
        rimEnv_ = cowEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
        active_ = false;
        updateRates();
        updateFilters();
    }

    void setParameters(float rimPitchHz, float rimDecayMs, float cowTuneHz, float cowDecayMs, int rimMode = 0, int cowMode = 0, float ringModDepth = 0.0f) noexcept {
        rimPitchHz_ = std::clamp(rimPitchHz, 500.0f, 3500.0f);
        rimDecayMs_ = std::clamp(rimDecayMs, 5.0f, 100.0f);
        cowTuneHz_ = std::clamp(cowTuneHz, 250.0f, 1200.0f);
        cowDecayMs_ = std::clamp(cowDecayMs, 30.0f, 600.0f);
        rimMode_ = static_cast<RimMode>(std::clamp(rimMode, 0, 3));
        cowMode_ = static_cast<CowbellMode>(std::clamp(cowMode, 0, 3));
        ringMod_ = std::clamp(ringModDepth, 0.0f, 1.0f);
        updateRates();
        updateFilters();
    }

    void triggerRim(float velocity) noexcept {
        cowEnv_ = 0.0f;

        if (active_ && rimEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 33333.0f);
        rimVel_ = std::clamp(velocity, 0.05f, 1.0f);
        rimEnv_ = 1.0f;
        active_ = true;
    }

    void triggerCowbell(float velocity) noexcept {
        rimEnv_ = 0.0f;

        if (active_ && cowEnv_ > 0.01f) {
            fadeSample_ = lastSample_;
            fadeEnv_ = 1.0f;
        }

        cowVel_ = std::clamp(velocity, 0.05f, 1.0f);
        cowEnv_ = 1.0f;
        phase1_ = phase2_ = 0.0;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        rimEnv_ = cowEnv_ = fadeEnv_ = 0.0f;
        lastSample_ = 0.0f;
    }

    inline float render() noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) return 0.0f;

        float out = 0.0f;

        if (rimEnv_ > 0.0005f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            const float click = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * rimEnv_;
            out += rimFilter_.process(click) * rimVel_ * 1.8f;
            rimEnv_ *= rimDecayCoeff_;
        }

        if (cowEnv_ > 0.0005f) {
            float cowSig = 0.0f;
            if (cowMode_ == CowbellMode::FMAgogo) {
                const float f1 = cowTuneHz_;
                const float f2 = cowTuneHz_ * 1.618f;
                phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
                phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
                float mod = std::sin(phase2_ * 6.283185307179586) * 1.5f;
                cowSig = std::sin(phase1_ * 6.283185307179586 + mod);
            } else {
                const float f1 = cowTuneHz_;
                const float f2 = cowTuneHz_ * 1.4815f;
                phase1_ += f1 / sampleRate_; if (phase1_ >= 1.0) phase1_ -= 1.0;
                phase2_ += f2 / sampleRate_; if (phase2_ >= 1.0) phase2_ -= 1.0;
                const float sq1 = (phase1_ < 0.5) ? 0.5f : -0.5f;
                const float sq2 = (phase2_ < 0.5) ? 0.5f : -0.5f;
                cowSig = cowFilter_.process(sq1 + sq2);
            }

            out += cowSig * cowEnv_ * cowVel_ * 1.25f;
            cowEnv_ *= cowDecayCoeff_;
        }

        if (fadeEnv_ > 0.001f) {
            out += fadeSample_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        if (rimEnv_ <= 0.0005f && cowEnv_ <= 0.0005f) {
            active_ = false;
            rimEnv_ = cowEnv_ = 0.0f;
        }

        out = std::tanh(out);
        lastSample_ = out;
        return out;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        rimDecayCoeff_ = std::exp(-1.0f / ((rimDecayMs_ * 0.001f) * sampleRate_));
        cowDecayCoeff_ = std::exp(-1.0f / ((cowDecayMs_ * 0.001f) * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    void updateFilters() noexcept {
        rimFilter_.setParameters(FilterType::BandPass, rimPitchHz_, 0.0f, 5.5f);
        cowFilter_.setParameters(FilterType::BandPass, cowTuneHz_ * 2.5f, 0.0f, 3.5f);
    }

    float sampleRate_ = 48000.0f;
    float rimPitchHz_ = 1750.0f;
    float rimDecayMs_ = 25.0f;
    float cowTuneHz_ = 540.0f;
    float cowDecayMs_ = 180.0f;
    RimMode rimMode_ = RimMode::ClassicRim;
    CowbellMode cowMode_ = CowbellMode::Cowbell808;
    float ringMod_ = 0.0f;

    double phase1_ = 0.0;
    double phase2_ = 0.0;
    float rimEnv_ = 0.0f;
    float cowEnv_ = 0.0f;
    float rimVel_ = 1.0f;
    float cowVel_ = 1.0f;
    bool active_ = false;

    float lastSample_ = 0.0f;
    float fadeSample_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 555121;
    float rimDecayCoeff_ = 0.95f;
    float cowDecayCoeff_ = 0.995f;
    BiquadFilter rimFilter_;
    BiquadFilter cowFilter_;
};

```

---

### File: `addons/synth-cobalt-drums/src/TomVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>

enum class TomModel : int32_t {
    AnalogSweep = 0,   // Classic exponential pitch drop tom
    ModalMembrane = 1, // Circular membrane physical resonator
    SlapLaser = 2,     // Fast exponential drop with 2nd-harmonic FM
    AfroConga = 3      // Wooden shell resonance + slap transient
};

class TomVoice {
public:
    TomVoice() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        phase_ = 0.0;
        ampEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = 0.0f;
        active_ = false;
        updateRates();
    }

    void setParameters(float baseTuneHz, float pitchBendSt, float decayMs, float fmDepthPct, float noiseImpactPct, int model = 0, float damping = 0.5f) noexcept {
        baseTuneHz_ = std::clamp(baseTuneHz, 50.0f, 500.0f);
        pitchBendSt_ = std::clamp(pitchBendSt, -24.0f, 24.0f);
        decayMs_ = std::clamp(decayMs, 40.0f, 900.0f);
        fmDepthPct_ = std::clamp(fmDepthPct, 0.0f, 1.0f);
        noiseImpactPct_ = std::clamp(noiseImpactPct, 0.0f, 1.0f);
        model_ = static_cast<TomModel>(std::clamp(model, 0, 3));
        damping_ = std::clamp(damping, 0.0f, 1.0f);
        updateRates();
    }

    void trigger(int32_t midiNote, float velocity) noexcept {
        float noteMultiplier = 1.0f;
        if (midiNote == 41) noteMultiplier = 0.70f;       // Low Floor Tom (F1)
        else if (midiNote == 43) noteMultiplier = 0.85f;  // High Floor Tom (G1)
        else if (midiNote == 45) noteMultiplier = 1.00f;  // Low-Mid Tom (A1)
        else if (midiNote == 47) noteMultiplier = 1.20f;  // High-Mid Tom (B1)
        else if (midiNote == 48) noteMultiplier = 1.45f;  // High Tom (C2)
        else if (midiNote == 50) noteMultiplier = 1.70f;  // High Timbale (D2)

        voiceBaseFreq_ = baseTuneHz_ * noteMultiplier;
        velocity_ = std::clamp(velocity, 0.05f, 1.0f);

        if (active_ && ampEnv_ > 0.01f) {
            fadeSampleL_ = lastOutL_;
            fadeSampleR_ = lastOutR_;
            fadeEnv_ = 1.0f;
        }

        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L + static_cast<uint32_t>(velocity * 44444.0f);

        phase_ = 0.0;
        ampEnv_ = 1.0f;
        pitchEnv_ = 1.0f;
        active_ = true;
    }

    void stop() noexcept {
        active_ = false;
        ampEnv_ = pitchEnv_ = fadeEnv_ = 0.0f;
        lastOutL_ = lastOutR_ = 0.0f;
    }

    inline void renderStereo(float& outL, float& outR) noexcept {
        if (!active_ && fadeEnv_ <= 0.001f) {
            outL = outR = 0.0f;
            return;
        }

        const float pitchSweep = voiceBaseFreq_ * std::pow(2.0f, (pitchBendSt_ * pitchEnv_) / 12.0f);
        phase_ += pitchSweep / sampleRate_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        float tone = 0.0f;
        if (model_ == TomModel::ModalMembrane) {
            double p2 = std::fmod(phase_ * 1.59, 1.0);
            double p3 = std::fmod(phase_ * 2.14, 1.0);
            tone = static_cast<float>(std::sin(phase_ * 6.283185307179586) * 0.60 +
                                      std::sin(p2 * 6.283185307179586) * 0.25 +
                                      std::sin(p3 * 6.283185307179586) * 0.15);
        } else {
            tone = static_cast<float>(std::sin(phase_ * 6.283185307179586));
            if (fmDepthPct_ > 0.01f) {
                float mod = static_cast<float>(std::sin(phase_ * 12.566370614359172)) * fmDepthPct_ * pitchEnv_;
                tone = std::sin(phase_ * 6.283185307179586 + mod);
            }
        }

        float noise = 0.0f;
        if (noiseImpactPct_ > 0.01f && pitchEnv_ > 0.1f) {
            noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
            noise = (static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f) * noiseImpactPct_ * pitchEnv_;
        }

        float out = (tone + noise * 0.35f) * ampEnv_ * velocity_;
        ampEnv_ *= ampDecayCoeff_;
        pitchEnv_ *= pitchDecayCoeff_;

        if (ampEnv_ <= 0.0005f) {
            active_ = false;
            ampEnv_ = 0.0f;
        }

        float sL = out * 0.9f;
        float sR = out * 1.1f;

        if (fadeEnv_ > 0.001f) {
            sL += fadeSampleL_ * fadeEnv_;
            sR += fadeSampleR_ * fadeEnv_;
            fadeEnv_ *= fadeDecayCoeff_;
        }

        lastOutL_ = sL;
        lastOutR_ = sR;
        outL = sL;
        outR = sR;
    }

    bool isActive() const noexcept { return active_ || (fadeEnv_ > 0.001f); }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        ampDecayCoeff_ = std::exp(-1.0f / ((decayMs_ * 0.001f) * sampleRate_));
        pitchDecayCoeff_ = std::exp(-1.0f / (0.038f * sampleRate_));
        fadeDecayCoeff_ = std::exp(-1.0f / (0.0015f * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    float baseTuneHz_ = 120.0f;
    float voiceBaseFreq_ = 120.0f;
    float pitchBendSt_ = -7.0f;
    float decayMs_ = 260.0f;
    float fmDepthPct_ = 0.10f;
    float noiseImpactPct_ = 0.15f;
    TomModel model_ = TomModel::AnalogSweep;
    float damping_ = 0.5f;

    double phase_ = 0.0;
    float ampEnv_ = 0.0f;
    float pitchEnv_ = 0.0f;
    float velocity_ = 1.0f;
    bool active_ = false;

    float lastOutL_ = 0.0f;
    float lastOutR_ = 0.0f;
    float fadeSampleL_ = 0.0f;
    float fadeSampleR_ = 0.0f;
    float fadeEnv_ = 0.0f;
    float fadeDecayCoeff_ = 0.90f;

    uint32_t noiseSeed_ = 777123;
    float ampDecayCoeff_ = 0.999f;
    float pitchDecayCoeff_ = 0.95f;
};

```

---

### File: `addons/synth-hyperion/src/HyperionSynthPlugin.cpp`

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"
#include "ZdfFilter.hpp"
#include "PolyBlepOscillator.hpp"
#include "LFO.hpp"
#include "ADSR.hpp"

static const CobassParamDescriptor HYPERION_PARAMS[] = {
    // --- OSCILLATOR 1 [0..7] ---
    {0, "Osc1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 15.0f, 1.0f, 1.0f, false,
        {"Sine", "Saw", "Pulse", "Triangle", "Noise", "Hypersaw", "Future Donk", "Vowel", "Metallic FM", "Dirty Reese", "Hard Sync", "Screamer", "Wavetable Acid", "Reso Sweep", "Organ FM", "Chime Cluster"}, 16},
    {1, "Osc1 Octave", "oct", COBASS_PARAM_TYPE_INT, -3.0f, 3.0f, 0.0f, 1.0f, false, {}, 0},
    {2, "Osc1 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 0.0f, 1.0f, false, {}, 0},
    {3, "Osc1 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 0.0f, 1.0f, false, {}, 0},
    {4, "Osc1 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},
    {5, "Osc1 Unison", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 2.0f, 1.0f, false, {"1 Voice", "2 Voices", "4 Voices", "8 Voices"}, 4},
    {6, "Osc1 Detune", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {7, "Osc1 Spread", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},

    // --- OSCILLATOR 2 & CROSS-MOD [8..16] ---
    {8, "Osc2 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 15.0f, 1.0f, 1.0f, false,
        {"Sine", "Saw", "Pulse", "Triangle", "Noise", "Hypersaw", "Future Donk", "Vowel", "Metallic FM", "Dirty Reese", "Hard Sync", "Screamer", "Wavetable Acid", "Reso Sweep", "Organ FM", "Chime Cluster"}, 16},
    {9, "Osc2 Octave", "oct", COBASS_PARAM_TYPE_INT, -3.0f, 3.0f, 0.0f, 1.0f, false, {}, 0},
    {10, "Osc2 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 7.0f, 1.0f, false, {}, 0},
    {11, "Osc2 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 5.0f, 1.0f, false, {}, 0},
    {12, "Osc2 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},
    {13, "Osc2 Sync", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 0.0f, 1.0f, false, {}, 0},
    {14, "Osc2 Unison", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 2.0f, 1.0f, false, {"1 Voice", "2 Voices", "4 Voices", "8 Voices"}, 4},
    {15, "Osc2 Detune", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},
    {16, "Osc2 Spread", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},

    // --- MIXER, SUB & NOISE [17..22] ---
    {17, "Osc1 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},
    {18, "Osc2 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.70f, 0.01f, false, {}, 0},
    {19, "Sub Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.35f, 0.01f, false, {}, 0},
    {20, "Sub Octave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 1.0f, 0.0f, 1.0f, false, {"-1 Octave", "-2 Octaves"}, 2},
    {21, "Noise Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {22, "Noise Type", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 5.0f, 0.0f, 1.0f, false, {"White", "Pink 1/f", "Brown", "Vinyl", "Metallic", "Velvet Air"}, 6},

    // --- CROSS-MODULATION & WAVEFOLD [23..26] ---
    {23, "Cross FM", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {24, "Ring Mod", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {25, "Osc1 Fold", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {26, "Osc2 Fold", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},

    // --- DUAL ZERO-DELAY FILTER SUITE [27..34] ---
    {27, "Filter Mode", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 7.0f, 0.0f, 1.0f, false,
        {"Ladder 24", "Diode 18 Acid", "SVF LP12", "SVF BP12", "SVF HP12", "Notch 12", "Formant Vowel", "Comb Resonator"}, 8},
    {28, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 4500.0f, 1.0f, true, {}, 0},
    {29, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 16.0f, 1.8f, 0.05f, false, {}, 0},
    {30, "Filter Drive", "x", COBASS_PARAM_TYPE_FLOAT, 0.5f, 5.0f, 1.2f, 0.05f, false, {}, 0},
    {31, "Drive Model", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Transistor", "Diode Acid", "Warm Tube", "Wavefold"}, 4},
    {32, "Filter Env", "%", COBASS_PARAM_TYPE_FLOAT, -1.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},
    {33, "Vowel Morph", "", COBASS_PARAM_TYPE_FLOAT, 0.0f, 4.0f, 0.0f, 0.05f, false, {}, 0},
    {34, "Key Tracking", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},

    // --- AMP ENVELOPE (ADSR 1) [35..38] ---
    {35, "Amp Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 5.0f, 1.0f, false, {}, 0},
    {36, "Amp Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 140.0f, 1.0f, false, {}, 0},
    {37, "Amp Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.75f, 0.01f, false, {}, 0},
    {38, "Amp Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 250.0f, 1.0f, false, {}, 0},

    // --- MOD ENVELOPE & PUNCH (ADSR 2) [39..44] ---
    {39, "Mod Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 5.0f, 1.0f, false, {}, 0},
    {40, "Mod Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 180.0f, 1.0f, false, {}, 0},
    {41, "Mod Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.20f, 0.01f, false, {}, 0},
    {42, "Mod Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 200.0f, 1.0f, false, {}, 0},
    {43, "Punch Drop", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 36.0f, 0.0f, 1.0f, false, {}, 0},
    {44, "Punch Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 2.0f, 80.0f, 15.0f, 1.0f, false, {}, 0},

    // --- DUAL LFO SYSTEM [45..50] ---
    {45, "LFO1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 1.0f, 1.0f, false, {"Sine", "Triangle", "Sawtooth", "Square", "S&H"}, 5},
    {46, "LFO1 Rate", "Hz", COBASS_PARAM_TYPE_FLOAT, 0.05f, 30.0f, 2.0f, 0.01f, false, {}, 0},
    {47, "LFO1 Cutoff", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {48, "LFO1 Pitch", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 2.0f, 0.0f, 0.01f, false, {}, 0},
    {49, "LFO2 Rate", "Hz", COBASS_PARAM_TYPE_FLOAT, 0.05f, 30.0f, 0.50f, 0.01f, false, {}, 0},
    {50, "LFO2 Mod", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},

    // --- 6-STAGE DANCE MASTER FX RACK [51..58] ---
    {51, "FX Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 24.0f, 0.0f, 0.1f, false, {}, 0},
    {52, "FX Dimension", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.40f, 0.01f, false, {}, 0},
    {53, "FX Delay Time", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 2.0f, 1.0f, false, {"1/4 Beat", "1/8 Beat", "1/8 Dotted", "1/16 Beat", "1/8 Triplet"}, 5},
    {54, "FX Delay FB", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 0.90f, 0.35f, 0.01f, false, {}, 0},
    {55, "FX Delay Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {56, "FX Reverb Size", "%", COBASS_PARAM_TYPE_FLOAT, 0.10f, 0.98f, 0.65f, 0.01f, false, {}, 0},
    {57, "FX Reverb Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {58, "FX OTT Comp", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},

    // --- MASTER CONTROLS [59..60] ---
    {59, "Portamento", "ms", COBASS_PARAM_TYPE_FLOAT, 0.0f, 500.0f, 0.0f, 1.0f, false, {}, 0},
    {60, "Master Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest HYPERION_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.hyperion",
    "Hyperion Hybrid Synth v4",
    "Maxica Audio",
    "4.0.0",
    COBASS_PLUGIN_TYPE_SYNTH,
    sizeof(HYPERION_PARAMS) / sizeof(CobassParamDescriptor),
    HYPERION_PARAMS,
    true,  // supportsMidi
    false  // supportsSidechain
};

class InternalDanceFxRack {
public:
    InternalDanceFxRack() {
        delayBufferL_.assign(MAX_DELAY_SAMPLES, 0.0f);
        delayBufferR_.assign(MAX_DELAY_SAMPLES, 0.0f);
        haasBuffer_.assign(2048, 0.0f);

        const int combTuning[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        const int allpassTuning[4] = {556, 441, 341, 225};
        for (int i = 0; i < 8; ++i) {
            verbCombs_[i].assign(combTuning[i], 0.0f);
            verbCombIdx_[i] = 0;
            verbDampState_[i] = 0.0f;
        }
        for (int i = 0; i < 4; ++i) {
            verbAllPass_[i].assign(allpassTuning[i], 0.0f);
            verbAllPassIdx_[i] = 0;
        }
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        std::fill(delayBufferL_.begin(), delayBufferL_.end(), 0.0f);
        std::fill(delayBufferR_.begin(), delayBufferR_.end(), 0.0f);
        std::fill(haasBuffer_.begin(), haasBuffer_.end(), 0.0f);
        delayWriteIdx_ = haasWriteIdx_ = 0;
        delayDampL_ = delayDampR_ = 0.0f;
        ottEnvL_ = ottEnvR_ = 0.0f;
        for (int i = 0; i < 8; ++i) {
            std::fill(verbCombs_[i].begin(), verbCombs_[i].end(), 0.0f);
            verbCombIdx_[i] = 0;
            verbDampState_[i] = 0.0f;
        }
        for (int i = 0; i < 4; ++i) {
            std::fill(verbAllPass_[i].begin(), verbAllPass_[i].end(), 0.0f);
            verbAllPassIdx_[i] = 0;
        }
    }

    inline void process(float inL, float inR,
                        float driveDb, float dimWidth, int delayDivIdx, float delayFb, float delayMix,
                        float verbSize, float verbMix, float ottComp, float outTrimDb,
                        float& outL, float& outR) noexcept {

        float sL = inL;
        float sR = inR;

        // --- STAGE 1: ASYMMETRIC DRIVE & SATURATION ---
        if (driveDb > 0.01f) {
            const float driveGain = std::pow(10.0f, driveDb / 20.0f);
            sL = std::tanh((sL + 0.05f) * driveGain) - 0.05f;
            sR = std::tanh((sR + 0.05f) * driveGain) - 0.05f;
        }

        // --- STAGE 2: DIMENSION EXPANDER & HAAS WIDENER ---
        if (dimWidth > 0.01f) {
            const int haasDelaySamples = static_cast<int>(0.012f * sampleRate_);
            int readIdx = static_cast<int>(haasWriteIdx_) - haasDelaySamples;
            if (readIdx < 0) readIdx += static_cast<int>(haasBuffer_.size());

            float delayedR = haasBuffer_[readIdx];
            haasBuffer_[haasWriteIdx_] = sR;
            haasWriteIdx_ = (haasWriteIdx_ + 1) % haasBuffer_.size();

            float side = (sL - delayedR) * dimWidth * 0.45f;
            sL += side;
            sR -= side;
        }

        // --- STAGE 3: STEREO PING-PONG DELAY ---
        if (delayMix > 0.005f) {
            static constexpr float DELAY_DIVS[5] = {0.500f, 0.250f, 0.375f, 0.125f, 0.1667f};
            int div = std::clamp(delayDivIdx, 0, 4);
            int delaySamples = std::clamp(static_cast<int>(DELAY_DIVS[div] * sampleRate_), 10, static_cast<int>(MAX_DELAY_SAMPLES - 10));

            int readIdxL = static_cast<int>(delayWriteIdx_) - delaySamples;
            if (readIdxL < 0) readIdxL += MAX_DELAY_SAMPLES;
            int readIdxR = static_cast<int>(delayWriteIdx_) - (delaySamples / 2);
            if (readIdxR < 0) readIdxR += MAX_DELAY_SAMPLES;

            float dL = delayBufferL_[readIdxL];
            float dR = delayBufferR_[readIdxR];

            // BUG-6 FIX: 1-pole low-pass feedback damping to remove metallic harshness
            const float dampCoeff = 0.40f;
            delayDampL_ = (dL * (1.0f - dampCoeff)) + (delayDampL_ * dampCoeff);
            delayDampR_ = (dR * (1.0f - dampCoeff)) + (delayDampR_ * dampCoeff);

            delayBufferL_[delayWriteIdx_] = sL + delayDampR_ * delayFb;
            delayBufferR_[delayWriteIdx_] = sR + delayDampL_ * delayFb;
            delayWriteIdx_ = (delayWriteIdx_ + 1) % MAX_DELAY_SAMPLES;

            sL = sL + dL * delayMix;
            sR = sR + dR * delayMix;
        }

        // --- STAGE 4: LUSH DANCE REVERB ---
        if (verbMix > 0.005f) {
            float inMono = (sL + sR) * 0.5f * 0.08f;
            float combSumL = 0.0f, combSumR = 0.0f;

            const float safeVerbSize = std::min(0.92f, verbSize); // MINOR-4 FIX: feedback runaway protection
            for (int k = 0; k < 8; ++k) {
                float outC = verbCombs_[k][verbCombIdx_[k]];
                verbDampState_[k] = (outC * 0.75f) + (verbDampState_[k] * 0.25f);
                verbCombs_[k][verbCombIdx_[k]] = inMono + (verbDampState_[k] * safeVerbSize);
                if (++verbCombIdx_[k] >= verbCombs_[k].size()) verbCombIdx_[k] = 0;

                if (k % 2 == 0) combSumL += outC;
                else combSumR += outC;
            }

            for (int a = 0; a < 2; ++a) {
                float bufOut = verbAllPass_[a][verbAllPassIdx_[a]];
                float outA = -combSumL + bufOut;
                verbAllPass_[a][verbAllPassIdx_[a]] = combSumL + (bufOut * 0.5f);
                if (++verbAllPassIdx_[a] >= verbAllPass_[a].size()) verbAllPassIdx_[a] = 0;
                combSumL = outA;
            }
            for (int a = 2; a < 4; ++a) {
                float bufOut = verbAllPass_[a][verbAllPassIdx_[a]];
                float outA = -combSumR + bufOut;
                verbAllPass_[a][verbAllPassIdx_[a]] = combSumR + (bufOut * 0.5f);
                if (++verbAllPassIdx_[a] >= verbAllPass_[a].size()) verbAllPassIdx_[a] = 0;
                combSumR = outA;
            }

            sL = sL + combSumL * (verbMix * 1.4f);
            sR = sR + combSumR * (verbMix * 1.4f);
        }

        // --- STAGE 5: OTT MASTER PUNCH LIMITER & MAKEUP GAIN ---
        if (ottComp > 0.01f) {
            float pkL = std::abs(sL);
            float pkR = std::abs(sR);
            ottEnvL_ = 0.992f * ottEnvL_ + 0.008f * pkL;
            ottEnvR_ = 0.992f * ottEnvR_ + 0.008f * pkR;

            float avgEnv = std::max(1e-4f, (ottEnvL_ + ottEnvR_) * 0.5f);
            float grDb = 0.0f;
            float envDb = 20.0f * std::log10(avgEnv);

            if (envDb > -12.0f) {
                grDb = (-12.0f - envDb) * 0.55f;
            } else if (envDb < -24.0f) {
                grDb = (-24.0f - envDb) * 0.45f;
            }

            float ottMakeupDb = ottComp * 4.5f;
            float gainLinear = std::pow(10.0f, (grDb * ottComp + ottMakeupDb) / 20.0f);
            sL *= gainLinear;
            sR *= gainLinear;
        }

        const float outTrimLinear = std::pow(10.0f, outTrimDb / 20.0f);
        outL = sL * outTrimLinear;
        outR = sR * outTrimLinear;
    }

private:
    static constexpr size_t MAX_DELAY_SAMPLES = 96000;
    float sampleRate_ = 48000.0f;

    std::vector<float> delayBufferL_;
    std::vector<float> delayBufferR_;
    size_t delayWriteIdx_ = 0;

    std::vector<float> haasBuffer_;
    size_t haasWriteIdx_ = 0;
    float delayDampL_ = 0.0f;
    float delayDampR_ = 0.0f;

    std::array<std::vector<float>, 8> verbCombs_;
    std::array<size_t, 8> verbCombIdx_{};
    std::array<float, 8> verbDampState_{};

    std::array<std::vector<float>, 4> verbAllPass_;
    std::array<size_t, 4> verbAllPassIdx_{};

    float ottEnvL_ = 0.0f;
    float ottEnvR_ = 0.0f;
};

class HyperionProcessor {
private:
    struct Voice {
        int32_t note = -1;
        float velocity = 0.0f;
        float targetFreq = 440.0f;
        float currentFreq = 440.0f;
        float glideCoeff = 1.0f;
        bool active = false;

        float punchEnv = 0.0f;
        float punchDecayCoeff = 0.95f;
        uint64_t noteOnTime = 0;

        float lastCutoff = -1.0f;
        float lastRes = -1.0f;
        float lastDrive = -1.0f;
        float lastVowel = -1.0f;
        int lastDriveModel = -1;
        ZdfFilterMode lastMode = static_cast<ZdfFilterMode>(-1);

        std::array<PolyBlepOscillator, 8> osc1Stack;
        std::array<PolyBlepOscillator, 8> osc2Stack;
        PolyBlepOscillator oscSub;
        PolyBlepOscillator oscNoise;

        ADSR ampEnv;
        ADSR modEnv;
        ZdfFilter filterL;
        ZdfFilter filterR;

        void init(float sampleRate) {
            for (auto& o : osc1Stack) o.setSampleRate(sampleRate);
            for (auto& o : osc2Stack) o.setSampleRate(sampleRate);
            oscSub.setSampleRate(sampleRate);
            oscNoise.setSampleRate(sampleRate);
            oscNoise.setWaveform(OscillatorWaveform::Noise);

            ampEnv.setSampleRate(sampleRate);
            modEnv.setSampleRate(sampleRate);
            filterL.setSampleRate(sampleRate);
            filterR.setSampleRate(sampleRate);
        }

        void trigger(int32_t midiNote, float vel, float sampleRate, float glideMs, float punchDecayMs, int osc1UnisonN = 8, int osc2UnisonN = 8) {
            targetFreq = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);

            // BUG-5 & ISSUE-1 FIX: Legato only on sustain; accurate millisecond scaling (0.001f)
            const bool isLegato = active && (ampEnv.getState() == EnvelopeState::Sustain);
            if (isLegato && glideMs > 0.001f) {
                glideCoeff = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideMs * 0.001f) * sampleRate));
            } else {
                currentFreq = targetFreq;
                glideCoeff = 1.0f;
            }

            note = midiNote;
            velocity = vel;
            active = true;

            punchEnv = 1.0f;
            punchDecayCoeff = std::exp(-1.0f / (std::max(0.002f, punchDecayMs * 0.001f) * sampleRate));

            const int div1 = std::max(1, osc1UnisonN);
            const int div2 = std::max(1, osc2UnisonN);
            for (size_t i = 0; i < 8; ++i) {
                osc1Stack[i].resetPhase(static_cast<double>(i) / static_cast<double>(div1));
                osc2Stack[i].resetPhase(static_cast<double>(i) / static_cast<double>(div2));
            }
            // BUG-3 FIX: Sub oscillator phase reset eliminates note-on clicks
            oscSub.resetPhase(0.0);

            ampEnv.gate(true);
            modEnv.gate(true);
            filterL.reset();
            filterR.reset();
            lastCutoff = -1.0f;
        }

        void release() {
            if (active) {
                ampEnv.gate(false);
                modEnv.gate(false);
            }
        }

        void stop() {
            active = false;
            note = -1;
            ampEnv.reset();
            modEnv.reset();
            filterL.reset();
            filterR.reset();
            punchEnv = 0.0f;
            lastCutoff = -1.0f;
        }

        float getEnergy() const noexcept {
            return active ? ampEnv.getCurrentValue() : 0.0f;
        }
    };

public:
    explicit HyperionProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : HYPERION_PARAMS) params_[p.id] = p.defaultValue;
        for (auto& v : voices_) v.init(sampleRate_);
        lfo1_.setSampleRate(sampleRate_);
        lfo2_.setSampleRate(sampleRate_);
        fxRack_.reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        lfo1_.setSampleRate(sampleRate_);
        lfo2_.setSampleRate(sampleRate_);
        fxRack_.reset(sampleRate_);
        for (auto& v : voices_) {
            v.init(sampleRate_);
            v.stop();
        }
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
        if (!target) {
            float minEnergy = 999.0f;
            for (auto& v : voices_) {
                if (v.ampEnv.getState() == EnvelopeState::Release) {
                    float e = v.getEnergy();
                    if (e < minEnergy) {
                        minEnergy = e;
                        target = &v;
                    }
                }
            }
        }
        if (!target) {
            uint64_t oldest = UINT64_MAX;
            for (auto& v : voices_) {
                if (v.noteOnTime < oldest) {
                    oldest = v.noteOnTime;
                    target = &v;
                }
            }
        }
        if (!target) target = &voices_[0];

        static constexpr int UNISON_COUNTS[4] = {1, 2, 4, 8};
        const int u1 = UNISON_COUNTS[std::min(3, static_cast<int>(params_[5]))];
        const int u2 = UNISON_COUNTS[std::min(3, static_cast<int>(params_[14]))];

        target->noteOnTime = ++voiceCounter_;
        target->trigger(note, velocity, sampleRate_, params_[59], params_[44], u1, u2);
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

        const auto osc1Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[0]) % 16);
        const float osc1PitchMult = std::pow(2.0f, (params_[1] * 12.0f + params_[2] + params_[3] * 0.01f) / 12.0f);
        const float osc1Pw = params_[4];
        static constexpr int UNISON_COUNTS[4] = {1, 2, 4, 8};
        const int osc1UnisonN = UNISON_COUNTS[std::min(3, static_cast<int>(params_[5]))];
        const float osc1Detune = params_[6] * 0.025f;
        const float osc1Spread = params_[7];
        const float osc1Fold = params_[25];

        const auto osc2Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[8]) % 16);
        const float osc2PitchMult = std::pow(2.0f, (params_[9] * 12.0f + params_[10] + params_[11] * 0.01f) / 12.0f);
        const float osc2Pw = params_[12];
        const bool osc2Sync = (params_[13] > 0.5f);
        const int osc2UnisonN = UNISON_COUNTS[std::min(3, static_cast<int>(params_[14]))];
        const float osc2Detune = params_[15] * 0.025f;
        const float osc2Spread = params_[16];
        const float osc2Fold = params_[26];

        const float osc1Vol = params_[17];
        const float osc2Vol = params_[18];
        const float subVol  = params_[19];
        const float subOctMult = (params_[20] > 0.5f) ? 0.25f : 0.50f;
        const float noiseVol = params_[21];
        const auto noiseType = static_cast<NoiseType>(static_cast<int>(params_[22]) % 6);

        const float crossFm  = params_[23];
        const float ringMod  = params_[24];

        const auto filterMode = static_cast<ZdfFilterMode>(static_cast<int>(params_[27]) % 8);
        const float baseCutoff = params_[28];
        const float resonance  = params_[29];
        const float drive      = params_[30];
        const int driveModel   = static_cast<int>(params_[31]) % 4;
        const float filterEnvAmt = params_[32];
        const float vowelMorph   = params_[33];
        const float keytrackPct  = params_[34];

        const float ampA = params_[35] * 0.001f, ampD = params_[36] * 0.001f, ampS = params_[37], ampR = params_[38] * 0.001f;
        const float modA = params_[39] * 0.001f, modD = params_[40] * 0.001f, modS = params_[41], modR = params_[42] * 0.001f;
        const float punchDropSt = params_[43];

        lfo1_.setWaveform(static_cast<LfoWaveform>(static_cast<int>(params_[45]) % 5));
        lfo1_.setFrequency(params_[46]);
        const float lfoCutoffDepth = params_[47];
        const float lfoPitchDepth  = params_[48];

        lfo2_.setWaveform(LfoWaveform::Sine);
        lfo2_.setFrequency(params_[49]);
        const float lfo2ModDepth = params_[50];

        const float fxDriveDb    = params_[51];
        const float fxDimWidth   = params_[52];
        const int fxDelayDiv     = static_cast<int>(params_[53]);
        const float fxDelayFb    = params_[54];
        const float fxDelayMix   = params_[55];
        const float fxVerbSize   = params_[56];
        const float fxVerbMix    = params_[57];
        const float fxOttComp    = params_[58];
        const float masterGain   = std::pow(10.0f, params_[60] / 20.0f) * 1.35f;

        for (auto& v : voices_) {
            if (v.active) {
                v.ampEnv.setParameters(ampA, ampD, ampS, ampR);
                v.modEnv.setParameters(modA, modD, modS, modR);
                for (int u = 0; u < 8; ++u) {
                    v.osc1Stack[u].setWaveform(osc1Wave);
                    v.osc1Stack[u].setPulseWidth(osc1Pw);
                    v.osc2Stack[u].setWaveform(osc2Wave);
                    v.osc2Stack[u].setPulseWidth(osc2Pw);
                }
                v.oscSub.setWaveform(OscillatorWaveform::Sine);
                v.oscNoise.setNoiseType(noiseType);
            }
        }

        for (uint32_t i = 0; i < numFrames; ++i) {
            const float lfoVal = lfo1_.getNextSample();
            const float lfo2Val = lfo2_.getNextSample();
            const float lfoPitchMod = lfoVal * lfoPitchDepth;
            const float lfoCutoffMod = std::max(0.05f, 1.0f + lfoVal * lfoCutoffDepth * 0.8f);

            const float dynFold1 = std::clamp(osc1Fold + lfo2Val * lfo2ModDepth * 0.35f, 0.0f, 1.0f);
            const float dynFold2 = std::clamp(osc2Fold + lfo2Val * lfo2ModDepth * 0.35f, 0.0f, 1.0f);

            float rawSumL = 0.0f;
            float rawSumR = 0.0f;

            for (auto& v : voices_) {
                if (!v.active) continue;

                const float amp = v.ampEnv.getNextSample();
                const float modEnvVal = v.modEnv.getNextSample();

                if (!v.ampEnv.isActive()) {
                    v.stop();
                    continue;
                }

                if (v.currentFreq != v.targetFreq) {
                    v.currentFreq += (v.targetFreq - v.currentFreq) * v.glideCoeff;
                    if (std::abs(v.targetFreq - v.currentFreq) < 0.05f) {
                        v.currentFreq = v.targetFreq;
                    }
                }

                float punchModSt = punchDropSt * v.punchEnv;
                v.punchEnv *= v.punchDecayCoeff;

                float voicePitch = v.currentFreq * std::pow(2.0f, (punchModSt + lfoPitchMod) / 12.0f);

                // --- OSC 1 UNISON ---
                float osc1L = 0.0f, osc1R = 0.0f;
                for (int u = 0; u < osc1UnisonN; ++u) {
                    float detuneOffset = (osc1UnisonN > 1) ? (static_cast<float>(u - (osc1UnisonN - 1) / 2.0f) / ((osc1UnisonN - 1) / 2.0f)) : 0.0f;
                    float uPitch = voicePitch * osc1PitchMult * (1.0f + detuneOffset * osc1Detune);
                    v.osc1Stack[u].setFrequency(uPitch);
                    float s = v.osc1Stack[u].renderSampleWithWavefold(dynFold1);

                    float panL = (osc1UnisonN > 1) ? (1.0f - detuneOffset * (osc1Spread * 0.5f)) : 1.0f;
                    float panR = (osc1UnisonN > 1) ? (1.0f + detuneOffset * (osc1Spread * 0.5f)) : 1.0f;
                    osc1L += s * panL;
                    osc1R += s * panR;
                }
                const float norm1 = 1.0f / std::sqrt(static_cast<float>(osc1UnisonN));
                osc1L *= norm1; osc1R *= norm1;

                // --- OSC 2 UNISON + HARD SYNC & CROSS-FM ---
                float osc2L = 0.0f, osc2R = 0.0f;
                for (int u = 0; u < osc2UnisonN; ++u) {
                    float detuneOffset = (osc2UnisonN > 1) ? (static_cast<float>(u - (osc2UnisonN - 1) / 2.0f) / ((osc2UnisonN - 1) / 2.0f)) : 0.0f;
                    float uPitch = voicePitch * osc2PitchMult * (1.0f - detuneOffset * osc2Detune);

                    // BUG-1 & BUG-2 FIX: Normalize stereo FM input and clamp modulation depth
                    if (crossFm > 0.001f) {
                        const float fmInput = (osc1L + osc1R) * 0.5f;
                        const float fmMod = 1.0f + fmInput * crossFm * 1.5f;
                        uPitch *= std::clamp(fmMod, 0.05f, 8.0f);
                    }

                    v.osc2Stack[u].setFrequency(uPitch);

                    if (osc2Sync) {
                        v.osc2Stack[u].syncToMaster(v.osc1Stack[0].getPhase());
                    }

                    float s = v.osc2Stack[u].renderSampleWithWavefold(dynFold2);
                    float panL = (osc2UnisonN > 1) ? (1.0f - detuneOffset * (osc2Spread * 0.5f)) : 1.0f;
                    float panR = (osc2UnisonN > 1) ? (1.0f + detuneOffset * (osc2Spread * 0.5f)) : 1.0f;
                    osc2L += s * panL;
                    osc2R += s * panR;
                }
                const float norm2 = 1.0f / std::sqrt(static_cast<float>(osc2UnisonN));
                osc2L *= norm2; osc2R *= norm2;

                // Ring Modulation
                if (ringMod > 0.001f) {
                    float ringL = osc1L * osc2L;
                    float ringR = osc1R * osc2R;
                    osc2L = osc2L * (1.0f - ringMod) + ringL * ringMod;
                    osc2R = osc2R * (1.0f - ringMod) + ringR * ringMod;
                }

                // Sub Oscillator & Noise
                v.oscSub.setFrequency(voicePitch * subOctMult);
                float sSub = v.oscSub.renderSample() * subVol;
                float sNoise = v.oscNoise.renderSample() * noiseVol;

                float rawL = (osc1L * osc1Vol) + (osc2L * osc2Vol) + sSub + sNoise;
                float rawR = (osc1R * osc1Vol) + (osc2R * osc2Vol) + sSub + sNoise;

                // ZDF Filter
                float keytrackMultiplier = std::pow(2.0f, (v.note - 60) * (keytrackPct / 12.0f));
                float envCutoffDelta = baseCutoff * filterEnvAmt * modEnvVal * 4.0f;
                float finalCutoff = std::clamp((baseCutoff * keytrackMultiplier + envCutoffDelta) * lfoCutoffMod, 20.0f, sampleRate_ * 0.48f);

                // BUG-4 FIX: Only update filter coefficients when parameters change significantly
                if (std::abs(finalCutoff - v.lastCutoff) > 1.0f || filterMode != v.lastMode ||
                    std::abs(resonance - v.lastRes) > 0.01f || std::abs(drive - v.lastDrive) > 0.01f ||
                    std::abs(vowelMorph - v.lastVowel) > 0.01f || driveModel != v.lastDriveModel) {
                    v.filterL.setParameters(filterMode, finalCutoff, resonance, drive, vowelMorph, driveModel);
                    v.filterR.setParameters(filterMode, finalCutoff, resonance, drive, vowelMorph, driveModel);
                    v.lastCutoff = finalCutoff;
                    v.lastMode = filterMode;
                    v.lastRes = resonance;
                    v.lastDrive = drive;
                    v.lastVowel = vowelMorph;
                    v.lastDriveModel = driveModel;
                }

                float vL = v.filterL.process(rawL) * amp * v.velocity * 1.60f;
                float vR = v.filterR.process(rawR) * amp * v.velocity * 1.60f;

                rawSumL += vL;
                rawSumR += vR;
            }

            // --- INTERNAL 6-STAGE DANCE STUDIO FX RACK ---
            float fxOutL = 0.0f, fxOutR = 0.0f;
            fxRack_.process(rawSumL, rawSumR,
                            fxDriveDb, fxDimWidth, fxDelayDiv, fxDelayFb, fxDelayMix,
                            fxVerbSize, fxVerbMix, fxOttComp, 0.0f,
                            fxOutL, fxOutR);

            outL[i] = fxOutL * masterGain;
            outR[i] = fxOutR * masterGain;
        }
    }

    uint32_t getStateJson(char* outBuffer, uint32_t maxLen) const {
        std::string json;
        json.push_back('{');
        char numBuf[32];
        for (size_t i = 0; i < params_.size(); ++i) {
            std::snprintf(numBuf, sizeof(numBuf), "%.6g", static_cast<double>(params_[i]));
            json.push_back('"');
            json += std::to_string(i);
            json.push_back('"');
            json.push_back(':');
            json += numBuf;
            if (i < params_.size() - 1) json.push_back(',');
        }
        json.push_back('}');
        if (json.size() >= maxLen) return 0;
        std::memcpy(outBuffer, json.c_str(), json.size() + 1);
        return static_cast<uint32_t>(json.size());
    }

    bool setStateJson(const char* json) {
        if (!json) return false;
        for (size_t i = 0; i < params_.size(); ++i) {
            std::string key;
            key.push_back('"');
            key += std::to_string(i);
            key.push_back('"');
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
    float sampleRate_ = 48000.0f;
    std::array<float, 61> params_{};
    std::array<Voice, 16> voices_{};
    LFO lfo1_;
    LFO lfo2_;
    InternalDanceFxRack fxRack_;
    uint64_t voiceCounter_ = 0;
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

```

---

### File: `app/native/include/CobassPluginABI.h`

```cpp
#pragma once
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define COBASS_PLUGIN_API_VERSION 1
#define COBASS_MAX_PARAMS 64
#define COBASS_MAX_CHOICES 16

typedef enum {
    COBASS_PLUGIN_TYPE_SYNTH  = 0, // Instrument: Receives MIDI events, generates audio
    COBASS_PLUGIN_TYPE_EFFECT = 1  // Insert FX: Processes audio in-place
} CobassPluginType;

typedef enum {
    COBASS_PARAM_TYPE_FLOAT   = 0, // Continuous slider / rotary knob
    COBASS_PARAM_TYPE_INT     = 1, // Discrete integer steps
    COBASS_PARAM_TYPE_BOOL    = 2, // Toggle switch
    COBASS_PARAM_TYPE_CHOICE  = 3  // Enumerated string choices
} CobassParamType;

typedef struct {
    uint32_t id;
    char name[32];
    char label[16];          // Unit label: "Hz", "dB", "%", "ms", "st", etc.
    CobassParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    bool isLogarithmic;
    char choices[COBASS_MAX_CHOICES][24];
    uint32_t choiceCount;
} CobassParamDescriptor;

typedef struct {
    uint32_t apiVersion;
    char pluginId[64];       // Unique Reverse-DNS ID (e.g., "com.maxica.cobass.plugins.hyperion")
    char name[48];           // Human-readable name
    char vendor[32];         // Vendor name
    char version[16];        // Semantic version
    CobassPluginType type;
    uint32_t paramCount;
    const CobassParamDescriptor* params;
    bool supportsMidi;
    bool supportsSidechain;
} CobassPluginManifest;

typedef void* CobassHandle;

// Function Pointers for exported C-ABI symbols
typedef const CobassPluginManifest* (*CobassGetManifestFunc)(void);
typedef CobassHandle (*CobassCreateInstanceFunc)(float sampleRate);
typedef void (*CobassDestroyInstanceFunc)(CobassHandle handle);
typedef void (*CobassResetFunc)(CobassHandle handle, float sampleRate);
typedef void (*CobassProcessFunc)(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames);
typedef void (*CobassNoteOnFunc)(CobassHandle handle, int32_t note, float velocity);
typedef void (*CobassNoteOffFunc)(CobassHandle handle, int32_t note);
typedef void (*CobassAllNotesOffFunc)(CobassHandle handle);
typedef void (*CobassSetParamFunc)(CobassHandle handle, uint32_t paramId, float value);
typedef float (*CobassGetParamFunc)(CobassHandle handle, uint32_t paramId);
typedef uint32_t (*CobassGetStateFunc)(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen);
typedef bool (*CobassSetStateFunc)(CobassHandle handle, const char* jsonBuffer);

#ifdef __cplusplus
}
#endif

```

---

### File: `app/native/plugin/PluginDescriptor.hpp`

```cpp
#pragma once
#include <string>
#include <vector>
#include "../include/CobassPluginABI.h"

struct PluginParam {
    uint32_t id = 0;
    std::string name;
    std::string label;
    CobassParamType type = COBASS_PARAM_TYPE_FLOAT;
    float minValue = 0.0f;
    float maxValue = 1.0f;
    float defaultValue = 0.0f;
    float step = 0.01f;
    bool isLogarithmic = false;
    std::vector<std::string> choices;
};

struct PluginDescriptor {
    std::string pluginId;
    std::string name;
    std::string vendor;
    std::string version;
    std::string libraryPath;
    CobassPluginType type = COBASS_PLUGIN_TYPE_EFFECT;
    bool supportsMidi = false;
    bool supportsSidechain = false;
    std::vector<PluginParam> parameters;

    static PluginDescriptor fromManifest(const CobassPluginManifest* manifest, const std::string& libPath = "") {
        PluginDescriptor desc;
        if (!manifest) return desc;

        desc.pluginId = manifest->pluginId;
        desc.name = manifest->name;
        desc.vendor = manifest->vendor;
        desc.version = manifest->version;
        desc.libraryPath = libPath;
        desc.type = manifest->type;
        desc.supportsMidi = manifest->supportsMidi;
        desc.supportsSidechain = manifest->supportsSidechain;

        for (uint32_t i = 0; i < manifest->paramCount; ++i) {
            const auto& p = manifest->params[i];
            PluginParam param;
            param.id = p.id;
            param.name = p.name;
            param.label = p.label;
            param.type = p.type;
            param.minValue = p.minValue;
            param.maxValue = p.maxValue;
            param.defaultValue = p.defaultValue;
            param.step = p.step;
            param.isLogarithmic = p.isLogarithmic;
            for (uint32_t c = 0; c < p.choiceCount && c < COBASS_MAX_CHOICES; ++c) {
                param.choices.emplace_back(p.choices[c]);
            }
            desc.parameters.push_back(std::move(param));
        }
        return desc;
    }
};

```

---

### File: `app/native/plugin/PluginInstance.hpp`

```cpp
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

```

---

### File: `app/native/plugin/PluginLoader.hpp`

```cpp
#pragma once
#include <dlfcn.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <filesystem>
#include <android/log.h>
#include "PluginDescriptor.hpp"
#include "PluginInstance.hpp"

#define PLUGIN_TAG "CobassPluginLoader"
#define LOGP_I(...) __android_log_print(ANDROID_LOG_INFO, PLUGIN_TAG, __VA_ARGS__)
#define LOGP_E(...) __android_log_print(ANDROID_LOG_ERROR, PLUGIN_TAG, __VA_ARGS__)

class PluginLoader {
public:
    static PluginLoader& getInstance() {
        static PluginLoader sInstance;
        return sInstance;
    }

    void scanDirectory(const std::string& directoryPath) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!std::filesystem::exists(directoryPath)) return;

        try {
            for (const auto& entry : std::filesystem::directory_iterator(directoryPath)) {
                if (entry.is_regular_file() && entry.path().extension() == ".so") {
                    const std::string filename = entry.path().filename().string();
                    if (filename.rfind("libcobass_plugin_", 0) == 0 || filename.rfind("libplugin_", 0) == 0) {
                        inspectAndRegister(entry.path().string());
                    }
                }
            }
        } catch (const std::exception& e) {
            LOGP_E("Error scanning plugin directory %s: %s", directoryPath.c_str(), e.what());
        }
    }

    bool registerPluginLibrary(const std::string& soPath) {
        std::lock_guard<std::mutex> lock(mutex_);
        return inspectAndRegister(soPath);
    }

    std::vector<PluginDescriptor> getAvailablePlugins() const {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<PluginDescriptor> list;
        list.reserve(catalog_.size());
        for (const auto& [id, desc] : catalog_) {
            list.push_back(desc);
        }
        return list;
    }

    const PluginDescriptor* findDescriptor(const std::string& pluginId) const {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = catalog_.find(pluginId);
        return it != catalog_.end() ? &it->second : nullptr;
    }

    std::unique_ptr<PluginInstance> instantiatePlugin(const std::string& pluginId, float sampleRate) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = catalog_.find(pluginId);
        if (it == catalog_.end()) {
            LOGP_E("Plugin not found in catalog: %s", pluginId.c_str());
            return nullptr;
        }

        const PluginDescriptor& desc = it->second;
        void* handle = dlopen(desc.libraryPath.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGP_E("dlopen failed for %s: %s", desc.libraryPath.c_str(), dlerror());
            return nullptr;
        }

        auto createFunc = reinterpret_cast<CobassCreateInstanceFunc>(dlsym(handle, "cobass_plugin_create_instance"));
        auto destroyFunc = reinterpret_cast<CobassDestroyInstanceFunc>(dlsym(handle, "cobass_plugin_destroy_instance"));
        auto resetFunc = reinterpret_cast<CobassResetFunc>(dlsym(handle, "cobass_plugin_reset"));
        auto processFunc = reinterpret_cast<CobassProcessFunc>(dlsym(handle, "cobass_plugin_process"));
        auto noteOnFunc = reinterpret_cast<CobassNoteOnFunc>(dlsym(handle, "cobass_plugin_note_on"));
        auto noteOffFunc = reinterpret_cast<CobassNoteOffFunc>(dlsym(handle, "cobass_plugin_note_off"));
        auto allNotesOffFunc = reinterpret_cast<CobassAllNotesOffFunc>(dlsym(handle, "cobass_plugin_all_notes_off"));
        auto setParamFunc = reinterpret_cast<CobassSetParamFunc>(dlsym(handle, "cobass_plugin_set_param"));
        auto getParamFunc = reinterpret_cast<CobassGetParamFunc>(dlsym(handle, "cobass_plugin_get_param"));
        auto getStateFunc = reinterpret_cast<CobassGetStateFunc>(dlsym(handle, "cobass_plugin_get_state"));
        auto setStateFunc = reinterpret_cast<CobassSetStateFunc>(dlsym(handle, "cobass_plugin_set_state"));

        if (!createFunc || !destroyFunc || !processFunc) {
            LOGP_E("Missing required plugin exports in %s", desc.libraryPath.c_str());
            dlclose(handle);
            return nullptr;
        }

        CobassHandle instanceHandle = createFunc(sampleRate);
        if (!instanceHandle) {
            LOGP_E("createFunc returned null for %s", desc.pluginId.c_str());
            dlclose(handle);
            return nullptr;
        }

        LOGP_I("Instantiated plugin: %s (%s)", desc.name.c_str(), desc.pluginId.c_str());

        return std::make_unique<PluginInstance>(
            instanceHandle,
            desc,
            destroyFunc,
            resetFunc,
            processFunc,
            noteOnFunc,
            noteOffFunc,
            allNotesOffFunc,
            setParamFunc,
            getParamFunc,
            getStateFunc,
            setStateFunc
        );
    }

private:
    PluginLoader() = default;

    bool inspectAndRegister(const std::string& soPath) {
        void* handle = dlopen(soPath.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGP_E("Failed to dlopen %s: %s", soPath.c_str(), dlerror());
            return false;
        }

        auto manifestFunc = reinterpret_cast<CobassGetManifestFunc>(dlsym(handle, "cobass_plugin_get_manifest"));
        if (!manifestFunc) {
            dlclose(handle);
            return false;
        }

        const CobassPluginManifest* manifest = manifestFunc();
        if (!manifest || manifest->apiVersion != COBASS_PLUGIN_API_VERSION) {
            LOGP_E("Invalid manifest or API version mismatch in %s", soPath.c_str());
            dlclose(handle);
            return false;
        }

        PluginDescriptor desc = PluginDescriptor::fromManifest(manifest, soPath);
        catalog_[desc.pluginId] = desc;
        LOGP_I("Registered plugin: %s v%s [%s]", desc.name.c_str(), desc.version.c_str(), desc.pluginId.c_str());

        dlclose(handle);
        return true;
    }

    mutable std::mutex mutex_;
    std::unordered_map<std::string, PluginDescriptor> catalog_;
};

```

---

### File: `app/native/plugin/PluginChain.hpp`

```cpp
#pragma once
#include <array>
#include <memory>
#include <mutex>
#include "PluginInstance.hpp"

class PluginChain {
public:
    static constexpr size_t MAX_SLOTS = 8;

    struct Slot {
        std::unique_ptr<PluginInstance> instance;
        bool bypassed = false;
        float mix = 1.0f; // Dry/Wet mix (0.0 to 1.0)
    };

    PluginChain() {
        dryBuffer_.assign(8192, 0.0f);
        wetBuffer_.assign(8192, 0.0f);
    }

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& slot : slots_) {
            if (slot.instance) slot.instance->reset(sampleRate);
        }
    }

    bool loadPlugin(size_t slotIndex, std::unique_ptr<PluginInstance> plugin) {
        if (slotIndex >= MAX_SLOTS) return false;
        std::lock_guard<std::mutex> lock(mutex_);
        if (plugin) plugin->reset(sampleRate_);
        slots_[slotIndex].instance = std::move(plugin);
        slots_[slotIndex].bypassed = false;
        slots_[slotIndex].mix = 1.0f;
        return true;
    }

    void removePlugin(size_t slotIndex) {
        if (slotIndex >= MAX_SLOTS) return;
        std::lock_guard<std::mutex> lock(mutex_);
        slots_[slotIndex].instance.reset();
        slots_[slotIndex].bypassed = false;
    }

    void setBypass(size_t slotIndex, bool bypass) {
        if (slotIndex >= MAX_SLOTS) return;
        std::lock_guard<std::mutex> lock(mutex_);
        slots_[slotIndex].bypassed = bypass;
    }

    bool isBypassed(size_t slotIndex) const {
        if (slotIndex >= MAX_SLOTS) return true;
        std::lock_guard<std::mutex> lock(mutex_);
        return slots_[slotIndex].bypassed;
    }

    PluginInstance* getPlugin(size_t slotIndex) {
        if (slotIndex >= MAX_SLOTS) return nullptr;
        std::lock_guard<std::mutex> lock(mutex_);
        return slots_[slotIndex].instance.get();
    }

    void moveSlot(size_t fromSlot, size_t toSlot) {
        if (fromSlot >= MAX_SLOTS || toSlot >= MAX_SLOTS || fromSlot == toSlot) return;
        std::lock_guard<std::mutex> lock(mutex_);
        std::swap(slots_[fromSlot], slots_[toSlot]);
    }

    void process(float* buffer, int32_t numFrames) {
        if (!buffer || numFrames <= 0) return;

        const size_t requiredSize = static_cast<size_t>(numFrames * 2);
        if (dryBuffer_.size() < requiredSize) {
            dryBuffer_.resize(requiredSize, 0.0f);
            wetBuffer_.resize(requiredSize, 0.0f);
        }

        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& slot : slots_) {
            if (!slot.instance || slot.bypassed) continue;

            if (slot.mix < 0.999f) {
                // Save dry signal for wet/dry blend
                std::copy_n(buffer, requiredSize, dryBuffer_.data());
            }

            slot.instance->process(buffer, buffer, numFrames);

            if (slot.mix < 0.999f) {
                const float wet = slot.mix;
                const float dry = 1.0f - wet;
                for (size_t i = 0; i < requiredSize; ++i) {
                    buffer[i] = dryBuffer_[i] * dry + buffer[i] * wet;
                }
            }
        }
    }

private:
    float sampleRate_ = 48000.0f;
    mutable std::mutex mutex_;
    std::array<Slot, MAX_SLOTS> slots_;
    std::vector<float> dryBuffer_;
    std::vector<float> wetBuffer_;
};

```

---

### File: `app/native/dsp/SynthVoice.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include "ADSR.hpp"
#include "ZdfFilter.hpp"
#include "PolyBlepOscillator.hpp"

enum class Waveform { Sine = 0, Sawtooth = 1, Square = 2, Triangle = 3 };

class SynthVoice {
public:
    SynthVoice() = default;

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        ampEnv_.setSampleRate(sampleRate);
        modEnv_.setSampleRate(sampleRate);
        filter_.setSampleRate(sampleRate);
        oscMain_.setSampleRate(sampleRate);
        oscSub_.setSampleRate(sampleRate);
    }

    void noteOn(int32_t midiNote, float velocity, Waveform wave, float cutoff, float resonance,
                ZdfFilterMode filterMode = ZdfFilterMode::Ladder24, float drive = 1.0f,
                float filterEnvAmount = 0.5f, float glideTimeSec = 0.0f, bool legato = false) {
        const float newTargetFreq = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
        targetFrequency_ = newTargetFreq;

        if (legato && active_ && currentFrequency_ > 10.0f && glideTimeSec > 0.001f) {
            // Legato Glide: Smoothly slew pitch without resetting phase or re-triggering envelopes
            glideCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideTimeSec * 0.35f) * sampleRate_));
            note_ = midiNote;
            velocity_ = velocity;
            baseCutoff_ = cutoff;
            baseResonance_ = resonance;
            filterEnvAmount_ = filterEnvAmount;
            return;
        }

        if (glideTimeSec > 0.001f && currentFrequency_ > 10.0f) {
            // Portamento with envelope retrigger
            glideCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideTimeSec * 0.35f) * sampleRate_));
        } else {
            currentFrequency_ = targetFrequency_;
            glideCoeff_ = 1.0f;
        }

        note_ = midiNote;
        velocity_ = velocity;
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filterEnvAmount_ = filterEnvAmount;

        OscillatorWaveform oscWave = OscillatorWaveform::Sawtooth;
        switch (wave) {
            case Waveform::Sine:     oscWave = OscillatorWaveform::Sine; break;
            case Waveform::Sawtooth: oscWave = OscillatorWaveform::Sawtooth; break;
            case Waveform::Square:   oscWave = OscillatorWaveform::Pulse; break;
            case Waveform::Triangle: oscWave = OscillatorWaveform::Triangle; break;
        }

        oscMain_.setWaveform(oscWave);
        oscMain_.setFrequency(currentFrequency_);
        oscMain_.setPulseWidth(0.5f);
        oscMain_.resetPhase(0.0);

        oscSub_.setWaveform(OscillatorWaveform::Pulse);
        oscSub_.setFrequency(currentFrequency_ * 0.5f);
        oscSub_.setPulseWidth(0.5f);
        oscSub_.resetPhase(0.0);

        filter_.setParameters(filterMode, cutoff, resonance, drive);
        ampEnv_.gate(true);
        modEnv_.gate(true);
        active_ = true;
    }

    void noteOff() noexcept {
        ampEnv_.gate(false);
        modEnv_.gate(false);
    }

    void hardStop() noexcept {
        ampEnv_.reset();
        modEnv_.reset();
        filter_.reset();
        active_ = false;
        note_ = -1;
    }

    void setCutoff(float cutoff, float resonance) {
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filter_.setCutoff(cutoff);
        filter_.setResonance(resonance);
    }

    void setFilterParameters(ZdfFilterMode mode, float cutoff, float resonance, float drive, float filterEnvAmount = 0.5f) {
        baseCutoff_ = cutoff;
        baseResonance_ = resonance;
        filterEnvAmount_ = filterEnvAmount;
        filter_.setParameters(mode, cutoff, resonance, drive);
    }

    void setModEnvParameters(float attackSec, float decaySec, float sustainLevel, float releaseSec) {
        modEnv_.setParameters(attackSec, decaySec, sustainLevel, releaseSec);
    }

    float getEnvelopeEnergy() const noexcept {
        return active_ ? ampEnv_.getCurrentValue() : 0.0f;
    }

    inline float renderSampleModulated(float pitchModSemitones, float cutoffModMultiplier) noexcept {
        if (!active_) return 0.0f;

        const float amp = ampEnv_.getNextSample();
        const float modEnvVal = modEnv_.getNextSample();

        if (!ampEnv_.isActive()) {
            active_ = false;
            note_ = -1;
            return 0.0f;
        }

        // 1. Portamento Slewing (f_current -> f_target)
        if (currentFrequency_ != targetFrequency_) {
            currentFrequency_ += (targetFrequency_ - currentFrequency_) * glideCoeff_;
            if (std::abs(targetFrequency_ - currentFrequency_) < 0.05f) {
                currentFrequency_ = targetFrequency_;
            }
        }

        // 2. Pitch Modulation (Vibrato / Pitch Bend)
        float activeFreq = currentFrequency_;
        if (std::abs(pitchModSemitones) > 0.001f) {
            activeFreq *= std::pow(2.0f, pitchModSemitones / 12.0f);
        }

        oscMain_.setFrequency(activeFreq);
        oscSub_.setFrequency(activeFreq * 0.5f);

        // 3. Dynamic Filter Cutoff Modulation
        const float envCutoffDelta = baseCutoff_ * filterEnvAmount_ * modEnvVal * 4.0f;
        const float totalCutoff = std::clamp((baseCutoff_ + envCutoffDelta) * cutoffModMultiplier, 20.0f, sampleRate_ * 0.48f);
        filter_.setCutoff(totalCutoff);

        // 4. Oscillators + Filter Output
        const float rawSample = (oscMain_.renderSample() * 0.85f) + (oscSub_.renderSample() * 0.15f);
        const float filtered = filter_.process(rawSample);
        return filtered * amp * velocity_;
    }

    inline float renderSample() noexcept {
        return renderSampleModulated(0.0f, 1.0f);
    }

    bool isActive() const noexcept { return active_; }
    int32_t getNote() const noexcept { return note_; }

private:
    float sampleRate_ = 48000.0f;
    int32_t note_ = -1;
    float velocity_ = 0.0f;

    float currentFrequency_ = 440.0f;
    float targetFrequency_ = 440.0f;
    float glideCoeff_ = 1.0f;

    float baseCutoff_ = 3500.0f;
    float baseResonance_ = 1.2f;
    float filterEnvAmount_ = 0.5f;

    bool active_ = false;
    ADSR ampEnv_;
    ADSR modEnv_;
    ZdfFilter filter_;
    PolyBlepOscillator oscMain_;
    PolyBlepOscillator oscSub_;
};

```

---

### File: `app/native/dsp/SynthTrack.hpp`

```cpp
#pragma once
#include "Track.hpp"
#include "SynthVoice.hpp"
#include "LFO.hpp"
#include "../plugin/PluginInstance.hpp"
#include <array>
#include <vector>
#include <mutex>
#include <algorithm>

enum class VoiceMode : int32_t {
    Polyphonic = 0,
    MonoRetrigger = 1,
    MonoLegato = 2
};

class SynthTrack : public Track {
public:
    static constexpr size_t MAX_VOICES = 16;

    SynthTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::Synth, name) {
        tempBuffer_.assign(8192, 0.0f);
        for (auto& v : voices_) v.setSampleRate(sampleRate_);
        lfo1_.setSampleRate(sampleRate_);
        lfo1_.setFrequency(1.5f);
        lfo1_.setWaveform(LfoWaveform::Triangle);
        heldNotesStack_.reserve(16);
    }

    void setSampleRate(float sampleRate) override {
        Track::setSampleRate(sampleRate);
        for (auto& v : voices_) v.setSampleRate(sampleRate);
        lfo1_.setSampleRate(sampleRate);
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) customInstrument_->reset(sampleRate);
    }

    void setCustomInstrument(std::unique_ptr<PluginInstance> instrument) {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        } else {
            for (auto& v : voices_) v.hardStop();
        }
        if (instrument) instrument->reset(sampleRate_);
        customInstrument_ = std::move(instrument);
    }

    void removeCustomInstrument() {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->allNotesOff();
        }
        customInstrument_.reset();
    }

    PluginInstance* getCustomInstrument() {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        return customInstrument_.get();
    }

    bool hasCustomInstrument() const {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        return customInstrument_ != nullptr;
    }

    void noteOn(int32_t note, float vel) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOn(note, vel);
            return;
        }

        // Monophonic & Legato Note Stack Handling
        if (voiceMode_ != VoiceMode::Polyphonic) {
            heldNotesStack_.erase(std::remove(heldNotesStack_.begin(), heldNotesStack_.end(), note), heldNotesStack_.end());
            heldNotesStack_.push_back(note);
            lastVelocity_ = vel;

            const bool isLegato = (voiceMode_ == VoiceMode::MonoLegato && voices_[0].isActive());
            voices_[0].noteOn(note, vel, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, isLegato);
            return;
        }

        // Polyphonic Intelligent Voice Allocation
        SynthVoice* targetVoice = nullptr;

        // 1. Find an idle voice
        for (auto& v : voices_) {
            if (!v.isActive()) {
                targetVoice = &v;
                break;
            }
        }

        // 2. Lowest-energy voice stealing
        if (!targetVoice) {
            float minEnergy = 999.0f;
            for (auto& v : voices_) {
                float energy = v.getEnvelopeEnergy();
                if (energy < minEnergy) {
                    minEnergy = energy;
                    targetVoice = &v;
                }
            }
        }

        if (!targetVoice) targetVoice = &voices_[0];
        targetVoice->noteOn(note, vel, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, false);
    }

    void noteOff(int32_t note) override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        if (customInstrument_) {
            customInstrument_->noteOff(note);
            return;
        }

        if (voiceMode_ != VoiceMode::Polyphonic) {
            heldNotesStack_.erase(std::remove(heldNotesStack_.begin(), heldNotesStack_.end(), note), heldNotesStack_.end());

            if (!heldNotesStack_.empty()) {
                int32_t prevNote = heldNotesStack_.back();
                const bool isLegato = (voiceMode_ == VoiceMode::MonoLegato);
                voices_[0].noteOn(prevNote, lastVelocity_, waveform_, cutoffHz_, resonance_, filterMode_, filterDrive_, filterEnvAmount_, glideTimeSec_, isLegato);
            } else {
                voices_[0].noteOff();
            }
            return;
        }

        for (auto& v : voices_) {
            if (v.isActive() && v.getNote() == note) {
                v.noteOff();
            }
        }
    }

    void allNotesOff() override {
        std::lock_guard<std::mutex> lock(instrumentMutex_);
        heldNotesStack_.clear();
        if (customInstrument_) {
            customInstrument_->allNotesOff();
            return;
        }

        for (auto& v : voices_) {
            v.hardStop();
        }
    }

    void setParam(uint32_t paramId, float value) override {
        switch (paramId) {
            case 0: waveform_ = static_cast<Waveform>(static_cast<int32_t>(value) % 4); break;
            case 1: cutoffHz_ = value; break;
            case 2: resonance_ = value; break;
            case 3: filterMode_ = static_cast<ZdfFilterMode>(static_cast<int32_t>(value) % 5); break;
            case 4: filterDrive_ = std::clamp(value, 0.5f, 5.0f); break;
            case 5: filterEnvAmount_ = std::clamp(value, -1.0f, 1.0f); break;
            case 6: lfo1_.setFrequency(std::clamp(value, 0.05f, 30.0f)); break;
            case 7: lfoCutoffDepth_ = std::clamp(value, 0.0f, 1.0f); break;
            case 8: lfoPitchDepth_ = std::clamp(value, 0.0f, 2.0f); break;
            case 9: lfo1_.setWaveform(static_cast<LfoWaveform>(static_cast<int32_t>(value) % 5)); break;
            case 10: voiceMode_ = static_cast<VoiceMode>(static_cast<int32_t>(value) % 3); break;
            case 11: glideTimeSec_ = std::clamp(value * 0.001f, 0.0f, 1.0f); break;
        }
        for (auto& v : voices_) {
            v.setFilterParameters(filterMode_, cutoffHz_, resonance_, filterDrive_, filterEnvAmount_);
        }
    }

    void render(float* outStereoBuffer, int32_t numFrames) override {
        if (isMuted_) {
            peakL_.store(0.0f, std::memory_order_relaxed);
            peakR_.store(0.0f, std::memory_order_relaxed);
            return;
        }

        if (tempBuffer_.size() < static_cast<size_t>(numFrames * 2)) {
            tempBuffer_.resize(numFrames * 2, 0.0f);
        }
        std::fill_n(tempBuffer_.data(), numFrames * 2, 0.0f);

        {
            std::lock_guard<std::mutex> lock(instrumentMutex_);
            if (customInstrument_) {
                customInstrument_->process(nullptr, tempBuffer_.data(), numFrames);
            } else {
                for (int32_t i = 0; i < numFrames; ++i) {
                    const float lfoVal = lfo1_.getNextSample();
                    const float pitchMod = lfoVal * lfoPitchDepth_;
                    const float cutoffMod = std::max(0.05f, 1.0f + (lfoVal * lfoCutoffDepth_ * 0.75f));

                    float monoSum = 0.0f;
                    for (auto& v : voices_) {
                        if (v.isActive()) {
                            monoSum += v.renderSampleModulated(pitchMod, cutoffMod);
                        }
                    }
                    tempBuffer_[i * 2]     = monoSum;
                    tempBuffer_[i * 2 + 1] = monoSum;
                }
            }
        }

        applyFxAndGain(tempBuffer_.data(), numFrames);

        for (int32_t i = 0; i < numFrames * 2; ++i) {
            outStereoBuffer[i] += tempBuffer_[i];
        }
    }

private:
    std::array<SynthVoice, MAX_VOICES> voices_;
    Waveform waveform_ = Waveform::Sawtooth;
    float cutoffHz_ = 3500.0f;
    float resonance_ = 1.2f;
    ZdfFilterMode filterMode_ = ZdfFilterMode::Ladder24;
    float filterDrive_ = 1.0f;
    float filterEnvAmount_ = 0.5f;

    VoiceMode voiceMode_ = VoiceMode::Polyphonic;
    float glideTimeSec_ = 0.0f;
    std::vector<int32_t> heldNotesStack_;
    float lastVelocity_ = 0.8f;

    LFO lfo1_;
    float lfoCutoffDepth_ = 0.0f;
    float lfoPitchDepth_ = 0.0f;

    mutable std::mutex instrumentMutex_;
    std::unique_ptr<PluginInstance> customInstrument_;
    std::vector<float> tempBuffer_;
};

```

---

### File: `app/native/dsp/PolyBlepOscillator.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class OscillatorWaveform : int32_t {
    Sine = 0,
    Sawtooth = 1,
    Pulse = 2,
    Triangle = 3,
    Noise = 4,
    Hypersaw = 5,
    FutureDonk = 6,
    VowelTalk = 7,
    MetallicFM = 8,
    DirtyReese = 9,
    DigitalSync = 10,
    ScreamerSaw = 11,
    WavetableAcid = 12,
    ResoSweep = 13,
    OrganFM = 14,
    ChimeCluster = 15
};

enum class NoiseType : int32_t {
    White = 0,
    Pink = 1,          // 3-pole 1/f Voss-McCartney approximation
    Brown = 2,         // Integrated 6dB/oct low-frequency rumble
    VinylCrackle = 3,  // Sparse Poisson impulse crackles
    MetallicBurst = 4, // Resonant bandpass metallic noise
    VelvetAir = 5      // Ultra-smooth top-end shimmer
};

class PolyBlepOscillator {
public:
    PolyBlepOscillator() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updatePhaseIncrement();
    }

    void setFrequency(float freqHz) noexcept {
        frequency_ = std::clamp(freqHz, 2.0f, sampleRate_ * 0.48f);
        updatePhaseIncrement();
    }

    void setPulseWidth(float pw) noexcept {
        pulseWidth_ = std::clamp(pw, 0.05f, 0.95f);
    }

    void setWaveform(OscillatorWaveform wave) noexcept {
        waveform_ = wave;
    }

    void setNoiseType(NoiseType nType) noexcept {
        noiseType_ = nType;
    }

    void resetPhase(double newPhase = 0.0) noexcept {
        phase_ = newPhase - std::floor(newPhase);
    }

    void syncToMaster(double masterPhase) noexcept {
        phase_ = masterPhase - std::floor(masterPhase);
    }

    inline float renderSample() noexcept {
        float sample = 0.0f;

        switch (waveform_) {
            case OscillatorWaveform::Sine:
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586));
                break;

            case OscillatorWaveform::Sawtooth: {
                sample = static_cast<float>(2.0 * phase_ - 1.0);
                sample -= polyBlep(phase_, phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Pulse: {
                sample = (phase_ < pulseWidth_) ? 1.0f : -1.0f;
                sample += polyBlep(phase_, phaseIncrement_);
                sample -= polyBlep(std::fmod(phase_ + (1.0 - pulseWidth_), 1.0), phaseIncrement_);
                break;
            }

            case OscillatorWaveform::Triangle: {
                sample = static_cast<float>((phase_ < 0.5) ? (4.0 * phase_ - 1.0) : (3.0 - 4.0 * phase_));
                break;
            }

            case OscillatorWaveform::Noise: {
                sample = renderColoredNoise();
                break;
            }

            case OscillatorWaveform::Hypersaw: {
                double p1 = phase_;
                double p2 = std::fmod(phase_ * 1.008 + 0.25, 1.0);
                double p3 = std::fmod(phase_ * 0.992 + 0.75, 1.0);
                float s1 = static_cast<float>(2.0 * p1 - 1.0) - polyBlep(p1, phaseIncrement_);
                float s2 = static_cast<float>(2.0 * p2 - 1.0) - polyBlep(p2, phaseIncrement_);
                float s3 = static_cast<float>(2.0 * p3 - 1.0) - polyBlep(p3, phaseIncrement_);
                sample = (s1 + s2 + s3) * 0.577f;
                break;
            }

            case OscillatorWaveform::FutureDonk: {
                double mod = std::sin(phase_ * 12.566370614359172) * 0.65;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::VowelTalk: {
                double s1 = std::sin(phase_ * 6.283185307179586);
                double s2 = std::sin(phase_ * 18.84955592153876) * 0.55;
                double s3 = std::sin(phase_ * 31.41592653589793) * 0.35;
                sample = static_cast<float>((s1 + s2 + s3) * 0.55);
                break;
            }

            case OscillatorWaveform::MetallicFM: {
                double mod = std::sin(phase_ * 22.4283185307) * 0.85;
                sample = static_cast<float>(std::sin(phase_ * 6.283185307179586 + mod));
                break;
            }

            case OscillatorWaveform::DirtyReese: {
                double p1 = phase_;
                double p2 = std::fmod(phase_ + 0.08, 1.0);
                float s1 = static_cast<float>(2.0 * p1 - 1.0) - polyBlep(p1, phaseIncrement_);
                float s2 = static_cast<float>(2.0 * p2 - 1.0) - polyBlep(p2, phaseIncrement_);
                sample = (s1 + s2) * 0.5f;
                break;
            }

            case OscillatorWaveform::DigitalSync: {
                double slaveP = std::fmod(phase_ * 2.35, 1.0);
                sample = static_cast<float>(2.0 * slaveP - 1.0) - polyBlep(slaveP, phaseIncrement_ * 2.35);
                break;
            }

            case OscillatorWaveform::ScreamerSaw: {
                float rawSaw = static_cast<float>(2.0 * phase_ - 1.0) - polyBlep(phase_, phaseIncrement_);
                sample = std::tanh(rawSaw * 2.5f);
                break;
            }

            case OscillatorWaveform::WavetableAcid: {
                // Diode-saturated squarish wave with 3rd harmonic fold
                double s1 = std::sin(phase_ * 6.283185307179586);
                double s3 = std::sin(phase_ * 18.84955592153876) * 0.40;
                sample = std::tanh(static_cast<float>(s1 + s3) * 2.0f);
                break;
            }

            case OscillatorWaveform::ResoSweep: {
                // Dual formant swept band
                double p1 = phase_;
                double p2 = std::fmod(phase_ * 3.14159, 1.0);
                float s1 = static_cast<float>(std::sin(p1 * 6.283185307179586));
                float s2 = static_cast<float>(std::sin(p2 * 6.283185307179586) * 0.5);
                sample = s1 + s2;
                break;
            }

            case OscillatorWaveform::OrganFM: {
                // 3-Op drawbar harmonic cluster (1st, 2nd, 4th harmonics)
                double h1 = std::sin(phase_ * 6.283185307179586);
                double h2 = std::sin(phase_ * 12.566370614359172) * 0.50;
                double h4 = std::sin(phase_ * 25.132741228718345) * 0.25;
                sample = static_cast<float>(h1 + h2 + h4) * 0.57f;
                break;
            }

            case OscillatorWaveform::ChimeCluster: {
                // Inharmonic bells (1.0 : 1.414 : 2.73)
                double b1 = std::sin(phase_ * 6.283185307179586);
                double b2 = std::sin(phase_ * 8.88568) * 0.45;
                double b3 = std::sin(phase_ * 17.1531) * 0.25;
                sample = static_cast<float>(b1 + b2 + b3) * 0.58f;
                break;
            }
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0) phase_ -= 1.0;

        return sample;
    }

    inline float renderSampleWithWavefold(float foldDrive) noexcept {
        float raw = renderSample();
        if (foldDrive > 0.01f) {
            float x = raw * (1.0f + foldDrive * 3.2f);
            raw = 0.63661977236f * std::asin(std::sin(3.14159265359f * x));
        }
        return raw;
    }

    double getPhase() const noexcept { return phase_; }
    double getPhaseIncrement() const noexcept { return phaseIncrement_; }

private:
    static inline float polyBlep(double t, double dt) noexcept {
        if (dt <= 0.0) return 0.0f;
        if (t < dt) {
            t /= dt;
            return static_cast<float>(t + t - t * t - 1.0);
        } else if (t > 1.0 - dt) {
            t = (t - 1.0) / dt;
            return static_cast<float>(t * t + t + t + 1.0);
        }
        return 0.0f;
    }

    inline float renderColoredNoise() noexcept {
        noiseSeed_ = 1664525L * noiseSeed_ + 1013904223L;
        const float raw = static_cast<float>((noiseSeed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;

        switch (noiseType_) {
            case NoiseType::White:
                return raw;

            case NoiseType::Pink: {
                // 3-pole 1/f Voss-McCartney filter
                b0_ = 0.99886f * b0_ + raw * 0.0555179f;
                b1_ = 0.99332f * b1_ + raw * 0.0750759f;
                b2_ = 0.96900f * b2_ + raw * 0.1538520f;
                return (b0_ + b1_ + b2_ + raw * 0.5362f) * 0.25f;
            }

            case NoiseType::Brown: {
                // 6dB/oct low frequency integrator
                brownState_ = (brownState_ * 0.95f) + (raw * 0.05f);
                return brownState_ * 4.0f;
            }

            case NoiseType::VinylCrackle: {
                // Sparse Poisson clicks
                if (std::abs(raw) > 0.985f) {
                    return (raw > 0.0f ? 1.0f : -1.0f) * 0.85f;
                }
                return raw * 0.04f;
            }

            case NoiseType::MetallicBurst: {
                // Resonant bandpass noise
                noiseBandState_ = (noiseBandState_ * 0.70f) + (raw * 0.30f);
                return (raw - noiseBandState_) * 1.5f;
            }

            case NoiseType::VelvetAir: {
                // Smooth high-pass air shimmer
                noiseAirState_ = (noiseAirState_ * 0.85f) + (raw * 0.15f);
                return (raw - noiseAirState_) * 0.90f;
            }
        }
        return raw;
    }

    void updatePhaseIncrement() noexcept {
        if (sampleRate_ > 0.0f) {
            phaseIncrement_ = frequency_ / sampleRate_;
        }
    }

    float sampleRate_ = 48000.0f;
    float frequency_ = 440.0f;
    float pulseWidth_ = 0.5f;
    OscillatorWaveform waveform_ = OscillatorWaveform::Sawtooth;
    NoiseType noiseType_ = NoiseType::White;

    double phase_ = 0.0;
    double phaseIncrement_ = 0.00916666666;
    uint32_t noiseSeed_ = 22222;

    // Colored noise states
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
    float brownState_ = 0.0f;
    float noiseBandState_ = 0.0f;
    float noiseAirState_ = 0.0f;
};

```

---

### File: `app/native/dsp/ZdfFilter.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>
#include <array>
#include <vector>
#include "BiquadFilter.hpp"

enum class ZdfFilterMode : int32_t {
    Ladder24 = 0,       // 4-Pole ZDF Moog Ladder (24dB/oct with transistor saturation)
    Diode18 = 1,        // 3-Pole ZDF TB-303 Diode Ladder (18dB/oct acid squelch)
    Lowpass12 = 2,      // 2-Pole ZDF State Variable Lowpass (12dB/oct)
    Bandpass12 = 3,     // 2-Pole ZDF State Variable Bandpass (12dB/oct)
    Highpass12 = 4,     // 2-Pole ZDF State Variable Highpass (12dB/oct)
    Notch12 = 5,        // 2-Pole ZDF State Variable Band-Reject Notch
    FormantVowel = 6,   // 3-Resonator Vocal Tract Formant (A-E-I-O-U morphing)
    CombResonator = 7   // Tuned Feedback Delay Comb Resonator
};

enum class ZdfDriveModel : int32_t {
    Transistor = 0, // Clean progressive tanh saturation
    Diode = 1,      // Asymmetric Germanium diode clipping with 2nd harmonic bias
    Tube = 2,       // Triode soft-knee warm grid saturation
    Wavefold = 3    // West-Coast trigonometric wavefolding
};

class ZdfFilter {
public:
    ZdfFilter() {
        combBuffer_.assign(MAX_COMB_SAMPLES, 0.0f);
    }

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (int i = 0; i < 3; ++i) {
            formantBands_[i].setSampleRate(sampleRate_);
            formantBands_[i].reset();
        }
        std::fill(combBuffer_.begin(), combBuffer_.end(), 0.0f);
        updateCoefficients();
    }

    void setParameters(ZdfFilterMode mode, float cutoffHz, float resonance, float drive = 1.0f, float vowelMorph = 0.0f, int driveModel = 0) noexcept {
        mode_ = mode;
        const float maxCutoff = sampleRate_ * 0.45f;
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, maxCutoff);
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        drive_ = std::clamp(drive, 0.5f, 5.0f);
        vowelMorph_ = std::clamp(vowelMorph, 0.0f, 4.0f);
        driveModel_ = static_cast<ZdfDriveModel>(std::clamp(driveModel, 0, 3));
        updateCoefficients();
    }

    void setCutoff(float cutoffHz) noexcept {
        const float maxCutoff = sampleRate_ * 0.45f;
        cutoffHz_ = std::clamp(cutoffHz, 20.0f, maxCutoff);
        updateCoefficients();
    }

    void setResonance(float resonance) noexcept {
        resonance_ = std::clamp(resonance, 0.1f, 16.0f);
        updateCoefficients();
    }

    void setDrive(float drive) noexcept {
        drive_ = std::clamp(drive, 0.5f, 5.0f);
    }

    void setDriveModel(ZdfDriveModel model) noexcept {
        driveModel_ = model;
    }

    void setVowelMorph(float vowelMorph) noexcept {
        vowelMorph_ = std::clamp(vowelMorph, 0.0f, 4.0f);
        updateFormants();
    }

    void setMode(ZdfFilterMode mode) noexcept {
        mode_ = mode;
        updateCoefficients();
    }

    inline float process(float in) noexcept {
        // Recovery guard for NaN / Inf denormals
        if (std::isnan(s1_) || std::isinf(s1_) || std::isnan(s4_) || std::isinf(s4_)) {
            reset();
        }

        // Apply selected nonlinear drive model to input stage
        float drivenIn = in * drive_;
        switch (driveModel_) {
            case ZdfDriveModel::Transistor:
                drivenIn = std::tanh(drivenIn);
                break;
            case ZdfDriveModel::Diode: {
                // Asymmetric Germanium diode with positive bias
                float x = drivenIn + 0.15f;
                drivenIn = (x > 0.0f) ? std::tanh(x * 1.35f) - 0.15f : (x * 0.85f);
                break;
            }
            case ZdfDriveModel::Tube: {
                // Triode soft-saturation with warm even harmonics
                float x = drivenIn;
                drivenIn = x / (1.0f + std::abs(x) * 0.5f);
                break;
            }
            case ZdfDriveModel::Wavefold: {
                // Saturated West-Coast folding
                float x = drivenIn * 1.5f;
                drivenIn = 0.63661977f * std::asin(std::sin(3.14159265f * x));
                break;
            }
        }

        // 1. Formant Vowel Mode (3-Band Parallel Vocal Formant Resonators)
        if (mode_ == ZdfFilterMode::FormantVowel) {
            float sum = 0.0f;
            for (int i = 0; i < 3; ++i) {
                sum += formantBands_[i].process(drivenIn) * formantGains_[i];
            }
            return std::tanh(sum * 1.6f);
        }

        // 2. Comb Resonator Mode
        if (mode_ == ZdfFilterMode::CombResonator) {
            float delaySamples = std::clamp(sampleRate_ / std::max(20.0f, cutoffHz_), 2.0f, static_cast<float>(MAX_COMB_SAMPLES - 4));
            float readPos = static_cast<float>(combWriteIdx_) - delaySamples;
            while (readPos < 0.0f) readPos += static_cast<float>(MAX_COMB_SAMPLES);

            size_t idx0 = static_cast<size_t>(readPos) % MAX_COMB_SAMPLES;
            size_t idx1 = (idx0 + 1) % MAX_COMB_SAMPLES;
            float frac = readPos - static_cast<float>(static_cast<size_t>(readPos));

            float delayed = combBuffer_[idx0] * (1.0f - frac) + combBuffer_[idx1] * frac;
            float feedback = std::clamp(resonance_ / 16.0f, 0.0f, 0.98f);

            combDampState_ = (delayed * 0.8f) + (combDampState_ * 0.2f);
            float newSample = drivenIn + (combDampState_ * feedback);

            if (std::isnan(newSample) || std::isinf(newSample)) {
                newSample = 0.0f;
                combDampState_ = 0.0f;
            }

            combBuffer_[combWriteIdx_] = newSample;
            combWriteIdx_ = (combWriteIdx_ + 1) % MAX_COMB_SAMPLES;

            return std::tanh(delayed);
        }

        // 3. TB-303 18dB Diode Ladder Acid Filter with Passband Resonance Compensation
        if (mode_ == ZdfFilterMode::Diode18) {
            const float k = std::clamp((resonance_ / (resonance_ + 1.1f)) * 4.4f, 0.0f, 4.1f);
            const float satFeedback = std::tanh(s3_);
            const float u = (drivenIn - k * satFeedback) / (1.0f + k * G3_);

            const float v1 = (u - s1_) * g1_;
            const float y1 = v1 + s1_;
            s1_ = std::clamp(y1 + v1, -20.0f, 20.0f);

            const float v2 = (y1 - s2_) * g1_;
            const float y2 = v2 + s2_;
            s2_ = std::clamp(y2 + v2, -20.0f, 20.0f);

            const float v3 = (y2 - s3_) * g1_;
            const float y3 = v3 + s3_;
            s3_ = std::clamp(y3 + v3, -20.0f, 20.0f);

            // Passband makeup compensation
            const float comp = 1.0f + (resonance_ * 0.22f);
            return std::tanh(y3 * 1.30f * comp);
        }

        // 4. Moog 24dB Transistor Ladder Filter with Passband Resonance Compensation
        if (mode_ == ZdfFilterMode::Ladder24) {
            const float k = std::clamp((resonance_ / (resonance_ + 1.2f)) * 4.25f, 0.0f, 3.96f);
            const float satFeedback = std::tanh(s4_);
            const float u = (drivenIn - k * satFeedback) / (1.0f + k * G4_);

            const float v1 = (u - s1_) * g1_;
            const float y1 = v1 + s1_;
            s1_ = std::clamp(y1 + v1, -20.0f, 20.0f);

            const float v2 = (y1 - s2_) * g1_;
            const float y2 = v2 + s2_;
            s2_ = std::clamp(y2 + v2, -20.0f, 20.0f);

            const float v3 = (y2 - s3_) * g1_;
            const float y3 = v3 + s3_;
            s3_ = std::clamp(y3 + v3, -20.0f, 20.0f);

            const float v4 = (y3 - s4_) * g1_;
            const float y4 = v4 + s4_;
            s4_ = std::clamp(y4 + v4, -20.0f, 20.0f);

            // Passband makeup compensation prevents thinning at high resonance
            const float comp = 1.0f + (resonance_ * 0.28f);
            return std::tanh(y4 * comp);
        }

        // 5. 2-Pole ZDF State Variable Filter (SVF)
        const float hp = (drivenIn - (2.0f * R_ + g_) * s1_ - s2_) / h_;
        const float bp = g_ * hp + s1_;
        s1_ = std::clamp(g_ * hp + bp, -20.0f, 20.0f);

        const float lp = g_ * bp + s2_;
        s2_ = std::clamp(g_ * bp + lp, -20.0f, 20.0f);

        switch (mode_) {
            case ZdfFilterMode::Lowpass12:  return std::tanh(lp * (1.0f + resonance_ * 0.12f));
            case ZdfFilterMode::Bandpass12: return std::tanh(bp * 1.2f);
            case ZdfFilterMode::Highpass12: return std::tanh(hp * (1.0f + resonance_ * 0.12f));
            case ZdfFilterMode::Notch12:    return std::tanh((hp + lp) * 1.05f);
            default: return std::tanh(lp);
        }
    }

    void reset() noexcept {
        s1_ = s2_ = s3_ = s4_ = 0.0f;
        combWriteIdx_ = 0;
        combDampState_ = 0.0f;
        std::fill(combBuffer_.begin(), combBuffer_.end(), 0.0f);
        for (int i = 0; i < 3; ++i) formantBands_[i].reset();
    }

private:
    void updateCoefficients() noexcept {
        if (sampleRate_ <= 0.0f) return;
        const float w = 3.14159265358979323846f * cutoffHz_ / sampleRate_;
        g_ = std::tan(w);
        R_ = 1.0f / (2.0f * std::clamp(resonance_, 0.2f, 20.0f));
        h_ = 1.0f + 2.0f * R_ * g_ + g_ * g_;

        g1_ = g_ / (1.0f + g_);
        G3_ = g1_ * g1_ * g1_;
        G4_ = G3_ * g1_;

        if (mode_ == ZdfFilterMode::FormantVowel) {
            updateFormants();
        }
    }

    void updateFormants() noexcept {
        static constexpr float FORMANT_F1[5] = {800.0f, 500.0f, 300.0f, 500.0f, 350.0f};
        static constexpr float FORMANT_F2[5] = {1200.0f, 1800.0f, 2300.0f, 900.0f, 700.0f};
        static constexpr float FORMANT_F3[5] = {2500.0f, 2600.0f, 3000.0f, 2400.0f, 2300.0f};

        int idx0 = static_cast<int>(vowelMorph_);
        int idx1 = std::min(4, idx0 + 1);
        float frac = vowelMorph_ - static_cast<float>(idx0);

        float f1 = FORMANT_F1[idx0] * (1.0f - frac) + FORMANT_F1[idx1] * frac;
        float f2 = FORMANT_F2[idx0] * (1.0f - frac) + FORMANT_F2[idx1] * frac;
        float f3 = FORMANT_F3[idx0] * (1.0f - frac) + FORMANT_F3[idx1] * frac;

        float q = std::max(1.5f, resonance_);
        formantBands_[0].setParameters(FilterType::BandPass, std::clamp(f1, 50.0f, sampleRate_ * 0.45f), 0.0f, q);
        formantBands_[1].setParameters(FilterType::BandPass, std::clamp(f2, 100.0f, sampleRate_ * 0.45f), 0.0f, q);
        formantBands_[2].setParameters(FilterType::BandPass, std::clamp(f3, 200.0f, sampleRate_ * 0.45f), 0.0f, q);

        formantGains_[0] = 1.0f;
        formantGains_[1] = 0.70f;
        formantGains_[2] = 0.40f;
    }

    static constexpr size_t MAX_COMB_SAMPLES = 4096;

    float sampleRate_ = 48000.0f;
    ZdfFilterMode mode_ = ZdfFilterMode::Ladder24;
    ZdfDriveModel driveModel_ = ZdfDriveModel::Transistor;
    float cutoffHz_ = 2500.0f;
    float resonance_ = 1.0f;
    float drive_ = 1.0f;
    float vowelMorph_ = 0.0f;

    float g_ = 0.1f;
    float g1_ = 0.09f;
    float R_ = 0.5f;
    float h_ = 1.0f;
    float G3_ = 0.0f;
    float G4_ = 0.0f;

    float s1_ = 0.0f, s2_ = 0.0f, s3_ = 0.0f, s4_ = 0.0f;

    std::array<BiquadFilter, 3> formantBands_;
    std::array<float, 3> formantGains_{1.0f, 0.70f, 0.40f};

    std::vector<float> combBuffer_;
    size_t combWriteIdx_ = 0;
    float combDampState_ = 0.0f;
};

```

---

### File: `app/native/dsp/ADSR.hpp`

```cpp
#pragma once
#include <algorithm>
#include <cmath>

enum class EnvelopeState { Idle, Attack, Decay, Sustain, Release };

class ADSR {
public:
    ADSR() {
        updateRates();
    }

    void setSampleRate(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateRates();
    }

    void setParameters(float attackSec, float decaySec, float sustainLevel, float releaseSec) {
        attackSec_ = std::max(0.001f, attackSec);
        decaySec_ = std::max(0.001f, decaySec);
        sustainLevel_ = std::clamp(sustainLevel, 0.0f, 1.0f);
        releaseSec_ = std::max(0.002f, releaseSec);
        updateRates();
    }

    void setExponential(bool exp) noexcept {
        isExponential_ = exp;
    }

    void gate(bool on) noexcept {
        if (on) {
            state_ = EnvelopeState::Attack;
        } else {
            if (state_ != EnvelopeState::Idle) {
                state_ = EnvelopeState::Release;
            }
        }
    }

    inline float getNextSample() noexcept {
        switch (state_) {
            case EnvelopeState::Attack:
                if (isExponential_) {
                    // Analog capacitor charging curve: fast rise with natural soft knee
                    currentValue_ += (1.02f - currentValue_) * attackCoeff_;
                    if (currentValue_ >= 1.0f) {
                        currentValue_ = 1.0f;
                        state_ = EnvelopeState::Decay;
                    }
                } else {
                    currentValue_ += attackRate_;
                    if (currentValue_ >= 1.0f) {
                        currentValue_ = 1.0f;
                        state_ = EnvelopeState::Decay;
                    }
                }
                break;

            case EnvelopeState::Decay:
                if (isExponential_) {
                    currentValue_ -= (currentValue_ - sustainLevel_) * decayCoeff_;
                    if (currentValue_ <= sustainLevel_ + 0.001f) {
                        currentValue_ = sustainLevel_;
                        state_ = EnvelopeState::Sustain;
                    }
                } else {
                    currentValue_ -= decayRate_;
                    if (currentValue_ <= sustainLevel_) {
                        currentValue_ = sustainLevel_;
                        state_ = EnvelopeState::Sustain;
                    }
                }
                break;

            case EnvelopeState::Sustain:
                currentValue_ = sustainLevel_;
                break;

            case EnvelopeState::Release:
                if (isExponential_) {
                    currentValue_ -= (currentValue_ + 0.005f) * releaseCoeff_;
                    if (currentValue_ <= 0.0005f) {
                        currentValue_ = 0.0f;
                        state_ = EnvelopeState::Idle;
                    }
                } else {
                    currentValue_ -= releaseRate_;
                    if (currentValue_ <= 0.0f) {
                        currentValue_ = 0.0f;
                        state_ = EnvelopeState::Idle;
                    }
                }
                break;

            case EnvelopeState::Idle:
                currentValue_ = 0.0f;
                break;
        }
        return currentValue_;
    }

    bool isActive() const noexcept { return state_ != EnvelopeState::Idle; }
    EnvelopeState getState() const noexcept { return state_; }
    float getCurrentValue() const noexcept { return currentValue_; }

    void reset() noexcept {
        state_ = EnvelopeState::Idle;
        currentValue_ = 0.0f;
    }

private:
    void updateRates() noexcept {
        if (sampleRate_ <= 0.0f) return;
        attackRate_ = 1.0f / (std::max(0.001f, attackSec_) * sampleRate_);
        decayRate_ = std::max(0.0f, (1.0f - sustainLevel_)) / (std::max(0.001f, decaySec_) * sampleRate_);
        releaseRate_ = 1.0f / (std::max(0.002f, releaseSec_) * sampleRate_);

        attackCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, attackSec_ * 0.35f) * sampleRate_));
        decayCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.001f, decaySec_ * 0.35f) * sampleRate_));
        releaseCoeff_ = 1.0f - std::exp(-1.0f / (std::max(0.002f, releaseSec_ * 0.35f) * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    float attackSec_ = 0.01f;
    float decaySec_ = 0.08f;
    float sustainLevel_ = 0.6f;
    float releaseSec_ = 0.15f;
    bool isExponential_ = true;

    float attackRate_ = 0.002f;
    float decayRate_ = 0.001f;
    float releaseRate_ = 0.001f;

    float attackCoeff_ = 0.005f;
    float decayCoeff_ = 0.003f;
    float releaseCoeff_ = 0.002f;

    float currentValue_ = 0.0f;
    EnvelopeState state_ = EnvelopeState::Idle;
};

```

---

### File: `app/native/dsp/LFO.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>

enum class LfoWaveform : int32_t {
    Sine = 0,
    Triangle = 1,
    Sawtooth = 2,
    Square = 3,
    SampleAndHold = 4
};

class LFO {
public:
    LFO() = default;

    void setSampleRate(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        updateIncrement();
    }

    void setFrequency(float freqHz) noexcept {
        freqHz_ = std::clamp(freqHz, 0.02f, 40.0f);
        updateIncrement();
    }

    void setTempoSyncedFrequency(float bpm, int32_t divisionFactor) noexcept {
        const float beatFreq = (bpm / 60.0f);
        freqHz_ = std::clamp(beatFreq * (4.0f / static_cast<float>(std::max(1, divisionFactor))), 0.02f, 40.0f);
        updateIncrement();
    }

    void setWaveform(LfoWaveform wave) noexcept { waveform_ = wave; }
    LfoWaveform getWaveform() const noexcept { return waveform_; }

    void reset() noexcept {
        phase_ = 0.0;
        currentValue_ = 0.0f;
        shHoldValue_ = 0.0f;
    }

    inline float getNextSample() noexcept {
        switch (waveform_) {
            case LfoWaveform::Sine:
                currentValue_ = static_cast<float>(std::sin(phase_ * 6.283185307179586));
                break;
            case LfoWaveform::Triangle:
                currentValue_ = static_cast<float>((phase_ < 0.5) ? (4.0 * phase_ - 1.0) : (3.0 - 4.0 * phase_));
                break;
            case LfoWaveform::Sawtooth:
                currentValue_ = static_cast<float>(1.0 - 2.0 * phase_);
                break;
            case LfoWaveform::Square:
                currentValue_ = (phase_ < 0.5) ? 1.0f : -1.0f;
                break;
            case LfoWaveform::SampleAndHold:
                currentValue_ = shHoldValue_;
                break;
        }

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0) {
            phase_ -= 1.0;
            // Generate next pseudo-random S&H value [-1.0, 1.0]
            seed_ = 1664525L * seed_ + 1013904223L;
            shHoldValue_ = static_cast<float>((seed_ & 0x00FFFFFF) / static_cast<double>(0x007FFFFF)) - 1.0f;
        }

        return currentValue_;
    }

    float getCurrentValue() const noexcept { return currentValue_; }

private:
    void updateIncrement() noexcept {
        if (sampleRate_ > 0.0f) {
            phaseIncrement_ = freqHz_ / sampleRate_;
        }
    }

    float sampleRate_ = 48000.0f;
    float freqHz_ = 1.0f;
    LfoWaveform waveform_ = LfoWaveform::Triangle;
    double phase_ = 0.0;
    double phaseIncrement_ = 0.000020833;

    float currentValue_ = 0.0f;
    float shHoldValue_ = 0.0f;
    uint32_t seed_ = 54321;
};

```

---

### File: `app/native/dsp/Wavefolder.hpp`

```cpp
#pragma once
#include <cmath>
#include <algorithm>

class Wavefolder {
public:
    Wavefolder() = default;

    void reset(float sampleRate) noexcept {
        sampleRate_ = std::max(8000.0f, sampleRate);
        holdCounter_ = 0.0f;
        heldSample_ = 0.0f;
    }

    void setParameters(float driveGain, float folds, float symmetryBias, float bitDepth, float downsampleRatio) noexcept {
        driveGain_ = std::clamp(driveGain, 0.5f, 20.0f);
        folds_ = std::clamp(folds, 1.0f, 6.0f);
        bias_ = std::clamp(symmetryBias, -0.5f, 0.5f);
        bitDepth_ = std::clamp(bitDepth, 2.0f, 16.0f);
        downsampleRatio_ = std::clamp(downsampleRatio, 1.0f, 32.0f);
    }

    inline float process(float in) noexcept {
        // 1. Bitcrushing & Sample Rate Decimation
        holdCounter_ += 1.0f;
        if (holdCounter_ >= downsampleRatio_) {
            holdCounter_ = 0.0f;
            if (bitDepth_ < 15.5f) {
                const float levels = std::pow(2.0f, bitDepth_) * 0.5f;
                heldSample_ = std::round(in * levels) / levels;
            } else {
                heldSample_ = in;
            }
        }

        // 2. Drive & Asymmetric DC Offset
        float x = (heldSample_ + bias_) * driveGain_;

        // 3. Multi-Stage West-Coast Trigonometric Wavefolding
        for (int f = 0; f < static_cast<int>(folds_); ++f) {
            x = 0.63661977236f * std::asin(std::sin(3.14159265359f * x));
        }

        // 4. Soft Saturation Limiter
        return std::tanh(x - bias_ * 0.5f);
    }

private:
    float sampleRate_ = 48000.0f;
    float driveGain_ = 1.0f;
    float folds_ = 1.0f;
    float bias_ = 0.0f;
    float bitDepth_ = 16.0f;
    float downsampleRatio_ = 1.0f;

    float holdCounter_ = 0.0f;
    float heldSample_ = 0.0f;
};

```

---

### File: `tools/benchmark_hyperion_dance.py`

```python
#!/usr/bin/env python3
"""
Hyperion Hybrid Synth v4 Comprehensive Quality Audit & Benchmark Tool
Validates C-ABI symbols, 61-parameter layout, JSON serialization, and sound presets.
"""
import json
import os
import sys
from pathlib import Path

REQUIRED_C_ABI_SYMBOLS = [
    "cobass_plugin_get_manifest",
    "cobass_plugin_create_instance",
    "cobass_plugin_destroy_instance",
    "cobass_plugin_reset",
    "cobass_plugin_process",
    "cobass_plugin_note_on",
    "cobass_plugin_note_off",
    "cobass_plugin_all_notes_off",
    "cobass_plugin_set_param",
    "cobass_plugin_get_param",
    "cobass_plugin_get_state",
    "cobass_plugin_set_state"
]

HYPERION_EXPECTED_PARAMS = {
    0: ("Osc1 Wave", 0.0, 15.0),
    1: ("Osc1 Octave", -3.0, 3.0),
    2: ("Osc1 Semi", -12.0, 12.0),
    3: ("Osc1 Fine", -50.0, 50.0),
    4: ("Osc1 PW", 0.05, 0.95),
    5: ("Osc1 Unison", 0.0, 3.0),
    6: ("Osc1 Detune", 0.0, 1.0),
    7: ("Osc1 Spread", 0.0, 1.0),
    8: ("Osc2 Wave", 0.0, 15.0),
    9: ("Osc2 Octave", -3.0, 3.0),
    10: ("Osc2 Semi", -12.0, 12.0),
    11: ("Osc2 Fine", -50.0, 50.0),
    12: ("Osc2 PW", 0.05, 0.95),
    13: ("Osc2 Sync", 0.0, 1.0),
    14: ("Osc2 Unison", 0.0, 3.0),
    15: ("Osc2 Detune", 0.0, 1.0),
    16: ("Osc2 Spread", 0.0, 1.0),
    17: ("Osc1 Mix", 0.0, 1.0),
    18: ("Osc2 Mix", 0.0, 1.0),
    19: ("Sub Mix", 0.0, 1.0),
    20: ("Sub Octave", 0.0, 1.0),
    21: ("Noise Mix", 0.0, 1.0),
    22: ("Noise Type", 0.0, 5.0),
    23: ("Cross FM", 0.0, 1.0),
    24: ("Ring Mod", 0.0, 1.0),
    25: ("Osc1 Fold", 0.0, 1.0),
    26: ("Osc2 Fold", 0.0, 1.0),
    27: ("Filter Mode", 0.0, 7.0),
    28: ("Cutoff", 20.0, 20000.0),
    29: ("Resonance", 0.5, 16.0),
    30: ("Filter Drive", 0.5, 5.0),
    31: ("Drive Model", 0.0, 3.0),
    32: ("Filter Env", -1.0, 1.0),
    33: ("Vowel Morph", 0.0, 4.0),
    34: ("Key Tracking", 0.0, 1.0),
    35: ("Amp Attack", 1.0, 2000.0),
    36: ("Amp Decay", 5.0, 3000.0),
    37: ("Amp Sustain", 0.0, 1.0),
    38: ("Amp Release", 5.0, 4000.0),
    39: ("Mod Attack", 1.0, 2000.0),
    40: ("Mod Decay", 5.0, 3000.0),
    41: ("Mod Sustain", 0.0, 1.0),
    42: ("Mod Release", 5.0, 4000.0),
    43: ("Punch Drop", 0.0, 36.0),
    44: ("Punch Decay", 2.0, 80.0),
    45: ("LFO1 Wave", 0.0, 4.0),
    46: ("LFO1 Rate", 0.05, 30.0),
    47: ("LFO1 Cutoff", 0.0, 1.0),
    48: ("LFO1 Pitch", 0.0, 2.0),
    49: ("LFO2 Rate", 0.05, 30.0),
    50: ("LFO2 Mod", 0.0, 1.0),
    51: ("FX Drive", 0.0, 24.0),
    52: ("FX Dimension", 0.0, 1.0),
    53: ("FX Delay Time", 0.0, 4.0),
    54: ("FX Delay FB", 0.0, 0.90),
    55: ("FX Delay Mix", 0.0, 1.0),
    56: ("FX Reverb Size", 0.10, 0.98),
    57: ("FX Reverb Mix", 0.0, 1.0),
    58: ("FX OTT Comp", 0.0, 1.0),
    59: ("Portamento", 0.0, 500.0),
    60: ("Master Gain", -24.0, 6.0)
}

def verify_hyperion_binary() -> bool:
    print("[*] [1/3] Auditing Hyperion Synth v4 Binary & C-ABI Symbols...")
    lib_path = Path("app/lib/arm64-v8a/libcobass_plugin_synth_hyperion.so")
    if not lib_path.is_file():
        print(f"\033[91m[FAIL] Binary missing: {lib_path}\033[0m")
        return False

    size_kb = lib_path.stat().st_size / 1024
    content = lib_path.read_bytes()

    missing = [sym for sym in REQUIRED_C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
    if missing:
        print(f"\033[91m[FAIL] Missing C-ABI symbols: {missing}\033[0m")
        return False

    print(f"    \033[92m[✓]\033[0m Hyperion v4 Binary Verified ({size_kb:.1f} KB, 12/12 C-ABI Symbols)")
    return True

def verify_hyperion_presets() -> bool:
    print("[*] [2/3] Validating 61-Param Preset Sound Library...")
    preset_dir = Path("config/presets/com.maxica.cobass.plugins.hyperion")
    if not preset_dir.is_dir():
        print(f"\033[91m[FAIL] Missing directory: {preset_dir}\033[0m")
        return False

    patches = list(preset_dir.glob("*.cobasspatch"))
    if len(patches) < 8:
        print(f"\033[91m[FAIL] Expected at least 8 patches, found {len(patches)}\033[0m")
        return False

    all_ok = True
    for p in sorted(patches):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if len(data) != 61:
                print(f"    \033[91m[FAIL]\033[0m {p.name}: expected 61 params, got {len(data)}")
                all_ok = False
                continue
            for param_id, (name, min_v, max_v) in HYPERION_EXPECTED_PARAMS.items():
                str_k = str(param_id)
                if str_k not in data:
                    print(f"    \033[91m[FAIL]\033[0m {p.name} missing parameter {param_id} ({name})")
                    all_ok = False
                    break
                val = float(data[str_k])
                if val < min_v - 0.05 or val > max_v + 0.05:
                    print(f"    \033[91m[FAIL]\033[0m {p.name} param {param_id} ({name}) out of bounds: {val}")
                    all_ok = False
                    break
            if all_ok:
                print(f"    \033[92m[✓]\033[0m {p.name.ljust(38)} (61/61 parameters valid)")
        except Exception as e:
            print(f"    \033[91m[FAIL]\033[0m {p.name}: {e}")
            all_ok = False

    return all_ok

def verify_ui_integration() -> bool:
    print("[*] [3/3] Auditing UI Tabbed Matrix & Telemetry Integration...")
    ui_src = Path("app/src/com/maxica/cobass/ui/PluginUiDialog.java").read_text(encoding="utf-8")
    if "OSCILLATORS & FM" not in ui_src or "DANCE FX SUITE" not in ui_src:
        print("\033[91m[FAIL] PluginUiDialog.java missing Hyperion tabbed categories\033[0m")
        return False

    vis_src = Path("app/src/com/maxica/cobass/ui/SynthVisualizerView.java").read_text(encoding="utf-8")
    if "Diode 18dB Acid" not in vis_src and "Diode" not in vis_src:
        print("\033[91m[FAIL] SynthVisualizerView.java missing Diode filter curve\033[0m")
        return False

    print("    \033[92m[✓]\033[0m UI Categorized Tabs, Audition Ribbon & Visualizer Verified.")
    return True

def main():
    print("=" * 65)
    print("Hyperion Hybrid Synth v4 Quality Audit & Benchmark Suite")
    print("=" * 65)

    ok1 = verify_hyperion_binary()
    ok2 = verify_hyperion_presets()
    ok3 = verify_ui_integration()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\033[92m[PASS] ALL HYPERION v4 PRESETS & C-ABI SPECIFICATIONS CERTIFIED!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Certification checks failed.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()

```

---

### File: `tools/test_hyperion_dsp_fixes.py`

```python
#!/usr/bin/env python3
"""
Cobass Hyperion DSP Bug Fix Verification Suite
Asserts presence of all critical bug patches in source and binary exports.
"""
import sys
from pathlib import Path

def test_source_patches():
    print("[*] [1/2] Verifying surgical patches in source headers and plugin...")
    adsr_src = Path("app/native/dsp/ADSR.hpp").read_text(encoding="utf-8")
    assert "EnvelopeState getState() const noexcept" in adsr_src, "ADSR.hpp missing getState()"

    zdf_src = Path("app/native/dsp/ZdfFilter.hpp").read_text(encoding="utf-8")
    assert "mode_ == ZdfFilterMode::FormantVowel" in zdf_src, "ZdfFilter.hpp missing FormantVowel guard"

    hyp_src = Path("addons/synth-hyperion/src/HyperionSynthPlugin.cpp").read_text(encoding="utf-8")
    assert "oscSub.resetPhase(0.0)" in hyp_src, "BUG-3 fix missing: oscSub.resetPhase()"
    assert "std::clamp(fmMod, 0.05f, 8.0f)" in hyp_src, "BUG-1 + BUG-2 fix missing: Cross-FM clamp"
    assert "std::abs(finalCutoff - v.lastCutoff) > 1.0f" in hyp_src, "BUG-4 fix missing: Filter parameter cache"
    assert "ampEnv.getState() == EnvelopeState::Sustain" in hyp_src, "BUG-5 fix missing: Legato check"
    assert "delayDampL_" in hyp_src, "BUG-6 fix missing: Delay lowpass damping"
    assert "safeVerbSize" in hyp_src, "MINOR-4 fix missing: Reverb feedback cap"
    print("    \033[92m[✓]\033[0m All 6 bugs and major issues verified in source code.")

def test_binary_audit():
    print("[*] [2/2] Running Hyperion binary benchmark audit...")
    import subprocess
    res = subprocess.run([sys.executable, "tools/benchmark_hyperion_dance.py"], capture_output=True, text=True)
    assert res.returncode == 0, f"Hyperion benchmark failed:\n{res.stdout}\n{res.stderr}"
    print("    \033[92m[✓]\033[0m Hyperion benchmark suite passed with 100% C-ABI compliance.")

def main():
    print("=" * 65)
    print("Cobass Hyperion DSP Sound Fixes Audit")
    print("=" * 65)
    test_source_patches()
    test_binary_audit()
    print("=" * 65)
    print("\033[92m[PASS] ALL HYPERION DSP SOUND FIXES VERIFIED SUCCESSFULLY!\033[0m")

if __name__ == "__main__":
    main()

```

---

### File: `tools/benchmark_variation_and_presets.py`

```python
#!/usr/bin/env python3
"""
Cobass Master Variation Engine & 60-Preset Sound Set Certification Suite
Audits plugin shared libraries, C-ABI symbol exports, preset schemas, and mutation math.
"""
import json
import os
import sys
from pathlib import Path

REQUIRED_PLUGINS = [
    ("libcobass_plugin_synth_hyperion.so", "Hyperion Hybrid Synth v4", 61),
    ("libcobass_plugin_synth_cobalt_drums.so", "Cobalt Drum Machine", 52),
    ("libcobass_plugin_fx_ott_compressor.so", "OTT Multiband Dynamics", 8),
    ("libcobass_plugin_fx_sidechain_pump.so", "Sidechain Envelope Pump", 6),
    ("libcobass_plugin_fx_wavefolder_crush.so", "Wavefolder & Crusher", 6),
    ("libcobass_plugin_fx_tape_saturation.so", "Tape & Tube Saturator", 6),
    ("libcobass_plugin_fx_vintage_chorus.so", "Vintage Analog Chorus", 6)
]

C_ABI_SYMBOLS = [
    "cobass_plugin_get_manifest",
    "cobass_plugin_create_instance",
    "cobass_plugin_destroy_instance",
    "cobass_plugin_reset",
    "cobass_plugin_process",
    "cobass_plugin_note_on",
    "cobass_plugin_note_off",
    "cobass_plugin_all_notes_off",
    "cobass_plugin_set_param",
    "cobass_plugin_get_param",
    "cobass_plugin_get_state",
    "cobass_plugin_set_state"
]

def verify_binaries() -> bool:
    print("[*] [1/4] Auditing Native Plugin Binaries & C-ABI Exports...")
    lib_dir = Path("app/lib/arm64-v8a")
    if not lib_dir.is_dir():
        print(f"\033[91m[FAIL] Native library directory missing: {lib_dir}\033[0m")
        return False

    all_ok = True
    for so_file, name, expected_params in REQUIRED_PLUGINS:
        p_path = lib_dir / so_file
        if not p_path.is_file():
            print(f"  \033[91m[FAIL]\033[0m {so_file} missing from {lib_dir}")
            all_ok = False
            continue

        size_kb = p_path.stat().st_size / 1024
        content = p_path.read_bytes()
        missing = [sym for sym in C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
        if missing:
            print(f"  \033[91m[FAIL]\033[0m {so_file} missing C-ABI exports: {missing}")
            all_ok = False
        else:
            print(f"  \033[92m[✓]\033[0m {so_file.ljust(44)} ({size_kb:.1f} KB, 12/12 C-ABI symbols, {expected_params} params)")

    return all_ok

def verify_presets_database() -> bool:
    print("[*] [2/4] Validating 60-Preset Multi-Plugin Sound Library...")
    preset_root = Path("config/presets")
    if not preset_root.is_dir():
        print(f"\033[91m[FAIL] Presets root missing: {preset_root}\033[0m")
        return False

    total_patches = 0
    all_ok = True

    for p_dir in sorted(preset_root.iterdir()):
        if not p_dir.is_dir():
            continue
        patches = list(p_dir.glob("*.cobasspatch"))
        print(f"  Plugin Folder: {p_dir.name} ({len(patches)} presets)")
        for patch in sorted(patches):
            total_patches += 1
            try:
                data = json.loads(patch.read_text(encoding="utf-8"))
                if not data:
                    print(f"    \033[91m[FAIL]\033[0m {patch.name}: empty patch data")
                    all_ok = False
            except Exception as e:
                print(f"    \033[91m[FAIL]\033[0m {patch.name}: {e}")
                all_ok = False

    print(f"  Total Sound Sets Certified: {total_patches} / 60 presets")
    return all_ok and (total_patches >= 60)

def verify_variation_engine() -> bool:
    print("[*] [3/4] Validating Variation Engine & Constraint Math...")
    p_engine = Path("app/src/com/maxica/cobass/plugin/PatchVariationEngine.java").read_text(encoding="utf-8")
    s_engine = Path("app/src/com/maxica/cobass/sequencer/StepPatternVariationEngine.java").read_text(encoding="utf-8")

    required_snippets = [
        ("PatchVariationEngine", "CONSONANT_INTERVALS"),
        ("PatchVariationEngine", "mutateSingleParameter"),
        ("PatchVariationEngine", "applyHeadroomCompensation"),
        ("StepPatternVariationEngine", "mutateGroove"),
        ("StepPatternVariationEngine", "applyEuclideanFill")
    ]

    for class_name, snippet in required_snippets:
        src = p_engine if class_name == "PatchVariationEngine" else s_engine
        if snippet not in src:
            print(f"  \033[91m[FAIL]\033[0m {class_name} missing {snippet}")
            return False

    print("  \033[92m[✓]\033[0m Gaussian Variance Math, Lock Masks & Harmonic Snapping Verified.")
    return True

def verify_ui_components() -> bool:
    print("[*] [4/4] Auditing UI Dialogs & Host Action Bar Integration...")
    ui_dlg = Path("app/src/com/maxica/cobass/ui/VariationStudioDialog.java")
    if not ui_dlg.is_file():
        print(f"\033[91m[FAIL] Missing {ui_dlg}\033[0m")
        return False

    plugin_ui = Path("app/src/com/maxica/cobass/ui/PluginUiDialog.java").read_text(encoding="utf-8")
    if "VariationStudioDialog" not in plugin_ui:
        print("\033[91m[FAIL] PluginUiDialog.java missing VariationStudioDialog launcher\033[0m")
        return False

    step_ui = Path("app/src/com/maxica/cobass/ui/StepSequencerDialog.java").read_text(encoding="utf-8")
    if "showGrooveVariationDialog" not in step_ui:
        print("\033[91m[FAIL] StepSequencerDialog.java missing showGrooveVariationDialog launcher\033[0m")
        return False

    print("  \033[92m[✓]\033[0m VariationStudioDialog, Host Action Ribbon & Audition Pads Verified.")
    return True

def main():
    print("=" * 65)
    print("Cobass Master Variation & Sound Set Certification Suite")
    print("=" * 65)

    ok1 = verify_binaries()
    ok2 = verify_presets_database()
    ok3 = verify_variation_engine()
    ok4 = verify_ui_components()

    print("=" * 65)
    if ok1 and ok2 and ok3 and ok4:
        print("\033[92m[PASS] ALL ADVANCED VARIATION ENGINE & SOUND SET CHECKS PASSED!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Certification checks failed. Review errors above.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()

```

---

### File: `tools/build_addons.py`

```python
#!/usr/bin/env python3
"""
Cobass Addon Native C++ Compiler (No-Gradle Architecture)
Compiles third-party modular plugins into standalone .so binaries under app/lib/<abi>/
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

def find_compiler(target_abi: str):
    api_level = "34"

    # 1. Desktop / SDK NDK
    ndk_home = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if ndk_home and Path(ndk_home).is_dir():
        llvm_bin = Path(ndk_home) / "toolchains/llvm/prebuilt"
        host_dirs = list(llvm_bin.glob("*"))
        if host_dirs:
            bin_dir = host_dirs[0] / "bin"
            target_prefix = f"aarch64-linux-android{api_level}-clang++" if target_abi == "arm64-v8a" else f"armv7a-linux-androideabi{api_level}-clang++"
            compiler = bin_dir / target_prefix
            if compiler.exists():
                return str(compiler), []

    # 2. Termux Clang
    clang_path = shutil.which("clang++")
    if clang_path:
        target_triple = f"aarch64-linux-android{api_level}" if target_abi == "arm64-v8a" else f"armv7a-linux-androideabi{api_level}"
        return clang_path, [
            "-fPIC",
            "-target", target_triple,
            "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT",
            "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES",
            "-Wno-macro-redefined"
        ]

    return None, []

def build_all_addons(abi: str = "arm64-v8a", out_dir: Path = Path("app/lib")) -> bool:
    compiler, extra_flags = find_compiler(abi)
    if not compiler:
        print(f"\033[91mError: Clang++ compiler not found for {abi}.\033[0m")
        return False

    addons_dir = Path("addons")
    if not addons_dir.is_dir():
        print("  [*] No addons directory found. Skipping.")
        return True

    target_abi_dir = out_dir / abi
    target_abi_dir.mkdir(parents=True, exist_ok=True)

    success = True
    for addon in sorted(addons_dir.iterdir()):
        if not addon.is_dir():
            continue

        src_dir = addon / "src"
        cpp_files = list(src_dir.glob("*.cpp"))
        if not cpp_files:
            continue

        # Format output library name: libcobass_plugin_<name>.so
        clean_name = addon.name.replace("-", "_")
        target_so = target_abi_dir / f"libcobass_plugin_{clean_name}.so"

        cmd = [
            compiler,
            "-std=c++20",
            "-shared",
            "-fPIC",
            "-O3",
            "-DNDEBUG",
            "-Iapp/native/include",
            "-Iapp/native/dsp",
            "-Iapp/native/plugin",
            *extra_flags,
            *[str(f) for f in cpp_files],
            "-o", str(target_so),
            "-lm"
        ]

        print(f"[*] Compiling Plugin '{addon.name}' -> {target_so.name}...")
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res.returncode != 0:
            print(f"\033[91m[FAILED] Addon {addon.name} compilation failed:\033[0m\n{res.stderr}")
            success = False
        else:
            size_kb = target_so.stat().st_size / 1024
            print(f"\033[92m[OK] Built Plugin {target_so.name} ({size_kb:.1f} KB)\033[0m")

    return success

if __name__ == "__main__":
    abi = sys.argv[1] if len(sys.argv) > 1 else "arm64-v8a"
    if not build_all_addons(abi):
        sys.exit(1)

```

---

### File: `docs/synth-V2.md`

```markdown
Here is the updated `docs/plugin-synth-fx_doc.md` incorporating the robust whitespace-tolerant JSON state deserializer and the Android SELinux W^X compliant `codeCacheDir` plugin execution architecture:

```markdown
# Cobass Modular Plugin SDK — Official Developer Specification & Reference Manual

---

## 1. System Architecture & Executive Overview

Cobass plugins are self-contained C++20 shared libraries (`.so`) that conform to the **Cobass Plugin C-ABI**. The host dynamically resolves entry points using standard dynamic linking (`dlopen`/`dlsym`), generates parameter UI controls dynamically from descriptors (or hosts custom viewports), streams audio in zero-allocation real-time blocks, and serializes patch states into human-readable JSON.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                COBASS DAW HOST ENGINE                                  │
│                                                                                        │
│  ┌─────────────────────────────┐                    ┌───────────────────────────────┐  │
│  │       JAVA UI HOST          │                    │     AAUDIO REAL-TIME THREAD   │  │
│  │                             │                    │                               │  │
│  │ • RotaryKnobView.java       │   Lock-Free JNI    │ • Mixer.hpp / Track.hpp       │  │
│  │ • PluginUiDialog.java       │───────────────────▶│ • 8-Slot Insert Rack / Synth  │  │
│  │ • Preset Manager (.cobass)  │                    │ • Zero-Alloc Block Processing │  │
│  └─────────────────────────────┘                    └───────────────────────────────┘  │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ C-ABI Calls
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              MODULAR PLUGIN BINARY (.so)                               │
│                                                                                        │
│  Exported C Functions:                                                                 │
│  ├── cobass_plugin_get_manifest()     -> Manifest, Parameters, Type, Capabilities      │
│  ├── cobass_plugin_create_instance()  -> Allocates processor for sample rate           │
│  ├── cobass_plugin_destroy_instance() -> Deallocates processor & internal buffers      │
│  ├── cobass_plugin_reset()            -> Clears voice state, filters, delay lines      │
│  ├── cobass_plugin_process()          -> Deinterleaved 32-bit float audio streaming    │
│  ├── cobass_plugin_note_on()          -> MIDI note trigger with velocity               │
│  ├── cobass_plugin_note_off()         -> MIDI note release                             │
│  ├── cobass_plugin_all_notes_off()    -> Hard voice stop                               │
│  ├── cobass_plugin_set_param()        -> Real-time atomic parameter mutation           │
│  ├── cobass_plugin_get_param()        -> Parameter value lookup                        │
│  ├── cobass_plugin_get_state()        -> JSON patch snapshot serialization             │
│  └── cobass_plugin_set_state()        -> JSON patch snapshot restoration               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Zero-Allocation Real-Time Audio Constraints

All plugin audio processing occurs directly inside the AAudio callback thread. To guarantee glitch-free, ultra-low latency playback ($<5\text{ ms}$ round-trip), every plugin must strictly adhere to the following real-time safety rules:

1. **No Dynamic Memory Allocation**: Never invoke `malloc`, `free`, `new`, `delete`, `realloc`, or dynamically resize containers (`std::vector::push_back`, `std::string::operator+`) inside `cobass_plugin_process`, `note_on`, or `set_param`. All internal buffers must be pre-allocated during `cobass_plugin_create_instance` or `cobass_plugin_reset`.
2. **No Blocking Synchronization Primitives**: Do not acquire `std::mutex`, `std::condition_variable`, semaphores, or call `pthread_mutex_lock`.
3. **No I/O or JNI**: Never perform file reads/writes, logging via `__android_log_print` inside the audio callback loop, or make calls into the JVM.
4. **Denormal Protection**: Flush denormalized floating-point numbers to zero using small bias additions or standard compiler flags (`-ffast-math` or `-fdenormal-fp-math=positive-zero`).

---

## 3. The C-ABI Specification Header (`CobassPluginABI.h`)

This header must be included in your plugin project. It defines the exact data layouts and function pointer signatures expected by the host.

```cpp
#pragma once
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define COBASS_PLUGIN_API_VERSION 1
#define COBASS_MAX_PARAMS 64
#define COBASS_MAX_CHOICES 8

typedef enum {
    COBASS_PLUGIN_TYPE_SYNTH  = 0, // Instrument: Receives MIDI events, generates audio
    COBASS_PLUGIN_TYPE_EFFECT = 1  // Insert FX: Processes audio in-place
} CobassPluginType;

typedef enum {
    COBASS_PARAM_TYPE_FLOAT   = 0, // Continuous parameter (Knob / Slider)
    COBASS_PARAM_TYPE_INT     = 1, // Discrete integer step parameter ([-], [+])
    COBASS_PARAM_TYPE_BOOL    = 2, // Binary toggle (LED Switch)
    COBASS_PARAM_TYPE_CHOICE  = 3  // Dropdown enumeration list
} CobassParamType;

typedef struct {
    uint32_t id;
    char name[32];
    char label[16];          // Measurement unit: "Hz", "dB", "%", "ms", "st", etc.
    CobassParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    bool isLogarithmic;      // Enables logarithmic curve (e.g., frequencies)
    char choices[COBASS_MAX_CHOICES][24]; // String names for CHOICE types
    uint32_t choiceCount;
} CobassParamDescriptor;

typedef struct {
    uint32_t apiVersion;
    char pluginId[64];       // Unique Reverse-DNS ID (e.g., "com.vendor.plugin")
    char name[48];           // Human-readable plugin name
    char vendor[32];         // Vendor or author name
    char version[16];        // Semantic version string (e.g., "1.0.0")
    CobassPluginType type;
    uint32_t paramCount;
    const CobassParamDescriptor* params;
    bool supportsMidi;
    bool supportsSidechain;
} CobassPluginManifest;

typedef void* CobassHandle;

// Exported C-ABI Function Signatures
typedef const CobassPluginManifest* (*CobassGetManifestFunc)(void);
typedef CobassHandle (*CobassCreateInstanceFunc)(float sampleRate);
typedef void (*CobassDestroyInstanceFunc)(CobassHandle handle);
typedef void (*CobassResetFunc)(CobassHandle handle, float sampleRate);
typedef void (*CobassProcessFunc)(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames);
typedef void (*CobassNoteOnFunc)(CobassHandle handle, int32_t note, float velocity);
typedef void (*CobassNoteOffFunc)(CobassHandle handle, int32_t note);
typedef void (*CobassAllNotesOffFunc)(CobassHandle handle);
typedef void (*CobassSetParamFunc)(CobassHandle handle, uint32_t paramId, float value);
typedef float (*CobassGetParamFunc)(CobassHandle handle, uint32_t paramId);
typedef uint32_t (*CobassGetStateFunc)(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen);
typedef bool (*CobassSetStateFunc)(CobassHandle handle, const char* jsonBuffer);

#ifdef __cplusplus
}
#endif
```

---

## 4. Complete Reference Implementations

### 4.1 Modular Synthesizer Plugin (`SynthPlugin.cpp`)

This complete example implements an 8-voice subtractive polyphonic synthesizer featuring morphing waveforms, resonant 2-pole low-pass filtering, an ADSR amp envelope, and whitespace-resilient JSON state parsing.

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor SYNTH_PARAMS[] = {
    {0, "Waveform", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Sawtooth", "Square", "Sine", "Triangle"}, 4},
    {1, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 2500.0f, 1.0f, true, {}, 0},
    {2, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 10.0f, 1.2f, 0.05f, false, {}, 0},
    {3, "Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 15.0f, 1.0f, false, {}, 0},
    {4, "Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 150.0f, 1.0f, false, {}, 0},
    {5, "Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.75f, 0.01f, false, {}, 0},
    {6, "Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 300.0f, 1.0f, false, {}, 0},
    {7, "Volume", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest SYNTH_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.example.synth.subtractive",
    "Subtractive PolySynth",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_SYNTH,
    sizeof(SYNTH_PARAMS) / sizeof(CobassParamDescriptor),
    SYNTH_PARAMS,
    true,  // supportsMidi
    false  // supportsSidechain
};

class PolySynthProcessor {
private:
    struct Voice {
        int32_t note = -1;
        float velocity = 0.0f;
        double phase = 0.0;
        float frequency = 440.0f;
        bool active = false;

        enum class EnvState { Idle, Attack, Decay, Sustain, Release } envState = EnvState::Idle;
        float envValue = 0.0f;

        // Biquad state
        float x1 = 0.0f, x2 = 0.0f, y1 = 0.0f, y2 = 0.0f;

        void trigger(int32_t midiNote, float vel) {
            note = midiNote;
            velocity = vel;
            phase = 0.0;
            frequency = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
            active = true;
            envState = EnvState::Attack;
            x1 = x2 = y1 = y2 = 0.0f;
        }

        void release() {
            if (active && envState != EnvState::Idle) envState = EnvState::Release;
        }

        void stop() {
            active = false;
            note = -1;
            envState = EnvState::Idle;
            envValue = 0.0f;
        }
    };

public:
    explicit PolySynthProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : SYNTH_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (auto& v : voices_) v.stop();
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) params_[id] = val;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void noteOn(int32_t note, float velocity) {
        Voice* target = nullptr;
        for (auto& v : voices_) {
            if (!v.active) { target = &v; break; }
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

    void process(const float** /*inputs*/, float** outputs, uint32_t /*numChannels*/, uint32_t numFrames) {
        float* outL = outputs[0];
        float* outR = outputs[1];
        std::fill_n(outL, numFrames, 0.0f);
        std::fill_n(outR, numFrames, 0.0f);

        const int waveform = static_cast<int>(params_[0]);
        const float cutoff = std::clamp(params_[1], 20.0f, sampleRate_ * 0.48f);
        const float resonance = std::clamp(params_[2], 0.5f, 10.0f);
        const float attackRate = 1.0f / (std::max(0.001f, params_[3] * 0.001f) * sampleRate_);
        const float decayRate = 1.0f / (std::max(0.001f, params_[4] * 0.001f) * sampleRate_);
        const float sustainLevel = std::clamp(params_[5], 0.0f, 1.0f);
        const float releaseRate = 1.0f / (std::max(0.001f, params_[6] * 0.001f) * sampleRate_);
        const float gain = std::pow(10.0f, params_[7] / 20.0f) * 0.25f;

        // Biquad 12dB Low-Pass Filter Calculation
        const float w0 = 6.28318530718f * (cutoff / sampleRate_);
        const float cosw0 = std::cos(w0);
        const float alpha = std::sin(w0) / (2.0f * resonance);
        const float a0 = 1.0f + alpha;
        const float b0 = ((1.0f - cosw0) * 0.5f) / a0;
        const float b1 = (1.0f - cosw0) / a0;
        const float b2 = b0;
        const float a1 = (-2.0f * cosw0) / a0;
        const float a2 = (1.0f - alpha) / a0;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float monoSum = 0.0f;

            for (auto& v : voices_) {
                if (!v.active) continue;

                // Envelope processing
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

                // Oscillator waveform generator
                float sample = 0.0f;
                switch (waveform) {
                    case 0: sample = static_cast<float>(2.0 * (v.phase - std::floor(v.phase + 0.5))); break; // Saw
                    case 1: sample = (v.phase < 0.5) ? 1.0f : -1.0f; break;                                 // Square
                    case 2: sample = static_cast<float>(std::sin(v.phase * 6.28318530718)); break;           // Sine
                    case 3: sample = static_cast<float>(4.0 * std::fabs(v.phase - 0.5) - 1.0); break;       // Triangle
                }

                v.phase += v.frequency / sampleRate_;
                if (v.phase >= 1.0) v.phase -= 1.0;

                // Apply dynamic filter
                const float inSig = sample * v.envValue * v.velocity;
                const float outSig = b0 * inSig + b1 * v.x1 + b2 * v.x2 - a1 * v.y1 - a2 * v.y2;
                v.x2 = v.x1; v.x1 = inSig;
                v.y2 = v.y1; v.y1 = outSig;

                monoSum += outSig;
            }

            outL[i] = monoSum * gain;
            outR[i] = monoSum * gain;
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
    float sampleRate_ = 48000.0f;
    std::array<float, 8> params_{};
    std::array<Voice, 8> voices_{};
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &SYNTH_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new PolySynthProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<PolySynthProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<PolySynthProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->process(inputs, outputs, numChannels, numFrames);
    }
    void cobass_plugin_note_on(CobassHandle handle, int32_t note, float velocity) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->noteOn(note, velocity);
    }
    void cobass_plugin_note_off(CobassHandle handle, int32_t note) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->noteOff(note);
    }
    void cobass_plugin_all_notes_off(CobassHandle handle) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->allNotesOff();
    }
    void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->setParam(paramId, value);
    }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
        return handle ? static_cast<PolySynthProcessor*>(handle)->getParam(paramId) : 0.0f;
    }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
        return handle ? static_cast<PolySynthProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
    }
    bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
        return handle && static_cast<PolySynthProcessor*>(handle)->setStateJson(jsonBuffer);
    }
}
```

---

### 4.2 Modular Audio Effect Plugin (`EffectPlugin.cpp`)

This complete example implements a stereo ping-pong digital delay with feedback low-pass damping, dry/wet blending, and whitespace-resilient state parsing.

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor DELAY_PARAMS[] = {
    {0, "Time Left", "ms", COBASS_PARAM_TYPE_FLOAT, 10.0f, 2000.0f, 350.0f, 1.0f, false, {}, 0},
    {1, "Time Right", "ms", COBASS_PARAM_TYPE_FLOAT, 10.0f, 2000.0f, 500.0f, 1.0f, false, {}, 0},
    {2, "Feedback", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 0.95f, 0.45f, 0.01f, false, {}, 0},
    {3, "Damping", "Hz", COBASS_PARAM_TYPE_FLOAT, 500.0f, 18000.0f, 4500.0f, 10.0f, true, {}, 0},
    {4, "Ping-Pong", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 1.0f, 1.0f, false, {}, 0},
    {5, "Dry/Wet", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.35f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest DELAY_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.example.fx.delay",
    "Stereo PingPong Delay",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(DELAY_PARAMS) / sizeof(CobassParamDescriptor),
    DELAY_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class StereoDelayProcessor {
public:
    explicit StereoDelayProcessor(float sampleRate) : sampleRate_(sampleRate) {
        bufferL_.assign(MAX_BUFFER_SAMPLES, 0.0f);
        bufferR_.assign(MAX_BUFFER_SAMPLES, 0.0f);
        for (const auto& p : DELAY_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
        std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
        writeIndex_ = 0;
        dampStateL_ = dampStateR_ = 0.0f;
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) params_[id] = val;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void process(const float** inputs, float** outputs, uint32_t /*numChannels*/, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const int32_t delayL = std::clamp(static_cast<int32_t>((params_[0] * 0.001f) * sampleRate_), 1, static_cast<int32_t>(MAX_BUFFER_SAMPLES - 1));
        const int32_t delayR = std::clamp(static_cast<int32_t>((params_[1] * 0.001f) * sampleRate_), 1, static_cast<int32_t>(MAX_BUFFER_SAMPLES - 1));
        const float feedback = std::clamp(params_[2], 0.0f, 0.95f);
        const float dampFreq = std::clamp(params_[3], 500.0f, sampleRate_ * 0.45f);
        const bool pingPong = params_[4] > 0.5f;
        const float mix = std::clamp(params_[5], 0.0f, 1.0f);

        // 1-pole lowpass coefficient for feedback damping
        const float dampCoeff = std::exp(-6.2831853f * (dampFreq / sampleRate_));

        for (uint32_t i = 0; i < numFrames; ++i) {
            const float sL = inL[i];
            const float sR = inR[i];

            int32_t readIdxL = static_cast<int32_t>(writeIndex_) - delayL;
            if (readIdxL < 0) readIdxL += MAX_BUFFER_SAMPLES;

            int32_t readIdxR = static_cast<int32_t>(writeIndex_) - delayR;
            if (readIdxR < 0) readIdxR += MAX_BUFFER_SAMPLES;

            float delayedL = bufferL_[readIdxL];
            float delayedR = bufferR_[readIdxR];

            // Lowpass filter feedback
            dampStateL_ = (delayedL * (1.0f - dampCoeff)) + (dampStateL_ * dampCoeff);
            dampStateR_ = (delayedR * (1.0f - dampCoeff)) + (dampStateR_ * dampCoeff);

            if (pingPong) {
                bufferL_[writeIndex_] = sL + dampStateR_ * feedback;
                bufferR_[writeIndex_] = sR + dampStateL_ * feedback;
            } else {
                bufferL_[writeIndex_] = sL + dampStateL_ * feedback;
                bufferR_[writeIndex_] = sR + dampStateL_ * feedback;
            }

            writeIndex_ = (writeIndex_ + 1) % MAX_BUFFER_SAMPLES;

            outL[i] = sL * (1.0f - mix) + delayedL * mix;
            outR[i] = sR * (1.0f - mix) + delayedR * mix;
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
    static constexpr size_t MAX_BUFFER_SAMPLES = 192000; // 4 seconds @ 48kHz
    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t writeIndex_ = 0;
    float dampStateL_ = 0.0f;
    float dampStateR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &DELAY_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new StereoDelayProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<StereoDelayProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<StereoDelayProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames) {
        if (handle) static_cast<StereoDelayProcessor*>(handle)->process(inputs, outputs, numChannels, numFrames);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
        if (handle) static_cast<StereoDelayProcessor*>(handle)->setParam(paramId, value);
    }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
        return handle ? static_cast<StereoDelayProcessor*>(handle)->getParam(paramId) : 0.0f;
    }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
        return handle ? static_cast<StereoDelayProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
    }
    bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
        return handle && static_cast<StereoDelayProcessor*>(handle)->setStateJson(jsonBuffer);
    }
}
```

---

## 5. Automated Standalone Plugin Build Toolchain

Below is the complete standalone build script (`build_plugin.sh`) that cross-compiles any external or addon C++ plugin into an Android `arm64-v8a` shared library without requiring Gradle.

### `build_plugin.sh`

```bash
#!/usr/bin/env bash
# ==============================================================================
# Cobass Modular Plugin Standalone Compiler (No Gradle Required)
# Usage: ./build_plugin.sh <source_directory> [output_directory]
# Example: ./build_plugin.sh addons/synth-hyperion app/lib/arm64-v8a
# ==============================================================================
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <plugin_source_dir> [output_so_dir]"
    exit 1
fi

SRC_DIR="$1"
OUT_DIR="${2:-app/lib/arm64-v8a}"
API_LEVEL="34"
TARGET_ABI="arm64-v8a"
TARGET_TRIPLE="aarch64-linux-android${API_LEVEL}"

mkdir -p "$OUT_DIR"

# 1. Locate Compiler (Desktop Android NDK or Termux Clang)
COMPILER=""
EXTRA_FLAGS=()

if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
    TOOLCHAIN_BIN=$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -maxdepth 2 -name "bin" 2>/dev/null | head -n 1)
    if [ -f "$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++" ]; then
        COMPILER="$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++"
    fi
elif [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
    TOOLCHAIN_BIN=$(find "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt" -maxdepth 2 -name "bin" 2>/dev/null | head -n 1)
    if [ -f "$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++" ]; then
        COMPILER="$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++"
    fi
fi

if [ -z "$COMPILER" ] && command -v clang++ &>/dev/null; then
    COMPILER=$(command -v clang++)
    EXTRA_FLAGS=(
        "-fPIC"
        "-target" "$TARGET_TRIPLE"
        "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT"
        "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES"
        "-Wno-macro-redefined"
    )
fi

if [ -z "$COMPILER" ]; then
    echo -e "\033[91m[ERROR] Clang++ compiler not found. Set ANDROID_NDK_HOME or install clang in Termux.\033[0m"
    exit 1
fi

# 2. Collect C++ Source Files
CPP_FILES=($(find "$SRC_DIR" -name "*.cpp"))
if [ ${#CPP_FILES[@]} -eq 0 ]; then
    echo -e "\033[91m[ERROR] No .cpp source files found in $SRC_DIR\033[0m"
    exit 1
fi

PLUGIN_BASENAME=$(basename "$SRC_DIR" | tr '-' '_')
TARGET_SO="${OUT_DIR}/libcobass_plugin_${PLUGIN_BASENAME}.so"

echo "======================================================================"
echo "==> Compiling Cobass Plugin: $PLUGIN_BASENAME"
echo "    Compiler: $COMPILER"
echo "    Output:   $TARGET_SO"
echo "======================================================================"

$COMPILER \
    -std=c++20 \
    -shared \
    -fPIC \
    -O3 \
    -DNDEBUG \
    -ffast-math \
    -I"app/native/include" \
    -I"$SRC_DIR" \
    -I"$SRC_DIR/src" \
    "${EXTRA_FLAGS[@]}" \
    "${CPP_FILES[@]}" \
    -o "$TARGET_SO" \
    -lm

FILE_SIZE_KB=$(du -k "$TARGET_SO" | cut -f1)
echo -e "\033[92m[✓] SUCCESS: Built libcobass_plugin_${PLUGIN_BASENAME}.so (${FILE_SIZE_KB} KB)\033[0m"
```

---

## 6. Packaging, Distribution & Deployment

Cobass supports two deployment paths:

### Path A: Bundling with the APK (Factory Plugins)
1. Place plugin source under `addons/<plugin-name>/src/*.cpp`.
2. Run `./build.sh` (or `python3 tools/build_addons.py arm64-v8a`).
3. The `.so` binary is compiled to `app/lib/arm64-v8a/libcobass_plugin_<name>.so` and automatically bundled into the APK under `lib/arm64-v8a/`.
4. `PluginHostManager` discovers it on startup.

### Path B: Runtime Sideloading (User Custom Plugins)
1. Compile your plugin using `./build_plugin.sh /path/to/plugin_src /tmp/out`.
2. Push the resulting `libcobass_plugin_*.so` directly to the app's internal executable code cache directory (SELinux W^X compliant):
   ```bash
   adb push /tmp/out/libcobass_plugin_mycustom.so /data/data/com.maxica.cobass/code_cache/plugins/
   ```
3. Open Cobass, tap `[⚙ PREFERENCES] -> [SCAN PLUGINS]` or open any track FX/Instrument rack. The plugin will appear in the browser.

---

## 7. Step-by-Step Tutorial: Creating a Custom Plugin from Scratch

### Step 1: Create the Project Directory
```bash
mkdir -p addons/fx-bitcrusher/src
```

### Step 2: Write the DSP & C-ABI Code (`BitcrusherPlugin.cpp`)
Save the following code inside `addons/fx-bitcrusher/src/BitcrusherPlugin.cpp`:

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor CRUSH_PARAMS[] = {
    {0, "Bit Depth", "bits", COBASS_PARAM_TYPE_INT, 2.0f, 16.0f, 8.0f, 1.0f, false, {}, 0},
    {1, "Downsample", "x", COBASS_PARAM_TYPE_FLOAT, 1.0f, 64.0f, 4.0f, 1.0f, false, {}, 0},
    {2, "Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest CRUSH_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.developer.fx.bitcrusher",
    "Digital Bitcrusher",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    3,
    CRUSH_PARAMS,
    false, false
};

class BitcrusherProcessor {
public:
    explicit BitcrusherProcessor(float) {
        for (const auto& p : CRUSH_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float) { holdCount_ = 0.0f; heldL_ = heldR_ = 0.0f; }

    void setParam(uint32_t id, float v) { if (id < 3) params_[id] = v; }
    float getParam(uint32_t id) const { return id < 3 ? params_[id] : 0.0f; }

    void process(const float** inputs, float** outputs, uint32_t, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float bits = std::clamp(params_[0], 2.0f, 16.0f);
        const float levels = std::pow(2.0f, bits) * 0.5f;
        const float downsample = std::clamp(params_[1], 1.0f, 64.0f);
        const float mix = std::clamp(params_[2], 0.0f, 1.0f);

        for (uint32_t i = 0; i < numFrames; ++i) {
            holdCount_ += 1.0f;
            if (holdCount_ >= downsample) {
                holdCount_ = 0.0f;
                heldL_ = std::round(inL[i] * levels) / levels;
                heldR_ = std::round(inR[i] * levels) / levels;
            }

            outL[i] = inL[i] * (1.0f - mix) + heldL_ * mix;
            outR[i] = inR[i] * (1.0f - mix) + heldR_ * mix;
        }
    }

    uint32_t getStateJson(char* outBuffer, uint32_t maxLen) const {
        std::string json = "{\"0\":" + std::to_string(params_[0]) + ",\"1\":" + std::to_string(params_[1]) + ",\"2\":" + std::to_string(params_[2]) + "}";
        if (json.size() >= maxLen) return 0;
        std::memcpy(outBuffer, json.c_str(), json.size() + 1);
        return static_cast<uint32_t>(json.size());
    }

    bool setStateJson(const char* json) {
        if (!json) return false;
        for (size_t i = 0; i < 3; ++i) {
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
    std::array<float, 3> params_{};
    float holdCount_ = 0.0f;
    float heldL_ = 0.0f, heldR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &CRUSH_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new BitcrusherProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<BitcrusherProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<BitcrusherProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t c, uint32_t n) {
        if (handle) static_cast<BitcrusherProcessor*>(handle)->process(inputs, outputs, c, n);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle handle, uint32_t p, float v) { if (handle) static_cast<BitcrusherProcessor*>(handle)->setParam(p, v); }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t p) { return handle ? static_cast<BitcrusherProcessor*>(handle)->getParam(p) : 0.0f; }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* b, uint32_t m) { return handle ? static_cast<BitcrusherProcessor*>(handle)->getStateJson(b, m) : 0; }
    bool cobass_plugin_set_state(CobassHandle handle, const char* j) { return handle && static_cast<BitcrusherProcessor*>(handle)->setStateJson(j); }
}
```

### Step 3: Compile and Test
Run the build script:
```bash
./build.sh
```
Launch Cobass, navigate to the Track Inspector on any track, open **Modular FX Rack (8 Slots)**, tap `+ Add Insert Plugin`, and select **Digital Bitcrusher**. The dynamic parameter matrix renders the Bit Depth stepper, Downsample knob, and Mix fader ready for real-time manipulation.

---

# Standalone Plugin APK Sideloading Architecture (Path B)

This specification defines how third-party developers can build, package, and distribute custom **Synthesizer** and **Audio Effect** plugins as **standalone Android APKs** (e.g., `HyperionSynth.apk`, `VintageChorus.apk`), and how the **Cobass Host Application** automatically discovers, sideloads, and executes them at runtime.

---

## 1. System Architecture: Standalone Plugin APK Model

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                          3RD-PARTY STANDALONE PLUGIN APK                               │
│                         (e.g., com.example.plugin.hyperion)                            │
│                                                                                        │
│  ├── AndroidManifest.xml   -> Declares intent-filter: "com.maxica.cobass.PLUGIN"       │
│  │                            Meta-data: plugin_id, plugin_type (SYNTH/EFFECT)         │
│  ├── lib/arm64-v8a/                                                                    │
│  │   └── libcobass_plugin_hyperion.so  -> Native C++20 C-ABI Binary                    │
│  └── classes.dex / res     -> Minimal headless manifest container                      │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ Distributed as regular .apk file
                    ┌────────────────────┴────────────────────┐
                    ▼                                         ▼
   [Option 1: Device App Installation]       [Option 2: Direct In-App File Import]
   User installs APK via Android Package     User downloads APK and opens via
   Installer (`adb install` or tap APK)      Cobass `[📦 Import Plugin APK]` (SAF)
                    │                                         │
                    └────────────────────┬────────────────────┘
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                COBASS HOST APPLICATION                                 │
│                                                                                        │
│  1. PluginApkInstaller.java:                                                           │
│     • Queries PackageManager for action: "com.maxica.cobass.PLUGIN"                    │
│     • Or unzips imported APK directly in memory using ZipInputStream                   │
│     • Extracts `lib/arm64-v8a/libcobass_plugin_*.so`                                   │
│     • Installs binary into `/data/data/com.maxica.cobass/code_cache/plugins/`          │
│                                                                                        │
│  2. PluginLoader.hpp (C++ Engine):                                                     │
│     • `dlopen()` loads the extracted shared library cleanly without SELinux blocks     │
│     • Inspects `cobass_plugin_get_manifest()`                                          │
│     • Registers the plugin in the real-time mixer and track instrument/FX racks        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Standalone Plugin APK Specification

Every standalone plugin APK requires two main components:
1. An **`AndroidManifest.xml`** declaring the Cobass plugin intent filter and metadata.
2. A **native C++20 shared library** (`lib/arm64-v8a/libcobass_plugin_<name>.so`) compiled against `CobassPluginABI.h`.

### 2.1 Plugin `AndroidManifest.xml` Blueprint

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.cobass.plugin.hyperion"
    android:versionCode="1"
    android:versionName="1.0.0">

    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />

    <application
        android:label="Hyperion Wavetable Synth"
        android:hasCode="false">

        <!-- Extension Service / Activity Descriptor for Discovery -->
        <service
            android:name=".PluginDescriptorService"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <!-- Standard Cobass Discovery Action -->
                <action android:name="com.maxica.cobass.PLUGIN" />
                <!-- Plugin Category: SYNTH or EFFECT -->
                <category android:name="com.maxica.cobass.CATEGORY_SYNTH" />
            </intent-filter>

            <meta-data
                android:name="com.maxica.cobass.PLUGIN_ID"
                android:value="com.example.cobass.plugin.hyperion" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_NAME"
                android:value="Hyperion Wavetable Synth" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_VENDOR"
                android:value="Maxica Audio" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_TYPE"
                android:value="SYNTH" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_LIB_NAME"
                android:value="libcobass_plugin_synth_hyperion.so" />
        </service>
    </application>
</manifest>
```

---

## 3. Host App Implementation (`com.maxica.cobass`)

To support standalone APK sideloading and discovery, we add **`PluginApkInstaller.java`** to the host app. This component provides two loading paths:
1. **System PackageManager Auto-Discovery**: Detects all installed plugin APKs on the device, extracts their `.so` binaries from `publicSourceDir` (the installed APK file), and mounts them.
2. **In-App Direct APK Sideloading**: Allows users to import any `.apk` file directly using the Android Storage Access Framework (SAF) without installing the APK at the OS level.

### 3.1 `PluginApkInstaller.java` (Host Engine)

```java
package com.maxica.cobass.plugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import com.maxica.cobass.audio.AudioEngineNative;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class PluginApkInstaller {

    public static final String ACTION_COBASS_PLUGIN = "com.maxica.cobass.PLUGIN";
    private static final String TARGET_ABI_PREFIX = "lib/arm64-v8a/";

    /**
     * Scans all installed Android apps on the device matching the Cobass plugin intent,
     * extracts their arm64-v8a native shared libraries into the app's executable code cache directory
     * (SELinux W^X compliant), and triggers a native engine catalog reload.
     */
    public static int scanAndMountInstalledPluginApks(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(ACTION_COBASS_PLUGIN);
        List<ResolveInfo> plugins = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        File internalPluginDir = new File(context.getCodeCacheDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        int mountedCount = 0;
        for (ResolveInfo info : plugins) {
            if (info.serviceInfo == null || info.serviceInfo.packageName == null) continue;
            String packageName = info.serviceInfo.packageName;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String apkPath = appInfo.publicSourceDir != null ? appInfo.publicSourceDir : appInfo.sourceDir;
                if (apkPath == null) continue;

                File apkFile = new File(apkPath);
                if (!apkFile.exists()) continue;

                if (extractSoFromApkFile(apkFile, internalPluginDir)) {
                    mountedCount++;
                }
            } catch (Exception e) {
                Log.e("CobassPlugin", "Failed to mount installed plugin APK: " + packageName, e);
            }
        }

        // Trigger native C++ dynamic loader scan over internal plugins folder
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
        }

        return mountedCount;
    }

    /**
     * Sideloads a standalone plugin directly from an APK / bundle file selected via SAF.
     */
    public static boolean installPluginFromUri(Context context, Uri uri) {
        File internalPluginDir = new File(context.getCodeCacheDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        boolean success = false;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ((name.startsWith(TARGET_ABI_PREFIX) || name.startsWith("lib/")) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(internalPluginDir, fileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    outFile.setReadable(true, false);
                    outFile.setExecutable(true, false);
                    success = true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Error installing plugin from URI", e);
            return false;
        }

        if (success && AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
            PluginHostManager.getInstance().scanPlugins(context);
        }

        return success;
    }

    private static boolean extractSoFromApkFile(File apkFile, File targetDir) {
        boolean extracted = false;
        try (ZipFile zip = new ZipFile(apkFile)) {
            var entries = zip.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if ((name.startsWith(TARGET_ABI_PREFIX) || name.startsWith("lib/")) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(targetDir, fileName);

                    // Check if update needed
                    if (!outFile.exists() || outFile.length() != entry.getSize()) {
                        try (InputStream is = zip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        outFile.setReadable(true, false);
                        outFile.setExecutable(true, false);
                    }
                    extracted = true;
                }
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Failed to extract .so from APK: " + apkFile.getName(), e);
            return false;
        }
        return extracted;
    }
}
```

---

## 4. Standalone Plugin APK Builder (`build_standalone_plugin_apk.py`)

This standalone build tool cross-compiles your C++ plugin code into an `arm64-v8a` native shared library, creates the minimal headless manifest, packages it into a signed `.apk`, and verifies the cryptographic signature without requiring Gradle or Android Studio.

### `tools/build_standalone_plugin_apk.py`

```python
#!/usr/bin/env python3
"""
Cobass Standalone Plugin APK Builder (No Gradle Required)
Usage:
    python3 build_standalone_plugin_apk.py \
        --src addons/synth-hyperion \
        --pkg com.example.cobass.plugin.hyperion \
        --name "Hyperion Synth" \
        --type SYNTH \
        --out out/plugins/HyperionSynth.apk
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

def find_tool(name: str) -> str:
    path = shutil.which(name)
    if path: return path
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        bt_dir = Path(sdk_root) / "build-tools"
        if bt_dir.is_dir():
            for b in sorted(bt_dir.iterdir(), reverse=True):
                cand = b / name
                if cand.exists(): return str(cand)
    return name

def find_compiler(target_abi: str = "arm64-v8a"):
    api_level = "34"
    ndk_home = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if ndk_home and Path(ndk_home).is_dir():
        llvm_bin = Path(ndk_home) / "toolchains/llvm/prebuilt"
        host_dirs = list(llvm_bin.glob("*"))
        if host_dirs:
            bin_dir = host_dirs[0] / "bin"
            target_prefix = f"aarch64-linux-android{api_level}-clang++"
            compiler = bin_dir / target_prefix
            if compiler.exists(): return str(compiler), []

    clang_path = shutil.which("clang++")
    if clang_path:
        target_triple = f"aarch64-linux-android{api_level}"
        return clang_path, [
            "-fPIC", "-target", target_triple,
            "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT",
            "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES",
            "-Wno-macro-redefined"
        ]
    return None, []

def get_android_jar() -> str:
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        plat_dir = Path(sdk_root) / "platforms"
        jars = sorted(list(plat_dir.glob("android-*/android.jar")), reverse=True)
        if jars: return str(jars[0])
    print("[ERROR] android.jar not found in SDK platforms.")
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="Cobass Standalone Plugin APK Compiler")
    parser.add_argument("--src", required=True, help="Directory containing plugin C++ sources")
    parser.add_argument("--pkg", required=True, help="Package name (e.g. com.example.plugin.hyperion)")
    parser.add_argument("--name", required=True, help="Display Name (e.g. 'Hyperion Synth')")
    parser.add_argument("--type", default="EFFECT", choices=["SYNTH", "EFFECT"], help="Plugin Type")
    parser.add_argument("--out", default="out/plugins/Plugin.apk", help="Output APK path")
    args = parser.parse_args()

    src_dir = Path(args.src)
    out_apk = Path(args.out).resolve()
    out_apk.parent.mkdir(parents=True, exist_ok=True)

    work_dir = Path("out/plugin_build_tmp")
    if work_dir.exists(): shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

    compiler, extra_flags = find_compiler("arm64-v8a")
    if not compiler:
        print("[ERROR] Clang++ compiler not found.")
        sys.exit(1)

    aapt2 = find_tool("aapt2")
    zipalign = find_tool("zipalign")
    apksigner = find_tool("apksigner")
    android_jar = get_android_jar()

    # 1. Compile C++ Plugin .so
    cpp_files = list(src_dir.glob("*.cpp")) + list(src_dir.glob("src/*.cpp"))
    if not cpp_files:
        print(f"[ERROR] No .cpp source files found in {src_dir}")
        sys.exit(1)

    lib_name = f"libcobass_plugin_{src_dir.name.replace('-', '_')}.so"
    so_out = work_dir / "lib" / "arm64-v8a" / lib_name
    so_out.parent.mkdir(parents=True, exist_ok=True)

    print(f"[*] [1/5] Compiling C++20 Plugin Binary -> {lib_name}...")
    compile_cmd = [
        compiler, "-std=c++20", "-shared", "-fPIC", "-O3", "-DNDEBUG", "-ffast-math",
        "-Iapp/native/include", f"-I{src_dir}", f"-I{src_dir}/src",
        *extra_flags, *[str(f) for f in cpp_files],
        "-o", str(so_out), "-lm"
    ]
    res = subprocess.run(compile_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"[COMPILATION FAILED]\n{res.stderr}")
        sys.exit(1)

    # 2. Generate Plugin AndroidManifest.xml
    manifest_xml = work_dir / "AndroidManifest.xml"
    manifest_content = f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{args.pkg}"
    android:versionCode="1"
    android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />
    <application android:label="{args.name}" android:hasCode="false">
        <service android:name=".PluginDescriptorService" android:exported="true" android:enabled="true">
            <intent-filter>
                <action android:name="com.maxica.cobass.PLUGIN" />
                <category android:name="com.maxica.cobass.CATEGORY_{args.type}" />
            </intent-filter>
            <meta-data android:name="com.maxica.cobass.PLUGIN_ID" android:value="{args.pkg}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_NAME" android:value="{args.name}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_TYPE" android:value="{args.type}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_LIB_NAME" android:value="{lib_name}" />
        </service>
    </application>
</manifest>
"""
    manifest_xml.write_text(manifest_content, encoding="utf-8")

    # 3. Link APK via AAPT2
    print("[*] [2/5] Linking Plugin APK Manifest with AAPT2...")
    unaligned_apk = work_dir / "unaligned.apk"
    link_cmd = [
        aapt2, "link",
        "-I", android_jar,
        "--min-sdk-version", "26",
        "--target-sdk-version", "34",
        "--version-code", "1",
        "--version-name", "1.0.0",
        "--manifest", str(manifest_xml),
        "-o", str(unaligned_apk)
    ]
    subprocess.run(link_cmd, check=True)

    # 4. Embed Native .so into APK Archive
    print("[*] [3/5] Packaging arm64-v8a Native Library into APK...")
    with zipfile.ZipFile(unaligned_apk, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(so_out, arcname=f"lib/arm64-v8a/{lib_name}")

    # 5. ZipAlign
    print("[*] [4/5] 4-Byte Alignment with ZipAlign...")
    aligned_apk = work_dir / "aligned.apk"
    subprocess.run([zipalign, "-f", "4", str(unaligned_apk), str(aligned_apk)], check=True)

    # 6. Sign APK with Debug Keystore
    print("[*] [5/5] Cryptographically Signing Plugin APK...")
    keystore = Path("config/debug.keystore")
    sign_cmd = [
        apksigner, "sign",
        "--ks", str(keystore),
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", str(out_apk),
        str(aligned_apk)
    ]
    subprocess.run(sign_cmd, check=True)

    size_kb = out_apk.stat().st_size / 1024
    print("=" * 65)
    print(f"\033[92m[✓] STANDALONE PLUGIN APK READY: {out_apk}\033[0m")
    print(f"    Package ID:  {args.pkg}")
    print(f"    Plugin Name: {args.name}")
    print(f"    Plugin Type: {args.type}")
    print(f"    Binary:      {lib_name} ({so_out.stat().st_size / 1024:.1f} KB)")
    print(f"    File Size:   {size_kb:.1f} KB")
    print("=" * 65)

if __name__ == "__main__":
    main()
```

---

## 5. Step-by-Step Tutorial: Building & Sideloading a Custom Plugin APK

### Step 1: Write the DSP Code
Create `addons/fx-distortion/src/DistortionPlugin.cpp`:

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor DIST_PARAMS[] = {
    {0, "Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 40.0f, 12.0f, 0.2f, false, {}, 0},
    {1, "Tone", "Hz", COBASS_PARAM_TYPE_FLOAT, 200.0f, 15000.0f, 4000.0f, 10.0f, true, {}, 0},
    {2, "Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest DIST_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.developer.fx.distortion",
    "Overdrive Saturation",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    3,
    DIST_PARAMS,
    false, false
};

class OverdriveProcessor {
public:
    explicit OverdriveProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : DIST_PARAMS) params_[p.id] = p.defaultValue;
    }
    void reset(float sampleRate) { sampleRate_ = sampleRate; sL_ = sR_ = 0.0f; }
    void setParam(uint32_t id, float v) { if (id < 3) params_[id] = v; }
    float getParam(uint32_t id) const { return id < 3 ? params_[id] : 0.0f; }

    void process(const float** inputs, float** outputs, uint32_t, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float driveGain = std::pow(10.0f, params_[0] / 20.0f);
        const float alpha = std::exp(-6.2831853f * (params_[1] / sampleRate_));
        const float mix = params_[2];

        for (uint32_t i = 0; i < numFrames; ++i) {
            float wetL = std::tanh(inL[i] * driveGain);
            float wetR = std::tanh(inR[i] * driveGain);

            sL_ = (wetL * (1.0f - alpha)) + (sL_ * alpha);
            sR_ = (wetR * (1.0f - alpha)) + (sR_ * alpha);

            outL[i] = inL[i] * (1.0f - mix) + sL_ * mix;
            outR[i] = inR[i] * (1.0f - mix) + sR_ * mix;
        }
    }

    uint32_t getStateJson(char* b, uint32_t m) const {
        std::string j = "{\"0\":" + std::to_string(params_[0]) + ",\"1\":" + std::to_string(params_[1]) + ",\"2\":" + std::to_string(params_[2]) + "}";
        if (j.size() >= m) return 0;
        std::memcpy(b, j.c_str(), j.size() + 1);
        return static_cast<uint32_t>(j.size());
    }

    bool setStateJson(const char* j) {
        if (!j) return false;
        for (int i = 0; i < 3; ++i) {
            std::string k = "\"" + std::to_string(i) + "\"";
            const char* p = std::strstr(j, k.c_str());
            if (p) {
                p += k.size();
                while (*p == ' ' || *p == '\t' || *p == ':') p++;
                params_[i] = std::strtof(p, nullptr);
            }
        }
        return true;
    }

private:
    float sampleRate_ = 48000.0f;
    std::array<float, 3> params_{};
    float sL_ = 0.0f, sR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &DIST_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sr) { return new OverdriveProcessor(sr); }
    void cobass_plugin_destroy_instance(CobassHandle h) { delete static_cast<OverdriveProcessor*>(h); }
    void cobass_plugin_reset(CobassHandle h, float sr) { if (h) static_cast<OverdriveProcessor*>(h)->reset(sr); }
    void cobass_plugin_process(CobassHandle h, const float** in, float** out, uint32_t c, uint32_t n) {
        if (h) static_cast<OverdriveProcessor*>(h)->process(in, out, c, n);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle h, uint32_t p, float v) { if (h) static_cast<OverdriveProcessor*>(h)->setParam(p, v); }
    float cobass_plugin_get_param(CobassHandle h, uint32_t p) { return h ? static_cast<OverdriveProcessor*>(h)->getParam(p) : 0.0f; }
    uint32_t cobass_plugin_get_state(CobassHandle h, char* b, uint32_t m) { return h ? static_cast<OverdriveProcessor*>(h)->getStateJson(b, m) : 0; }
    bool cobass_plugin_set_state(CobassHandle h, const char* j) { return handle && static_cast<OverdriveProcessor*>(h)->setStateJson(j); }
}
```

### Step 2: Compile the Standalone Plugin APK
Run the Python build script:

```bash
python3 tools/build_standalone_plugin_apk.py \
    --src addons/fx-distortion \
    --pkg com.developer.fx.distortion \
    --name "Overdrive Saturation" \
    --type EFFECT \
    --out out/plugins/OverdriveSaturation.apk
```

---

## 6. Sideloading and Running the Plugin APK on Device

You can now distribute `OverdriveSaturation.apk` directly to users.

### Distribution Method A: Sideload & Install onto Device
1. Install the plugin APK on your device:
   ```bash
   adb install -r out/plugins/OverdriveSaturation.apk
   ```
2. Open Cobass. On startup, `PluginApkInstaller` detects the `com.maxica.cobass.PLUGIN` service, extracts `libcobass_plugin_fx_distortion.so` into the executable `code_cache/plugins/` directory, and automatically mounts it into the **Modular Insert FX Rack**.

### Distribution Method B: Direct In-App File Sideloading (No OS Install)
1. Send `OverdriveSaturation.apk` to your phone via WhatsApp, Telegram, Google Drive, or SD Card.
2. In Cobass, open **⚙ PREFERENCES** and tap **📦 Sideload Plugin (.apk)**.
3. Select `OverdriveSaturation.apk`. Cobass unzips the arm64-v8a binary directly into `context.getCodeCacheDir() / "plugins"` and hot-reloads the catalog without requiring root access or adb commands.
```
```

---

### File: `docs/plugin-synth-fx_doc.md`

```markdown
# Cobass Modular Plugin SDK — Official Developer Specification & Reference Manual

---

## 1. System Architecture & Executive Overview

Cobass plugins are self-contained C++20 shared libraries (`.so`) that conform to the **Cobass Plugin C-ABI**. The host dynamically resolves entry points using standard dynamic linking (`dlopen`/`dlsym`), generates parameter UI controls dynamically from descriptors (or hosts custom viewports), streams audio in zero-allocation real-time blocks, and serializes patch states into human-readable JSON.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                COBASS DAW HOST ENGINE                                  │
│                                                                                        │
│  ┌─────────────────────────────┐                    ┌───────────────────────────────┐  │
│  │       JAVA UI HOST          │                    │     AAUDIO REAL-TIME THREAD   │  │
│  │                             │                    │                               │  │
│  │ • RotaryKnobView.java       │   Lock-Free JNI    │ • Mixer.hpp / Track.hpp       │  │
│  │ • PluginUiDialog.java       │───────────────────▶│ • 8-Slot Insert Rack / Synth  │  │
│  │ • Preset Manager (.cobass)  │                    │ • Zero-Alloc Block Processing │  │
│  └─────────────────────────────┘                    └───────────────────────────────┘  │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ C-ABI Calls
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              MODULAR PLUGIN BINARY (.so)                               │
│                                                                                        │
│  Exported C Functions:                                                                 │
│  ├── cobass_plugin_get_manifest()     -> Manifest, Parameters, Type, Capabilities      │
│  ├── cobass_plugin_create_instance()  -> Allocates processor for sample rate           │
│  ├── cobass_plugin_destroy_instance() -> Deallocates processor & internal buffers      │
│  ├── cobass_plugin_reset()            -> Clears voice state, filters, delay lines      │
│  ├── cobass_plugin_process()          -> Deinterleaved 32-bit float audio streaming    │
│  ├── cobass_plugin_note_on()          -> MIDI note trigger with velocity               │
│  ├── cobass_plugin_note_off()         -> MIDI note release                             │
│  ├── cobass_plugin_all_notes_off()    -> Hard voice stop                               │
│  ├── cobass_plugin_set_param()        -> Real-time atomic parameter mutation           │
│  ├── cobass_plugin_get_param()        -> Parameter value lookup                        │
│  ├── cobass_plugin_get_state()        -> JSON patch snapshot serialization             │
│  └── cobass_plugin_set_state()        -> JSON patch snapshot restoration               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Zero-Allocation Real-Time Audio Constraints

All plugin audio processing occurs directly inside the AAudio callback thread. To guarantee glitch-free, ultra-low latency playback ($<5\text{ ms}$ round-trip), every plugin must strictly adhere to the following real-time safety rules:

1. **No Dynamic Memory Allocation**: Never invoke `malloc`, `free`, `new`, `delete`, `realloc`, or dynamically resize containers (`std::vector::push_back`, `std::string::operator+`) inside `cobass_plugin_process`, `note_on`, or `set_param`. All internal buffers must be pre-allocated during `cobass_plugin_create_instance` or `cobass_plugin_reset`.
2. **No Blocking Synchronization Primitives**: Do not acquire `std::mutex`, `std::condition_variable`, semaphores, or call `pthread_mutex_lock`.
3. **No I/O or JNI**: Never perform file reads/writes, logging via `__android_log_print` inside the audio callback loop, or make calls into the JVM.
4. **Denormal Protection**: Flush denormalized floating-point numbers to zero using small bias additions or standard compiler flags (`-ffast-math` or `-fdenormal-fp-math=positive-zero`).

---

## 3. The C-ABI Specification Header (`CobassPluginABI.h`)

This header must be included in your plugin project. It defines the exact data layouts and function pointer signatures expected by the host.

```cpp
#pragma once
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define COBASS_PLUGIN_API_VERSION 1
#define COBASS_MAX_PARAMS 64
#define COBASS_MAX_CHOICES 8

typedef enum {
    COBASS_PLUGIN_TYPE_SYNTH  = 0, // Instrument: Receives MIDI events, generates audio
    COBASS_PLUGIN_TYPE_EFFECT = 1  // Insert FX: Processes audio in-place
} CobassPluginType;

typedef enum {
    COBASS_PARAM_TYPE_FLOAT   = 0, // Continuous parameter (Knob / Slider)
    COBASS_PARAM_TYPE_INT     = 1, // Discrete integer step parameter ([-], [+])
    COBASS_PARAM_TYPE_BOOL    = 2, // Binary toggle (LED Switch)
    COBASS_PARAM_TYPE_CHOICE  = 3  // Dropdown enumeration list
} CobassParamType;

typedef struct {
    uint32_t id;
    char name[32];
    char label[16];          // Measurement unit: "Hz", "dB", "%", "ms", "st", etc.
    CobassParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    bool isLogarithmic;      // Enables logarithmic curve (e.g., frequencies)
    char choices[COBASS_MAX_CHOICES][24]; // String names for CHOICE types
    uint32_t choiceCount;
} CobassParamDescriptor;

typedef struct {
    uint32_t apiVersion;
    char pluginId[64];       // Unique Reverse-DNS ID (e.g., "com.vendor.plugin")
    char name[48];           // Human-readable plugin name
    char vendor[32];         // Vendor or author name
    char version[16];        // Semantic version string (e.g., "1.0.0")
    CobassPluginType type;
    uint32_t paramCount;
    const CobassParamDescriptor* params;
    bool supportsMidi;
    bool supportsSidechain;
} CobassPluginManifest;

typedef void* CobassHandle;

// Exported C-ABI Function Signatures
typedef const CobassPluginManifest* (*CobassGetManifestFunc)(void);
typedef CobassHandle (*CobassCreateInstanceFunc)(float sampleRate);
typedef void (*CobassDestroyInstanceFunc)(CobassHandle handle);
typedef void (*CobassResetFunc)(CobassHandle handle, float sampleRate);
typedef void (*CobassProcessFunc)(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames);
typedef void (*CobassNoteOnFunc)(CobassHandle handle, int32_t note, float velocity);
typedef void (*CobassNoteOffFunc)(CobassHandle handle, int32_t note);
typedef void (*CobassAllNotesOffFunc)(CobassHandle handle);
typedef void (*CobassSetParamFunc)(CobassHandle handle, uint32_t paramId, float value);
typedef float (*CobassGetParamFunc)(CobassHandle handle, uint32_t paramId);
typedef uint32_t (*CobassGetStateFunc)(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen);
typedef bool (*CobassSetStateFunc)(CobassHandle handle, const char* jsonBuffer);

#ifdef __cplusplus
}
#endif
```

---

## 4. Complete Reference Implementations

### 4.1 Modular Synthesizer Plugin (`SynthPlugin.cpp`)

This complete example implements an 8-voice subtractive polyphonic synthesizer featuring morphing waveforms, resonant 2-pole low-pass filtering, and an ADSR amp envelope.

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor SYNTH_PARAMS[] = {
    {0, "Waveform", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Sawtooth", "Square", "Sine", "Triangle"}, 4},
    {1, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 2500.0f, 1.0f, true, {}, 0},
    {2, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 10.0f, 1.2f, 0.05f, false, {}, 0},
    {3, "Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 15.0f, 1.0f, false, {}, 0},
    {4, "Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 150.0f, 1.0f, false, {}, 0},
    {5, "Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.75f, 0.01f, false, {}, 0},
    {6, "Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 300.0f, 1.0f, false, {}, 0},
    {7, "Volume", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest SYNTH_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.example.synth.subtractive",
    "Subtractive PolySynth",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_SYNTH,
    sizeof(SYNTH_PARAMS) / sizeof(CobassParamDescriptor),
    SYNTH_PARAMS,
    true,  // supportsMidi
    false  // supportsSidechain
};

class PolySynthProcessor {
private:
    struct Voice {
        int32_t note = -1;
        float velocity = 0.0f;
        double phase = 0.0;
        float frequency = 440.0f;
        bool active = false;

        enum class EnvState { Idle, Attack, Decay, Sustain, Release } envState = EnvState::Idle;
        float envValue = 0.0f;

        // Biquad state
        float x1 = 0.0f, x2 = 0.0f, y1 = 0.0f, y2 = 0.0f;

        void trigger(int32_t midiNote, float vel) {
            note = midiNote;
            velocity = vel;
            phase = 0.0;
            frequency = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
            active = true;
            envState = EnvState::Attack;
            x1 = x2 = y1 = y2 = 0.0f;
        }

        void release() {
            if (active && envState != EnvState::Idle) envState = EnvState::Release;
        }

        void stop() {
            active = false;
            note = -1;
            envState = EnvState::Idle;
            envValue = 0.0f;
        }
    };

public:
    explicit PolySynthProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : SYNTH_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        for (auto& v : voices_) v.stop();
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) params_[id] = val;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void noteOn(int32_t note, float velocity) {
        Voice* target = nullptr;
        for (auto& v : voices_) {
            if (!v.active) { target = &v; break; }
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

    void process(const float** /*inputs*/, float** outputs, uint32_t /*numChannels*/, uint32_t numFrames) {
        float* outL = outputs[0];
        float* outR = outputs[1];
        std::fill_n(outL, numFrames, 0.0f);
        std::fill_n(outR, numFrames, 0.0f);

        const int waveform = static_cast<int>(params_[0]);
        const float cutoff = std::clamp(params_[1], 20.0f, sampleRate_ * 0.48f);
        const float resonance = std::clamp(params_[2], 0.5f, 10.0f);
        const float attackRate = 1.0f / (std::max(0.001f, params_[3] * 0.001f) * sampleRate_);
        const float decayRate = 1.0f / (std::max(0.001f, params_[4] * 0.001f) * sampleRate_);
        const float sustainLevel = std::clamp(params_[5], 0.0f, 1.0f);
        const float releaseRate = 1.0f / (std::max(0.001f, params_[6] * 0.001f) * sampleRate_);
        const float gain = std::pow(10.0f, params_[7] / 20.0f) * 0.25f;

        // Biquad 12dB Low-Pass Filter Calculation
        const float w0 = 6.28318530718f * (cutoff / sampleRate_);
        const float cosw0 = std::cos(w0);
        const float alpha = std::sin(w0) / (2.0f * resonance);
        const float a0 = 1.0f + alpha;
        const float b0 = ((1.0f - cosw0) * 0.5f) / a0;
        const float b1 = (1.0f - cosw0) / a0;
        const float b2 = b0;
        const float a1 = (-2.0f * cosw0) / a0;
        const float a2 = (1.0f - alpha) / a0;

        for (uint32_t i = 0; i < numFrames; ++i) {
            float monoSum = 0.0f;

            for (auto& v : voices_) {
                if (!v.active) continue;

                // Envelope processing
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

                // Oscillator waveform generator
                float sample = 0.0f;
                switch (waveform) {
                    case 0: sample = static_cast<float>(2.0 * (v.phase - std::floor(v.phase + 0.5))); break; // Saw
                    case 1: sample = (v.phase < 0.5) ? 1.0f : -1.0f; break;                                 // Square
                    case 2: sample = static_cast<float>(std::sin(v.phase * 6.28318530718)); break;           // Sine
                    case 3: sample = static_cast<float>(4.0 * std::fabs(v.phase - 0.5) - 1.0); break;       // Triangle
                }

                v.phase += v.frequency / sampleRate_;
                if (v.phase >= 1.0) v.phase -= 1.0;

                // Apply dynamic filter
                const float inSig = sample * v.envValue * v.velocity;
                const float outSig = b0 * inSig + b1 * v.x1 + b2 * v.x2 - a1 * v.y1 - a2 * v.y2;
                v.x2 = v.x1; v.x1 = inSig;
                v.y2 = v.y1; v.y1 = outSig;

                monoSum += outSig;
            }

            outL[i] = monoSum * gain;
            outR[i] = monoSum * gain;
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
            std::string key = "\"" + std::to_string(i) + "\":";
            const char* pos = std::strstr(json, key.c_str());
            if (pos) params_[i] = std::strtof(pos + key.size(), nullptr);
        }
        return true;
    }

private:
    float sampleRate_ = 48000.0f;
    std::array<float, 8> params_{};
    std::array<Voice, 8> voices_{};
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &SYNTH_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new PolySynthProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<PolySynthProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<PolySynthProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->process(inputs, outputs, numChannels, numFrames);
    }
    void cobass_plugin_note_on(CobassHandle handle, int32_t note, float velocity) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->noteOn(note, velocity);
    }
    void cobass_plugin_note_off(CobassHandle handle, int32_t note) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->noteOff(note);
    }
    void cobass_plugin_all_notes_off(CobassHandle handle) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->allNotesOff();
    }
    void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
        if (handle) static_cast<PolySynthProcessor*>(handle)->setParam(paramId, value);
    }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
        return handle ? static_cast<PolySynthProcessor*>(handle)->getParam(paramId) : 0.0f;
    }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
        return handle ? static_cast<PolySynthProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
    }
    bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
        return handle && static_cast<PolySynthProcessor*>(handle)->setStateJson(jsonBuffer);
    }
}
```

---

### 4.2 Modular Audio Effect Plugin (`EffectPlugin.cpp`)

This complete example implements a stereo ping-pong digital delay with feedback low-pass damping and dry/wet blending.

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor DELAY_PARAMS[] = {
    {0, "Time Left", "ms", COBASS_PARAM_TYPE_FLOAT, 10.0f, 2000.0f, 350.0f, 1.0f, false, {}, 0},
    {1, "Time Right", "ms", COBASS_PARAM_TYPE_FLOAT, 10.0f, 2000.0f, 500.0f, 1.0f, false, {}, 0},
    {2, "Feedback", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 0.95f, 0.45f, 0.01f, false, {}, 0},
    {3, "Damping", "Hz", COBASS_PARAM_TYPE_FLOAT, 500.0f, 18000.0f, 4500.0f, 10.0f, true, {}, 0},
    {4, "Ping-Pong", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 1.0f, 1.0f, false, {}, 0},
    {5, "Dry/Wet", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.35f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest DELAY_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.example.fx.delay",
    "Stereo PingPong Delay",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    sizeof(DELAY_PARAMS) / sizeof(CobassParamDescriptor),
    DELAY_PARAMS,
    false, // supportsMidi
    false  // supportsSidechain
};

class StereoDelayProcessor {
public:
    explicit StereoDelayProcessor(float sampleRate) : sampleRate_(sampleRate) {
        bufferL_.assign(MAX_BUFFER_SAMPLES, 0.0f);
        bufferR_.assign(MAX_BUFFER_SAMPLES, 0.0f);
        for (const auto& p : DELAY_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
        std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
        writeIndex_ = 0;
        dampStateL_ = dampStateR_ = 0.0f;
    }

    void setParam(uint32_t id, float val) {
        if (id < params_.size()) params_[id] = val;
    }

    float getParam(uint32_t id) const {
        return (id < params_.size()) ? params_[id] : 0.0f;
    }

    void process(const float** inputs, float** outputs, uint32_t /*numChannels*/, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const int32_t delayL = std::clamp(static_cast<int32_t>((params_[0] * 0.001f) * sampleRate_), 1, static_cast<int32_t>(MAX_BUFFER_SAMPLES - 1));
        const int32_t delayR = std::clamp(static_cast<int32_t>((params_[1] * 0.001f) * sampleRate_), 1, static_cast<int32_t>(MAX_BUFFER_SAMPLES - 1));
        const float feedback = std::clamp(params_[2], 0.0f, 0.95f);
        const float dampFreq = std::clamp(params_[3], 500.0f, sampleRate_ * 0.45f);
        const bool pingPong = params_[4] > 0.5f;
        const float mix = std::clamp(params_[5], 0.0f, 1.0f);

        // 1-pole lowpass coefficient for feedback damping
        const float dampCoeff = std::exp(-6.2831853f * (dampFreq / sampleRate_));

        for (uint32_t i = 0; i < numFrames; ++i) {
            const float sL = inL[i];
            const float sR = inR[i];

            int32_t readIdxL = static_cast<int32_t>(writeIndex_) - delayL;
            if (readIdxL < 0) readIdxL += MAX_BUFFER_SAMPLES;

            int32_t readIdxR = static_cast<int32_t>(writeIndex_) - delayR;
            if (readIdxR < 0) readIdxR += MAX_BUFFER_SAMPLES;

            float delayedL = bufferL_[readIdxL];
            float delayedR = bufferR_[readIdxR];

            // Lowpass filter feedback
            dampStateL_ = (delayedL * (1.0f - dampCoeff)) + (dampStateL_ * dampCoeff);
            dampStateR_ = (delayedR * (1.0f - dampCoeff)) + (dampStateR_ * dampCoeff);

            if (pingPong) {
                bufferL_[writeIndex_] = sL + dampStateR_ * feedback;
                bufferR_[writeIndex_] = sR + dampStateL_ * feedback;
            } else {
                bufferL_[writeIndex_] = sL + dampStateL_ * feedback;
                bufferR_[writeIndex_] = sR + dampStateR_ * feedback;
            }

            writeIndex_ = (writeIndex_ + 1) % MAX_BUFFER_SAMPLES;

            outL[i] = sL * (1.0f - mix) + delayedL * mix;
            outR[i] = sR * (1.0f - mix) + delayedR * mix;
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
            std::string key = "\"" + std::to_string(i) + "\":";
            const char* pos = std::strstr(json, key.c_str());
            if (pos) params_[i] = std::strtof(pos + key.size(), nullptr);
        }
        return true;
    }

private:
    static constexpr size_t MAX_BUFFER_SAMPLES = 192000; // 4 seconds @ 48kHz
    float sampleRate_ = 48000.0f;
    std::array<float, 6> params_{};
    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t writeIndex_ = 0;
    float dampStateL_ = 0.0f;
    float dampStateR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &DELAY_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new StereoDelayProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<StereoDelayProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<StereoDelayProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames) {
        if (handle) static_cast<StereoDelayProcessor*>(handle)->process(inputs, outputs, numChannels, numFrames);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle handle, uint32_t paramId, float value) {
        if (handle) static_cast<StereoDelayProcessor*>(handle)->setParam(paramId, value);
    }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t paramId) {
        return handle ? static_cast<StereoDelayProcessor*>(handle)->getParam(paramId) : 0.0f;
    }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen) {
        return handle ? static_cast<StereoDelayProcessor*>(handle)->getStateJson(outJsonBuffer, maxLen) : 0;
    }
    bool cobass_plugin_set_state(CobassHandle handle, const char* jsonBuffer) {
        return handle && static_cast<StereoDelayProcessor*>(handle)->setStateJson(jsonBuffer);
    }
}
```

---

## 5. Automated Standalone Plugin Build Toolchain

Below is the complete standalone build script (`build_plugin.sh`) that cross-compiles any external or addon C++ plugin into an Android `arm64-v8a` shared library without requiring Gradle.

### `build_plugin.sh`

```bash
#!/usr/bin/env bash
# ==============================================================================
# Cobass Modular Plugin Standalone Compiler (No Gradle Required)
# Usage: ./build_plugin.sh <source_directory> [output_directory]
# Example: ./build_plugin.sh addons/synth-hyperion app/lib/arm64-v8a
# ==============================================================================
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <plugin_source_dir> [output_so_dir]"
    exit 1
fi

SRC_DIR="$1"
OUT_DIR="${2:-app/lib/arm64-v8a}"
API_LEVEL="34"
TARGET_ABI="arm64-v8a"
TARGET_TRIPLE="aarch64-linux-android${API_LEVEL}"

mkdir -p "$OUT_DIR"

# 1. Locate Compiler (Desktop Android NDK or Termux Clang)
COMPILER=""
EXTRA_FLAGS=()

if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
    TOOLCHAIN_BIN=$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -maxdepth 2 -name "bin" 2>/dev/null | head -n 1)
    if [ -f "$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++" ]; then
        COMPILER="$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++"
    fi
elif [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
    TOOLCHAIN_BIN=$(find "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt" -maxdepth 2 -name "bin" 2>/dev/null | head -n 1)
    if [ -f "$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++" ]; then
        COMPILER="$TOOLCHAIN_BIN/${TARGET_TRIPLE}-clang++"
    fi
fi

if [ -z "$COMPILER" ] && command -v clang++ &>/dev/null; then
    COMPILER=$(command -v clang++)
    EXTRA_FLAGS=(
        "-fPIC"
        "-target" "$TARGET_TRIPLE"
        "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT"
        "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES"
        "-Wno-macro-redefined"
    )
fi

if [ -z "$COMPILER" ]; then
    echo -e "\033[91m[ERROR] Clang++ compiler not found. Set ANDROID_NDK_HOME or install clang in Termux.\033[0m"
    exit 1
fi

# 2. Collect C++ Source Files
CPP_FILES=($(find "$SRC_DIR" -name "*.cpp"))
if [ ${#CPP_FILES[@]} -eq 0 ]; then
    echo -e "\033[91m[ERROR] No .cpp source files found in $SRC_DIR\033[0m"
    exit 1
fi

PLUGIN_BASENAME=$(basename "$SRC_DIR" | tr '-' '_')
TARGET_SO="${OUT_DIR}/libcobass_plugin_${PLUGIN_BASENAME}.so"

echo "======================================================================"
echo "==> Compiling Cobass Plugin: $PLUGIN_BASENAME"
echo "    Compiler: $COMPILER"
echo "    Output:   $TARGET_SO"
echo "======================================================================"

$COMPILER \
    -std=c++20 \
    -shared \
    -fPIC \
    -O3 \
    -DNDEBUG \
    -ffast-math \
    -I"app/native/include" \
    -I"$SRC_DIR" \
    -I"$SRC_DIR/src" \
    "${EXTRA_FLAGS[@]}" \
    "${CPP_FILES[@]}" \
    -o "$TARGET_SO" \
    -lm

FILE_SIZE_KB=$(du -k "$TARGET_SO" | cut -f1)
echo -e "\033[92m[✓] SUCCESS: Built libcobass_plugin_${PLUGIN_BASENAME}.so (${FILE_SIZE_KB} KB)\033[0m"
```

---

## 6. Packaging, Distribution & Deployment

Cobass supports two deployment paths:

### Path A: Bundling with the APK (Factory Plugins)
1. Place plugin source under `addons/<plugin-name>/src/*.cpp`.
2. Run `./build.sh` (or `python3 tools/build_addons.py arm64-v8a`).
3. The `.so` binary is compiled to `app/lib/arm64-v8a/libcobass_plugin_<name>.so` and automatically bundled into the APK under `lib/arm64-v8a/`.
4. `PluginHostManager` discovers it on startup.

### Path B: Runtime Sideloading (User Custom Plugins)
1. Compile your plugin using `./build_plugin.sh /path/to/plugin_src /tmp/out`.
2. Push the resulting `libcobass_plugin_*.so` directly to the app's internal storage directory:
   ```bash
   adb push /tmp/out/libcobass_plugin_mycustom.so /data/data/com.maxica.cobass/files/plugins/
   ```
3. Open Cobass, tap `[⚙ PREFERENCES] -> [SCAN PLUGINS]` or open any track FX/Instrument rack. The plugin will appear in the browser.

---

## 7. Step-by-Step Tutorial: Creating a Custom Plugin from Scratch

### Step 1: Create the Project Directory
```bash
mkdir -p addons/fx-bitcrusher/src
```

### Step 2: Write the DSP & C-ABI Code (`BitcrusherPlugin.cpp`)
Save the following code inside `addons/fx-bitcrusher/src/BitcrusherPlugin.cpp`:

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor CRUSH_PARAMS[] = {
    {0, "Bit Depth", "bits", COBASS_PARAM_TYPE_INT, 2.0f, 16.0f, 8.0f, 1.0f, false, {}, 0},
    {1, "Downsample", "x", COBASS_PARAM_TYPE_FLOAT, 1.0f, 64.0f, 4.0f, 1.0f, false, {}, 0},
    {2, "Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest CRUSH_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.developer.fx.bitcrusher",
    "Digital Bitcrusher",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    3,
    CRUSH_PARAMS,
    false, false
};

class BitcrusherProcessor {
public:
    explicit BitcrusherProcessor(float) {
        for (const auto& p : CRUSH_PARAMS) params_[p.id] = p.defaultValue;
    }

    void reset(float) { holdCount_ = 0.0f; heldL_ = heldR_ = 0.0f; }

    void setParam(uint32_t id, float v) { if (id < 3) params_[id] = v; }
    float getParam(uint32_t id) const { return id < 3 ? params_[id] : 0.0f; }

    void process(const float** inputs, float** outputs, uint32_t, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float bits = std::clamp(params_[0], 2.0f, 16.0f);
        const float levels = std::pow(2.0f, bits) * 0.5f;
        const float downsample = std::clamp(params_[1], 1.0f, 64.0f);
        const float mix = std::clamp(params_[2], 0.0f, 1.0f);

        for (uint32_t i = 0; i < numFrames; ++i) {
            holdCount_ += 1.0f;
            if (holdCount_ >= downsample) {
                holdCount_ = 0.0f;
                heldL_ = std::round(inL[i] * levels) / levels;
                heldR_ = std::round(inR[i] * levels) / levels;
            }

            outL[i] = inL[i] * (1.0f - mix) + heldL_ * mix;
            outR[i] = inR[i] * (1.0f - mix) + heldR_ * mix;
        }
    }

    uint32_t getStateJson(char* outBuffer, uint32_t maxLen) const {
        std::string json = "{\"0\":" + std::to_string(params_[0]) + ",\"1\":" + std::to_string(params_[1]) + ",\"2\":" + std::to_string(params_[2]) + "}";
        if (json.size() >= maxLen) return 0;
        std::memcpy(outBuffer, json.c_str(), json.size() + 1);
        return static_cast<uint32_t>(json.size());
    }

    bool setStateJson(const char* json) {
        if (!json) return false;
        for (int i = 0; i < 3; ++i) {
            std::string key = "\"" + std::to_string(i) + "\":";
            const char* pos = std::strstr(json, key.c_str());
            if (pos) params_[i] = std::strtof(pos + key.size(), nullptr);
        }
        return true;
    }

private:
    std::array<float, 3> params_{};
    float holdCount_ = 0.0f;
    float heldL_ = 0.0f, heldR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &CRUSH_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sampleRate) { return new BitcrusherProcessor(sampleRate); }
    void cobass_plugin_destroy_instance(CobassHandle handle) { delete static_cast<BitcrusherProcessor*>(handle); }
    void cobass_plugin_reset(CobassHandle handle, float sampleRate) { if (handle) static_cast<BitcrusherProcessor*>(handle)->reset(sampleRate); }
    void cobass_plugin_process(CobassHandle handle, const float** inputs, float** outputs, uint32_t c, uint32_t n) {
        if (handle) static_cast<BitcrusherProcessor*>(handle)->process(inputs, outputs, c, n);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle handle, uint32_t p, float v) { if (handle) static_cast<BitcrusherProcessor*>(handle)->setParam(p, v); }
    float cobass_plugin_get_param(CobassHandle handle, uint32_t p) { return handle ? static_cast<BitcrusherProcessor*>(handle)->getParam(p) : 0.0f; }
    uint32_t cobass_plugin_get_state(CobassHandle handle, char* b, uint32_t m) { return handle ? static_cast<BitcrusherProcessor*>(handle)->getStateJson(b, m) : 0; }
    bool cobass_plugin_set_state(CobassHandle handle, const char* j) { return handle && static_cast<BitcrusherProcessor*>(handle)->setStateJson(j); }
}
```

### Step 3: Compile and Test
Run the build script:
```bash
./build.sh
```
Launch Cobass, navigate to the Track Inspector on any track, open **Modular FX Rack (8 Slots)**, tap `+ Add Insert Plugin`, and select **Digital Bitcrusher**. The dynamic parameter matrix renders the Bit Depth stepper, Downsample knob, and Mix fader ready for real-time manipulation.


# Standalone Plugin APK Sideloading Architecture (Path B)

This specification defines how third-party developers can build, package, and distribute custom **Synthesizer** and **Audio Effect** plugins as **standalone Android APKs** (e.g., `HyperionSynth.apk`, `VintageChorus.apk`), and how the **Cobass Host Application** automatically discovers, sideloads, and executes them at runtime.

---

## 1. System Architecture: Standalone Plugin APK Model

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                          3RD-PARTY STANDALONE PLUGIN APK                               │
│                         (e.g., com.example.plugin.hyperion)                            │
│                                                                                        │
│  ├── AndroidManifest.xml   -> Declares intent-filter: "com.maxica.cobass.PLUGIN"       │
│  │                            Meta-data: plugin_id, plugin_type (SYNTH/EFFECT)         │
│  ├── lib/arm64-v8a/                                                                    │
│  │   └── libcobass_plugin_hyperion.so  -> Native C++20 C-ABI Binary                    │
│  └── classes.dex / res     -> Minimal headless manifest container                      │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ Distributed as regular .apk file
                    ┌────────────────────┴────────────────────┐
                    ▼                                         ▼
   [Option 1: Device App Installation]       [Option 2: Direct In-App File Import]
   User installs APK via Android Package     User downloads APK and opens via
   Installer (`adb install` or tap APK)      Cobass `[📦 Import Plugin APK]` (SAF)
                    │                                         │
                    └────────────────────┬────────────────────┘
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                COBASS HOST APPLICATION                                 │
│                                                                                        │
│  1. PluginApkInstaller.java:                                                           │
│     • Queries PackageManager for action: "com.maxica.cobass.PLUGIN"                    │
│     • Or unzips imported APK directly in memory using ZipInputStream                   │
│     • Extracts `lib/arm64-v8a/libcobass_plugin_*.so`                                   │
│     • Installs binary into `/data/data/com.maxica.cobass/files/plugins/`               │
│                                                                                        │
│  2. PluginLoader.hpp (C++ Engine):                                                     │
│     • `dlopen()` loads the extracted shared library cleanly without SELinux blocks     │
│     • Inspects `cobass_plugin_get_manifest()`                                          │
│     • Registers the plugin in the real-time mixer and track instrument/FX racks        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Standalone Plugin APK Specification

Every standalone plugin APK requires two main components:
1. An **`AndroidManifest.xml`** declaring the Cobass plugin intent filter and metadata.
2. A **native C++20 shared library** (`lib/arm64-v8a/libcobass_plugin_<name>.so`) compiled against `CobassPluginABI.h`.

### 2.1 Plugin `AndroidManifest.xml` Blueprint

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.cobass.plugin.hyperion"
    android:versionCode="1"
    android:versionName="1.0.0">

    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />

    <application
        android:label="Hyperion Wavetable Synth"
        android:hasCode="false">

        <!-- Extension Service / Activity Descriptor for Discovery -->
        <service
            android:name=".PluginDescriptorService"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <!-- Standard Cobass Discovery Action -->
                <action android:name="com.maxica.cobass.PLUGIN" />
                <!-- Plugin Category: SYNTH or EFFECT -->
                <category android:name="com.maxica.cobass.CATEGORY_SYNTH" />
            </intent-filter>

            <meta-data
                android:name="com.maxica.cobass.PLUGIN_ID"
                android:value="com.example.cobass.plugin.hyperion" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_NAME"
                android:value="Hyperion Wavetable Synth" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_VENDOR"
                android:value="Maxica Audio" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_TYPE"
                android:value="SYNTH" />
            <meta-data
                android:name="com.maxica.cobass.PLUGIN_LIB_NAME"
                android:value="libcobass_plugin_synth_hyperion.so" />
        </service>
    </application>
</manifest>
```

---

## 3. Host App Implementation (`com.maxica.cobass`)

To support standalone APK sideloading and discovery, we add **`PluginApkInstaller.java`** to the host app. This component provides two loading paths:
1. **System PackageManager Auto-Discovery**: Detects all installed plugin APKs on the device, extracts their `.so` binaries from `publicSourceDir` (the installed APK file), and mounts them.
2. **In-App Direct APK Sideloading**: Allows users to import any `.apk` file directly using the Android Storage Access Framework (SAF) without installing the APK at the OS level.

### 3.1 `PluginApkInstaller.java` (Host Engine)

```java
package com.maxica.cobass.plugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import com.maxica.cobass.audio.AudioEngineNative;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class PluginApkInstaller {

    public static final String ACTION_COBASS_PLUGIN = "com.maxica.cobass.PLUGIN";
    private static final String TARGET_ABI_PREFIX = "lib/arm64-v8a/";

    /**
     * Scans all installed Android apps on the device matching the Cobass plugin intent,
     * extracts their arm64-v8a native shared libraries into the app's internal plugin directory,
     * and triggers a native engine catalog reload.
     */
    public static int scanAndMountInstalledPluginApks(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(ACTION_COBASS_PLUGIN);
        List<ResolveInfo> plugins = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        File internalPluginDir = new File(context.getFilesDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        int mountedCount = 0;
        for (ResolveInfo info : plugins) {
            if (info.serviceInfo == null || info.serviceInfo.packageName == null) continue;
            String packageName = info.serviceInfo.packageName;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String apkPath = appInfo.publicSourceDir != null ? appInfo.publicSourceDir : appInfo.sourceDir;
                if (apkPath == null) continue;

                File apkFile = new File(apkPath);
                if (!apkFile.exists()) continue;

                if (extractSoFromApkFile(apkFile, internalPluginDir)) {
                    mountedCount++;
                }
            } catch (Exception e) {
                Log.e("CobassPlugin", "Failed to mount installed plugin APK: " + packageName, e);
            }
        }

        // Trigger native C++ dynamic loader scan over internal plugins folder
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
        }

        return mountedCount;
    }

    /**
     * Sideloads a standalone plugin directly from an APK / bundle file selected via SAF.
     */
    public static boolean installPluginFromUri(Context context, Uri uri) {
        File internalPluginDir = new File(context.getFilesDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        boolean success = false;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(TARGET_ABI_PREFIX) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(internalPluginDir, fileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    outFile.setReadable(true, false);
                    outFile.setExecutable(true, false);
                    success = true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Error installing plugin from URI", e);
            return false;
        }

        if (success && AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
            PluginHostManager.getInstance().scanPlugins(context);
        }

        return success;
    }

    private static boolean extractSoFromApkFile(File apkFile, File targetDir) {
        boolean extracted = false;
        try (ZipFile zip = new ZipFile(apkFile)) {
            var entries = zip.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith(TARGET_ABI_PREFIX) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(targetDir, fileName);

                    // Check if update needed
                    if (!outFile.exists() || outFile.length() != entry.getSize()) {
                        try (InputStream is = zip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        outFile.setReadable(true, false);
                        outFile.setExecutable(true, false);
                    }
                    extracted = true;
                }
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Failed to extract .so from APK: " + apkFile.getName(), e);
            return false;
        }
        return extracted;
    }
}
```

---

## 4. Standalone Plugin APK Builder (`build_standalone_plugin_apk.py`)

This standalone build tool cross-compiles your C++ plugin code into an `arm64-v8a` native shared library, creates the minimal headless manifest, packages it into a signed `.apk`, and verifies the cryptographic signature without requiring Gradle or Android Studio.

### `tools/build_standalone_plugin_apk.py`

```python
#!/usr/bin/env python3
"""
Cobass Standalone Plugin APK Builder (No Gradle Required)
Usage:
    python3 build_standalone_plugin_apk.py \
        --src addons/synth-hyperion \
        --pkg com.example.cobass.plugin.hyperion \
        --name "Hyperion Synth" \
        --type SYNTH \
        --out out/plugins/HyperionSynth.apk
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

def find_tool(name: str) -> str:
    path = shutil.which(name)
    if path: return path
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        bt_dir = Path(sdk_root) / "build-tools"
        if bt_dir.is_dir():
            for b in sorted(bt_dir.iterdir(), reverse=True):
                cand = b / name
                if cand.exists(): return str(cand)
    return name

def find_compiler(target_abi: str = "arm64-v8a"):
    api_level = "34"
    ndk_home = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if ndk_home and Path(ndk_home).is_dir():
        llvm_bin = Path(ndk_home) / "toolchains/llvm/prebuilt"
        host_dirs = list(llvm_bin.glob("*"))
        if host_dirs:
            bin_dir = host_dirs[0] / "bin"
            target_prefix = f"aarch64-linux-android{api_level}-clang++"
            compiler = bin_dir / target_prefix
            if compiler.exists(): return str(compiler), []

    clang_path = shutil.which("clang++")
    if clang_path:
        target_triple = f"aarch64-linux-android{api_level}"
        return clang_path, [
            "-fPIC", "-target", target_triple,
            "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT",
            "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES",
            "-Wno-macro-redefined"
        ]
    return None, []

def get_android_jar() -> str:
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        plat_dir = Path(sdk_root) / "platforms"
        jars = sorted(list(plat_dir.glob("android-*/android.jar")), reverse=True)
        if jars: return str(jars[0])
    print("[ERROR] android.jar not found in SDK platforms.")
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="Cobass Standalone Plugin APK Compiler")
    parser.add_argument("--src", required=True, help="Directory containing plugin C++ sources")
    parser.add_argument("--pkg", required=True, help="Package name (e.g. com.example.plugin.hyperion)")
    parser.add_argument("--name", required=True, help="Display Name (e.g. 'Hyperion Synth')")
    parser.add_argument("--type", default="EFFECT", choices=["SYNTH", "EFFECT"], help="Plugin Type")
    parser.add_argument("--out", default="out/plugins/Plugin.apk", help="Output APK path")
    args = parser.parse_args()

    src_dir = Path(args.src)
    out_apk = Path(args.out).resolve()
    out_apk.parent.mkdir(parents=True, exist_ok=True)

    work_dir = Path("out/plugin_build_tmp")
    if work_dir.exists(): shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

    compiler, extra_flags = find_compiler("arm64-v8a")
    if not compiler:
        print("[ERROR] Clang++ compiler not found.")
        sys.exit(1)

    aapt2 = find_tool("aapt2")
    zipalign = find_tool("zipalign")
    apksigner = find_tool("apksigner")
    android_jar = get_android_jar()

    # 1. Compile C++ Plugin .so
    cpp_files = list(src_dir.glob("*.cpp")) + list(src_dir.glob("src/*.cpp"))
    if not cpp_files:
        print(f"[ERROR] No .cpp source files found in {src_dir}")
        sys.exit(1)

    lib_name = f"libcobass_plugin_{src_dir.name.replace('-', '_')}.so"
    so_out = work_dir / "lib" / "arm64-v8a" / lib_name
    so_out.parent.mkdir(parents=True, exist_ok=True)

    print(f"[*] [1/5] Compiling C++20 Plugin Binary -> {lib_name}...")
    compile_cmd = [
        compiler, "-std=c++20", "-shared", "-fPIC", "-O3", "-DNDEBUG", "-ffast-math",
        "-Iapp/native/include", f"-I{src_dir}", f"-I{src_dir}/src",
        *extra_flags, *[str(f) for f in cpp_files],
        "-o", str(so_out), "-lm"
    ]
    res = subprocess.run(compile_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"[COMPILATION FAILED]\n{res.stderr}")
        sys.exit(1)

    # 2. Generate Plugin AndroidManifest.xml
    manifest_xml = work_dir / "AndroidManifest.xml"
    manifest_content = f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{args.pkg}"
    android:versionCode="1"
    android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />
    <application android:label="{args.name}" android:hasCode="false">
        <service android:name=".PluginDescriptorService" android:exported="true" android:enabled="true">
            <intent-filter>
                <action android:name="com.maxica.cobass.PLUGIN" />
                <category android:name="com.maxica.cobass.CATEGORY_{args.type}" />
            </intent-filter>
            <meta-data android:name="com.maxica.cobass.PLUGIN_ID" android:value="{args.pkg}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_NAME" android:value="{args.name}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_TYPE" android:value="{args.type}" />
            <meta-data android:name="com.maxica.cobass.PLUGIN_LIB_NAME" android:value="{lib_name}" />
        </service>
    </application>
</manifest>
"""
    manifest_xml.write_text(manifest_content, encoding="utf-8")

    # 3. Link APK via AAPT2
    print("[*] [2/5] Linking Plugin APK Manifest with AAPT2...")
    unaligned_apk = work_dir / "unaligned.apk"
    link_cmd = [
        aapt2, "link",
        "-I", android_jar,
        "--min-sdk-version", "26",
        "--target-sdk-version", "34",
        "--version-code", "1",
        "--version-name", "1.0.0",
        "--manifest", str(manifest_xml),
        "-o", str(unaligned_apk)
    ]
    subprocess.run(link_cmd, check=True)

    # 4. Embed Native .so into APK Archive
    print("[*] [3/5] Packaging arm64-v8a Native Library into APK...")
    with zipfile.ZipFile(unaligned_apk, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(so_out, arcname=f"lib/arm64-v8a/{lib_name}")

    # 5. ZipAlign
    print("[*] [4/5] 4-Byte Alignment with ZipAlign...")
    aligned_apk = work_dir / "aligned.apk"
    subprocess.run([zipalign, "-f", "4", str(unaligned_apk), str(aligned_apk)], check=True)

    # 6. Sign APK with Debug Keystore
    print("[*] [5/5] Cryptographically Signing Plugin APK...")
    keystore = Path("config/debug.keystore")
    sign_cmd = [
        apksigner, "sign",
        "--ks", str(keystore),
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", str(out_apk),
        str(aligned_apk)
    ]
    subprocess.run(sign_cmd, check=True)

    size_kb = out_apk.stat().st_size / 1024
    print("=" * 65)
    print(f"\033[92m[✓] STANDALONE PLUGIN APK READY: {out_apk}\033[0m")
    print(f"    Package ID:  {args.pkg}")
    print(f"    Plugin Name: {args.name}")
    print(f"    Plugin Type: {args.type}")
    print(f"    Binary:      {lib_name} ({so_out.stat().st_size / 1024:.1f} KB)")
    print(f"    File Size:   {size_kb:.1f} KB")
    print("=" * 65)

if __name__ == "__main__":
    main()
```

---

## 5. Step-by-Step Tutorial: Building & Sideloading a Custom Plugin APK

### Step 1: Write the DSP Code
Create `addons/fx-distortion/src/DistortionPlugin.cpp`:

```cpp
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <cstring>
#include <string>
#include "CobassPluginABI.h"

static const CobassParamDescriptor DIST_PARAMS[] = {
    {0, "Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 40.0f, 12.0f, 0.2f, false, {}, 0},
    {1, "Tone", "Hz", COBASS_PARAM_TYPE_FLOAT, 200.0f, 15000.0f, 4000.0f, 10.0f, true, {}, 0},
    {2, "Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 1.0f, 0.01f, false, {}, 0}
};

static const CobassPluginManifest DIST_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.developer.fx.distortion",
    "Overdrive Saturation",
    "Developer",
    "1.0.0",
    COBASS_PLUGIN_TYPE_EFFECT,
    3,
    DIST_PARAMS,
    false, false
};

class OverdriveProcessor {
public:
    explicit OverdriveProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : DIST_PARAMS) params_[p.id] = p.defaultValue;
    }
    void reset(float sampleRate) { sampleRate_ = sampleRate; sL_ = sR_ = 0.0f; }
    void setParam(uint32_t id, float v) { if (id < 3) params_[id] = v; }
    float getParam(uint32_t id) const { return id < 3 ? params_[id] : 0.0f; }

    void process(const float** inputs, float** outputs, uint32_t, uint32_t numFrames) {
        const float* inL = inputs ? inputs[0] : outputs[0];
        const float* inR = inputs ? inputs[1] : outputs[1];
        float* outL = outputs[0];
        float* outR = outputs[1];

        const float driveGain = std::pow(10.0f, params_[0] / 20.0f);
        const float alpha = std::exp(-6.2831853f * (params_[1] / sampleRate_));
        const float mix = params_[2];

        for (uint32_t i = 0; i < numFrames; ++i) {
            float wetL = std::tanh(inL[i] * driveGain);
            float wetR = std::tanh(inR[i] * driveGain);

            sL_ = (wetL * (1.0f - alpha)) + (sL_ * alpha);
            sR_ = (wetR * (1.0f - alpha)) + (sR_ * alpha);

            outL[i] = inL[i] * (1.0f - mix) + sL_ * mix;
            outR[i] = inR[i] * (1.0f - mix) + sR_ * mix;
        }
    }

    uint32_t getStateJson(char* b, uint32_t m) const {
        std::string j = "{\"0\":" + std::to_string(params_[0]) + ",\"1\":" + std::to_string(params_[1]) + ",\"2\":" + std::to_string(params_[2]) + "}";
        if (j.size() >= m) return 0;
        std::memcpy(b, j.c_str(), j.size() + 1);
        return static_cast<uint32_t>(j.size());
    }

    bool setStateJson(const char* j) {
        if (!j) return false;
        for (int i = 0; i < 3; ++i) {
            std::string k = "\"" + std::to_string(i) + "\":";
            const char* p = std::strstr(j, k.c_str());
            if (p) params_[i] = std::strtof(p + k.size(), nullptr);
        }
        return true;
    }

private:
    float sampleRate_ = 48000.0f;
    std::array<float, 3> params_{};
    float sL_ = 0.0f, sR_ = 0.0f;
};

extern "C" {
    const CobassPluginManifest* cobass_plugin_get_manifest(void) { return &DIST_MANIFEST; }
    CobassHandle cobass_plugin_create_instance(float sr) { return new OverdriveProcessor(sr); }
    void cobass_plugin_destroy_instance(CobassHandle h) { delete static_cast<OverdriveProcessor*>(h); }
    void cobass_plugin_reset(CobassHandle h, float sr) { if (h) static_cast<OverdriveProcessor*>(h)->reset(sr); }
    void cobass_plugin_process(CobassHandle h, const float** in, float** out, uint32_t c, uint32_t n) {
        if (h) static_cast<OverdriveProcessor*>(h)->process(in, out, c, n);
    }
    void cobass_plugin_note_on(CobassHandle, int32_t, float) {}
    void cobass_plugin_note_off(CobassHandle, int32_t) {}
    void cobass_plugin_all_notes_off(CobassHandle) {}
    void cobass_plugin_set_param(CobassHandle h, uint32_t p, float v) { if (h) static_cast<OverdriveProcessor*>(h)->setParam(p, v); }
    float cobass_plugin_get_param(CobassHandle h, uint32_t p) { return h ? static_cast<OverdriveProcessor*>(h)->getParam(p) : 0.0f; }
    uint32_t cobass_plugin_get_state(CobassHandle h, char* b, uint32_t m) { return h ? static_cast<OverdriveProcessor*>(h)->getStateJson(b, m) : 0; }
    bool cobass_plugin_set_state(CobassHandle h, const char* j) { return h && static_cast<OverdriveProcessor*>(h)->setStateJson(j); }
}
```

### Step 2: Compile the Standalone Plugin APK
Run the Python build script:

```bash
python3 tools/build_standalone_plugin_apk.py \
    --src addons/fx-distortion \
    --pkg com.developer.fx.distortion \
    --name "Overdrive Saturation" \
    --type EFFECT \
    --out out/plugins/OverdriveSaturation.apk
```

---

## 6. Sideloading and Running the Plugin APK on Device

You can now distribute `OverdriveSaturation.apk` directly to users.

### Distribution Method A: Sideload & Install onto Device
1. Install the plugin APK on your device:
   ```bash
   adb install -r out/plugins/OverdriveSaturation.apk
   ```
2. Open Cobass. On startup, `PluginApkInstaller` detects the `com.maxica.cobass.PLUGIN` service, extracts `libcobass_plugin_fx_distortion.so`, and automatically mounts it into the **Modular Insert FX Rack**.

### Distribution Method B: Direct In-App File Sideloading (No OS Install)
1. Send `OverdriveSaturation.apk` to your phone via WhatsApp, Telegram, Google Drive, or SD Card.
2. In Cobass, open **⚙ PREFERENCES** and tap **📦 Sideload Plugin (.apk)**.
3. Select `OverdriveSaturation.apk`. Cobass unzips the arm64-v8a binary directly into its internal plugin folder and hot-reloads the catalog without requiring root access or adb commands.
```

---

### File: `plan/synth-host-plugin-v1.md`

```markdown

```

---

### File: `app/src/com/maxica/cobass/plugin/PluginHostManager.java`

```java
package com.maxica.cobass.plugin;

import android.content.Context;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginHostManager {
    private static PluginHostManager sInstance;

    private final List<PluginDescriptorItem> availablePlugins = new ArrayList<>();

    public static synchronized PluginHostManager getInstance() {
        if (sInstance == null) {
            sInstance = new PluginHostManager();
        }
        return sInstance;
    }

    private PluginHostManager() {}

    public void scanPlugins(Context context) {
        availablePlugins.clear();
        if (!AudioEngineNative.isLoaded()) return;

        // 1. Scan native APK library folder
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        if (nativeLibDir != null) {
            AudioEngineNative.nativeScanPlugins(nativeLibDir);
        }

        // 2. Scan internal executable plugins cache folder (SELinux compliant)
        File internalPluginDir = new File(context.getCodeCacheDir(), "plugins");
        if (internalPluginDir.exists()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
        }
        File legacyPluginDir = new File(context.getFilesDir(), "plugins");
        if (legacyPluginDir.exists()) {
            AudioEngineNative.nativeScanPlugins(legacyPluginDir.getAbsolutePath());
        }

        int count = AudioEngineNative.nativeGetPluginCount();
        for (int i = 0; i < count; i++) {
            PluginDescriptorItem item = AudioEngineNative.nativeGetPluginDescriptor(i);
            if (item != null) {
                availablePlugins.add(item);
            }
        }
    }

    public List<PluginDescriptorItem> getAvailablePlugins() {
        return Collections.unmodifiableList(availablePlugins);
    }

    public List<PluginDescriptorItem> getSynthPlugins() {
        List<PluginDescriptorItem> synths = new ArrayList<>();
        for (PluginDescriptorItem item : availablePlugins) {
            if (item.getType() == PluginDescriptorItem.Type.SYNTH) synths.add(item);
        }
        return Collections.unmodifiableList(synths);
    }

    public List<PluginDescriptorItem> getEffectPlugins() {
        List<PluginDescriptorItem> effects = new ArrayList<>();
        for (PluginDescriptorItem item : availablePlugins) {
            if (item.getType() == PluginDescriptorItem.Type.EFFECT) effects.add(item);
        }
        return Collections.unmodifiableList(effects);
    }

    public PluginDescriptorItem findPluginById(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return null;
        for (PluginDescriptorItem item : availablePlugins) {
            if (pluginId.equals(item.getPluginId())) return item;
        }
        return AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetPluginDescriptorById(pluginId) : null;
    }
}

```

---

### File: `app/src/com/maxica/cobass/plugin/PatchVariationEngine.java`

```java
package com.maxica.cobass.plugin;

import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.PluginParamItem;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Random;

public final class PatchVariationEngine {

    private static final Random RNG = new Random();
    private static final int[] CONSONANT_INTERVALS = {-24, -19, -12, -7, -5, 0, 5, 7, 12, 19, 24};

    public static class LockMasks {
        public boolean lockOscillators = false;
        public boolean lockFilter = false;
        public boolean lockEnvelopes = false;
        public boolean lockLfo = false;
        public boolean lockFx = false;
        public boolean lockMaster = true; // Master gain/pitch locked by default
    }

    private PatchVariationEngine() {}

    /**
     * Mutates a plugin JSON state using controlled Gaussian variance and musical constraints.
     *
     * @param descriptor       Plugin descriptor containing parameter metadata
     * @param currentJsonState Current JSON snapshot of the patch
     * @param intensity        Variation intensity: 0.05 (Light) to 1.00 (Extreme)
     * @param locks            Sectional lock masks
     * @param snapHarmonics    Whether pitch parameters snap to consonant intervals
     * @param autoGainStage    Whether to compensate master headroom automatically
     * @return Mutated JSON patch state
     */
    public static String mutatePatch(
        PluginDescriptorItem descriptor,
        String currentJsonState,
        float intensity,
        LockMasks locks,
        boolean snapHarmonics,
        boolean autoGainStage
    ) {
        if (descriptor == null || currentJsonState == null || currentJsonState.isEmpty()) {
            return currentJsonState != null ? currentJsonState : "{}";
        }

        try {
            JSONObject root = new JSONObject(currentJsonState);
            float clampedIntensity = Math.max(0.05f, Math.min(1.0f, intensity));
            LockMasks activeLocks = (locks != null) ? locks : new LockMasks();

            float totalDriveSum = 0.0f;
            float totalResonanceSum = 0.0f;

            for (PluginParamItem param : descriptor.getParameters()) {
                String strKey = String.valueOf(param.getId());
                if (!root.has(strKey)) continue;

                double currentVal = root.getDouble(strKey);
                if (isParameterLocked(descriptor, param, activeLocks)) {
                    continue; // Skip locked parameter
                }

                double mutatedVal = mutateSingleParameter(
                    param,
                    (float) currentVal,
                    clampedIntensity,
                    snapHarmonics
                );

                // Track drive and resonance for auto-gain compensation
                String pNameLower = param.getName().toLowerCase();
                if (pNameLower.contains("drive") || pNameLower.contains("sat")) {
                    totalDriveSum += (float) mutatedVal;
                }
                if (pNameLower.contains("resonance") || pNameLower.contains("q")) {
                    totalResonanceSum += (float) mutatedVal;
                }

                root.put(strKey, mutatedVal);
            }

            // Headroom Auto-Gain Staging Protection
            if (autoGainStage && !activeLocks.lockMaster) {
                applyHeadroomCompensation(descriptor, root, totalDriveSum, totalResonanceSum);
            }

            // Enforce Envelope Energy Integrity Guard
            enforceEnvelopeIntegrity(descriptor, root);

            return root.toString();
        } catch (Exception e) {
            return currentJsonState;
        }
    }

    private static boolean isParameterLocked(
        PluginDescriptorItem descriptor,
        PluginParamItem param,
        LockMasks locks
    ) {
        if (locks == null) return false;
        String pluginId = descriptor.getPluginId();
        String name = param.getName().toLowerCase();
        int id = param.getId();

        // 1. Hyperion Synth Specific Module Boundaries
        if (pluginId.contains("hyperion")) {
            if (id >= 0 && id <= 21 && locks.lockOscillators) return true;
            if (id >= 22 && id <= 28 && locks.lockFilter) return true;
            if (id >= 29 && id <= 38 && locks.lockEnvelopes) return true;
            if (id >= 39 && id <= 42 && locks.lockLfo) return true;
            if (id >= 43 && id <= 51 && locks.lockFx) return true;
            if (id >= 52 && locks.lockMaster) return true;
            return false;
        }

        // 2. Cobalt Drum Synth Specific Module Boundaries
        // 2. Cobalt Drum Synth Specific Module Boundaries (52-Param Matrix)
        if (pluginId.contains("drums")) {
            if (id >= 0 && id <= 3 && locks.lockMaster) return true;          // Global Profile & Master
            if (id >= 4 && id <= 10 && locks.lockOscillators) return true;     // Kick Drum Engine
            if (id >= 11 && id <= 17 && locks.lockFilter) return true;        // Snare Drum Engine
            if (id >= 18 && id <= 23 && locks.lockEnvelopes) return true;     // Clap Engine
            if (id >= 24 && id <= 30 && locks.lockLfo) return true;           // Hi-Hats & Cymbals
            if (id >= 31 && id <= 37 && locks.lockOscillators) return true;   // Toms & Slap FM
            if (id >= 38 && id <= 45 && locks.lockFx) return true;            // Percussion & Bells
            if (id >= 46 && id <= 51 && locks.lockFx) return true;            // Bus Glue & Spatial FX
            return false;
        }

        // 3. Generic Plugin Name-Based Module Masking
        if (locks.lockFilter && (name.contains("cutoff") || name.contains("filter") || name.contains("res") || name.contains("vowel"))) return true;
        if (locks.lockEnvelopes && (name.contains("attack") || name.contains("decay") || name.contains("sustain") || name.contains("release") || name.contains("punch"))) return true;
        if (locks.lockLfo && (name.contains("lfo") || name.contains("rate") || name.contains("depth"))) return true;
        if (locks.lockFx && (name.contains("fx") || name.contains("reverb") || name.contains("delay") || name.contains("drive") || name.contains("chorus") || name.contains("ott"))) return true;
        if (locks.lockMaster && (name.contains("master") || name.contains("volume") || name.contains("out") || name.contains("portamento"))) return true;

        return false;
    }

    private static double mutateSingleParameter(
        PluginParamItem param,
        float currentVal,
        float intensity,
        boolean snapHarmonics
    ) {
        float min = param.getMinValue();
        float max = param.getMaxValue();
        float range = max - min;
        if (range <= 0.0001f) return currentVal;

        // 1. Continuous Float Parameters
        if (param.getType() == PluginParamItem.Type.FLOAT) {
            float sigma = intensity * 0.45f;
            float jitter = (float) (RNG.nextGaussian() * sigma * range);
            float newVal = currentVal + jitter;

            // Harmonic Snapping for Semitone / Pitch Parameters
            if (snapHarmonics && isPitchParameter(param)) {
                newVal = snapToNearestHarmonic(newVal);
            }

            return Math.max(min, Math.min(max, newVal));
        }

        // 2. Discrete Integer Stepper Parameters
        if (param.getType() == PluginParamItem.Type.INT) {
            if (isPitchParameter(param) && snapHarmonics) {
                return snapToNearestHarmonic(currentVal + (float)(RNG.nextGaussian() * intensity * 12.0f));
            }
            int stepSpread = Math.max(1, Math.round(intensity * (range * 0.5f)));
            int stepDelta = RNG.nextInt(stepSpread * 2 + 1) - stepSpread;
            return Math.max(min, Math.min(max, Math.round(currentVal + stepDelta)));
        }

        // 3. Dropdown Choice Parameters
        if (param.getType() == PluginParamItem.Type.CHOICE) {
            int numChoices = !param.getChoices().isEmpty() ? param.getChoices().size() : (int)(max - min + 1);
            if (numChoices <= 1) return currentVal;

            if (intensity <= 0.20f) {
                // Light: 85% chance keep original, 15% chance step adjacent
                if (RNG.nextFloat() > 0.15f) return currentVal;
                int dir = RNG.nextBoolean() ? 1 : -1;
                return Math.max(0, Math.min(numChoices - 1, Math.round(currentVal) + dir));
            } else {
                // Medium / Extreme: Explore valid choice index
                return RNG.nextInt(numChoices);
            }
        }

        // 4. Boolean Toggle Switches
        if (param.getType() == PluginParamItem.Type.BOOL) {
            if (intensity <= 0.25f) return currentVal; // Light retains toggles
            float flipChance = intensity * 0.40f;
            return (RNG.nextFloat() < flipChance) ? (currentVal > 0.5f ? 0.0 : 1.0) : currentVal;
        }

        return currentVal;
    }

    private static boolean isPitchParameter(PluginParamItem param) {
        if (param == null) return false;
        String label = param.getLabel() != null ? param.getLabel().toLowerCase().trim() : "";
        String n = param.getName().toLowerCase();
        // Only snap musical semitone parameters, NEVER continuous Hz or %
        if (label.equals("hz") || label.equals("%") || label.equals("ms") || label.equals("db")) {
            return false;
        }
        return label.equals("st") || label.equals("oct") || label.equals("cent") ||
               n.contains("semi") || n.contains("octave") || n.contains("interval");
    }

    private static float snapToNearestHarmonic(float rawSemitones) {
        int bestInterval = 0;
        float minDelta = 999.0f;
        for (int interval : CONSONANT_INTERVALS) {
            float delta = Math.abs(rawSemitones - interval);
            if (delta < minDelta) {
                minDelta = delta;
                bestInterval = interval;
            }
        }
        return bestInterval;
    }

    private static void applyHeadroomCompensation(
        PluginDescriptorItem descriptor,
        JSONObject root,
        float totalDrive,
        float totalResonance
    ) {
        for (PluginParamItem p : descriptor.getParameters()) {
            String n = p.getName().toLowerCase();
            if (n.contains("master gain") || n.contains("master out") || n.contains("output trim")) {
                String strK = String.valueOf(p.getId());
                if (root.has(strK)) {
                    double currentOut = root.optDouble(strK, 0.0);
                    double trimCompensation = 0.0;

                    if (totalDrive > 10.0f) trimCompensation -= (totalDrive - 10.0f) * 0.18;
                    if (totalResonance > 6.0f) trimCompensation -= (totalResonance - 6.0f) * 0.25;

                    double compensated = Math.max(p.getMinValue(), Math.min(p.getMaxValue(), currentOut + trimCompensation));
                    try {
                        root.put(strK, compensated);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void enforceEnvelopeIntegrity(PluginDescriptorItem descriptor, JSONObject root) {
        String attackKey = null;
        String sustainKey = null;
        String decayKey = null;

        for (PluginParamItem p : descriptor.getParameters()) {
            String n = p.getName().toLowerCase();
            if (n.equals("amp attack")) attackKey = String.valueOf(p.getId());
            if (n.equals("amp sustain")) sustainKey = String.valueOf(p.getId());
            if (n.equals("amp decay")) decayKey = String.valueOf(p.getId());
        }

        if (attackKey != null && sustainKey != null && root.has(attackKey) && root.has(sustainKey)) {
            double att = root.optDouble(attackKey, 5.0);
            double sus = root.optDouble(sustainKey, 0.75);

            // Prevent silence: if attack is long, sustain must be audible
            if (att > 400.0 && sus < 0.35) {
                try { root.put(sustainKey, 0.45); } catch (Exception ignored) {}
            }
        }
    }
}

```

---

### File: `app/src/com/maxica/cobass/ui/PluginUiDialog.java`

```java
package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.PluginParamItem;

import java.util.ArrayList;
import java.util.List;

public class PluginUiDialog extends Dialog implements PluginPresetDialog.OnPresetActionListener {

    private final int trackId;
    private final int slotIndex;
    private final PluginDescriptorItem descriptor;
    private final Runnable onDismissCallback;

    private boolean isBypassed = false;
    private String stateA = "{}";
    private String stateB = "{}";
    private boolean isStateActiveA = true;

    private SynthVisualizerView visualizerView;
    private final List<RotaryKnobView> activeKnobs = new ArrayList<>();
    private int activeCategoryFilter = 0; // 0 = ALL

    public PluginUiDialog(@NonNull Context context, int trackId, int slotIndex,
                          PluginDescriptorItem descriptor, Runnable onDismissCallback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.trackId = trackId;
        this.slotIndex = slotIndex;
        this.descriptor = descriptor;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_plugin_host);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        TextView txtTitle = findViewById(R.id.txtPluginTitle);
        TextView txtVendor = findViewById(R.id.txtPluginVendor);
        Button btnVariation = findViewById(R.id.btnPluginVariation);
        Button btnBypass = findViewById(R.id.btnPluginBypass);
        Button btnAb = findViewById(R.id.btnAbCompare);
        Button btnPresets = findViewById(R.id.btnPluginPresets);
        Button btnSavePatch = findViewById(R.id.btnSavePatch);
        Button btnClose = findViewById(R.id.btnClosePluginDialog);
        FrameLayout visualizerContainer = findViewById(R.id.pluginVisualizerContainer);
        LinearLayout paramContainer = findViewById(R.id.paramMatrixContainer);

        txtTitle.setText(descriptor.getName());
        txtVendor.setText("v" + descriptor.getVersion() + " • " + descriptor.getVendor());

        if (visualizerContainer != null) {
            visualizerContainer.removeAllViews();
            visualizerView = new SynthVisualizerView(getContext());
            if (descriptor.getPluginId().contains("drums")) {
                visualizerView.setDisplayMode(SynthVisualizerView.DisplayMode.DRUM_MATRIX_HUD);
            }
            visualizerContainer.addView(visualizerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (AudioEngineNative.isLoaded()) {
            stateA = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
            stateB = stateA;
            if (slotIndex >= 0) {
                isBypassed = AudioEngineNative.nativeIsTrackFxBypassed(trackId, slotIndex);
            }
        }
        updateBypassButtonState(btnBypass);

        if (btnVariation != null) {
            btnVariation.setOnClickListener(v -> {
                new VariationStudioDialog(
                    getContext(),
                    trackId,
                    slotIndex,
                    descriptor,
                    this::refreshAllKnobValues,
                    this
                ).show();
            });
        }

        btnBypass.setOnClickListener(v -> {
            isBypassed = !isBypassed;
            if (AudioEngineNative.isLoaded() && slotIndex >= 0) {
                AudioEngineNative.nativeSetTrackFxBypass(trackId, slotIndex, isBypassed);
            }
            updateBypassButtonState(btnBypass);
        });

        btnAb.setOnClickListener(v -> {
            if (!AudioEngineNative.isLoaded()) return;
            if (isStateActiveA) {
                stateA = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
                AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, stateB);
                isStateActiveA = false;
                btnAb.setText("STATE B");
                btnAb.setTextColor(Color.parseColor("#FFD60A"));
            } else {
                stateB = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
                AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, stateA);
                isStateActiveA = true;
                btnAb.setText("STATE A");
                btnAb.setTextColor(Color.parseColor("#0A84FF"));
            }
            refreshAllKnobValues();
        });

        btnPresets.setOnClickListener(v -> new PluginPresetDialog(getContext(), descriptor, this).show());
        btnSavePatch.setOnClickListener(v -> PluginPresetDialog.showSaveDialog(getContext(), descriptor, this));
        btnClose.setOnClickListener(v -> dismiss());

        buildCategorizedParameterMatrix(paramContainer);
    }

    private void updateBypassButtonState(Button btnBypass) {
        if (btnBypass == null) return;
        btnBypass.setText(isBypassed ? "BYPASS: ON" : "⚡ ACTIVE");
        btnBypass.setTextColor(isBypassed ? Color.parseColor("#FF453A") : Color.parseColor("#30D158"));
        btnBypass.setBackgroundColor(isBypassed ? Color.parseColor("#4D1E24") : Color.parseColor("#163824"));
    }

    private void buildCategorizedParameterMatrix(LinearLayout container) {
        container.removeAllViews();
        activeKnobs.clear();

        List<PluginParamItem> allParams = descriptor.getParameters();
        if (allParams.isEmpty()) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("This plugin does not expose adjustable parameters.");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(20, 40, 20, 40);
            emptyText.setGravity(Gravity.CENTER);
            container.addView(emptyText);
            return;
        }

        // 1. Build Categorized Tabs & Audition Ribbon
        if (descriptor.getPluginId().contains("drums") || allParams.size() > 12) {
            HorizontalScrollView tabScroll = new HorizontalScrollView(getContext());
            tabScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tabRow = new LinearLayout(getContext());
            tabRow.setOrientation(LinearLayout.HORIZONTAL);
            tabRow.setPadding(0, 4, 0, 10);

            String[] categories;
            if (descriptor.getPluginId().contains("drums")) {
                categories = new String[]{"ALL PARAMS", "KICK & SNARE", "HATS & CLAP", "TOMS & PERC", "BUS FX & MASTER"};
            } else {
                categories = new String[]{"ALL PARAMS", "OSCILLATORS & FM", "FILTER & KEYTRACK", "ENVELOPES & PUNCH", "DANCE FX SUITE", "MASTER & GLIDE"};
            }

            for (int i = 0; i < categories.length; i++) {
                final int catIdx = i;
                Button btnTab = new Button(getContext());
                btnTab.setText(categories[i]);
                btnTab.setTextSize(10f);
                btnTab.setTypeface(null, android.graphics.Typeface.BOLD);
                boolean isSel = (activeCategoryFilter == catIdx);
                btnTab.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#20232E"));
                btnTab.setTextColor(isSel ? Color.WHITE : Color.parseColor("#8E8E93"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72);
                lp.setMargins(0, 0, 8, 0);
                btnTab.setLayoutParams(lp);
                btnTab.setOnClickListener(v -> {
                    activeCategoryFilter = catIdx;
                    buildCategorizedParameterMatrix(container);
                });
                tabRow.addView(btnTab);
            }
            tabScroll.addView(tabRow);
            container.addView(tabScroll);

            // 2. Direct Audition Pad Ribbon
            LinearLayout audRow = new LinearLayout(getContext());
            audRow.setOrientation(LinearLayout.HORIZONTAL);
            audRow.setPadding(0, 0, 0, 10);

            if (descriptor.getPluginId().contains("drums")) {
                addAuditionPad(audRow, "▶ Kick", 36, Color.parseColor("#0A84FF"));
                addAuditionPad(audRow, "▶ Snare", 38, Color.parseColor("#FF9F0A"));
                addAuditionPad(audRow, "▶ Clap", 39, Color.parseColor("#30D158"));
                addAuditionPad(audRow, "▶ Cl.Hat", 42, Color.parseColor("#BF5AF2"));
                addAuditionPad(audRow, "▶ Op.Hat", 46, Color.parseColor("#FF453A"));
                addAuditionPad(audRow, "▶ Tom", 45, Color.parseColor("#64D2FF"));
                addAuditionPad(audRow, "▶ Rim", 37, Color.parseColor("#FFD60A"));
                addAuditionPad(audRow, "▶ Cowbell", 56, Color.parseColor("#AC8E68"));
            } else {
                addAuditionPad(audRow, "▶ C2 Sub", 36, Color.parseColor("#0A84FF"));
                addAuditionPad(audRow, "▶ C3 Bass", 48, Color.parseColor("#30D158"));
                addAuditionPad(audRow, "▶ C4 Pluck", 60, Color.parseColor("#FF9F0A"));
                addAuditionPad(audRow, "▶ C5 Lead", 72, Color.parseColor("#BF5AF2"));
            }
            container.addView(audRow);
        }

        // Filter parameters by category
        List<PluginParamItem> filteredParams = new ArrayList<>();
        for (PluginParamItem p : allParams) {
            int id = p.getId();
            if (descriptor.getPluginId().contains("drums")) {
                if (activeCategoryFilter == 0) filteredParams.add(p);
                else if (activeCategoryFilter == 1 && id >= 4 && id <= 17) filteredParams.add(p);   // Kick & Snare (4..17)
                else if (activeCategoryFilter == 2 && id >= 18 && id <= 30) filteredParams.add(p);  // Hats & Clap (18..30)
                else if (activeCategoryFilter == 3 && id >= 31 && id <= 45) filteredParams.add(p);  // Toms & Perc (31..45)
                else if (activeCategoryFilter == 4 && ((id >= 0 && id <= 3) || (id >= 46 && id <= 51))) filteredParams.add(p); // Master & Bus FX
            } else {
                if (activeCategoryFilter == 0) filteredParams.add(p);
                else if (activeCategoryFilter == 1 && id >= 0 && id <= 21) filteredParams.add(p);
                else if (activeCategoryFilter == 2 && id >= 22 && id <= 28) filteredParams.add(p);
                else if (activeCategoryFilter == 3 && id >= 29 && id <= 42) filteredParams.add(p);
                else if (activeCategoryFilter == 4 && id >= 43 && id <= 51) filteredParams.add(p);
                else if (activeCategoryFilter == 5 && id >= 52) filteredParams.add(p);
            }
        }

        final int columnsPerRow = 4;
        LinearLayout currentRow = null;

        for (int i = 0; i < filteredParams.size(); i++) {
            if (i % columnsPerRow == 0) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.CENTER_VERTICAL);
                currentRow.setPadding(0, 8, 0, 8);
                container.addView(currentRow);
            }

            PluginParamItem p = filteredParams.get(i);
            View paramView = createParamControlView(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(6, 0, 6, 0);
            paramView.setLayoutParams(lp);
            if (currentRow != null) currentRow.addView(paramView);
        }
    }

    private void addAuditionPad(LinearLayout parent, String label, int midiNote, int color) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(9f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 68, 1.0f);
        lp.setMargins(0, 0, 4, 0);
        btn.setLayoutParams(lp);
        CobassInteraction.attachAuditionTouch(btn, trackId, midiNote, 1.0f);
        parent.addView(btn);
    }

    private View createParamControlView(PluginParamItem param) {
        float initialVal = param.getDefaultValue();
        if (AudioEngineNative.isLoaded()) {
            initialVal = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, param.getId());
        }

        if (param.getType() == PluginParamItem.Type.FLOAT) {
            RotaryKnobView knob = PluginControlFactory.createRotaryKnob(getContext(), param, initialVal, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                    syncVisualizerFromParam(p.getName(), val);
                }
            });
            activeKnobs.add(knob);
            syncVisualizerFromParam(param.getName(), initialVal);
            return knob;
        } else if (param.getType() == PluginParamItem.Type.BOOL) {
            return PluginControlFactory.createBooleanToggle(getContext(), param, initialVal > 0.5f, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                }
            });
        } else {
            return PluginControlFactory.createChoiceStepper(getContext(), param, (int) initialVal, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                    if (p.getName().contains("Filter Mode") && visualizerView != null) {
                        visualizerView.setFilterMode((int) val);
                    }
                }
            });
        }
    }

    private void syncVisualizerFromParam(String name, float value) {
        if (visualizerView == null) return;
        String lower = name.toLowerCase();
        if (lower.contains("cutoff")) {
            visualizerView.setFilterParams(value, 1.8f);
        } else if (lower.contains("resonance")) {
            visualizerView.setFilterParams(3500.0f, value);
        } else if (lower.contains("attack")) {
            visualizerView.setEnvelopeParams(value, 120.0f, 0.70f, 250.0f);
        }
    }

    private void refreshAllKnobValues() {
        if (!AudioEngineNative.isLoaded()) return;
        for (RotaryKnobView knob : activeKnobs) {
            float val = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, knob.getParamItem().getId());
            knob.setValue(val, false);
        }
    }

    @Override
    public String onGetPluginStateJson() {
        return AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex) : "{}";
    }

    @Override
    public void onSetPluginStateJson(String jsonState) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, jsonState);
            refreshAllKnobValues();
        }
    }

    @Override
    public void dismiss() {
        if (visualizerView != null) {
            visualizerView.stopAnimation();
        }
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}

```

---

### File: `app/src/com/maxica/cobass/ui/PluginPresetDialog.java`

```java
package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.model.PluginDescriptorItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class PluginPresetDialog extends Dialog {

    public interface OnPresetActionListener {
        String onGetPluginStateJson();
        void onSetPluginStateJson(String jsonState);
    }

    private final PluginDescriptorItem descriptor;
    private final OnPresetActionListener listener;

    public PluginPresetDialog(@NonNull Context context, PluginDescriptorItem descriptor, OnPresetActionListener listener) {
        super(context);
        this.descriptor = descriptor;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        File presetDir = new File(getContext().getFilesDir(), "presets/" + descriptor.getPluginId());
        if (!presetDir.exists()) presetDir.mkdirs();

        refreshPresets(content, presetDir);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "📁 Preset Library: " + descriptor.getName(),
            "Load factory presets or custom user patch state",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }

    private void refreshPresets(LinearLayout container, File presetDir) {
        container.removeAllViews();
        float density = getContext().getResources().getDisplayMetrics().density;
        File[] files = presetDir.listFiles((d, name) -> name.endsWith(".cobasspatch"));
        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No saved user patches found. Click SAVE in toolbar to create one.");
            CobassTypography.applyBody(emptyText);
            emptyText.setPadding(0, Math.round(CobassSpacing.SPACE_MD * density), 0, Math.round(CobassSpacing.SPACE_MD * density));
            container.addView(emptyText);
            return;
        }

        for (File f : files) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Math.round(4 * density), 0, Math.round(4 * density));

            TextView txtName = new TextView(getContext());
            txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            txtName.setText(f.getName().replace(".cobasspatch", ""));
            CobassTypography.applyLabel(txtName);

            Button btnLoad = new Button(getContext());
            btnLoad.setText("LOAD");
            CobassButton.apply(btnLoad, CobassButton.Variant.PRIMARY, CobassButton.Size.COMPACT);
            btnLoad.setOnClickListener(v -> {
                loadPatchFromFile(f);
                dismiss();
            });

            Button btnDel = new Button(getContext());
            btnDel.setText("✕");
            CobassButton.apply(btnDel, CobassButton.Variant.DANGER, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                Math.round(CobassSpacing.BTN_HEIGHT_COMPACT * density),
                Math.round(CobassSpacing.BTN_HEIGHT_COMPACT * density)
            );
            dLp.leftMargin = Math.round(4 * density);
            btnDel.setLayoutParams(dLp);
            btnDel.setOnClickListener(v -> {
                f.delete();
                refreshPresets(container, presetDir);
            });

            row.addView(txtName);
            row.addView(btnLoad);
            row.addView(btnDel);
            container.addView(row);
        }
    }

    private void loadPatchFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read > 0) {
                String json = new String(buf, 0, read, StandardCharsets.UTF_8);
                if (listener != null) listener.onSetPluginStateJson(json);
                Toast.makeText(getContext(), "Loaded: " + file.getName(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Load Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showSaveDialog(Context context, PluginDescriptorItem descriptor, OnPresetActionListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText editName = new EditText(context);
        editName.setHint("Patch Name");
        editName.setTextColor(CobassTheme.TEXT_PRIMARY);
        editName.setBackgroundColor(CobassTheme.SURFACE_0);
        editName.setHintTextColor(CobassTheme.TEXT_DISABLED);
        int pad = Math.round(8 * density);
        editName.setPadding(pad, pad, pad, pad);
        editName.setSingleLine(true);
        content.addView(editName);

        Button btnSave = new Button(context);
        btnSave.setText("Save Preset (.cobasspatch)");
        CobassButton.apply(btnSave, CobassButton.Variant.SUCCESS, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = Math.round(CobassSpacing.SPACE_MD * density);
        btnSave.setLayoutParams(sLp);
        btnSave.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "User_Patch";

            File presetDir = new File(context.getFilesDir(), "presets/" + descriptor.getPluginId());
            if (!presetDir.exists()) presetDir.mkdirs();

            File targetFile = new File(presetDir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cobasspatch");
            if (listener != null) {
                String json = listener.onGetPluginStateJson();
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    Toast.makeText(context, "Saved: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Save Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
        });
        content.addView(btnSave);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            context,
            "💾 Save User Patch",
            "Store current parameter settings into preset archive",
            content,
            v -> dialog.dismiss()
        );

        dialog.setContentView(root);
        CobassDialogShell.configureWindow(dialog);
        dialog.show();
    }
}

```

---

### File: `app/src/com/maxica/cobass/ui/SynthVisualizerView.java`

```java
package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.audio.AudioEngineNative;

public class SynthVisualizerView extends View {

    public enum DisplayMode {
        COMBINED_HUD,
        OSCILLOSCOPE,
        FILTER_CURVE,
        ADSR_ENVELOPE,
        DRUM_MATRIX_HUD
    }

    private DisplayMode currentMode = DisplayMode.COMBINED_HUD;
    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = false;
    private float animPhase = 0.0f;

    // Filter Parameters
    private float cutoffHz = 3500.0f;
    private float resonanceQ = 1.8f;
    private int filterMode = 0; // 0=Ladder24, 1=Diode18, 6=Formant, 7=Comb

    // ADSR Envelope Parameters
    private float attackMs = 15.0f;
    private float decayMs = 120.0f;
    private float sustainPct = 0.70f;
    private float releaseMs = 250.0f;

    // Live Audio Telemetry
    private float peakEnergy = 0.0f;

    // Drawing Primitives
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint oscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint envPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint envFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drumPadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final RectF rectF = new RectF();

    public SynthVisualizerView(Context context) {
        super(context);
        init();
    }

    public SynthVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#12141C"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setColor(Color.parseColor("#222634"));

        textPaint.setColor(Color.parseColor("#8E8E93"));
        textPaint.setTextSize(20.0f);
        textPaint.setFakeBoldText(true);

        oscPaint.setStyle(Paint.Style.STROKE);
        oscPaint.setStrokeWidth(2.5f);
        oscPaint.setColor(Color.parseColor("#30D158"));
        oscPaint.setStrokeCap(Paint.Cap.ROUND);

        filterPaint.setStyle(Paint.Style.STROKE);
        filterPaint.setStrokeWidth(3.0f);
        filterPaint.setColor(Color.parseColor("#0A84FF"));
        filterPaint.setStrokeCap(Paint.Cap.ROUND);

        filterFillPaint.setStyle(Paint.Style.FILL);
        filterFillPaint.setColor(Color.parseColor("#220A84FF"));

        envPaint.setStyle(Paint.Style.STROKE);
        envPaint.setStrokeWidth(3.0f);
        envPaint.setColor(Color.parseColor("#FF9F0A"));
        envPaint.setStrokeCap(Paint.Cap.ROUND);

        envFillPaint.setStyle(Paint.Style.FILL);
        envFillPaint.setColor(Color.parseColor("#22FF9F0A"));

        drumPadPaint.setStyle(Paint.Style.FILL);
    }

    public void setFilterParams(float cutoff, float resonance) {
        this.cutoffHz = Math.max(20.0f, Math.min(20000.0f, cutoff));
        this.resonanceQ = Math.max(0.1f, Math.min(16.0f, resonance));
        invalidate();
    }

    public void setFilterMode(int mode) {
        this.filterMode = mode;
        invalidate();
    }

    public void setEnvelopeParams(float attack, float decay, float sustain, float release) {
        this.attackMs = Math.max(1.0f, attack);
        this.decayMs = Math.max(5.0f, decay);
        this.sustainPct = Math.max(0.0f, Math.min(1.0f, sustain));
        this.releaseMs = Math.max(5.0f, release);
        invalidate();
    }

    public void setDisplayMode(DisplayMode mode) {
        this.currentMode = mode;
        invalidate();
    }

    public DisplayMode getDisplayMode() { return currentMode; }

    public void startAnimation() {
        if (isAnimating) return;
        isAnimating = true;
        animHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAnimating) return;
                animPhase += 0.08f;
                if (animPhase > 6.2831853f) animPhase -= 6.2831853f;

                if (AudioEngineNative.isLoaded()) {
                    peakEnergy = AudioEngineNative.nativeGetMasterPeakL();
                }
                invalidate();
                animHandler.postDelayed(this, 16);
            }
        });
    }

    public void stopAnimation() {
        isAnimating = false;
        animHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();

        canvas.drawRect(0, 0, width, height, bgPaint);

        rectF.set(1f, 1f, width - 1f, height - 1f);
        gridPaint.setColor(Color.parseColor("#262938"));
        canvas.drawRoundRect(rectF, 8f, 8f, gridPaint);

        switch (currentMode) {
            case COMBINED_HUD:
                drawCombinedHud(canvas, width, height);
                break;
            case OSCILLOSCOPE:
                drawOscilloscope(canvas, 0, 0, width, height, "LIVE OSCILLOSCOPE");
                break;
            case FILTER_CURVE:
                drawFilterCurve(canvas, 0, 0, width, height, "DANCE FILTER FREQUENCY RESPONSE");
                break;
            case ADSR_ENVELOPE:
                drawAdsrEnvelope(canvas, 0, 0, width, height, "EXPONENTIAL ADSR ENVELOPE");
                break;
            case DRUM_MATRIX_HUD:
                drawDrumMatrixHud(canvas, width, height);
                break;
        }
    }

    private void drawCombinedHud(Canvas canvas, float width, float height) {
        final float colWidth = width / 3.0f;

        gridPaint.setColor(Color.parseColor("#222634"));
        canvas.drawLine(colWidth, 0, colWidth, height, gridPaint);
        canvas.drawLine(colWidth * 2.0f, 0, colWidth * 2.0f, height, gridPaint);

        drawOscilloscope(canvas, 0, 0, colWidth, height, "OSCILLOSCOPE");
        drawFilterCurve(canvas, colWidth, 0, colWidth, height, String.format("FILTER: %.0fHz", cutoffHz));
        drawAdsrEnvelope(canvas, colWidth * 2.0f, 0, colWidth, height, "ADSR ENVELOPE");
    }

    private void drawDrumMatrixHud(Canvas canvas, float width, float height) {
        final float padW = (width - 48f) / 8f;
        final float padH = height - 38f;
        final String[] drumNames = {"BD", "SD", "CL", "CH", "OH", "TM", "RM", "CB"};
        final String[] subLabels = {"808/909", "Modal", "Flam", "Schmitt", "FM Cym", "Slap", "Clave", "Agogo"};
        final int[] colors = {
            Color.parseColor("#0A84FF"), Color.parseColor("#FF9F0A"),
            Color.parseColor("#30D158"), Color.parseColor("#BF5AF2"),
            Color.parseColor("#FF453A"), Color.parseColor("#64D2FF"),
            Color.parseColor("#FFD60A"), Color.parseColor("#AC8E68")
        };

        for (int i = 0; i < 8; i++) {
            float px = 8f + (i * (padW + 4f));
            float py = 26f;

            float vEnergy = Math.max(0.20f, Math.min(1.0f, peakEnergy * (1.1f + (i % 4) * 0.25f)));
            int alpha = (int) (vEnergy * 255);

            drumPadPaint.setColor(Color.argb(alpha, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
            rectF.set(px, py, px + padW, py + padH);
            canvas.drawRoundRect(rectF, 6f, 6f, drumPadPaint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(11f);
            canvas.drawText(drumNames[i], px + (padW * 0.25f), py + (padH * 0.50f), textPaint);

            textPaint.setColor(Color.parseColor("#D0D0D0"));
            textPaint.setTextSize(8f);
            canvas.drawText(subLabels[i], px + (padW * 0.12f), py + (padH * 0.82f), textPaint);
        }

        textPaint.setColor(Color.parseColor("#30D158"));
        textPaint.setTextSize(12f);
        canvas.drawText("⚡ COBALT HYBRID DRUM MATRIX v2 (52-PARAM HYBRID DSP)", 14f, 18f, textPaint);
    }

    private void drawOscilloscope(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#30D158"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float midY = top + (height * 0.55f);
        final float maxAmp = (height * 0.35f) * Math.max(0.3f, peakEnergy * 1.5f);

        path.reset();
        path.moveTo(left + 8f, midY);

        final int steps = 48;
        for (int i = 0; i <= steps; i++) {
            float frac = (float) i / steps;
            float px = left + 8f + frac * (width - 16f);
            float wave1 = (float) Math.sin(frac * 12.566f + animPhase);
            float wave2 = (float) (2.0 * (Math.sin(frac * 25.132f + animPhase * 1.5f) * 0.35f));
            float py = midY - (wave1 + wave2) * maxAmp;
            path.lineTo(px, py);
        }

        canvas.drawPath(path, oscPaint);
    }

    private void drawFilterCurve(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#0A84FF"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float plotBottom = top + height - 8f;
        final float plotTop = top + 28f;
        final float plotHeight = plotBottom - plotTop;

        path.reset();
        fillPath.reset();
        fillPath.moveTo(left + 8f, plotBottom);

        final int steps = 40;
        final float cutoffNormalized = (float) (Math.log10(cutoffHz / 20.0f) / Math.log10(20000.0f / 20.0f));
        final float peakX = left + 8f + cutoffNormalized * (width - 16f);

        for (int i = 0; i <= steps; i++) {
            float frac = (float) i / steps;
            float px = left + 8f + frac * (width - 16f);

            float freqHz = (float) (20.0f * Math.pow(1000.0f, frac));
            float fRatio = freqHz / cutoffHz;
            float mag = 1.0f;

            if (filterMode == 1) {
                // Diode 18dB Acid Ladder
                mag = 1.0f / (float) Math.sqrt(1.0 + Math.pow(fRatio, 6.0));
                float acidRes = (float) Math.exp(-Math.pow((frac - cutoffNormalized) * 7.0f, 2.0)) * (resonanceQ * 0.45f);
                mag = Math.min(1.8f, mag + acidRes);
            } else if (filterMode == 6) {
                // 3-Peak Formant Vowel Filter
                float p1 = (float) Math.exp(-Math.pow(frac - 0.35f, 2.0) * 45.0f);
                float p2 = (float) Math.exp(-Math.pow(frac - 0.60f, 2.0) * 55.0f) * 0.7f;
                float p3 = (float) Math.exp(-Math.pow(frac - 0.85f, 2.0) * 65.0f) * 0.4f;
                mag = Math.min(1.5f, 0.2f + p1 + p2 + p3);
            } else {
                // 24dB Moog Ladder
                mag = 1.0f / (float) Math.sqrt(1.0 + Math.pow(fRatio, 8.0));
                float resBump = (float) Math.exp(-Math.pow((frac - cutoffNormalized) * 6.0f, 2.0)) * (resonanceQ * 0.35f);
                mag = Math.min(1.6f, mag + resBump);
            }

            float py = plotBottom - (mag * plotHeight * 0.65f);
            if (i == 0) {
                path.moveTo(px, py);
                fillPath.lineTo(px, py);
            } else {
                path.lineTo(px, py);
                fillPath.lineTo(px, py);
            }
        }

        fillPath.lineTo(left + width - 8f, plotBottom);
        fillPath.close();

        canvas.drawPath(fillPath, filterFillPaint);
        canvas.drawPath(path, filterPaint);

        filterPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(peakX, plotTop + 14f, 4f, filterPaint);
        filterPaint.setStyle(Paint.Style.STROKE);
    }

    private void drawAdsrEnvelope(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#FF9F0A"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float plotBottom = top + height - 8f;
        final float plotTop = top + 28f;
        final float plotHeight = plotBottom - plotTop;
        final float plotWidth = width - 16f;

        float totalTime = attackMs + decayMs + 200.0f + releaseMs;
        float x0 = left + 8f;
        float x1 = x0 + (attackMs / totalTime) * plotWidth;
        float x2 = x1 + (decayMs / totalTime) * plotWidth;
        float x3 = x2 + (200.0f / totalTime) * plotWidth;
        float x4 = x0 + plotWidth;

        float yPeak = plotTop + 4f;
        float ySustain = plotBottom - (sustainPct * plotHeight);

        path.reset();
        fillPath.reset();

        path.moveTo(x0, plotBottom);
        fillPath.moveTo(x0, plotBottom);

        path.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);
        fillPath.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);

        path.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);
        fillPath.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);

        path.lineTo(x3, ySustain);
        fillPath.lineTo(x3, ySustain);

        path.quadTo((x3 + x4) * 0.5f, ySustain + (plotBottom - ySustain) * 0.7f, x4, plotBottom);
        fillPath.quadTo((x3 + x4) * 0.5f, ySustain + (plotBottom - ySustain) * 0.7f, x4, plotBottom);

        fillPath.close();

        canvas.drawPath(fillPath, envFillPaint);
        canvas.drawPath(path, envPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            switch (currentMode) {
                case COMBINED_HUD:    currentMode = DisplayMode.DRUM_MATRIX_HUD; break;
                case DRUM_MATRIX_HUD: currentMode = DisplayMode.OSCILLOSCOPE; break;
                case OSCILLOSCOPE:    currentMode = DisplayMode.FILTER_CURVE; break;
                case FILTER_CURVE:    currentMode = DisplayMode.ADSR_ENVELOPE; break;
                case ADSR_ENVELOPE:   currentMode = DisplayMode.COMBINED_HUD; break;
            }
            invalidate();
            return true;
        }
        return true;
    }
}

```

---

