#pragma once
#include <aaudio/AAudio.h>
#include <atomic>
#include <vector>
#include <memory>
#include <string>
#include "LockFreeQueue.hpp"
#include "dsp/Mixer.hpp"
#include "dsp/StepSequencerTrack.hpp"
#include "dsp/SynthTrack.hpp"
#include "dsp/AudioTrack.hpp"
#include "sequencer/Sequencer.hpp"
#include "export/WavExporter.hpp"
#include "plugin/PluginLoader.hpp"

enum class EngineCmdType : uint32_t {
    AddSynthTrack,
    AddAudioTrack,
    RemoveTrack,
    NoteOn,
    NoteOff,
    SetTrackVolume,
    SetTrackPan,
    SetTrackMute,
    SetTrackSolo,
    SetTrackParam,
    SetMasterVolume,
    TransportPlay,
    TransportPause,
    TransportStop,
    TransportSeek,
    SetBpm,
    SetLoopRange,
    AddClip,
    RemoveClip,
    MoveClip,
    ResizeClip
};

struct EngineMsg {
    EngineCmdType type;
    int32_t trackId;
    int32_t data1;
    float value1;
    float value2;
    int64_t int64_val1;
    int64_t int64_val2;
};

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();

    void resetProject() {
        sequencer_.getTransport().stop();
        sequencer_.clearAllClips();
        mixer_.clearAllTracks();
        EngineMsg msg;
        while (commandQueue_.pop(msg)) {}
    }

    int32_t addSynthTrack(const std::string& name);
    int32_t addAudioTrack(const std::string& name);
    int32_t addStepSequencerTrack(const std::string& name);
    void setStepSequencerStep(int32_t trackId, int32_t laneIndex, int32_t stepIndex, bool active, float velocity, int32_t pitch, float gate, float nudge, int32_t ratchets, float prob);
    void loadStepSequencerSample(int32_t trackId, int32_t laneIndex, const float* data, int32_t length, int32_t channels);
    void clearStepSequencerLane(int32_t trackId, int32_t laneIndex);
    void setStepSequencerLaneParams(int32_t trackId, int32_t laneIndex, int32_t midiNote, int32_t stepCount, int32_t stepTicks, float volume, float pan, bool mute, bool solo);

    void removeTrack(int32_t trackId);

    void noteOn(int32_t trackId, int32_t note, float velocity);
    void noteOff(int32_t trackId, int32_t note);
    void setTrackVolume(int32_t trackId, float volume);
    void setTrackPan(int32_t trackId, float pan);
    void setTrackMute(int32_t trackId, bool mute);
    void setTrackSolo(int32_t trackId, bool solo);
    void setTrackParam(int32_t trackId, uint32_t paramId, float value);

    void setTrackPhaseInvert(int32_t trackId, bool invert) {
        Track* t = mixer_.getTrack(trackId);
        if (t) t->setPhaseInvert(invert);
    }

    void setTrackFxParam(int32_t trackId, int32_t fxSlot, uint32_t paramId, float value) {
        Track* t = mixer_.getTrack(trackId);
        if (t) t->setFxParam(fxSlot, paramId, value);
    }

    // Modular Plugin Host APIs
    int32_t scanPlugins(const std::string& searchDirectory) {
        PluginLoader::getInstance().scanDirectory(searchDirectory);
        return static_cast<int32_t>(PluginLoader::getInstance().getAvailablePlugins().size());
    }

    bool setTrackSynthPlugin(int32_t trackId, const std::string& pluginId) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return false;

        auto instance = PluginLoader::getInstance().instantiatePlugin(pluginId, static_cast<float>(sampleRate_));
        if (!instance) return false;

        if (t->getType() == TrackType::Synth) {
            static_cast<SynthTrack*>(t)->setCustomInstrument(std::move(instance));
            return true;
        } else if (t->getType() == TrackType::StepSequencer) {
            static_cast<StepSequencerTrack*>(t)->setCustomInstrument(std::move(instance));
            return true;
        }
        return false;
    }

    void removeTrackSynthPlugin(int32_t trackId) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return;
        if (t->getType() == TrackType::Synth) {
            static_cast<SynthTrack*>(t)->removeCustomInstrument();
        } else if (t->getType() == TrackType::StepSequencer) {
            static_cast<StepSequencerTrack*>(t)->removeCustomInstrument();
        }
    }

    std::string getTrackSynthPluginId(int32_t trackId) {
        Track* t = mixer_.getTrack(trackId);
        if (t) {
            if (t->getType() == TrackType::Synth) {
                auto* inst = static_cast<SynthTrack*>(t)->getCustomInstrument();
                if (inst) return inst->getDescriptor().pluginId;
            } else if (t->getType() == TrackType::StepSequencer) {
                auto* inst = static_cast<StepSequencerTrack*>(t)->getCustomInstrument();
                if (inst) return inst->getDescriptor().pluginId;
            }
        }
        return "";
    }

    bool addTrackFxPlugin(int32_t trackId, int32_t slotIndex, const std::string& pluginId) {
        Track* t = mixer_.getTrack(trackId);
        if (!t || slotIndex < 0 || slotIndex >= 8) return false;

        auto instance = PluginLoader::getInstance().instantiatePlugin(pluginId, static_cast<float>(sampleRate_));
        if (!instance) return false;

        return t->getPluginChain().loadPlugin(static_cast<size_t>(slotIndex), std::move(instance));
    }

    void removeTrackFxPlugin(int32_t trackId, int32_t slotIndex) {
        Track* t = mixer_.getTrack(trackId);
        if (t && slotIndex >= 0 && slotIndex < 8) {
            t->getPluginChain().removePlugin(static_cast<size_t>(slotIndex));
        }
    }

    void setTrackFxBypass(int32_t trackId, int32_t slotIndex, bool bypass) {
        Track* t = mixer_.getTrack(trackId);
        if (t && slotIndex >= 0 && slotIndex < 8) {
            t->getPluginChain().setBypass(static_cast<size_t>(slotIndex), bypass);
        }
    }

    bool isTrackFxBypassed(int32_t trackId, int32_t slotIndex) {
        Track* t = mixer_.getTrack(trackId);
        if (t && slotIndex >= 0 && slotIndex < 8) {
            return t->getPluginChain().isBypassed(static_cast<size_t>(slotIndex));
        }
        return true;
    }

    void moveTrackFxSlot(int32_t trackId, int32_t fromSlot, int32_t toSlot) {
        Track* t = mixer_.getTrack(trackId);
        if (t && fromSlot >= 0 && fromSlot < 8 && toSlot >= 0 && toSlot < 8) {
            t->getPluginChain().moveSlot(static_cast<size_t>(fromSlot), static_cast<size_t>(toSlot));
        }
    }

    std::string getTrackFxPluginId(int32_t trackId, int32_t slotIndex) {
        Track* t = mixer_.getTrack(trackId);
        if (t && slotIndex >= 0 && slotIndex < 8) {
            auto* fx = t->getPluginChain().getPlugin(static_cast<size_t>(slotIndex));
            if (fx) return fx->getDescriptor().pluginId;
        }
        return "";
    }

    void setPluginParameter(int32_t trackId, int32_t slotIndex, uint32_t paramId, float value) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return;

        if (slotIndex == -1) {
            PluginInstance* inst = nullptr;
            if (t->getType() == TrackType::Synth) inst = static_cast<SynthTrack*>(t)->getCustomInstrument();
            else if (t->getType() == TrackType::StepSequencer) inst = static_cast<StepSequencerTrack*>(t)->getCustomInstrument();
            if (inst) inst->setParameter(paramId, value);
        } else if (slotIndex >= 0 && slotIndex < 8) {
            auto* fx = t->getPluginChain().getPlugin(static_cast<size_t>(slotIndex));
            if (fx) fx->setParameter(paramId, value);
        }
    }

    float getPluginParameter(int32_t trackId, int32_t slotIndex, uint32_t paramId) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return 0.0f;

        if (slotIndex == -1) {
            PluginInstance* inst = nullptr;
            if (t->getType() == TrackType::Synth) inst = static_cast<SynthTrack*>(t)->getCustomInstrument();
            else if (t->getType() == TrackType::StepSequencer) inst = static_cast<StepSequencerTrack*>(t)->getCustomInstrument();
            return inst ? inst->getParameter(paramId) : 0.0f;
        } else if (slotIndex >= 0 && slotIndex < 8) {
            auto* fx = t->getPluginChain().getPlugin(static_cast<size_t>(slotIndex));
            return fx ? fx->getParameter(paramId) : 0.0f;
        }
        return 0.0f;
    }

    std::string getPluginStateJson(int32_t trackId, int32_t slotIndex) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return "{}";

        if (slotIndex == -1) {
            PluginInstance* inst = nullptr;
            if (t->getType() == TrackType::Synth) inst = static_cast<SynthTrack*>(t)->getCustomInstrument();
            else if (t->getType() == TrackType::StepSequencer) inst = static_cast<StepSequencerTrack*>(t)->getCustomInstrument();
            return inst ? inst->getStateJson() : "{}";
        } else if (slotIndex >= 0 && slotIndex < 8) {
            auto* fx = t->getPluginChain().getPlugin(static_cast<size_t>(slotIndex));
            return fx ? fx->getStateJson() : "{}";
        }
        return "{}";
    }

    bool setPluginStateJson(int32_t trackId, int32_t slotIndex, const std::string& jsonState) {
        Track* t = mixer_.getTrack(trackId);
        if (!t) return false;

        if (slotIndex == -1) {
            PluginInstance* inst = nullptr;
            if (t->getType() == TrackType::Synth) inst = static_cast<SynthTrack*>(t)->getCustomInstrument();
            else if (t->getType() == TrackType::StepSequencer) inst = static_cast<StepSequencerTrack*>(t)->getCustomInstrument();
            return inst ? inst->setStateJson(jsonState) : false;
        } else if (slotIndex >= 0 && slotIndex < 8) {
            auto* fx = t->getPluginChain().getPlugin(static_cast<size_t>(slotIndex));
            return fx ? fx->setStateJson(jsonState) : false;
        }
        return false;
    }

    void setMasterVolume(float volume);
    void setMasterLimiter(bool enabled) { mixer_.setMasterLimiter(enabled); }

    float getTrackPeakL(int32_t trackId) {
        Track* t = mixer_.getTrack(trackId);
        return t ? t->getPeakL() : 0.0f;
    }

    float getTrackPeakR(int32_t trackId) {
        Track* t = mixer_.getTrack(trackId);
        return t ? t->getPeakR() : 0.0f;
    }

    float getMasterPeakL() const { return mixer_.getMasterPeakL(); }
    float getMasterPeakR() const { return mixer_.getMasterPeakR(); }

    void loadTrackSample(int32_t trackId, const float* data, int32_t length, int32_t channels);

    void setTrackTrimAndFade(int32_t trackId, float trimStart, float trimEnd, float fadeIn, float fadeOut) {
        Track* t = mixer_.getTrack(trackId);
        if (t && t->getType() == TrackType::Audio) {
            static_cast<AudioTrack*>(t)->setTrimAndFade(trimStart, trimEnd, fadeIn, fadeOut);
        }
    }

    float getTrackPlaybackPosition(int32_t trackId) {
        Track* t = mixer_.getTrack(trackId);
        if (t && t->getType() == TrackType::Audio) {
            return static_cast<AudioTrack*>(t)->getPlaybackFraction();
        }
        return 0.0f;
    }

    // Transport & Sequencer
    void transportPlayFromStart();
    void transportPlay();
    void transportPause();
    void transportStop();
    void transportSeek(int64_t tick);
    void setBpm(float bpm);
    void setLoop(int64_t startTick, int64_t endTick, bool enabled);

    int64_t getLoopStart() const { return sequencer_.getTransport().getLoopStart(); }
    int64_t getLoopEnd() const { return sequencer_.getTransport().getLoopEnd(); }
    bool isLoopEnabled() const { return sequencer_.getTransport().isLoopEnabled(); }

    int32_t addClip(int32_t trackId, int64_t startTick, int64_t lengthTicks, const std::string& name);
    void removeClip(int32_t clipId);
    void moveClip(int32_t clipId, int32_t newTrackId, int64_t newStartTick);
    void resizeClip(int32_t clipId, int64_t newLengthTicks);
    void clearClipNotes(int32_t clipId) { sequencer_.clearClipNotes(clipId); }
    void addNoteToClip(int32_t clipId, int32_t note, float vel, int64_t startOffset, int64_t len);

    // Fast Offline Audio Export
    bool exportWav(const std::string& filePath, float sampleRate, int64_t totalTicks) {
        exportCancelFlag_.store(false);
        exportProgress_.store(0.0f);
        return WavExporter::exportToWav(filePath, mixer_, sequencer_, sampleRate, totalTicks, exportProgress_, exportCancelFlag_);
    }

    void cancelExport() { exportCancelFlag_.store(true); }
    float getExportProgress() const { return exportProgress_.load(std::memory_order_relaxed); }

    int64_t getCurrentTick() const { return sequencer_.getTransport().getCurrentTick(); }
    bool isPlaying() const { return sequencer_.getTransport().isPlaying(); }
    float getBpm() const { return sequencer_.getTransport().getBpm(); }

    int32_t getSampleRate() const { return sampleRate_; }
    int32_t getFramesPerBurst() const { return framesPerBurst_; }
    bool isLowLatency() const { return isLowLatency_; }
    int32_t getTrackCount() { return mixer_.getTrackCount(); }

private:
    static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream, void* userData, void* audioData, int32_t numFrames);

    static void errorCallback(
        AAudioStream* stream, void* userData, aaudio_result_t error);

    aaudio_data_callback_result_t renderAudio(float* output, int32_t numFrames);

    AAudioStream* stream_ = nullptr;
    int32_t sampleRate_ = 48000;
    int32_t framesPerBurst_ = 192;
    bool isLowLatency_ = false;

    Mixer mixer_;
    Sequencer sequencer_;
    LockFreeQueue<EngineMsg, 2048> commandQueue_;
    std::atomic<bool> isRunning_{false};

    std::atomic<float> exportProgress_{0.0f};
    std::atomic<bool> exportCancelFlag_{false};
};
