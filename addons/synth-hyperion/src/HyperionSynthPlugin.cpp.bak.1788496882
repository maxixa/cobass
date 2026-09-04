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
    {0, "Osc1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 11.0f, 1.0f, 1.0f, false,
        {"Sine", "Saw", "Pulse", "Triangle", "Noise", "Hypersaw", "Future Donk", "Vowel", "Metallic FM", "Dirty Reese", "Hard Sync", "Screamer"}, 12},
    {1, "Osc1 Octave", "oct", COBASS_PARAM_TYPE_INT, -3.0f, 3.0f, 0.0f, 1.0f, false, {}, 0},
    {2, "Osc1 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 0.0f, 1.0f, false, {}, 0},
    {3, "Osc1 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 0.0f, 1.0f, false, {}, 0},
    {4, "Osc1 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},
    {5, "Osc1 Unison", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 2.0f, 1.0f, false, {"1 Voice", "2 Voices", "4 Voices", "8 Voices"}, 4},
    {6, "Osc1 Detune", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {7, "Osc1 Spread", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},

    // --- OSCILLATOR 2 [8..16] ---
    {8, "Osc2 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 11.0f, 1.0f, 1.0f, false,
        {"Sine", "Saw", "Pulse", "Triangle", "Noise", "Hypersaw", "Future Donk", "Vowel", "Metallic FM", "Dirty Reese", "Hard Sync", "Screamer"}, 12},
    {9, "Osc2 Octave", "oct", COBASS_PARAM_TYPE_INT, -3.0f, 3.0f, 0.0f, 1.0f, false, {}, 0},
    {10, "Osc2 Semi", "st", COBASS_PARAM_TYPE_INT, -12.0f, 12.0f, 7.0f, 1.0f, false, {}, 0},
    {11, "Osc2 Fine", "cent", COBASS_PARAM_TYPE_FLOAT, -50.0f, 50.0f, 5.0f, 1.0f, false, {}, 0},
    {12, "Osc2 PW", "%", COBASS_PARAM_TYPE_FLOAT, 0.05f, 0.95f, 0.50f, 0.01f, false, {}, 0},
    {13, "Osc2 Sync", "", COBASS_PARAM_TYPE_BOOL, 0.0f, 1.0f, 0.0f, 1.0f, false, {}, 0},
    {14, "Osc2 Unison", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 3.0f, 2.0f, 1.0f, false, {"1 Voice", "2 Voices", "4 Voices", "8 Voices"}, 4},
    {15, "Osc2 Detune", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},
    {16, "Osc2 Spread", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},

    // --- MIXER, SUB & CROSS-FM [17..21] ---
    {17, "Osc1 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.80f, 0.01f, false, {}, 0},
    {18, "Osc2 Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.70f, 0.01f, false, {}, 0},
    {19, "Sub Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.35f, 0.01f, false, {}, 0},
    {20, "Noise Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},
    {21, "Cross FM", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.0f, 0.01f, false, {}, 0},

    // --- EXPANDED DANCE FILTER SUITE [22..28] ---
    {22, "Filter Mode", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 7.0f, 0.0f, 1.0f, false,
        {"Ladder 24", "Diode 18 Acid", "SVF LP12", "SVF BP12", "SVF HP12", "Notch 12", "Formant Vowel", "Comb Resonator"}, 8},
    {23, "Cutoff", "Hz", COBASS_PARAM_TYPE_FLOAT, 20.0f, 20000.0f, 4500.0f, 1.0f, true, {}, 0},
    {24, "Resonance", "Q", COBASS_PARAM_TYPE_FLOAT, 0.5f, 16.0f, 1.8f, 0.05f, false, {}, 0},
    {25, "Filter Drive", "x", COBASS_PARAM_TYPE_FLOAT, 0.5f, 5.0f, 1.2f, 0.05f, false, {}, 0},
    {26, "Filter Env", "%", COBASS_PARAM_TYPE_FLOAT, -1.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},
    {27, "Vowel Morph", "", COBASS_PARAM_TYPE_FLOAT, 0.0f, 4.0f, 0.0f, 0.05f, false, {}, 0},
    {28, "Key Tracking", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.50f, 0.01f, false, {}, 0},

    // --- AMP ENVELOPE [29..32] ---
    {29, "Amp Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 5.0f, 1.0f, false, {}, 0},
    {30, "Amp Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 140.0f, 1.0f, false, {}, 0},
    {31, "Amp Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.75f, 0.01f, false, {}, 0},
    {32, "Amp Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 250.0f, 1.0f, false, {}, 0},

    // --- MOD ENVELOPE & ATTACK PUNCH [33..38] ---
    {33, "Mod Attack", "ms", COBASS_PARAM_TYPE_FLOAT, 1.0f, 2000.0f, 5.0f, 1.0f, false, {}, 0},
    {34, "Mod Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 3000.0f, 180.0f, 1.0f, false, {}, 0},
    {35, "Mod Sustain", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.20f, 0.01f, false, {}, 0},
    {36, "Mod Release", "ms", COBASS_PARAM_TYPE_FLOAT, 5.0f, 4000.0f, 200.0f, 1.0f, false, {}, 0},
    {37, "Punch Drop", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 36.0f, 0.0f, 1.0f, false, {}, 0},
    {38, "Punch Decay", "ms", COBASS_PARAM_TYPE_FLOAT, 2.0f, 60.0f, 15.0f, 1.0f, false, {}, 0},

    // --- LFO 1 [39..42] ---
    {39, "LFO1 Wave", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 1.0f, 1.0f, false, {"Sine", "Triangle", "Sawtooth", "Square", "S&H"}, 5},
    {40, "LFO1 Rate", "Hz", COBASS_PARAM_TYPE_FLOAT, 0.05f, 30.0f, 2.0f, 0.01f, false, {}, 0},
    {41, "LFO1 Cutoff", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {42, "LFO1 Pitch", "st", COBASS_PARAM_TYPE_FLOAT, 0.0f, 2.0f, 0.0f, 0.01f, false, {}, 0},

    // --- INTERNAL DANCE FX RACK [43..51] ---
    {43, "FX Drive", "dB", COBASS_PARAM_TYPE_FLOAT, 0.0f, 24.0f, 0.0f, 0.1f, false, {}, 0},
    {44, "FX Dimension", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.40f, 0.01f, false, {}, 0},
    {45, "FX Delay Time", "", COBASS_PARAM_TYPE_CHOICE, 0.0f, 4.0f, 2.0f, 1.0f, false, {"1/4 Beat", "1/8 Beat", "1/8 Dotted", "1/16 Beat", "1/8 Triplet"}, 5},
    {46, "FX Delay FB", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 0.90f, 0.35f, 0.01f, false, {}, 0},
    {47, "FX Delay Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {48, "FX Reverb Size", "%", COBASS_PARAM_TYPE_FLOAT, 0.10f, 0.98f, 0.65f, 0.01f, false, {}, 0},
    {49, "FX Reverb Mix", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.25f, 0.01f, false, {}, 0},
    {50, "FX OTT Comp", "%", COBASS_PARAM_TYPE_FLOAT, 0.0f, 1.0f, 0.30f, 0.01f, false, {}, 0},
    {51, "FX Output Trim", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0},

    // --- MASTER CONTROLS [52..53] ---
    {52, "Portamento", "ms", COBASS_PARAM_TYPE_FLOAT, 0.0f, 500.0f, 0.0f, 1.0f, false, {}, 0},
    {53, "Master Gain", "dB", COBASS_PARAM_TYPE_FLOAT, -24.0f, 6.0f, 0.0f, 0.1f, false, {}, 0}
};

static const CobassPluginManifest HYPERION_MANIFEST = {
    COBASS_PLUGIN_API_VERSION,
    "com.maxica.cobass.plugins.hyperion",
    "Hyperion Dance Synth v3",
    "Maxica Audio",
    "3.0.0",
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
        delayWriteIdx_ = 0;
        haasWriteIdx_ = 0;
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
            const int haasDelaySamples = static_cast<int>(0.012f * sampleRate_); // 12ms delay
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

            sL = sL * (1.0f - delayMix) + dL * delayMix;
            sR = sR * (1.0f - delayMix) + dR * delayMix;
        }

        // --- STAGE 4: LUSH DANCE REVERB ---
        if (verbMix > 0.005f) {
            float inMono = (sL + sR) * 0.5f * 0.025f;
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

            // Allpass diffusers
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

            sL = sL * (1.0f - verbMix) + combSumL * verbMix;
            sR = sR * (1.0f - verbMix) + combSumR * verbMix;
        }

        // --- STAGE 5: OTT MASTER PUNCH LIMITER ---
        if (ottComp > 0.01f) {
            float pkL = std::abs(sL);
            float pkR = std::abs(sR);
            ottEnvL_ = 0.992f * ottEnvL_ + 0.008f * pkL;
            ottEnvR_ = 0.992f * ottEnvR_ + 0.008f * pkR;

            float avgEnv = std::max(1e-4f, (ottEnvL_ + ottEnvR_) * 0.5f);
            float grDb = 0.0f;
            float envDb = 20.0f * std::log10(avgEnv);

            if (envDb > -12.0f) {
                grDb = (-12.0f - envDb) * 0.65f; // Downward compression
            } else if (envDb < -28.0f) {
                grDb = (-28.0f - envDb) * 0.40f; // Upward expansion
            }

            float gainLinear = std::pow(10.0f, (grDb * ottComp) / 20.0f);
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

        // BUG-4 FIX: Filter parameter caching to eliminate per-sample recalculations
        float lastCutoff = -1.0f;
        float lastRes = -1.0f;
        float lastDrive = -1.0f;
        float lastVowel = -1.0f;
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

            // ISSUE-3 FIX: Normalized unison phase spread by active voice count
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
            lastCutoff = -1.0f; // Invalidate filter parameter cache
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
        fxRack_.reset(sampleRate_);
    }

    void reset(float sampleRate) {
        sampleRate_ = std::max(8000.0f, sampleRate);
        lfo1_.setSampleRate(sampleRate_);
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
        // 1. First pick an idle voice
        for (auto& v : voices_) {
            if (!v.active) {
                target = &v;
                break;
            }
        }
        // 2. ISSUE-2 FIX: Prefer stealing voice in release phase with lowest energy
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
        // 3. ISSUE-2 FIX: If all voices are active/sustaining, steal oldest triggered voice
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
        target->trigger(note, velocity, sampleRate_, params_[52], params_[38], u1, u2);
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

        // --- EXTRACT SYNTH PARAMETERS ---
        const auto osc1Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[0]) % 12);
        const float osc1PitchMult = std::pow(2.0f, (params_[1] * 12.0f + params_[2] + params_[3] * 0.01f) / 12.0f);
        const float osc1Pw = params_[4];
        static constexpr int UNISON_COUNTS[4] = {1, 2, 4, 8};
        const int osc1UnisonN = UNISON_COUNTS[std::min(3, static_cast<int>(params_[5]))];
        const float osc1Detune = params_[6] * 0.025f;
        const float osc1Spread = params_[7];

        const auto osc2Wave = static_cast<OscillatorWaveform>(static_cast<int>(params_[8]) % 12);
        const float osc2PitchMult = std::pow(2.0f, (params_[9] * 12.0f + params_[10] + params_[11] * 0.01f) / 12.0f);
        const float osc2Pw = params_[12];
        const bool osc2Sync = (params_[13] > 0.5f);
        const int osc2UnisonN = UNISON_COUNTS[std::min(3, static_cast<int>(params_[14]))];
        const float osc2Detune = params_[15] * 0.025f;
        const float osc2Spread = params_[16];

        const float osc1Vol = params_[17];
        const float osc2Vol = params_[18];
        const float subVol  = params_[19];
        const float noiseVol = params_[20];
        const float crossFm  = params_[21];

        const auto filterMode = static_cast<ZdfFilterMode>(static_cast<int>(params_[22]) % 8);
        const float baseCutoff = params_[23];
        const float resonance  = params_[24];
        const float drive      = params_[25];
        const float filterEnvAmt = params_[26];
        const float vowelMorph   = params_[27];
        const float keytrackPct  = params_[28];

        const float ampA = params_[29] * 0.001f, ampD = params_[30] * 0.001f, ampS = params_[31], ampR = params_[32] * 0.001f;
        const float modA = params_[33] * 0.001f, modD = params_[34] * 0.001f, modS = params_[35], modR = params_[36] * 0.001f;
        const float punchDropSt = params_[37];

        lfo1_.setWaveform(static_cast<LfoWaveform>(static_cast<int>(params_[39]) % 5));
        lfo1_.setFrequency(params_[40]);
        const float lfoCutoffDepth = params_[41];
        const float lfoPitchDepth  = params_[42];

        // --- INTERNAL FX PARAMETERS [43..51] ---
        const float fxDriveDb    = params_[43];
        const float fxDimWidth   = params_[44];
        const int fxDelayDiv     = static_cast<int>(params_[45]);
        const float fxDelayFb    = params_[46];
        const float fxDelayMix   = params_[47];
        const float fxVerbSize   = params_[48];
        const float fxVerbMix    = params_[49];
        const float fxOttComp    = params_[50];
        const float fxOutTrimDb  = params_[51];

        const float masterGain = std::pow(10.0f, params_[53] / 20.0f) * 0.20f;

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
            }
        }

        for (uint32_t i = 0; i < numFrames; ++i) {
            const float lfoVal = lfo1_.getNextSample();
            const float lfoPitchMod = lfoVal * lfoPitchDepth;
            const float lfoCutoffMod = std::max(0.05f, 1.0f + lfoVal * lfoCutoffDepth * 0.8f);

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
                    float s = v.osc1Stack[u].renderSample();

                    float panL = (osc1UnisonN > 1) ? (0.5f - detuneOffset * (osc1Spread * 0.5f)) : 0.5f;
                    float panR = (osc1UnisonN > 1) ? (0.5f + detuneOffset * (osc1Spread * 0.5f)) : 0.5f;
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

                    float s = v.osc2Stack[u].renderSample();
                    float panL = (osc2UnisonN > 1) ? (0.5f - detuneOffset * (osc2Spread * 0.5f)) : 0.5f;
                    float panR = (osc2UnisonN > 1) ? (0.5f + detuneOffset * (osc2Spread * 0.5f)) : 0.5f;
                    osc2L += s * panL;
                    osc2R += s * panR;
                }
                const float norm2 = 1.0f / std::sqrt(static_cast<float>(osc2UnisonN));
                osc2L *= norm2; osc2R *= norm2;

                // Sub Oscillator & Noise
                v.oscSub.setFrequency(voicePitch * 0.5f);
                float sSub = v.oscSub.renderSample() * subVol;
                float sNoise = v.oscNoise.renderSample() * noiseVol;

                float rawL = (osc1L * osc1Vol) + (osc2L * osc2Vol) + sSub * 0.5f + sNoise * 0.5f;
                float rawR = (osc1R * osc1Vol) + (osc2R * osc2Vol) + sSub * 0.5f + sNoise * 0.5f;

                // ZDF Filter
                float keytrackMultiplier = std::pow(2.0f, (v.note - 60) * (keytrackPct / 12.0f));
                float envCutoffDelta = baseCutoff * filterEnvAmt * modEnvVal * 4.0f;
                float finalCutoff = std::clamp((baseCutoff * keytrackMultiplier + envCutoffDelta) * lfoCutoffMod, 20.0f, sampleRate_ * 0.48f);

                // BUG-4 FIX: Only update filter coefficients when parameters change significantly
                if (std::abs(finalCutoff - v.lastCutoff) > 1.0f || filterMode != v.lastMode ||
                    std::abs(resonance - v.lastRes) > 0.01f || std::abs(drive - v.lastDrive) > 0.01f ||
                    std::abs(vowelMorph - v.lastVowel) > 0.01f) {
                    v.filterL.setParameters(filterMode, finalCutoff, resonance, drive, vowelMorph);
                    v.filterR.setParameters(filterMode, finalCutoff, resonance, drive, vowelMorph);
                    v.lastCutoff = finalCutoff;
                    v.lastMode = filterMode;
                    v.lastRes = resonance;
                    v.lastDrive = drive;
                    v.lastVowel = vowelMorph;
                }

                float vL = v.filterL.process(rawL) * amp * v.velocity;
                float vR = v.filterR.process(rawR) * amp * v.velocity;

                rawSumL += vL;
                rawSumR += vR;
            }

            // --- INTERNAL 5-STAGE DANCE STUDIO FX RACK ---
            float fxOutL = 0.0f, fxOutR = 0.0f;
            fxRack_.process(rawSumL, rawSumR,
                            fxDriveDb, fxDimWidth, fxDelayDiv, fxDelayFb, fxDelayMix,
                            fxVerbSize, fxVerbMix, fxOttComp, fxOutTrimDb,
                            fxOutL, fxOutR);

            outL[i] = fxOutL * masterGain;
            outR[i] = fxOutR * masterGain;
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
                params_[i] = std::strtof(pos, nullptr);
            }
        }
        return true;
    }

private:
    float sampleRate_ = 48000.0f;
    std::array<float, 54> params_{};
    std::array<Voice, 16> voices_{};
    LFO lfo1_;
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
