#include "AudioEngine.hpp"
#include <android/log.h>

#define TAG "CobassEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AudioEngine::AudioEngine() = default;

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    if (isRunning_.load()) return true;

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return false;

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

    aaudio_result_t res = AAudioStreamBuilder_openStream(builder, &stream_);
    if (res != AAUDIO_OK) {
        LOGI("Falling back to SHARED mode...");
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        res = AAudioStreamBuilder_openStream(builder, &stream_);
    }
    AAudioStreamBuilder_delete(builder);

    if (res != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(res));
        return false;
    }

    sampleRate_ = AAudioStream_getSampleRate(stream_);
    framesPerBurst_ = AAudioStream_getFramesPerBurst(stream_);
    isLowLatency_ = (AAudioStream_getPerformanceMode(stream_) == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);

    mixer_.setSampleRate(static_cast<float>(sampleRate_));
    sequencer_.getTransport().setSampleRate(static_cast<float>(sampleRate_));

    res = AAudioStream_requestStart(stream_);
    if (res != AAUDIO_OK) {
        AAudioStream_close(stream_);
        stream_ = nullptr;
        return false;
    }

    isRunning_.store(true);
    LOGI("Cobass Audio Engine Started (%d Hz, LowLatency=%d)", sampleRate_, isLowLatency_);
    return true;
}

void AudioEngine::stop() {
    if (!isRunning_.load()) return;
    isRunning_.store(false);
    if (stream_) {
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
}

int32_t AudioEngine::addSynthTrack(const std::string& name) {
    return mixer_.addSynthTrack(name);
}

int32_t AudioEngine::addStepSequencerTrack(const std::string& name) {
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

int32_t AudioEngine::addAudioTrack(const std::string& name) {
    return mixer_.addAudioTrack(name);
}

void AudioEngine::removeTrack(int32_t trackId) {
    commandQueue_.push({EngineCmdType::RemoveTrack, trackId, 0, 0.0f, 0.0f, 0, 0});
}

void AudioEngine::noteOn(int32_t trackId, int32_t note, float velocity) {
    commandQueue_.push({EngineCmdType::NoteOn, trackId, note, velocity, 0.0f, 0, 0});
}

void AudioEngine::noteOff(int32_t trackId, int32_t note) {
    commandQueue_.push({EngineCmdType::NoteOff, trackId, note, 0.0f, 0.0f, 0, 0});
}

void AudioEngine::setTrackVolume(int32_t trackId, float volume) {
    commandQueue_.push({EngineCmdType::SetTrackVolume, trackId, 0, volume, 0.0f, 0, 0});
}

void AudioEngine::setTrackPan(int32_t trackId, float pan) {
    commandQueue_.push({EngineCmdType::SetTrackPan, trackId, 0, pan, 0.0f, 0, 0});
}

void AudioEngine::setTrackMute(int32_t trackId, bool mute) {
    commandQueue_.push({EngineCmdType::SetTrackMute, trackId, 0, mute ? 1.0f : 0.0f, 0.0f, 0, 0});
}

void AudioEngine::setTrackSolo(int32_t trackId, bool solo) {
    commandQueue_.push({EngineCmdType::SetTrackSolo, trackId, 0, solo ? 1.0f : 0.0f, 0.0f, 0, 0});
}

void AudioEngine::setTrackParam(int32_t trackId, uint32_t paramId, float value) {
    commandQueue_.push({EngineCmdType::SetTrackParam, trackId, static_cast<int32_t>(paramId), value, 0.0f, 0, 0});
}

void AudioEngine::setMasterVolume(float volume) {
    commandQueue_.push({EngineCmdType::SetMasterVolume, 0, 0, volume, 0.0f, 0, 0});
}

void AudioEngine::loadTrackSample(int32_t trackId, const float* data, int32_t length, int32_t channels) {
    Track* t = mixer_.getTrack(trackId);
    if (t) t->loadSampleData(data, length, channels);
}

void AudioEngine::transportPlayFromStart() {
    for (size_t i = 0; i < Mixer::MAX_TRACKS; ++i) {
        Track* t = mixer_.getTrack((int32_t)i);
        if (t) t->allNotesOff();
    }
    sequencer_.getTransport().playFromStart();
}

void AudioEngine::transportPlay() {
    sequencer_.getTransport().play();
}

void AudioEngine::transportPause() {
    sequencer_.getTransport().pause();
    for (size_t i = 0; i < Mixer::MAX_TRACKS; ++i) {
        Track* t = mixer_.getTrack((int32_t)i);
        if (t) t->allNotesOff();
    }
}

void AudioEngine::transportStop() {
    sequencer_.getTransport().stop();
    for (size_t i = 0; i < Mixer::MAX_TRACKS; ++i) {
        Track* t = mixer_.getTrack((int32_t)i);
        if (t) t->allNotesOff();
    }
}

void AudioEngine::transportSeek(int64_t tick) {
    sequencer_.getTransport().seekToTick(tick);
    for (size_t i = 0; i < Mixer::MAX_TRACKS; ++i) {
        Track* t = mixer_.getTrack((int32_t)i);
        if (t) t->allNotesOff();
    }
}

void AudioEngine::setBpm(float bpm) {
    sequencer_.getTransport().setBpm(bpm);
}

void AudioEngine::setLoop(int64_t startTick, int64_t endTick, bool enabled) {
    sequencer_.getTransport().setLoop(startTick, endTick, enabled);
}

int32_t AudioEngine::addClip(int32_t trackId, int64_t startTick, int64_t lengthTicks, const std::string& name) {
    return sequencer_.addClip(trackId, startTick, lengthTicks, name);
}

void AudioEngine::removeClip(int32_t clipId) {
    sequencer_.removeClip(clipId);
}

void AudioEngine::moveClip(int32_t clipId, int32_t newTrackId, int64_t newStartTick) {
    sequencer_.moveClip(clipId, newTrackId, newStartTick);
}

void AudioEngine::resizeClip(int32_t clipId, int64_t newLengthTicks) {
    sequencer_.resizeClip(clipId, newLengthTicks);
}

void AudioEngine::addNoteToClip(int32_t clipId, int32_t note, float vel, int64_t startOffset, int64_t len) {
    sequencer_.addNoteToClip(clipId, note, vel, startOffset, len);
}

aaudio_data_callback_result_t AudioEngine::dataCallback(
    AAudioStream* /*stream*/, void* userData, void* audioData, int32_t numFrames) {
    return static_cast<AudioEngine*>(userData)->renderAudio(static_cast<float*>(audioData), numFrames);
}

void AudioEngine::errorCallback(
    AAudioStream* /*stream*/, void* userData, aaudio_result_t error) {
    LOGE("AAudio stream error: %d", error);
    static_cast<AudioEngine*>(userData)->isRunning_.store(false);
}

aaudio_data_callback_result_t AudioEngine::renderAudio(float* output, int32_t numFrames) {
    EngineMsg msg;
    while (commandQueue_.pop(msg)) {
        switch (msg.type) {
            case EngineCmdType::RemoveTrack:
                mixer_.removeTrack(msg.trackId);
                break;
            case EngineCmdType::NoteOn: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->noteOn(msg.data1, msg.value1);
                break;
            }
            case EngineCmdType::NoteOff: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->noteOff(msg.data1);
                break;
            }
            case EngineCmdType::SetTrackVolume: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->setVolume(msg.value1);
                break;
            }
            case EngineCmdType::SetTrackPan: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->setPan(msg.value1);
                break;
            }
            case EngineCmdType::SetTrackMute: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->setMute(msg.value1 > 0.5f);
                break;
            }
            case EngineCmdType::SetTrackSolo: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->setSolo(msg.value1 > 0.5f);
                break;
            }
            case EngineCmdType::SetTrackParam: {
                Track* t = mixer_.getTrack(msg.trackId);
                if (t) t->setParam(static_cast<uint32_t>(msg.data1), msg.value1);
                break;
            }
            case EngineCmdType::SetMasterVolume:
                mixer_.setMasterVolume(msg.value1);
                break;
            default: break;
        }
    }

    // 1. Advance sequencer & dispatch note events
    sequencer_.processAudioBlock(mixer_, numFrames);

    // 2. Render mix
    mixer_.renderMix(output, numFrames);

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
