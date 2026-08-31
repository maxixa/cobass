#!/usr/bin/env bash
# ==============================================================================
# Cobass Step Sequencer - Phase 1 Surgical Patch Script
# Targets:
#   1. Model: TrackItem.java (Type.STEP_SEQUENCER)
#   2. Model: StepPatternItem.java (Lanes, Steps, Ratchets, Probability)
#   3. Native DSP: Track.hpp (TrackType::StepSequencer)
#   4. Native DSP: StepSequencerTrack.hpp (16-Lane Multi-Pad Sampler & Real-Time Engine)
#   5. Native DSP: Mixer.hpp (addStepSequencerTrack)
#   6. Native Sequencer: Sequencer.hpp (advancePlayback for StepSequencer tracks)
#   7. Native Core: AudioEngine.hpp & AudioEngine.cpp (Step Sequencer Management API)
#   8. JNI: AudioEngineNative.java & jni_bridge.cpp (JNI Bridge Bindings)
# ==============================================================================
set -euo pipefail

echo "======================================================================"
echo "   APPLYING PHASE 1: STEP SEQUENCER DATA MODEL & NATIVE DSP ENGINE    "
echo "======================================================================"

python3 - << 'EOF'
import sys
from pathlib import Path

def patch_file(filepath: Path, find_str: str, replace_str: str, label: str):
    if not filepath.exists():
        print(f"\033[91m[-] Missing file: {filepath}\033[0m")
        sys.exit(1)
    content = filepath.read_text(encoding="utf-8")
    if find_str not in content:
        if replace_str in content:
            print(f"\033[93m[*] Already patched: {label}\033[0m")
            return
        print(f"\033[91m[-] Target string not found for: {label}\033[0m")
        sys.exit(1)
    new_content = content.replace(find_str, replace_str, 1)
    filepath.write_text(new_content, encoding="utf-8")
    print(f"\033[92m[✓] Patched: {label}\033[0m")

# ----------------------------------------------------------------------
# 1. TrackItem.java -> Add STEP_SEQUENCER to Type enum
# ----------------------------------------------------------------------
track_item_path = Path("app/src/com/maxica/cobass/model/TrackItem.java")
patch_file(
    track_item_path,
    "public enum Type { SYNTH, AUDIO }",
    "public enum Type { SYNTH, AUDIO, STEP_SEQUENCER }",
    "TrackItem.java enum Type"
)

# ----------------------------------------------------------------------
# 2. Create StepPatternItem.java
# ----------------------------------------------------------------------
step_pattern_path = Path("app/src/com/maxica/cobass/model/StepPatternItem.java")
step_pattern_content = '''package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.List;

public class StepPatternItem {

    public static class Step {
        public boolean active = false;
        public float velocity = 0.85f;
        public int pitchOffset = 0;       // Semitone shift (-24 to +24)
        public float gate = 0.75f;        // Step gate duration (0.05 to 2.0)
        public float nudge = 0.0f;        // Micro-timing offset (-0.5 to +0.5)
        public int ratchets = 1;          // 1=Single, 2=Double, 3=Triplet, 4=Quad, 8=Roll
        public float probability = 1.0f;  // 0.0 to 1.0

        public Step copy() {
            Step s = new Step();
            s.active = this.active;
            s.velocity = this.velocity;
            s.pitchOffset = this.pitchOffset;
            s.gate = this.gate;
            s.nudge = this.nudge;
            s.ratchets = this.ratchets;
            s.probability = this.probability;
            return s;
        }
    }

    public static class Lane {
        public int id;
        public String name = "Lane";
        public int midiNote = 60;         // Default note or sampler pad index (36=Kick, 38=Snare, 42=CHH)
        public int stepCount = 16;        // Polymeter length (1 to 64)
        public SnapGrid subdivision = SnapGrid.BEAT_1_4; // Step resolution (default 1/16)
        public float volume = 0.8f;
        public float pan = 0.0f;
        public boolean isMuted = false;
        public boolean isSolo = false;
        public float[] sampleData = null; // One-shot PCM for sampler drum lanes
        public final List<Step> steps = new ArrayList<>();

        public Lane(int id, String name, int midiNote, int stepCount) {
            this.id = id;
            this.name = name;
            this.midiNote = midiNote;
            this.stepCount = stepCount;
            for (int i = 0; i < 64; i++) {
                steps.add(new Step());
            }
        }

        public Lane copy() {
            Lane l = new Lane(id, name, midiNote, stepCount);
            l.subdivision = subdivision;
            l.volume = volume;
            l.pan = pan;
            l.isMuted = isMuted;
            l.isSolo = isSolo;
            if (sampleData != null) l.sampleData = sampleData.clone();
            l.steps.clear();
            for (Step s : steps) l.steps.add(s.copy());
            return l;
        }
    }

    private int id;
    private String name = "Pattern 1";
    private int baseLength = 16;
    private final List<Lane> lanes = new ArrayList<>();

    public StepPatternItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public List<Lane> getLanes() { return lanes; }
    public int getBaseLength() { return baseLength; }
    public void setBaseLength(int len) { this.baseLength = Math.max(1, Math.min(64, len)); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getId() { return id; }

    public StepPatternItem copy() {
        StepPatternItem clone = new StepPatternItem(id, name);
        clone.baseLength = this.baseLength;
        for (Lane l : this.lanes) clone.lanes.add(l.copy());
        return clone;
    }
}
'''
step_pattern_path.write_text(step_pattern_content, encoding="utf-8")
print(f"\033[92m[✓] Created: {step_pattern_path}\033[0m")

# ----------------------------------------------------------------------
# 3. Track.hpp -> Add StepSequencer to TrackType enum
# ----------------------------------------------------------------------
track_hpp_path = Path("app/native/dsp/Track.hpp")
patch_file(
    track_hpp_path,
    "enum class TrackType : int32_t { Synth = 0, Audio = 1, Bus = 2 };",
    "enum class TrackType : int32_t { Synth = 0, Audio = 1, Bus = 2, StepSequencer = 3 };",
    "Track.hpp TrackType enum"
)

# ----------------------------------------------------------------------
# 4. Create StepSequencerTrack.hpp
# ----------------------------------------------------------------------
step_seq_track_path = Path("app/native/dsp/StepSequencerTrack.hpp")
step_seq_track_content = '''#pragma once
#include "Track.hpp"
#include <array>
#include <vector>
#include <random>
#include <cmath>
#include <algorithm>

struct NativeStep {
    bool active = false;
    float velocity = 0.85f;
    int32_t pitchOffset = 0;
    float gate = 0.75f;
    float nudge = 0.0f;
    int32_t ratchets = 1;
    float probability = 1.0f;
};

struct NativeLane {
    int32_t id = 0;
    int32_t midiNote = 60;
    int32_t stepCount = 16;
    int32_t stepTicks = 120; // 1/16th note at PPQ=480
    float volume = 0.8f;
    float pan = 0.0f;
    bool isMuted = false;
    bool isSolo = false;

    // Sampler Pad Buffer
    std::vector<float> sampleData;
    int32_t channels = 1;
    double playbackPos = 0.0;
    bool isPlayingSample = false;
    float currentPitch = 1.0f;
    float currentVelocity = 1.0f;

    std::array<NativeStep, 64> steps{};
};

class StepSequencerTrack : public Track {
public:
    static constexpr size_t MAX_LANES = 16;

    StepSequencerTrack(int32_t id, const std::string& name)
        : Track(id, TrackType::StepSequencer, name), rng_(std::random_device{}()) {
        lanes_.resize(MAX_LANES);
        for (size_t i = 0; i < MAX_LANES; ++i) {
            lanes_[i].id = static_cast<int32_t>(i);
        }
        tempBuffer_.assign(8192, 0.0f);
    }

    void loadLaneSample(size_t laneIndex, const float* data, int32_t length, int32_t channels) {
        if (laneIndex >= MAX_LANES || !data || length <= 0) return;
        lanes_[laneIndex].sampleData.assign(data, data + length);
        lanes_[laneIndex].channels = std::clamp(channels, 1, 2);
        lanes_[laneIndex].playbackPos = 0.0;
        lanes_[laneIndex].isPlayingSample = false;
    }

    void setLaneStep(size_t laneIndex, size_t stepIndex, bool active, float velocity,
                     int32_t pitch, float gate, float nudge, int32_t ratchets, float prob) {
        if (laneIndex >= MAX_LANES || stepIndex >= 64) return;
        auto& s = lanes_[laneIndex].steps[stepIndex];
        s.active = active;
        s.velocity = std::clamp(velocity, 0.0f, 1.0f);
        s.pitchOffset = std::clamp(pitch, -24, 24);
        s.gate = std::clamp(gate, 0.05f, 2.0f);
        s.nudge = std::clamp(nudge, -0.5f, 0.5f);
        s.ratchets = std::clamp(ratchets, 1, 8);
        s.probability = std::clamp(prob, 0.0f, 1.0f);
    }

    void setLaneParams(size_t laneIndex, int32_t midiNote, int32_t stepCount, int32_t stepTicks,
                       float volume, float pan, bool mute, bool solo) {
        if (laneIndex >= MAX_LANES) return;
        auto& l = lanes_[laneIndex];
        l.midiNote = midiNote;
        l.stepCount = std::clamp(stepCount, 1, 64);
        l.stepTicks = std::max(10, stepTicks);
        l.volume = std::clamp(volume, 0.0f, 2.0f);
        l.pan = std::clamp(pan, -1.0f, 1.0f);
        l.isMuted = mute;
        l.isSolo = solo;
    }

    void clearLaneSteps(size_t laneIndex) {
        if (laneIndex >= MAX_LANES) return;
        for (auto& s : lanes_[laneIndex].steps) {
            s.active = false;
        }
    }

    void advancePlayback(int64_t startTick, int64_t endTick, bool loopWrapped) {
        std::uniform_real_distribution<float> dist(0.0f, 1.0f);

        for (auto& lane : lanes_) {
            if (lane.stepCount <= 0 || lane.isMuted || lane.sampleData.empty()) continue;

            const int64_t laneLoopTicks = static_cast<int64_t>(lane.stepCount * lane.stepTicks);
            if (laneLoopTicks <= 0) continue;

            for (int32_t s = 0; s < lane.stepCount; ++s) {
                const auto& step = lane.steps[s];
                if (!step.active) continue;

                const int32_t ratchets = std::max(1, step.ratchets);
                const int64_t subStepDuration = lane.stepTicks / ratchets;

                for (int32_t r = 0; r < ratchets; ++r) {
                    const int64_t stepTickInPattern = (s * lane.stepTicks) + (r * subStepDuration) + static_cast<int64_t>(step.nudge * lane.stepTicks);

                    bool shouldTrigger = false;
                    if (!loopWrapped) {
                        int64_t patternStart = startTick % laneLoopTicks;
                        int64_t patternEnd = endTick % laneLoopTicks;
                        if (patternEnd > patternStart) {
                            shouldTrigger = (patternStart <= stepTickInPattern && patternEnd > stepTickInPattern);
                        } else {
                            shouldTrigger = (patternStart <= stepTickInPattern || patternEnd > stepTickInPattern);
                        }
                    } else {
                        shouldTrigger = true;
                    }

                    if (shouldTrigger) {
                        if (step.probability < 0.999f && dist(rng_) > step.probability) {
                            continue;
                        }
                        lane.playbackPos = 0.0;
                        lane.currentPitch = std::pow(2.0f, step.pitchOffset / 12.0f);
                        lane.currentVelocity = step.velocity;
                        lane.isPlayingSample = true;
                    }
                }
            }
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

        for (auto& lane : lanes_) {
            if (!lane.isPlayingSample || lane.sampleData.empty() || lane.isMuted) continue;

            const size_t totalFrames = lane.sampleData.size() / lane.channels;
            const float panL = lane.pan <= 0.0f ? 1.0f : (1.0f - lane.pan);
            const float panR = lane.pan >= 0.0f ? 1.0f : (1.0f + lane.pan);
            const float gainL = lane.volume * lane.currentVelocity * panL;
            const float gainR = lane.volume * lane.currentVelocity * panR;

            for (int32_t i = 0; i < numFrames; ++i) {
                if (lane.playbackPos >= totalFrames) {
                    lane.isPlayingSample = false;
                    break;
                }

                const size_t frameIdx = static_cast<size_t>(lane.playbackPos);
                float sL = 0.0f, sR = 0.0f;
                if (lane.channels == 1) {
                    sL = sR = lane.sampleData[frameIdx];
                } else {
                    sL = lane.sampleData[frameIdx * 2];
                    sR = lane.sampleData[frameIdx * 2 + 1];
                }

                tempBuffer_[i * 2]     += sL * gainL;
                tempBuffer_[i * 2 + 1] += sR * gainR;
                lane.playbackPos += lane.currentPitch;
            }
        }

        applyFxAndGain(tempBuffer_.data(), numFrames);

        for (int32_t i = 0; i < numFrames * 2; ++i) {
            outStereoBuffer[i] += tempBuffer_[i];
        }
    }

private:
    std::vector<NativeLane> lanes_;
    std::vector<float> tempBuffer_;
    std::mt19937 rng_;
};
'''
step_seq_track_path.write_text(step_seq_track_content, encoding="utf-8")
print(f"\033[92m[✓] Created: {step_seq_track_path}\033[0m")

# ----------------------------------------------------------------------
# 5. Mixer.hpp -> Add StepSequencerTrack inclusion and factory method
# ----------------------------------------------------------------------
mixer_hpp_path = Path("app/native/dsp/Mixer.hpp")
patch_file(
    mixer_hpp_path,
    '#include "AudioTrack.hpp"',
    '#include "AudioTrack.hpp"\n#include "StepSequencerTrack.hpp"',
    "Mixer.hpp header include"
)

add_step_seq_method = '''    int32_t addStepSequencerTrack(const std::string& name) {
        for (size_t i = 0; i < MAX_TRACKS; ++i) {
            Track* expected = nullptr;
            if (tracks_[i].load(std::memory_order_relaxed) == nullptr) {
                const int32_t id = nextTrackId_++;
                Track* track = new StepSequencerTrack(id, name);
                track->setSampleRate(sampleRate_);
                if (tracks_[i].compare_exchange_strong(expected, track, std::memory_order_release)) {
                    return id;
                }
                delete track;
            }
        }
        return -1;
    }
'''

patch_file(
    mixer_hpp_path,
    "    bool removeTrack(int32_t id) {",
    f"{add_step_seq_method}\n    bool removeTrack(int32_t id) {{",
    "Mixer.hpp addStepSequencerTrack method"
)

# ----------------------------------------------------------------------
# 6. Sequencer.hpp -> Advance StepSequencer tracks in processAudioBlock
# ----------------------------------------------------------------------
seq_hpp_path = Path("app/native/sequencer/Sequencer.hpp")
advance_step_seq_call = '''        // Advance Step Sequencer Tracks
        for (size_t t = 0; t < Mixer::MAX_TRACKS; ++t) {
            Track* track = mixer.getTrack(static_cast<int32_t>(t));
            if (track && track->getType() == TrackType::StepSequencer) {
                static_cast<StepSequencerTrack*>(track)->advancePlayback(startTick, endTick, loopWrapped);
            }
        }
'''
patch_file(
    seq_hpp_path,
    "        const bool loopWrapped = (endTick < startTick);",
    f"        const bool loopWrapped = (endTick < startTick);\n{advance_step_seq_call}",
    "Sequencer.hpp advancePlayback dispatch"
)

# ----------------------------------------------------------------------
# 7. AudioEngine.hpp -> Add Step Sequencer APIs
# ----------------------------------------------------------------------
engine_hpp_path = Path("app/native/AudioEngine.hpp")
engine_step_apis = '''    int32_t addStepSequencerTrack(const std::string& name);
    void setStepSequencerStep(int32_t trackId, int32_t laneIndex, int32_t stepIndex, bool active, float velocity, int32_t pitch, float gate, float nudge, int32_t ratchets, float prob);
    void loadStepSequencerSample(int32_t trackId, int32_t laneIndex, const float* data, int32_t length, int32_t channels);
    void clearStepSequencerLane(int32_t trackId, int32_t laneIndex);
    void setStepSequencerLaneParams(int32_t trackId, int32_t laneIndex, int32_t midiNote, int32_t stepCount, int32_t stepTicks, float volume, float pan, bool mute, bool solo);
'''
patch_file(
    engine_hpp_path,
    "    int32_t addAudioTrack(const std::string& name);",
    f"    int32_t addAudioTrack(const std::string& name);\n{engine_step_apis}",
    "AudioEngine.hpp Step Sequencer method declarations"
)

# ----------------------------------------------------------------------
# 8. AudioEngine.cpp -> Implement Step Sequencer APIs
# ----------------------------------------------------------------------
engine_cpp_path = Path("app/native/AudioEngine.cpp")
engine_step_impls = '''int32_t AudioEngine::addStepSequencerTrack(const std::string& name) {
    return mixer_.addStepSequencerTrack(name);
}

void AudioEngine::setStepSequencerStep(int32_t trackId, int32_t laneIndex, int32_t stepIndex, bool active, float velocity, int32_t pitch, float gate, float nudge, int32_t ratchets, float prob) {
    Track* t = mixer_.getTrack(trackId);
    if (t && t->getType() == TrackType::StepSequencer) {
        static_cast<StepSequencerTrack*>(t)->setLaneStep(laneIndex, stepIndex, active, velocity, pitch, gate, nudge, ratchets, prob);
    }
}

void AudioEngine::loadStepSequencerSample(int32_t trackId, int32_t laneIndex, const float* data, int32_t length, int32_t channels) {
    Track* t = mixer_.getTrack(trackId);
    if (t && t->getType() == TrackType::StepSequencer) {
        static_cast<StepSequencerTrack*>(t)->loadLaneSample(laneIndex, data, length, channels);
    }
}

void AudioEngine::clearStepSequencerLane(int32_t trackId, int32_t laneIndex) {
    Track* t = mixer_.getTrack(trackId);
    if (t && t->getType() == TrackType::StepSequencer) {
        static_cast<StepSequencerTrack*>(t)->clearLaneSteps(laneIndex);
    }
}

void AudioEngine::setStepSequencerLaneParams(int32_t trackId, int32_t laneIndex, int32_t midiNote, int32_t stepCount, int32_t stepTicks, float volume, float pan, bool mute, bool solo) {
    Track* t = mixer_.getTrack(trackId);
    if (t && t->getType() == TrackType::StepSequencer) {
        static_cast<StepSequencerTrack*>(t)->setLaneParams(laneIndex, midiNote, stepCount, stepTicks, volume, pan, mute, solo);
    }
}
'''
patch_file(
    engine_cpp_path,
    "int32_t AudioEngine::addAudioTrack(const std::string& name) {",
    f"{engine_step_impls}\nint32_t AudioEngine::addAudioTrack(const std::string& name) {{",
    "AudioEngine.cpp Step Sequencer method implementations"
)

# ----------------------------------------------------------------------
# 9. AudioEngineNative.java -> Add Step Sequencer JNI declarations
# ----------------------------------------------------------------------
native_java_path = Path("app/src/com/maxica/cobass/audio/AudioEngineNative.java")
java_native_declarations = '''    public static native int nativeAddStepSequencerTrack(String name);
    public static native void nativeSetStepSequencerStep(int trackId, int laneIndex, int stepIndex, boolean active, float velocity, int pitch, float gate, float nudge, int ratchets, float prob);
    public static native void nativeLoadStepSequencerSample(int trackId, int laneIndex, float[] data, int length, int channels);
    public static native void nativeClearStepSequencerLane(int trackId, int laneIndex);
    public static native void nativeSetStepSequencerLaneParams(int trackId, int laneIndex, int midiNote, int stepCount, int stepTicks, float volume, float pan, boolean mute, boolean solo);
'''
patch_file(
    native_java_path,
    "    public static native int nativeAddAudioTrack(String name);",
    f"    public static native int nativeAddAudioTrack(String name);\n{java_native_declarations}",
    "AudioEngineNative.java native Step Sequencer methods"
)

# ----------------------------------------------------------------------
# 10. jni_bridge.cpp -> Add Step Sequencer JNI wrappers
# ----------------------------------------------------------------------
jni_bridge_path = Path("app/native/jni_bridge.cpp")
jni_bridge_wrappers = '''JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddStepSequencerTrack(JNIEnv* env, jclass /*clazz*/, jstring name) {
    if (!gAudioEngine) return -1;
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    int32_t id = gAudioEngine->addStepSequencerTrack(nativeName ? nativeName : "Step Drum");
    if (nativeName) env->ReleaseStringUTFChars(name, nativeName);
    return id;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetStepSequencerStep(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex, jint stepIndex, jboolean active, jfloat velocity, jint pitch, jfloat gate, jfloat nudge, jint ratchets, jfloat prob) {
    if (gAudioEngine) gAudioEngine->setStepSequencerStep(trackId, laneIndex, stepIndex, active == JNI_TRUE, velocity, pitch, gate, nudge, ratchets, prob);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeLoadStepSequencerSample(JNIEnv* env, jclass /*clazz*/, jint trackId, jint laneIndex, jfloatArray data, jint length, jint channels) {
    if (!gAudioEngine || !data) return;
    jfloat* pcm = env->GetFloatArrayElements(data, nullptr);
    if (pcm) {
        gAudioEngine->loadStepSequencerSample(trackId, laneIndex, pcm, length, channels);
        env->ReleaseFloatArrayElements(data, pcm, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeClearStepSequencerLane(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex) {
    if (gAudioEngine) gAudioEngine->clearStepSequencerLane(trackId, laneIndex);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetStepSequencerLaneParams(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex, jint midiNote, jint stepCount, jint stepTicks, jfloat volume, jfloat pan, jboolean mute, jboolean solo) {
    if (gAudioEngine) gAudioEngine->setStepSequencerLaneParams(trackId, laneIndex, midiNote, stepCount, stepTicks, volume, pan, mute == JNI_TRUE, solo == JNI_TRUE);
}
'''
patch_file(
    jni_bridge_path,
    "JNIEXPORT jint JNICALL\nJava_com_maxica_cobass_audio_AudioEngineNative_nativeAddAudioTrack",
    f"{jni_bridge_wrappers}\nJNIEXPORT jint JNICALL\nJava_com_maxica_cobass_audio_AudioEngineNative_nativeAddAudioTrack",
    "jni_bridge.cpp JNI wrappers"
)

EOF

echo "======================================================================"
echo "======================================================================"