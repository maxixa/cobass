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
    // --- OSCILLATOR 1 [0..4] ---
    {0, "Osc1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Sawtooth", "Pulse", "Triangle", "Sine"}, 4},
    {1, "Osc1 Octave", "oct", COBASS_PARAM_TYPE_INT, -2.0f, 2.0f, 0.0f, 1.0f, false, {}, 0},
    {2, "Osc1 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 0.0f, 1.0f, false, {}, 0},
    {3, "Osc1 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 0.0f, 1.0f, false, {}, 0},
    {4, "Osc1 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},

    // --- OSCILLATOR 2 [5..10] ---
    {5, "Osc2 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 0.0f, 1.0f, false, {"Sawtooth", "Pulse", "Triangle", "Sine"}, 4},
    {6, "Osc2 Octave", "oct", COBASS_PARAM_TYPE_INT, -2.0f, 2.0f, 0.0f, 1.0f, false, {}, 0},
    {7, "Osc2 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 7.0f, 1.0f, false, {}, 0},
    {8, "Osc2 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 5.0f, 1.0f, false, {}, 0},
    {9, "Osc2 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},
    {10, "Osc2 Sync", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 0.0f, 1.0f, false, {}, 0},

    // --- MIXER & CROSS-MODULATION [11..15] ---
    {11, "Osc1 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},
    {12, "Osc2 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.65f, 0.01f, false, {}, 0},
    {13, "Sub Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},
    {14, "Noise Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {15, "Cross FM", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},

    // --- ZERO-DELAY FEEDBACK FILTER [16..20] ---
    {16, "Filter Mode", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 0.0f, 1.0f, false, {"Ladder 24", "SVF LP12", "SVF BP12", "SVF HP12", "Notch12"}, 5},
    {17, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 4500.0f, 1.0f, true, {}, 0},
    {18, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 14.0f, 1.5f, 0.05f, false, {}, 0},
    {19, "Drive", "x", COBASS_PARAM_TYPE_FLOAT, 0.5f, 4.0f, 1.0f, 0.05f, false, {}, 0},
    {20, "Filter Env", "%", COBASS_PARAM_TYPE_FLOAT, -1.0f, 1.0f, 0.60f, 0.01f, false, {}, 0},

    // --- AMP EXPONENTIAL ENVELOPE [21..24] ---
    {21, "Amp Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 10.0f, 1.0f, false, {}, 0},
    {22, "Amp Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 120.0f, 1.0f, false, {}, 0},
    {23, "Amp Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.70f, 0.01f, false, {}, 0},
    {24, "Amp Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 250.0f, 1.0f, false, {}, 0},

    // --- MODULATION ENVELOPE [25..28] ---
    {25, "Mod Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 5.0f, 1.0f, false, {}, 0},
    {26, "Mod Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 220.0f, 1.0f, false, {}, 0},
    {27, "Mod Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.20f, 0.01f, false, {}, 0},
    {28, "Mod Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 300.0f, 1.0f, false, {}, 0},

    // --- LFO 1 [29..32] ---
    {29, "LFO1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 1.0f, 1.0f, false, {"Sine", "Triangle", "Sawtooth", "Square", "S&H"}, 5},
    {30, "LFO1 Rate", "Hz", COBASS_PARAM_TYPE_FLOAT, 0.05f, 30.0f, 2.0f, 0.01f, false, {}, 0},
    {31, "LFO1 Cutoff", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {32, "LFO1 Pitch", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 2.0f, 0.0f, 0.01f, false, {}, 0},

    // --- UNISON & MASTER ARTICULATION [33..36] ---
    {33, "Unison Mode", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 1.0f, 1.0f, false, {"1 Voice", "3 Voices", "5 Voices", "7 Supersaw"}, 4},
    {34, "Unison Detune", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.20f, 0.01f, false, {}, 0},
    {35, "Portamento", "ms", COBASS_PARAM_TYPE_FLOAT, 0.0f, 500.0f, 0.0f, 1.0f, false, {}, 0},
    {36, "Master Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest HYPERION_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.hyperion",
    "Hyperion Wavetable Synth v2",
    "Maxica Audio",
    "2.0.0",
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
        float targetFreq = 440.0f;
        float currentFreq = 440.0f;
        float glideCoeff = 1.0f;
        bool active = false;

        // PolyBLEP Oscillators per Voice
        PolyBlepOscillator osc1;
        PolyBlepOscillator osc2;
        PolyBlepOscillator oscSub;
        PolyBlepOscillator oscNoise;

        // Unison Sub-phases (up to 7 unison spread)
        std::array<double, 7> unisonPhases{};

        // Envelopes & ZDF Filters
        ADSR ampEnv;
        ADSR modEnv;
        ZdfFilter filterL;
        ZdfFilter filterR;

        void init(float sampleRate) {
            osc1.setSampleRate(sampleRate);
            osc2.setSampleRate(sampleRate);
            oscSub.setSampleRate(sampleRate);
            oscNoise.setSampleRate(sampleRate);
            oscNoise.setWaveform(OscillatorWaveform::Noise);
            ampEnv.setSampleRate(sampleRate);
            modEnv.setSampleRate(sampleRate);
            filterL.setSampleRate(sampleRate);
            filterR.setSampleRate(sampleRate);
        }

        void trigger(int32_t midiNote, float vel, float sampleRate, float glideMs) {
            targetFreq = 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);

            if (active && glideMs > 0.001f) {
                glideCoeff = 1.0f - std::exp(-1.0f / (std::max(0.001f, glideMs * 0.00035f) * sampleRate));
            } else {
                currentFreq = targetFreq;
                glideCoeff = 1.0f;
            }

            note = midiNote;
            velocity = vel;
            active = true;

            for (size_t i = 0; i < unisonPhases.size(); ++i) {
                unisonPhases[i] = static_cast<double>(i) / static_cast<double>(unisonPhases.size());
            }

            ampEnv.gate(true);
            modEnv.gate(true);
            filterL.reset();
            filterR.reset();
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
        }

        float getEnergy() const noexcept {
            return active ? ampEnv.getCurrentValue() : 0.0f;
        }
    };

public:
    explicit HyperionProcessor(float sampleRate) : sampleRate_(sampleRate) {
        for (const auto& p : HYPERION_PARAMS) {
            params_[p.id] = p.defaultValue;
        }
        for (auto& v : voices_) v.init(sampleRate_);
        lfo1_.setSampleRate(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        lfo1_.setSampleRate(sampleRate_);
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
                float e = v.getEnergy();
                if (e < minEnergy) {
                    minEnergy = e;
                    target = &v;
                }
            }
        }
        if (!target) target = &voices_[0];
        target->trigger(note, velocity, sampleRate_, params_[35]);
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

        // Extract Global Parameters
        const auto osc1Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[0]) % 4);
        const float osc1PitchMult = std::pow(2.0f, (params_[1] * 12.0f + params_[2] + params_[3] * 0.01f) / 12.0f);
        const float osc1Pw = params_[4];

        const auto osc2Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[5]) % 4);
        const float osc2PitchMult = std::pow(2.0f, (params_[6] * 12.0f + params_[7] + params_[8] * 0.01f) / 12.0f);
        const float osc2Pw = params_[9];
        const bool osc2Sync = (params_[10] > 0.5f);

        const float osc1Vol = params_[11];
        const float osc2Vol = params_[12];
        const float subVol = params_[13];
        const float noiseVol = params_[14];
        const float crossFm = params_[15];

        const auto filterMode = static_cast<ZdfFilterMode>(static_cast<int>(params_[16]) % 5);
        const float baseCutoff = params_[17];
        const float resonance = params_[18];
        const float drive = params_[19];
        const float filterEnvAmt = params_[20];

        // Envelope Rates
        const float ampA = params_[21] * 0.001f, ampD = params_[22] * 0.001f, ampS = params_[23], ampR = params_[24] * 0.001f;
        const float modA = params_[25] * 0.001f, modD = params_[26] * 0.001f, modS = params_[27], modR = params_[28] * 0.001f;

        // LFO 1
        lfo1_.setWaveform(static_cast<LfoWaveform>(static_cast<int>(params_[29]) % 5));
        lfo1_.setFrequency(params_[30]);
        const float lfoCutoffDepth = params_[31];
        const float lfoPitchDepth = params_[32];

        // Unison Mode (1, 3, 5, 7 Voices)
        const int unisonCount = 1 + (static_cast<int>(params_[33]) % 4) * 2;
        const float unisonDetune = params_[34] * 0.018f;
        const float masterGain = std::pow(10.0f, params_[36] / 20.0f) * 0.22f;

        for (auto& v : voices_) {
            if (v.active) {
                v.ampEnv.setParameters(ampA, ampD, ampS, ampR);
                v.modEnv.setParameters(modA, modD, modS, modR);
                v.osc1.setWaveform(osc1Wave);
                v.osc1.setPulseWidth(osc1Pw);
                v.osc2.setWaveform(osc2Wave);
                v.osc2.setPulseWidth(osc2Pw);
                v.oscSub.setWaveform(OscillatorWaveform::Pulse);
                v.oscSub.setPulseWidth(0.5f);
            }
        }

        for (uint32_t i = 0; i < numFrames; ++i) {
            const float lfoVal = lfo1_.getNextSample();
            const float lfoPitchMod = lfoVal * lfoPitchDepth;
            const float lfoCutoffMod = std::max(0.05f, 1.0f + lfoVal * lfoCutoffDepth * 0.8f);

            float sumL = 0.0f;
            float sumR = 0.0f;

            for (auto& v : voices_) {
                if (!v.active) continue;

                const float amp = v.ampEnv.getNextSample();
                const float modEnvVal = v.modEnv.getNextSample();

                if (!v.ampEnv.isActive()) {
                    v.stop();
                    continue;
                }

                // Portamento Glide Slew
                if (v.currentFreq != v.targetFreq) {
                    v.currentFreq += (v.targetFreq - v.currentFreq) * v.glideCoeff;
                    if (std::abs(v.targetFreq - v.currentFreq) < 0.05f) {
                        v.currentFreq = v.targetFreq;
                    }
                }

                float voicePitch = v.currentFreq;
                if (std::abs(lfoPitchMod) > 0.001f) {
                    voicePitch *= std::pow(2.0f, lfoPitchMod / 12.0f);
                }

                // Sub Oscillator & Noise
                v.oscSub.setFrequency(voicePitch * 0.5f);
                float sSub = v.oscSub.renderSample() * subVol;
                float sNoise = v.oscNoise.renderSample() * noiseVol;

                // 7-Voice Supersaw Unison Generation
                float rawL = 0.0f;
                float rawR = 0.0f;

                for (int u = 0; u < unisonCount; ++u) {
                    float detuneOffset = (unisonCount > 1) ? (static_cast<float>(u - (unisonCount - 1) / 2.0f) / ((unisonCount - 1) / 2.0f)) : 0.0f;
                    float uPitch1 = voicePitch * osc1PitchMult * (1.0f + detuneOffset * unisonDetune);
                    float uPitch2 = voicePitch * osc2PitchMult * (1.0f - detuneOffset * unisonDetune);

                    v.osc1.setFrequency(uPitch1);
                    v.osc2.setFrequency(uPitch2);

                    if (osc2Sync) {
                        v.osc2.syncToMaster(v.osc1.getPhase());
                    }

                    float sOsc1 = v.osc1.renderSample();

                    // Cross-FM Modulates Osc2 Frequency by Osc1 Output
                    if (crossFm > 0.001f) {
                        v.osc2.setFrequency(uPitch2 * (1.0f + sOsc1 * crossFm * 2.0f));
                    }
                    float sOsc2 = v.osc2.renderSample();

                    float panL = (unisonCount > 1) ? (0.5f - detuneOffset * 0.45f) : 0.5f;
                    float panR = (unisonCount > 1) ? (0.5f + detuneOffset * 0.45f) : 0.5f;

                    float mixVoice = (sOsc1 * osc1Vol) + (sOsc2 * osc2Vol);
                    rawL += mixVoice * panL;
                    rawR += mixVoice * panR;
                }

                const float normFactor = 1.0f / std::sqrt(static_cast<float>(unisonCount));
                rawL = (rawL * normFactor) + sSub * 0.5f + sNoise * 0.5f;
                rawR = (rawR * normFactor) + sSub * 0.5f + sNoise * 0.5f;

                // Apply Dynamic Filter Modulation (ModEnv + LFO)
                float envCutoffDelta = baseCutoff * filterEnvAmt * modEnvVal * 4.0f;
                float finalCutoff = std::clamp((baseCutoff + envCutoffDelta) * lfoCutoffMod, 20.0f, sampleRate_ * 0.48f);

                v.filterL.setParameters(filterMode, finalCutoff, resonance, drive);
                v.filterR.setParameters(filterMode, finalCutoff, resonance, drive);

                float vL = v.filterL.process(rawL) * amp * v.velocity;
                float vR = v.filterR.process(rawR) * amp * v.velocity;

                sumL += vL;
                sumR += vR;
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
    std::array<float, 37> params_{};
    std::array<Voice, 16> voices_{};
    LFO lfo1_;
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
