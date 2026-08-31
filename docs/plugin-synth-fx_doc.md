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